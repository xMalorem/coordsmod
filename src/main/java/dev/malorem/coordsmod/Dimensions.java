package dev.malorem.coordsmod;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.ChatFormatting;

/** Dimension ids, the command aliases that map onto them, and how they are shown in chat. */
public final class Dimensions {
	public static final String OVERWORLD = "minecraft:overworld";
	public static final String NETHER = "minecraft:the_nether";
	public static final String END = "minecraft:the_end";

	/** /cl subcommand alias -> dimension id. Insertion order is the registration order. */
	public static final Map<String, String> ALIASES = new LinkedHashMap<>();

	static {
		ALIASES.put("ow", OVERWORLD);
		ALIASES.put("overworld", OVERWORLD);
		ALIASES.put("nether", NETHER);
		ALIASES.put("n", NETHER);
		ALIASES.put("end", END);
		ALIASES.put("e", END);
	}

	public static String displayName(String id) {
		return switch (id) {
			case OVERWORLD -> "Overworld";
			case NETHER -> "Nether";
			case END -> "End";
			default -> id;
		};
	}

	public static ChatFormatting color(String id) {
		return switch (id) {
			case OVERWORLD -> ChatFormatting.GREEN;
			case NETHER -> ChatFormatting.RED;
			case END -> ChatFormatting.LIGHT_PURPLE;
			default -> ChatFormatting.AQUA;
		};
	}

	/** Short prefix used when auto-naming a waypoint, e.g. "ow-1". */
	public static String shortTag(String id) {
		return switch (id) {
			case OVERWORLD -> "ow";
			case NETHER -> "nether";
			case END -> "end";
			default -> "dim";
		};
	}

	private Dimensions() {
	}
}
