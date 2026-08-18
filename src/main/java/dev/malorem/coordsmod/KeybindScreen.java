package dev.malorem.coordsmod;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Two rows, click one and press a key. Reached from the chat prompt shown when
 * nothing is bound, or from /ckeybind.
 */
public class KeybindScreen extends Screen {
	private static final InputConstants.Key ESCAPE = InputConstants.getKey("key.keyboard.escape");

	private final Screen parent;
	private Button quickSaveButton;
	private Button openListButton;
	private KeyMapping listeningFor;

	public KeybindScreen(Screen parent) {
		super(Component.literal("CoordsMod controls"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x = width / 2 - 110;
		int y = height / 2 - 50;

		quickSaveButton = addRenderableWidget(
				Button.builder(label("Quick-save coord", Keybinds.QUICK_SAVE), b -> listen(Keybinds.QUICK_SAVE))
						.bounds(x, y, 220, 20).build());

		openListButton = addRenderableWidget(
				Button.builder(label("Open coord list", Keybinds.OPEN_LIST), b -> listen(Keybinds.OPEN_LIST))
						.bounds(x, y + 24, 220, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Escape clears a binding"), b -> {
		}).bounds(x, y + 52, 220, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(x, y + 80, 220, 20).build());
	}

	private void listen(KeyMapping mapping) {
		listeningFor = mapping;
		refresh();
	}

	private Component label(String text, KeyMapping mapping) {
		if (listeningFor == mapping) {
			return Component.literal(text + ":  > press a key <");
		}

		if (!Keybinds.isBound(mapping)) {
			return Component.literal(text + ":  not bound");
		}

		return Component.literal(text + ":  " + Keybinds.describe(mapping).getString());
	}

	private void refresh() {
		quickSaveButton.setMessage(label("Quick-save coord", Keybinds.QUICK_SAVE));
		openListButton.setMessage(label("Open coord list", Keybinds.OPEN_LIST));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listeningFor == null) {
			return super.keyPressed(event);
		}

		InputConstants.Key key = InputConstants.getKey(event);
		// Escape clears the binding rather than binding Escape itself.
		listeningFor.setKey(key.equals(ESCAPE) ? InputConstants.UNKNOWN : key);
		KeyMapping.resetMapping();

		if (minecraft != null) {
			minecraft.options.save();
		}

		listeningFor = null;
		refresh();
		return true;
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(parent);
		}
	}
}
