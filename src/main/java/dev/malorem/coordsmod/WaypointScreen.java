package dev.malorem.coordsmod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The waypoint manager: a scrollable list, nearest first, with the actions on a
 * footer bar operating on the selected row. The dimension button cycles through
 * the three vanilla dimensions and an "All" view.
 */
public class WaypointScreen extends Screen {
	private static final int ROW_HEIGHT = 26;
	private static final int LIST_TOP = 32;
	private static final int FOOTER = 64;

	private static final int NAME_COLOR = 0xFFFFFFFF;
	private static final int TRACKED_COLOR = 0xFFFFDD55;
	private static final int COORD_COLOR = 0xFFAAAAAA;
	private static final int DISTANCE_COLOR = 0xFF55DDFF;

	/** Cycle order for the dimension button; null is the "All" view. */
	private static final String[] VIEWS = {
			Dimensions.OVERWORLD, Dimensions.NETHER, Dimensions.END, null
	};

	private final Screen parent;

	/** Which dimension is being listed, or null for all of them. */
	private String view;
	private boolean viewChosen;

	private String playerDimension = Dimensions.OVERWORLD;
	private Vec3 viewer;
	private WaypointList list;
	private Button trackButton;
	private Button deleteButton;
	private Button stopButton;

	public WaypointScreen(Screen parent) {
		this(parent, null, false);
	}

	private WaypointScreen(Screen parent, String view, boolean viewChosen) {
		super(Component.literal("Coords"));
		this.parent = parent;
		this.view = view;
		this.viewChosen = viewChosen;
	}

	@Override
	protected void init() {
		Minecraft client = Minecraft.getInstance();

		if (client.level != null) {
			playerDimension = client.level.dimension().identifier().toString();
		}

		// First open lands on wherever the player actually is.
		if (!viewChosen) {
			view = playerDimension;
			viewChosen = true;
		}

		viewer = client.player != null ? client.player.position() : null;

		addRenderableWidget(Button.builder(viewLabel(), b -> reopen(nextView()))
				.bounds(width / 2 - 80, 6, 160, 20).build());

		list = new WaypointList(client, width, height - LIST_TOP - FOOTER, LIST_TOP, ROW_HEIGHT);
		addRenderableWidget(list);

		for (Map.Entry<String, List<Waypoint>> entry : visible().entrySet()) {
			String dimensionId = entry.getKey();
			List<Waypoint> waypoints = new ArrayList<>(entry.getValue());

			if (viewer != null && dimensionId.equals(playerDimension)) {
				waypoints.sort(Comparator.comparingDouble(w -> Geo.horizontalDistance(viewer, w)));
			}

			for (Waypoint waypoint : waypoints) {
				list.add(dimensionId, waypoint);
			}
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

	/** The dimensions currently in scope, in a stable order. */
	private Map<String, List<Waypoint>> visible() {
		Map<String, List<Waypoint>> all = CoordStore.all();
		Map<String, List<Waypoint>> result = new java.util.LinkedHashMap<>();

		if (view != null) {
			List<Waypoint> waypoints = all.get(view);

			if (waypoints != null && !waypoints.isEmpty()) {
				result.put(view, waypoints);
			}

			return result;
		}

		for (String known : List.of(Dimensions.OVERWORLD, Dimensions.NETHER, Dimensions.END)) {
			if (all.containsKey(known)) {
				result.put(known, all.get(known));
			}
		}

		for (Map.Entry<String, List<Waypoint>> entry : all.entrySet()) {
			result.putIfAbsent(entry.getKey(), entry.getValue());
		}

		return result;
	}

	private String nextView() {
		for (int i = 0; i < VIEWS.length; i++) {
			if (java.util.Objects.equals(VIEWS[i], view)) {
				return VIEWS[(i + 1) % VIEWS.length];
			}
		}

		return VIEWS[0];
	}

	private Component viewLabel() {
		if (view == null) {
			return Component.literal("All dimensions");
		}

		return Component.literal(Dimensions.displayName(view)).withStyle(Dimensions.color(view));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(extractor, mouseX, mouseY, partialTick);

		int count = 0;

		for (List<Waypoint> waypoints : visible().values()) {
			count += waypoints.size();
		}

		extractor.centeredText(font, Component.literal(count + " saved").withStyle(ChatFormatting.GRAY),
				width / 2, height - 66, 0xFFFFFFFF);
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
			Tracker.track(row.dimensionId, row.waypoint);
			updateButtons();
		}
	}

	private void deleteSelected() {
		Row row = list.getSelected();

		if (row == null) {
			return;
		}

		CoordStore.remove(row.dimensionId, row.waypoint.name);
		reopen(view);
	}

	/** Rebuilt by reopening rather than mutating widgets, which keeps init() the single layout path. */
	private void reopen(String targetView) {
		if (minecraft != null) {
			minecraft.setScreenAndShow(new WaypointScreen(parent, targetView, true));
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

		void add(String dimensionId, Waypoint waypoint) {
			addEntry(new Row(dimensionId, waypoint));
		}
	}

	private class Row extends ObjectSelectionList.Entry<Row> {
		private final String dimensionId;
		private final Waypoint waypoint;

		Row(String dimensionId, Waypoint waypoint) {
			this.dimensionId = dimensionId;
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

			Component detail = Component.literal(Chat.coords(waypoint));

			if (view == null) {
				detail = detail.copy().append(Component.literal("  " + Dimensions.displayName(dimensionId))
						.withStyle(Dimensions.color(dimensionId)));
			}

			extractor.text(font, detail, x, y + 13, COORD_COLOR);

			// A distance only means anything for the dimension the player is standing in.
			if (viewer != null && dimensionId.equals(playerDimension)) {
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
