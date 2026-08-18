package dev.malorem.coordsmod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Saves where you died, because /cs is useless at the one moment you most want
 * it - you are staring at the death screen and about to respawn somewhere else.
 *
 * There is no client death event in Fabric API, so this watches for the
 * alive-to-dead edge on the client tick instead.
 */
public final class DeathTracker {
	/** Ticks after a death during which a dimension change is a respawn, not a portal. */
	private static final int DEATH_COOLDOWN_TICKS = 100;

	private static boolean wasAlive = true;
	private static int deathCooldown;

	private DeathTracker() {
	}

	/** True shortly after dying, so PortalTracker can ignore respawn teleports. */
	public static boolean recentlyDied() {
		return deathCooldown > 0;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (deathCooldown > 0) {
				deathCooldown--;
			}

			LocalPlayer player = client.player;

			if (player == null || client.level == null) {
				// Between worlds; assume alive so rejoining does not look like a death.
				wasAlive = true;
				return;
			}

			boolean alive = !player.isDeadOrDying();

			if (wasAlive && !alive) {
				save(client, player);
			}

			wasAlive = alive;
		});
	}

	private static void save(Minecraft client, LocalPlayer player) {
		String dimensionId = client.level.dimension().identifier().toString();
		BlockPos pos = player.blockPosition();
		String name = CoordStore.autoName(dimensionId, "death");

		Waypoint waypoint = new Waypoint(name, pos.getX(), pos.getY(), pos.getZ(), System.currentTimeMillis());
		CoordStore.put(dimensionId, waypoint);

		deathCooldown = DEATH_COOLDOWN_TICKS;
		// Pin it straight away - walking back to your stuff is the whole point.
		Tracker.track(dimensionId, waypoint);

		player.sendSystemMessage(Chat.prefix()
				.append(Component.literal("Died at ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(Chat.coords(waypoint)).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(" - saved as ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(name).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" and now tracked").withStyle(ChatFormatting.GRAY)));
	}
}
