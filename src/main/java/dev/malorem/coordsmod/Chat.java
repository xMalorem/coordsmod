package dev.malorem.coordsmod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/** Everything this mod shows the player is a chat component built here. */
public final class Chat {
	public static MutableComponent prefix() {
		return Component.literal("[Coords] ").withStyle(ChatFormatting.DARK_AQUA);
	}

	public static MutableComponent info(String text) {
		return prefix().append(Component.literal(text).withStyle(ChatFormatting.GRAY));
	}

	public static MutableComponent error(String text) {
		return prefix().append(Component.literal(text).withStyle(ChatFormatting.RED));
	}

	/** A header such as "Overworld (3)". */
	public static MutableComponent header(String dimensionId, int count) {
		return prefix()
				.append(Component.literal(Dimensions.displayName(dimensionId))
						.withStyle(Dimensions.color(dimensionId), ChatFormatting.BOLD))
				.append(Component.literal(" (" + count + ")").withStyle(ChatFormatting.DARK_GRAY));
	}

	/** A single list entry such as " 1. base  120, 64, -305". */
	public static MutableComponent entry(int index, Waypoint waypoint) {
		return Component.literal(" " + index + ". ").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal(waypoint.name).withStyle(ChatFormatting.WHITE))
				.append(Component.literal("  " + coords(waypoint)).withStyle(ChatFormatting.YELLOW));
	}

	/** Confirmation shown by /cs. */
	public static MutableComponent saved(boolean replaced, Waypoint waypoint, String dimensionId) {
		return prefix()
				.append(Component.literal(replaced ? "Replaced " : "Saved ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(waypoint.name).withStyle(ChatFormatting.WHITE))
				.append(Component.literal("  " + coords(waypoint)).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(" in ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(Dimensions.displayName(dimensionId))
						.withStyle(Dimensions.color(dimensionId)));
	}

	public static String coords(Waypoint waypoint) {
		return waypoint.x + ", " + waypoint.y + ", " + waypoint.z;
	}

	public static MutableComponent helpHeader() {
		return prefix()
				.append(Component.literal("CoordsMod").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
				.append(Component.literal(" - everything lives in chat, nothing is rendered.")
						.withStyle(ChatFormatting.DARK_GRAY));
	}

	/** One /chelp row. The usage text is clickable and drops the command into the chat box. */
	public static MutableComponent helpLine(String usage, String description) {
		String command = usage.split(" ")[0];

		return Component.literal(" ")
				.append(Component.literal(usage).withStyle(style -> style
						.withColor(ChatFormatting.YELLOW)
						.withClickEvent(new ClickEvent.SuggestCommand(command))
						.withHoverEvent(new HoverEvent.ShowText(
								Component.literal("Click to put " + command + " in the chat box")))))
				.append(Component.literal("  " + description).withStyle(ChatFormatting.GRAY));
	}

	private Chat() {
	}
}
