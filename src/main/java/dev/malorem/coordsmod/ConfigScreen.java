package dev.malorem.coordsmod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Settings, as cycling buttons rather than sliders and colour pickers - the
 * values worth changing all have a handful of sensible choices. Two columns,
 * because one would run off the bottom at large GUI scales.
 */
public class ConfigScreen extends Screen {
	private static final int COLUMN = 150;
	private static final int GAP = 8;
	private static final int ROW = 24;

	private final Screen parent;

	private Button hiddenButton;
	private Button labelsButton;
	private Button distanceButton;
	private Button radiusButton;
	private Button untrackButton;
	private Button positionButton;
	private Button colorButton;
	private Button deathButton;
	private Button portalButton;

	public ConfigScreen(Screen parent) {
		super(Component.literal("CoordsMod settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config config = Config.get();

		int leftX = width / 2 - COLUMN - GAP / 2;
		int rightX = width / 2 + GAP / 2;
		int top = height / 2 - 76;

		// Left column: what is drawn.
		hiddenButton = addRenderableWidget(Button.builder(hiddenLabel(), b -> {
			config.hidden = !config.hidden;
			commit();
		}).bounds(leftX, top, COLUMN, 20).build());

		labelsButton = addRenderableWidget(Button.builder(labelsLabel(), b -> {
			config.labels = !config.labels;
			commit();
		}).bounds(leftX, top + ROW, COLUMN, 20).build());

		distanceButton = addRenderableWidget(Button.builder(distanceLabel(), b -> {
			config.labelMaxDistance = Config.cycle(Config.LABEL_DISTANCES, config.labelMaxDistance);
			commit();
		}).bounds(leftX, top + ROW * 2, COLUMN, 20).build());

		positionButton = addRenderableWidget(Button.builder(positionLabel(), b -> {
			config.hudPosition = config.hudAtTop() ? "BOTTOM" : "TOP";
			commit();
		}).bounds(leftX, top + ROW * 3, COLUMN, 20).build());

		colorButton = addRenderableWidget(Button.builder(colorLabel(), b -> {
			config.needleColor = Config.cycleColor(config.needleColor);
			commit();
		}).bounds(leftX, top + ROW * 4, COLUMN, 20).build());

		// Right column: how it behaves.
		radiusButton = addRenderableWidget(Button.builder(radiusLabel(), b -> {
			config.arrivedRadius = Config.cycle(Config.ARRIVED_RADII, config.arrivedRadius);
			commit();
		}).bounds(rightX, top, COLUMN, 20).build());

		untrackButton = addRenderableWidget(Button.builder(untrackLabel(), b -> {
			config.autoUntrack = Config.cycle(Config.AUTO_UNTRACK_RADII, config.autoUntrack);
			commit();
		}).bounds(rightX, top + ROW, COLUMN, 20).build());

		deathButton = addRenderableWidget(Button.builder(deathLabel(), b -> {
			config.deathWaypoints = !config.deathWaypoints;
			commit();
		}).bounds(rightX, top + ROW * 2, COLUMN, 20).build());

		portalButton = addRenderableWidget(Button.builder(portalLabel(), b -> {
			config.portalWaypoints = !config.portalWaypoints;
			commit();
		}).bounds(rightX, top + ROW * 3, COLUMN, 20).build());

		int footer = top + ROW * 5 + 12;

		addRenderableWidget(Button.builder(Component.literal("Controls"),
				b -> minecraft.setScreenAndShow(new KeybindScreen(this)))
				.bounds(width / 2 - COLUMN - GAP / 2, footer, COLUMN, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(rightX, footer, COLUMN, 20).build());

		commit();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(extractor, mouseX, mouseY, partialTick);
		extractor.centeredText(font, Component.literal("Display"),
				width / 2 - COLUMN / 2 - GAP / 2, height / 2 - 90, 0xFFAAAAAA);
		extractor.centeredText(font, Component.literal("Behaviour"),
				width / 2 + COLUMN / 2 + GAP / 2, height / 2 - 90, 0xFFAAAAAA);
	}

	private void commit() {
		Config.save();

		hiddenButton.setMessage(hiddenLabel());
		labelsButton.setMessage(labelsLabel());
		distanceButton.setMessage(distanceLabel());
		radiusButton.setMessage(radiusLabel());
		untrackButton.setMessage(untrackLabel());
		positionButton.setMessage(positionLabel());
		colorButton.setMessage(colorLabel());
		deathButton.setMessage(deathLabel());
		portalButton.setMessage(portalLabel());

		// Only the drawing options are pointless while the master switch is on.
		boolean shown = !Config.get().hidden;
		labelsButton.active = shown;
		distanceButton.active = shown;
		positionButton.active = shown;
		colorButton.active = shown;
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
		double radius = Config.get().arrivedRadius;
		return Component.literal("Arrival radius:  " + (int) radius + (radius == 1.0 ? " block" : " blocks"));
	}

	private Component untrackLabel() {
		double radius = Config.get().autoUntrack;

		if (radius <= 0.0) {
			return Component.literal("Untrack on arrival:  Off");
		}

		return Component.literal("Untrack on arrival:  " + (int) radius
				+ (radius == 1.0 ? " block" : " blocks"));
	}

	private Component positionLabel() {
		return Component.literal("Tracker:  " + (Config.get().hudAtTop() ? "Top" : "Bottom"));
	}

	private Component colorLabel() {
		return Component.literal("Needle:  " + Config.colorName(Config.get().needleColor));
	}

	private Component deathLabel() {
		return Component.literal("Death waypoints:  " + (Config.get().deathWaypoints ? "On" : "Off"));
	}

	private Component portalLabel() {
		return Component.literal("Portal waypoints:  " + (Config.get().portalWaypoints ? "On" : "Off"));
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(parent);
		}
	}
}
