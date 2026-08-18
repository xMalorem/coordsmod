package dev.malorem.coordsmod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;

/** Everything this mod shows the player is a chat component built here. */
public final class Chat {
	private static final String ARROW = "→";

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
				.append(Component.literal(" (" + count + ")").withStyle(ChatFormatting.GRAY));
	}

	/**
	 * One list entry. {@code viewer} is the player position when the listed
	 * dimension is the one they are standing in, otherwise null - a distance
	 * across dimensions would be meaningless.
	 */
	public static MutableComponent entry(int index, Waypoint waypoint, String dimensionId, Vec3 viewer) {
		MutableComponent line = Component.literal(" " + index + ". ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(waypoint.name).withStyle(ChatFormatting.WHITE))
				.append(Component.literal("  " + coords(waypoint)).withStyle(ChatFormatting.YELLOW));

		if (viewer != null) {
			line = line.append(Component.literal("  " + Geo.relative(viewer, waypoint))
					.withStyle(ChatFormatting.AQUA));
		}

		String converted = conversion(dimensionId, waypoint);

		if (converted != null) {
			line = line.append(Component.literal("  " + converted).withStyle(ChatFormatting.GRAY));
		}

		// Worth saying here too, or a hidden label looks like a broken one.
		if (waypoint.hidden) {
			line = line.append(Component.literal("  (label hidden)").withStyle(ChatFormatting.DARK_GRAY));
		}

		String copy = waypoint.x + " " + waypoint.y + " " + waypoint.z;
		StringBuilder tooltip = new StringBuilder("Click to copy  " + copy);

		if (converted != null) {
			tooltip.append('\n').append(conversionTooltip(dimensionId, waypoint));
		}

		// Applied to the root so every appended part inherits the click and hover.
		return line.withStyle(style -> style
				.withClickEvent(new ClickEvent.CopyToClipboard(copy))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(tooltip.toString()))));
	}

	public static MutableComponent more(int remaining) {
		return Component.literal("  ... and " + remaining + " more").withStyle(ChatFormatting.GRAY);
	}

	/** Confirmation shown by /cs. */
	public static MutableComponent saved(boolean replaced, Waypoint waypoint, String dimensionId) {
		return prefix()
				.append(Component.literal(replaced ? "Replaced " : "Saved ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(waypoint.name).withStyle(ChatFormatting.WHITE))
				.append(Component.literal("  " + coords(waypoint)).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(" in ").withStyle(ChatFormatting.GRAY))
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
						.withStyle(ChatFormatting.GRAY));
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

	/** Short inline portal-ratio hint, or null where the ratio does not apply. */
	private static String conversion(String dimensionId, Waypoint waypoint) {
		if (Dimensions.OVERWORLD.equals(dimensionId)) {
			return ARROW + "N " + Geo.toNether(waypoint);
		}

		if (Dimensions.NETHER.equals(dimensionId)) {
			return ARROW + "OW " + Geo.toOverworld(waypoint);
		}

		return null;
	}

	private static String conversionTooltip(String dimensionId, Waypoint waypoint) {
		if (Dimensions.OVERWORLD.equals(dimensionId)) {
			return "Nether portal: " + Geo.toNether(waypoint);
		}

		return "Overworld portal: " + Geo.toOverworld(waypoint);
	}

	private Chat() {
	}
}
