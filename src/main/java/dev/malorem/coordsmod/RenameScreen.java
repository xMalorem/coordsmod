package dev.malorem.coordsmod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Renames one waypoint in place, keeping its position. */
public class RenameScreen extends Screen {
	private static final int WIDTH = 220;

	private final Screen parent;
	private final String dimensionId;
	private final Waypoint waypoint;
	private final String original;

	private EditBox nameBox;
	private Button saveButton;

	public RenameScreen(Screen parent, String dimensionId, Waypoint waypoint) {
		super(Component.literal("Rename waypoint"));
		this.parent = parent;
		this.dimensionId = dimensionId;
		this.waypoint = waypoint;
		this.original = waypoint.name;
	}

	@Override
	protected void init() {
		int x = width / 2 - WIDTH / 2;
		int y = height / 2 - 20;

		nameBox = new EditBox(font, x, y, WIDTH, 20, Component.literal("Name"));
		nameBox.setMaxLength(32);
		nameBox.setValue(original);
		nameBox.setResponder(value -> refresh());
		addRenderableWidget(nameBox);
		setInitialFocus(nameBox);

		saveButton = addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
				.bounds(x, y + 28, 106, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
				.bounds(x + 114, y + 28, 106, 20).build());

		refresh();
	}

	private void refresh() {
		// The store strips characters that would break the share format and the
		// [+Add] command, so an entry that cleans up to nothing is not saveable.
		saveButton.active = !CoordStore.sanitizeName(nameBox.getValue()).isEmpty();
	}

	private void save() {
		String wanted = CoordStore.sanitizeName(nameBox.getValue());

		if (wanted.isEmpty()) {
			return;
		}

		if (!wanted.equals(original)) {
			CoordStore.rename(dimensionId, original, wanted);
		}

		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(extractor, mouseX, mouseY, partialTick);
		extractor.centeredText(font, Component.literal("Rename \"" + original + "\""),
				width / 2, height / 2 - 40, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(parent);
		}
	}
}
