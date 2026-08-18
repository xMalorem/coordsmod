package dev.malorem.coordsmod;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * Sharing rides on ordinary chat, so it works on vanilla servers: the sender
 * transmits a marked plain-text line, and any receiver who also has this mod
 * swallows that line and re-prints it with a clickable [+Add] button.
 *
 * Players without the mod simply see the readable text version.
 */
public final class ShareHandler {
	/** Wire format: {@code [CM] name | x y z | dimension}. */
	private static final Pattern SHARE_PATTERN = Pattern
			.compile("\\[CM]\\s+(.+?)\\s+\\|\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\s+\\|\\s+(\\S+)");

	/**
	 * The server command used by /sc to whisper. Change to "w" or "tell" if your
	 * server does not have /msg.
	 */
	private static final String WHISPER_COMMAND = "msg";

	/** Guards against re-entering the receive events while we print our replacement. */
	private static boolean printing;

	private ShareHandler() {
	}

	public static void register() {
		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, playerChatMessage, sender, boundChatType, timeStamp) -> {
			String senderName = sender != null ? sender.name() : null;
			return !intercept(message.getString(), senderName);
		});

		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			if (overlay) {
				return true;
			}

			return !intercept(message.getString(), null);
		});
	}

	// ------------------------------------------------------------------ sending

	public static String payload(String name, int x, int y, int z, String dimensionId) {
		return "[CM] " + name + " | " + x + " " + y + " " + z + " | " + dimensionId;
	}

	/** Whispers a waypoint to one player. */
	public static void shareWith(String player, String line) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();

		if (connection != null) {
			connection.sendCommand(WHISPER_COMMAND + " " + player + " " + line);
		}
	}

	/** Sends a waypoint to public chat. */
	public static void shareAll(String line) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();

		if (connection != null) {
			connection.sendChat(line);
		}
	}

	public static List<String> onlinePlayerNames() {
		List<String> names = new ArrayList<>();
		ClientPacketListener connection = Minecraft.getInstance().getConnection();

		if (connection != null) {
			for (PlayerInfo info : connection.getOnlinePlayers()) {
				names.add(info.getProfile().name());
			}
		}

		return names;
	}

	// ---------------------------------------------------------------- receiving

	/** @return true when the message was a share and we have re-printed it ourselves. */
	private static boolean intercept(String raw, String senderName) {
		if (printing) {
			return false;
		}

		Matcher matcher = SHARE_PATTERN.matcher(raw);

		if (!matcher.find()) {
			return false;
		}

		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player == null) {
			return false;
		}

		String name = matcher.group(1).trim();
		int x;
		int y;
		int z;

		try {
			x = Integer.parseInt(matcher.group(2));
			y = Integer.parseInt(matcher.group(3));
			z = Integer.parseInt(matcher.group(4));
		} catch (NumberFormatException e) {
			return false;
		}

		String dimensionId = matcher.group(5);
		String from = senderName;

		if (from == null || from.isBlank()) {
			String before = raw.substring(0, matcher.start()).trim();
			from = before.isEmpty() ? "Someone" : before;
		}

		printing = true;

		try {
			minecraft.player.sendSystemMessage(render(from, name, x, y, z, dimensionId));
		} finally {
			printing = false;
		}

		return true;
	}

	private static MutableComponent render(String from, String name, int x, int y, int z, String dimensionId) {
		// Name is quoted (it may contain spaces) and the dimension id trails, because
		// ids contain a ':' that only a greedy argument will accept.
		String command = "/cadd " + x + " " + y + " " + z
				+ " \"" + name.replace("\\", "").replace("\"", "") + "\" " + dimensionId;

		MutableComponent line = Chat.prefix()
				.append(Component.literal(from).withStyle(ChatFormatting.AQUA))
				.append(Component.literal(" shared ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(name).withStyle(ChatFormatting.WHITE))
				.append(Component.literal("  " + x + ", " + y + ", " + z).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(Dimensions.displayName(dimensionId))
						.withStyle(Dimensions.color(dimensionId)))
				.append(Component.literal(")").withStyle(ChatFormatting.GRAY));

		Component add = Component.literal(" [+Add]").withStyle(style -> style
				.withColor(ChatFormatting.GREEN)
				.withBold(true)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(
						Component.literal("Click to save this coordinate"))));

		return line.append(add);
	}
}
