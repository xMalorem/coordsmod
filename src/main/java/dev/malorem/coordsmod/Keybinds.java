package dev.malorem.coordsmod;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/**
 * Saving a coord through /cs means stopping and typing. A single key is the
 * whole point, so both mappings ship <b>unbound</b> and the player is offered a
 * binding screen the first time they join a world without one.
 */
public final class Keybinds {
	private static final int UNBOUND = InputConstants.UNKNOWN.getValue();

	public static final KeyMapping QUICK_SAVE = new KeyMapping(
			"key.coordsmod.quicksave", InputConstants.Type.KEYSYM, UNBOUND, KeyMapping.Category.MISC);

	public static final KeyMapping OPEN_LIST = new KeyMapping(
			"key.coordsmod.openlist", InputConstants.Type.KEYSYM, UNBOUND, KeyMapping.Category.MISC);

	/** Master hide: pulls every drawn element off the screen in one keypress. */
	public static final KeyMapping TOGGLE_HIDE = new KeyMapping(
			"key.coordsmod.hide", InputConstants.Type.KEYSYM, UNBOUND, KeyMapping.Category.MISC);

	public static final KeyMapping UNTRACK = new KeyMapping(
			"key.coordsmod.untrack", InputConstants.Type.KEYSYM, UNBOUND, KeyMapping.Category.MISC);

	/** Only nag once per session, however many worlds get joined. */
	private static boolean prompted;

	private Keybinds() {
	}

	public static void register() {
		KeyMappingHelper.registerKeyMapping(QUICK_SAVE);
		KeyMappingHelper.registerKeyMapping(OPEN_LIST);
		KeyMappingHelper.registerKeyMapping(TOGGLE_HIDE);
		KeyMappingHelper.registerKeyMapping(UNTRACK);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (QUICK_SAVE.consumeClick()) {
				quickSave(client);
			}

			while (OPEN_LIST.consumeClick()) {
				if (client.player != null) {
					client.setScreenAndShow(new WaypointScreen(null));
				}
			}

			while (TOGGLE_HIDE.consumeClick()) {
				toggleHidden();
			}

			while (UNTRACK.consumeClick()) {
				Tracker.clear();
			}
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (prompted || isBound(QUICK_SAVE)) {
				return;
			}

			prompted = true;
			// Deferred: at join time the chat overlay is not ready to be written to.
			client.execute(Keybinds::promptToBind);
		});
	}

	public static boolean isBound(KeyMapping mapping) {
		return KeyMappingHelper.getBoundKeyOf(mapping).getValue() != UNBOUND;
	}

	public static Component describe(KeyMapping mapping) {
		return KeyMappingHelper.getBoundKeyOf(mapping).getDisplayName();
	}

	private static void promptToBind() {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null) {
			return;
		}

		client.player.sendSystemMessage(Chat.prefix()
				.append(Component.literal("No quick-save key is bound yet. ")
						.withStyle(ChatFormatting.GRAY))
				.append(Component.literal("[Bind a key]").withStyle(style -> style
						.withColor(ChatFormatting.GREEN)
						.withBold(true)
						.withClickEvent(new ClickEvent.RunCommand("/ckeybind"))
						.withHoverEvent(new HoverEvent.ShowText(
								Component.literal("Opens the CoordsMod key binding screen"))))));
	}

	/**
	 * Flips the master hide. Deliberately silent - things appearing or vanishing
	 * is the feedback, and a chat line every toggle is just noise.
	 */
	public static boolean toggleHidden() {
		Config config = Config.get();
		config.hidden = !config.hidden;
		Config.save();
		return config.hidden;
	}

	private static void quickSave(Minecraft client) {
		LocalPlayer player = client.player;

		if (player == null || client.level == null) {
			return;
		}

		String dimensionId = client.level.dimension().identifier().toString();
		BlockPos pos = player.blockPosition();
		String name = CoordStore.autoName(dimensionId);

		Waypoint waypoint = new Waypoint(name, pos.getX(), pos.getY(), pos.getZ(), System.currentTimeMillis());
		CoordStore.put(dimensionId, waypoint);
		player.sendSystemMessage(Chat.saved(false, waypoint, dimensionId));
	}
}
