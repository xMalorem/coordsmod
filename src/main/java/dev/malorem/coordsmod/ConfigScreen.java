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

	private Button hiddenButton;
	private Button labelsButton;
	private Button distanceButton;
	private Button radiusButton;
	private Button untrackButton;
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
		int y = height / 2 - 88;

		// Master switch first: it overrides everything below it.
		hiddenButton = addRenderableWidget(Button.builder(hiddenLabel(), b -> {
			config.hidden = !config.hidden;
			commit();
		}).bounds(x, y, WIDTH, 20).build());

		labelsButton = addRenderableWidget(Button.builder(labelsLabel(), b -> {
			config.labels = !config.labels;
			commit();
		}).bounds(x, y + 28, WIDTH, 20).build());

		distanceButton = addRenderableWidget(Button.builder(distanceLabel(), b -> {
			config.labelMaxDistance = Config.cycle(Config.LABEL_DISTANCES, config.labelMaxDistance);
			commit();
		}).bounds(x, y + 52, WIDTH, 20).build());

		radiusButton = addRenderableWidget(Button.builder(radiusLabel(), b -> {
			config.arrivedRadius = Config.cycle(Config.ARRIVED_RADII, config.arrivedRadius);
			commit();
		}).bounds(x, y + 76, WIDTH, 20).build());

		untrackButton = addRenderableWidget(Button.builder(untrackLabel(), b -> {
			config.autoUntrack = Config.cycle(Config.AUTO_UNTRACK_RADII, config.autoUntrack);
			commit();
		}).bounds(x, y + 100, WIDTH, 20).build());

		positionButton = addRenderableWidget(Button.builder(positionLabel(), b -> {
			config.hudPosition = config.hudAtTop() ? "BOTTOM" : "TOP";
			commit();
		}).bounds(x, y + 124, WIDTH, 20).build());

		colorButton = addRenderableWidget(Button.builder(colorLabel(), b -> {
			config.needleColor = Config.cycleColor(config.needleColor);
			commit();
		}).bounds(x, y + 148, WIDTH, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Controls"),
				b -> minecraft.setScreenAndShow(new KeybindScreen(this)))
				.bounds(x, y + 176, WIDTH, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(x, y + 200, WIDTH, 20).build());

		commit();
	}

	private void commit() {
		Config.save();
		hiddenButton.setMessage(hiddenLabel());
		labelsButton.setMessage(labelsLabel());

		// Everything below the master switch is inert while it is on.
		boolean shown = !Config.get().hidden;
		labelsButton.active = shown;
		distanceButton.active = shown;
		radiusButton.active = shown;
		positionButton.active = shown;
		colorButton.active = shown;

		// Auto-untrack is behaviour, not drawing, so it stays usable while hidden.
		untrackButton.setMessage(untrackLabel());
		distanceButton.setMessage(distanceLabel());
		radiusButton.setMessage(radiusLabel());
		positionButton.setMessage(positionLabel());
		colorButton.setMessage(colorLabel());
	}

	private Component hiddenLabel() {
		return Component.literal("Overlays:  " + (Config.get().hidden ? "Hidden" : "Shown"));
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

	private Component untrackLabel() {
		double radius = Config.get().autoUntrack;

		if (radius <= 0.0) {
			return Component.literal("Untrack on arrival:  Off");
		}

		return Component.literal("Untrack on arrival:  within " + (int) radius
				+ (radius == 1.0 ? " block" : " blocks"));
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
