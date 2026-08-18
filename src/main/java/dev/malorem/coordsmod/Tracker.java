package dev.malorem.coordsmod;

import org.joml.Matrix3x2fStack;

import net.minecraft.ChatFormatting;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * A single pinned waypoint drawn as a needle plus a label, so you can walk back
 * to something without re-running /cl every thirty seconds.
 *
 * The needle points where you actually have to walk - it is rotated by the
 * bearing relative to the way you are facing, not by an absolute compass angle,
 * and it eases toward each new angle instead of snapping between headings.
 *
 * This is the only thing the mod draws. Nothing shows until you pin something,
 * so chat-only is still the default.
 */
public final class Tracker {
	private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("coordsmod", "tracker");

	/** Per-frame easing. Low enough to look smooth, high enough to keep up with a spin. */
	private static final float EASING = 0.35f;

	private static final int ARROW_COLOR = 0xFFFFDD55;
	private static final int CHECK_COLOR = 0xFF55FF55;
	private static final int LABEL_COLOR = 0xFFFFFFFF;
	private static final int BACKDROP = 0x66000000;

	/** Close enough to be standing on it, at which point a bearing is just noise. */
	private static final double ARRIVED_RADIUS = 1.0;

	/** Pixel run for a tick mark: a short down-right stroke into a longer up-right one. */
	private static final int[][] CHECK_PIXELS = {
			{ -6, -2 }, { -5, -1 }, { -4, 0 }, { -3, 1 }, { -2, 2 },
			{ -1, 1 }, { 0, 0 }, { 1, -1 }, { 2, -2 }, { 3, -3 }, { 4, -4 }
	};

	private static final int TOP = 8;
	private static final int ARROW_CENTER_Y = TOP + 7;
	private static final int LABEL_Y = TOP + 18;

	private static String dimension;
	private static Waypoint waypoint;

	private static float needleAngle;
	private static boolean snapNeedle;

	private Tracker() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, HUD_ID, (extractor, delta) -> draw(extractor));
	}

	public static void track(String dimensionId, Waypoint target) {
		dimension = dimensionId;
		waypoint = target;
		// Jump straight to the new bearing rather than sweeping round to it.
		snapNeedle = true;
	}

	public static Waypoint tracked() {
		return waypoint;
	}

	public static boolean clear() {
		boolean had = waypoint != null;
		dimension = null;
		waypoint = null;
		return had;
	}

	private static void draw(GuiGraphicsExtractor extractor) {
		Waypoint target = waypoint;

		if (target == null) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;

		// No hide-GUI check needed: the whole HUD stack is skipped when it is hidden.
		if (player == null || client.level == null) {
			return;
		}

		// A bearing into another dimension is meaningless, so hide rather than mislead.
		if (!client.level.dimension().identifier().toString().equals(dimension)) {
			return;
		}

		Vec3 eye = player.position();
		double distance = Geo.horizontalDistance(eye, target);
		boolean arrived = distance <= ARRIVED_RADIUS;

		Font font = client.font;
		Component label = Component.literal(target.name).withStyle(ChatFormatting.WHITE)
				.append(Component.literal(arrived ? "  here" : "  " + Geo.formatDistance(distance))
						.withStyle(arrived ? ChatFormatting.GREEN : ChatFormatting.GRAY));

		int centerX = extractor.guiWidth() / 2;
		int halfWidth = Math.max(font.width(label) / 2, 10);

		extractor.fill(centerX - halfWidth - 6, TOP - 6, centerX + halfWidth + 6, LABEL_Y + 11, BACKDROP);

		if (arrived) {
			drawCheck(extractor, centerX, ARROW_CENTER_Y);
			// Re-aim on the way out, so leaving does not sweep from a stale angle.
			snapNeedle = true;
		} else {
			float wanted = Geo.relativeBearing(eye, player.getYRot(), target);

			if (snapNeedle) {
				needleAngle = wanted;
				snapNeedle = false;
			} else {
				needleAngle = Geo.approachAngle(needleAngle, wanted, EASING);
			}

			// The needle is centred on the screen and the label sits underneath it.
			Matrix3x2fStack pose = extractor.pose();
			pose.pushMatrix();
			pose.translate(centerX, ARROW_CENTER_Y);
			pose.rotate((float) Math.toRadians(needleAngle));
			drawNeedle(extractor);
			pose.popMatrix();
		}

		extractor.centeredText(font, label, centerX, LABEL_Y, LABEL_COLOR);
	}

	/** Drawn unrotated: you have arrived, so there is no direction left to point in. */
	private static void drawCheck(GuiGraphicsExtractor extractor, int centerX, int centerY) {
		for (int[] pixel : CHECK_PIXELS) {
			int x = centerX + pixel[0];
			int y = centerY + pixel[1];
			extractor.fill(x, y, x + 2, y + 2, CHECK_COLOR);
		}
	}

	/**
	 * Solid rectangles rather than a rotated glyph. Rotating text resamples the
	 * font atlas and comes out blurry; flat-coloured geometry has nothing to
	 * sample, so the edges stay crisp at any angle.
	 *
	 * Drawn pointing straight up, which is why no angular offset is needed.
	 */
	private static void drawNeedle(GuiGraphicsExtractor extractor) {
		for (int i = 0; i < 6; i++) {
			int halfWidth = Math.max(1, i);
			extractor.fill(-halfWidth, -6 + i, halfWidth, -5 + i, ARROW_COLOR);
		}

		extractor.fill(-2, 0, 2, 6, ARROW_COLOR);
	}
}
