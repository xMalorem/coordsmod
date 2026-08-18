package dev.malorem.coordsmod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The waypoint manager: a real scrollable list, nearest first, with the actions
 * on a footer bar operating on the selected row.
 */
public class WaypointScreen extends Screen {
	private static final int ROW_HEIGHT = 26;
	private static final int HEADER = 32;
	private static final int FOOTER = 64;

	private static final int NAME_COLOR = 0xFFFFFFFF;
	private static final int TRACKED_COLOR = 0xFFFFDD55;
	private static final int COORD_COLOR = 0xFFAAAAAA;
	private static final int DISTANCE_COLOR = 0xFF55DDFF;

	private final Screen parent;

	private String dimensionId = Dimensions.OVERWORLD;
	private Vec3 viewer;
	private WaypointList list;
	private Button trackButton;
	private Button deleteButton;
	private Button stopButton;

	public WaypointScreen(Screen parent) {
		super(Component.literal("Coords"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Minecraft client = Minecraft.getInstance();

		if (client.level != null) {
			dimensionId = client.level.dimension().identifier().toString();
		}

		viewer = client.player != null ? client.player.position() : null;

		list = new WaypointList(client, width, height - HEADER - FOOTER, HEADER, ROW_HEIGHT);
		addRenderableWidget(list);

		List<Waypoint> waypoints = new ArrayList<>(CoordStore.list(dimensionId));

		if (viewer != null) {
			waypoints.sort(Comparator.comparingDouble(w -> Geo.horizontalDistance(viewer, w)));
		}

		for (Waypoint waypoint : waypoints) {
			list.add(waypoint);
		}

		int y = height - 52;
		trackButton = addRenderableWidget(Button.builder(Component.literal("Track"), b -> trackSelected())
				.bounds(width / 2 - 154, y, 100, 20).build());
		deleteButton = addRenderableWidget(Button.builder(Component.literal("Delete"), b -> deleteSelected())
				.bounds(width / 2 - 50, y, 100, 20).build());
		stopButton = addRenderableWidget(Button.builder(Component.literal("Stop tracking"), b -> {
			Tracker.clear();
			updateButtons();
		}).bounds(width / 2 + 54, y, 100, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(width / 2 - 100, height - 28, 200, 20).build());

		updateButtons();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(extractor, mouseX, mouseY, partialTick);

		Component heading = Component.literal(Dimensions.displayName(dimensionId))
				.withStyle(Dimensions.color(dimensionId))
				.append(Component.literal("  " + CoordStore.list(dimensionId).size() + " saved")
						.withStyle(ChatFormatting.GRAY));

		extractor.centeredText(font, heading, width / 2, 12, 0xFFFFFFFF);
	}

	private void updateButtons() {
		boolean selected = list != null && list.getSelected() != null;
		trackButton.active = selected;
		deleteButton.active = selected;
		stopButton.active = Tracker.tracked() != null;
	}

	private void trackSelected() {
		Row row = list.getSelected();

		if (row != null) {
			Tracker.track(dimensionId, row.waypoint);
			updateButtons();
		}
	}

	private void deleteSelected() {
		Row row = list.getSelected();

		if (row == null) {
			return;
		}

		CoordStore.remove(dimensionId, row.waypoint.name);

		// Rebuilding by reopening keeps init() as the single layout path.
		if (minecraft != null) {
			minecraft.setScreenAndShow(new WaypointScreen(parent));
		}
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(parent);
		}
	}

	// -------------------------------------------------------------------- list

	private class WaypointList extends ObjectSelectionList<Row> {
		WaypointList(Minecraft client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		@Override
		public int getRowWidth() {
			return 300;
		}

		void add(Waypoint waypoint) {
			addEntry(new Row(waypoint));
		}
	}

	private class Row extends ObjectSelectionList.Entry<Row> {
		private final Waypoint waypoint;

		Row(Waypoint waypoint) {
			this.waypoint = waypoint;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
				boolean hovered, float partialTick) {
			int x = getContentX();
			int y = getContentY();
			boolean isTracked = Tracker.tracked() == waypoint;

			extractor.text(font, Component.literal(isTracked ? "→ " + waypoint.name : waypoint.name),
					x, y + 2, isTracked ? TRACKED_COLOR : NAME_COLOR);
			extractor.text(font, Component.literal(Chat.coords(waypoint)), x, y + 13, COORD_COLOR);

			if (viewer != null) {
				String away = Geo.relative(viewer, waypoint);
				extractor.text(font, Component.literal(away),
						getContentRight() - font.width(away), y + 8, DISTANCE_COLOR);
			}
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			list.setSelected(this);
			updateButtons();

			// Double-click is the shortcut for "track this and get out of my way".
			if (doubleClick) {
				Tracker.track(dimensionId, waypoint);
				onClose();
			}

			return true;
		}

		@Override
		public Component getNarration() {
			return Component.literal(waypoint.name + " " + Chat.coords(waypoint));
		}
	}
}
