package dev.malorem.coordsmod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Records both ends of a nether portal the moment you step through one. Portal
 * pairs are exactly the thing people lose track of, and the mod already shows
 * the 8:1 conversion, so recording the real pair completes that feature.
 *
 * Deliberately limited to Overworld/Nether: an End trip or a respawn is a
 * dimension change too, and auto-saving those would just be noise.
 */
public final class PortalTracker {
	private static String lastDimension;
	private static BlockPos lastPos;

	private PortalTracker() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) {
				lastDimension = null;
				lastPos = null;
				return;
			}

			String dimensionId = client.level.dimension().identifier().toString();
			BlockPos pos = client.player.blockPosition();

			if (lastDimension != null
					&& !lastDimension.equals(dimensionId)
					&& isPortalPair(lastDimension, dimensionId)
					&& lastPos != null
					&& !DeathTracker.recentlyDied()) {
				savePair(client, lastDimension, lastPos, dimensionId, pos);
			}

			lastDimension = dimensionId;
			lastPos = pos;
		});
	}

	private static boolean isPortalPair(String from, String to) {
		return (Dimensions.OVERWORLD.equals(from) && Dimensions.NETHER.equals(to))
				|| (Dimensions.NETHER.equals(from) && Dimensions.OVERWORLD.equals(to));
	}

	private static void savePair(Minecraft client, String fromDimension, BlockPos fromPos,
			String toDimension, BlockPos toPos) {
		long now = System.currentTimeMillis();

		String fromName = CoordStore.autoName(fromDimension, "portal");
		CoordStore.put(fromDimension, new Waypoint(fromName, fromPos.getX(), fromPos.getY(), fromPos.getZ(), now));

		String toName = CoordStore.autoName(toDimension, "portal");
		Waypoint arrival = new Waypoint(toName, toPos.getX(), toPos.getY(), toPos.getZ(), now);
		CoordStore.put(toDimension, arrival);

		if (client.player == null) {
			return;
		}

		client.player.sendSystemMessage(Chat.prefix()
				.append(Component.literal("Portal saved - ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(fromName).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" in " + Dimensions.displayName(fromDimension))
						.withStyle(Dimensions.color(fromDimension)))
				.append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(toName).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" in " + Dimensions.displayName(toDimension))
						.withStyle(Dimensions.color(toDimension))));
	}
}
