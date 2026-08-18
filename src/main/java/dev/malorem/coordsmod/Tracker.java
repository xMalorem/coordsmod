package dev.malorem.coordsmod;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A single pinned waypoint drawn as one HUD line, so you can walk back to
 * something without re-running /cl every thirty seconds.
 *
 * This is the one part of the mod that draws to the screen. Nothing shows until
 * you actually pin something with /ctrack, so the chat-only behaviour is still
 * what you get by default.
 */
public final class Tracker {
	private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("coordsmod", "tracker");
	private static final int COLOR = 0xFFFFDD55;

	private static String dimension;
	private static Waypoint waypoint;

	private Tracker() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, HUD_ID, (extractor, delta) -> draw(extractor));
	}

	public static void track(String dimensionId, Waypoint target) {
		dimension = dimensionId;
		waypoint = target;
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

		// No hide-GUI check needed: the whole HUD stack is skipped when it is hidden.
		if (client.player == null || client.level == null) {
			return;
		}

		// Bearings across dimensions are meaningless, so hide rather than mislead.
		if (!client.level.dimension().identifier().toString().equals(dimension)) {
			return;
		}

		String text = "→ " + target.name + "  " + Geo.relative(client.player.position(), target);
		extractor.text(client.font, Component.literal(text), 4, 4, COLOR);
	}
}
