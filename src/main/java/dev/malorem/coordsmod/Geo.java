package dev.malorem.coordsmod;

import java.util.Locale;

import net.minecraft.world.phys.Vec3;

/**
 * Distance, bearing and portal-ratio maths. With nothing drawn on screen, these
 * numbers are the only sense of direction the player gets.
 */
public final class Geo {
	private static final String[] COMPASS = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };

	/** Horizontal distance only - vertical travel is rarely the hard part. */
	public static double horizontalDistance(Vec3 from, Waypoint to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	public static String compass(Vec3 from, Waypoint to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;

		if (Math.abs(dx) < 1.5 && Math.abs(dz) < 1.5) {
			return "here";
		}

		// In Minecraft +X is east and +Z is south, so north is -Z.
		double degrees = Math.toDegrees(Math.atan2(dx, -dz));
		int index = (int) Math.round((((degrees % 360) + 360) % 360) / 45.0) % 8;
		return COMPASS[index];
	}

	/** "here" when you are standing on it, otherwise "182m NW". */
	public static String relative(Vec3 from, Waypoint to) {
		String bearing = compass(from, to);

		if (bearing.equals("here")) {
			return bearing;
		}

		return formatDistance(horizontalDistance(from, to)) + " " + bearing;
	}

	public static String formatDistance(double blocks) {
		if (blocks < 1000) {
			return Math.round(blocks) + "m";
		}

		return String.format(Locale.ROOT, "%.1fkm", blocks / 1000.0);
	}

	/**
	 * Overworld X/Z to the matching Nether X/Z. floorDiv, not '/', so negative
	 * coordinates land on the correct side rather than truncating toward zero.
	 */
	public static String toNether(Waypoint waypoint) {
		return Math.floorDiv(waypoint.x, 8) + ", " + Math.floorDiv(waypoint.z, 8);
	}

	public static String toOverworld(Waypoint waypoint) {
		return (waypoint.x * 8) + ", " + (waypoint.z * 8);
	}

	private Geo() {
	}
}
