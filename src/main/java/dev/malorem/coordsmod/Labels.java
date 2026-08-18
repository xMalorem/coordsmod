package dev.malorem.coordsmod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.joml.Matrix3x2fStack;
import org.joml.Vector3fc;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Waypoint names floating at their real positions in the world.
 *
 * No mixins and no world-render hook: 26.2 exposes
 * {@link net.minecraft.client.renderer.GameRenderer#projectPointToScreen(Vec3)}
 * publicly, so each waypoint is projected to a screen position and drawn as
 * ordinary HUD text. That keeps the text crisp and always legible through
 * terrain, which is what you want from a waypoint marker.
 */
public final class Labels {
	private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("coordsmod", "labels");

	private static final int NAME_COLOR = 0xFFFFFFFF;
	private static final int BACKDROP = 0x66000000;
	private static final int DOT_COLOR = 0xFFFFDD55;

	/** Points nearer than this to the camera plane are skipped along with everything behind it. */
	private static final double MIN_DEPTH = 0.5;

	/** Keeps labels that are only just off-screen from popping at the very edge. */
	private static final int MARGIN = 8;

	private Labels() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, HUD_ID, (extractor, delta) -> draw(extractor));
	}

	private static void draw(GuiGraphicsExtractor extractor) {
		Config config = Config.get();

		if (config.hidden || !config.labels) {
			return;
		}

		Minecraft client = Minecraft.getInstance();

		if (client.player == null || client.level == null || client.gameRenderer == null) {
			return;
		}

		String dimensionId = client.level.dimension().identifier().toString();
		List<Waypoint> waypoints = CoordStore.list(dimensionId);

		if (waypoints.isEmpty()) {
			return;
		}

		Camera camera = client.gameRenderer.mainCamera();

		if (camera == null) {
			return;
		}

		Vec3 cameraPos = camera.position();
		Vector3fc forward = camera.forwardVector();
		double maxDistance = config.labelMaxDistance;

		List<Placed> placed = new ArrayList<>();

		for (Waypoint waypoint : waypoints) {
			if (waypoint.hidden) {
				continue;
			}

			// Aim at the middle of the block, a little above it, so the label floats clear.
			Vec3 world = new Vec3(waypoint.x + 0.5, waypoint.y + 1.4, waypoint.z + 0.5);
			Vec3 offset = world.subtract(cameraPos);

			// projectPointToScreen divides through by w, which mirrors anything behind
			// the camera onto the screen. Cull those before projecting.
			double depth = offset.x * forward.x() + offset.y * forward.y() + offset.z * forward.z();

			if (depth < MIN_DEPTH) {
				continue;
			}

			double distance = offset.length();

			if (distance > maxDistance) {
				continue;
			}

			Vec3 ndc = client.gameRenderer.projectPointToScreen(world);
			double screenX = (ndc.x + 1.0) * 0.5 * extractor.guiWidth();
			double screenY = (1.0 - ndc.y) * 0.5 * extractor.guiHeight();

			if (screenX < -MARGIN || screenX > extractor.guiWidth() + MARGIN
					|| screenY < -MARGIN || screenY > extractor.guiHeight() + MARGIN) {
				continue;
			}

			placed.add(new Placed(waypoint, screenX, screenY, distance));
		}

		// Farthest first, so nearer labels end up drawn over the top of them.
		placed.sort(Comparator.comparingDouble((Placed p) -> p.distance).reversed());

		Font font = client.font;
		double guiScale = Math.max(1, client.getWindow().getGuiScale());
		Matrix3x2fStack pose = extractor.pose();

		for (Placed entry : placed) {
			Component label = Component.literal(entry.waypoint.name).withStyle(ChatFormatting.WHITE)
					.append(Component.literal("  " + Geo.formatDistance(entry.distance))
							.withStyle(ChatFormatting.GRAY));

			int halfWidth = font.width(label) / 2;
			int textY = -font.lineHeight - 3;

			// Positioned by the matrix rather than by rounded coordinates. Rounding to
			// whole GUI pixels makes a label jump a full GUI pixel at a time, which at
			// GUI scale 3 is a three-screen-pixel hop every step - the jitter you see
			// while walking. Snapping to whole *screen* pixels instead keeps the glyphs
			// aligned to the display grid, so the text stays sharp but moves smoothly.
			pose.pushMatrix();
			pose.translate((float) snap(entry.screenX, guiScale), (float) snap(entry.screenY, guiScale));

			extractor.fill(-halfWidth - 2, textY - 2, halfWidth + 2, textY + font.lineHeight, BACKDROP);
			extractor.centeredText(font, label, 0, textY, NAME_COLOR);

			// A small mark at the projected point itself, so the label has an anchor.
			extractor.fill(-1, -1, 1, 1, DOT_COLOR);

			pose.popMatrix();
		}
	}

	/** Rounds a GUI-space coordinate to the nearest whole physical pixel. */
	private static double snap(double value, double guiScale) {
		return Math.round(value * guiScale) / guiScale;
	}

	private record Placed(Waypoint waypoint, double screenX, double screenY, double distance) {
	}
}
