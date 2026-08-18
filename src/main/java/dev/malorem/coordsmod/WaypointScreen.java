package dev.malorem.coordsmod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Managing thirty waypoints through chat commands is clumsy, so this is where
 * /cren and /cdel graduate to: one row per waypoint, nearest first, click to
 * track or delete.
 */
public class WaypointScreen extends Screen {
	private static final int PER_PAGE = 7;
	private static final int ROW_WIDTH = 220;

	private final Screen parent;
	private int page;

	public WaypointScreen(Screen parent, int page) {
		super(Component.literal("Coords"));
		this.parent = parent;
		this.page = page;
	}

	@Override
	protected void init() {
		Minecraft client = Minecraft.getInstance();
		String dimensionId = client.level != null
				? client.level.dimension().identifier().toString()
				: Dimensions.OVERWORLD;

		List<Waypoint> waypoints = new ArrayList<>(CoordStore.list(dimensionId));
		Vec3 viewer = client.player != null ? client.player.position() : null;

		if (viewer != null) {
			waypoints.sort(Comparator.comparingDouble(w -> Geo.horizontalDistance(viewer, w)));
		}

		int pages = Math.max(1, (waypoints.size() + PER_PAGE - 1) / PER_PAGE);
		page = Math.max(0, Math.min(page, pages - 1));

		int x = width / 2 - 130;
		int y = 40;

		if (waypoints.isEmpty()) {
			Button empty = Button.builder(
					Component.literal("No coords in " + Dimensions.displayName(dimensionId)), b -> {
					}).bounds(x, y, ROW_WIDTH, 20).build();
			empty.active = false;
			addRenderableWidget(empty);
			y += 22;
		}

		int first = page * PER_PAGE;
		int last = Math.min(waypoints.size(), first + PER_PAGE);

		for (int i = first; i < last; i++) {
			Waypoint waypoint = waypoints.get(i);
			String label = waypoint.name + "  " + Chat.coords(waypoint)
					+ (viewer != null ? "  " + Geo.relative(viewer, waypoint) : "");

			addRenderableWidget(Button.builder(Component.literal(label), b -> {
				Tracker.track(dimensionId, waypoint);
				onClose();
			}).bounds(x, y, ROW_WIDTH, 20).build());

			addRenderableWidget(Button.builder(Component.literal("X"), b -> {
				CoordStore.remove(dimensionId, waypoint.name);
				reopen(page);
			}).bounds(x + ROW_WIDTH + 4, y, 20, 20).build());

			y += 22;
		}

		y += 6;

		if (pages > 1) {
			Button previous = Button.builder(Component.literal("< Prev"), b -> reopen(page - 1))
					.bounds(x, y, 70, 20).build();
			previous.active = page > 0;
			addRenderableWidget(previous);

			Button indicator = Button.builder(
					Component.literal((page + 1) + " / " + pages), b -> {
					}).bounds(x + 74, y, 72, 20).build();
			indicator.active = false;
			addRenderableWidget(indicator);

			Button next = Button.builder(Component.literal("Next >"), b -> reopen(page + 1))
					.bounds(x + 150, y, 70, 20).build();
			next.active = page < pages - 1;
			addRenderableWidget(next);

			y += 26;
		}

		if (Tracker.tracked() != null) {
			addRenderableWidget(Button.builder(Component.literal("Stop tracking"), b -> {
				Tracker.clear();
				reopen(page);
			}).bounds(x, y, ROW_WIDTH, 20).build());

			y += 22;
		}

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(x, y, ROW_WIDTH, 20).build());
	}

	/** Rebuilt by reopening rather than mutating widgets, which keeps init() the single layout path. */
	private void reopen(int targetPage) {
		if (minecraft != null) {
			minecraft.setScreenAndShow(new WaypointScreen(parent, targetPage));
		}
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(parent);
		}
	}
}
