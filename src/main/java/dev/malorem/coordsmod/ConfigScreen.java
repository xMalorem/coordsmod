package dev.malorem.coordsmod;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Settings, as cycling buttons rather than sliders and colour pickers - the
 * values worth changing all have a handful of sensible choices.
 */
public class ConfigScreen extends Screen {
	private static final int WIDTH = 220;

	private final Screen parent;

	private Button labelsButton;
	private Button distanceButton;
	private Button radiusButton;
	private Button positionButton;
	private Button colorButton;

	public ConfigScreen(Screen parent) {
		super(Component.literal("CoordsMod settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config config = Config.get();
		int x = width / 2 - WIDTH / 2;
		int y = height / 2 - 74;

		labelsButton = addRenderableWidget(Button.builder(labelsLabel(), b -> {
			config.labels = !config.labels;
			commit();
		}).bounds(x, y, WIDTH, 20).build());

		distanceButton = addRenderableWidget(Button.builder(distanceLabel(), b -> {
			config.labelMaxDistance = Config.cycle(Config.LABEL_DISTANCES, config.labelMaxDistance);
			commit();
		}).bounds(x, y + 24, WIDTH, 20).build());

		radiusButton = addRenderableWidget(Button.builder(radiusLabel(), b -> {
			config.arrivedRadius = Config.cycle(Config.ARRIVED_RADII, config.arrivedRadius);
			commit();
		}).bounds(x, y + 48, WIDTH, 20).build());

		positionButton = addRenderableWidget(Button.builder(positionLabel(), b -> {
			config.hudPosition = config.hudAtTop() ? "BOTTOM" : "TOP";
			commit();
		}).bounds(x, y + 72, WIDTH, 20).build());

		colorButton = addRenderableWidget(Button.builder(colorLabel(), b -> {
			config.needleColor = Config.cycleColor(config.needleColor);
			commit();
		}).bounds(x, y + 96, WIDTH, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Controls"),
				b -> minecraft.setScreenAndShow(new KeybindScreen(this)))
				.bounds(x, y + 124, WIDTH, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(x, y + 148, WIDTH, 20).build());
	}

	private void commit() {
		Config.save();
		labelsButton.setMessage(labelsLabel());
		distanceButton.setMessage(distanceLabel());
		radiusButton.setMessage(radiusLabel());
		positionButton.setMessage(positionLabel());
		colorButton.setMessage(colorLabel());
	}

	private Component labelsLabel() {
		return Component.literal("In-world labels:  " + (Config.get().labels ? "On" : "Off"));
	}

	private Component distanceLabel() {
		return Component.literal("Label distance:  " + (int) Config.get().labelMaxDistance + "m");
	}

	private Component radiusLabel() {
		return Component.literal("Arrival radius:  " + (int) Config.get().arrivedRadius
				+ (Config.get().arrivedRadius == 1.0 ? " block" : " blocks"));
	}

	private Component positionLabel() {
		return Component.literal("Tracker position:  " + (Config.get().hudAtTop() ? "Top" : "Bottom"));
	}

	private Component colorLabel() {
		return Component.literal("Needle colour:  " + Config.colorName(Config.get().needleColor));
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(parent);
		}
	}
}
