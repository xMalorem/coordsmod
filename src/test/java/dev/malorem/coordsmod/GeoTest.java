package dev.malorem.coordsmod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

/** Bearing, distance and portal-ratio maths. */
class GeoTest {
	private static final Vec3 ORIGIN = new Vec3(0, 64, 0);

	private static Waypoint at(int x, int z) {
		return new Waypoint("w", x, 64, z, 0);
	}

	@Test
	@DisplayName("compass points the right way on all four axes")
	void compassAxes() {
		// +X is east and +Z is south, so north is -Z.
		assertEquals("N", Geo.compass(ORIGIN, at(0, -100)));
		assertEquals("S", Geo.compass(ORIGIN, at(0, 100)));
		assertEquals("E", Geo.compass(ORIGIN, at(100, 0)));
		assertEquals("W", Geo.compass(ORIGIN, at(-100, 0)));
		assertEquals("NE", Geo.compass(ORIGIN, at(100, -100)));
		assertEquals("SW", Geo.compass(ORIGIN, at(-100, 100)));
		assertEquals("here", Geo.compass(ORIGIN, at(0, 0)));
	}

	@Test
	@DisplayName("distance is horizontal, so height does not inflate it")
	void distanceIgnoresHeight() {
		assertEquals(5, Math.round(Geo.horizontalDistance(ORIGIN, at(3, 4))));
		assertEquals(5, Math.round(Geo.horizontalDistance(ORIGIN, new Waypoint("h", 3, 5000, 4, 0))));
	}

	@Test
	void distanceFormatting() {
		assertEquals("182m", Geo.formatDistance(182));
		assertEquals("999m", Geo.formatDistance(999));
		assertEquals("1.2km", Geo.formatDistance(1200));
	}

	@Test
	@DisplayName("standing on a point reads as 'here', not '1m here'")
	void relativeCollapsesWhenArrived() {
		assertEquals("here", Geo.relative(ORIGIN, at(0, 0)));
		assertEquals("100m N", Geo.relative(ORIGIN, at(0, -100)));
	}

	@Test
	@DisplayName("needle bearing is relative to facing, not absolute")
	void relativeBearing() {
		// Yaw 0 faces south (+Z).
		assertEquals(0, Math.round(Geo.relativeBearing(ORIGIN, 0f, at(0, 100))));
		assertEquals(90, Math.round(Geo.relativeBearing(ORIGIN, 0f, at(-100, 0))));
		assertEquals(-90, Math.round(Geo.relativeBearing(ORIGIN, 0f, at(100, 0))));
		assertEquals(180f, Math.abs(Geo.relativeBearing(ORIGIN, 0f, at(0, -100))));

		// Turning to face the target puts it dead ahead.
		assertEquals(0, Math.round(Geo.relativeBearing(ORIGIN, 90f, at(-100, 0))));
	}

	@Test
	void angleWrapping() {
		assertEquals(10, Math.round(Geo.wrap180(370f)));
		assertEquals(170, Math.round(Geo.wrap180(-190f)));
	}

	@Test
	@DisplayName("needle eases the short way round the wrap point")
	void easingTakesShortestPath() {
		// 170 to -170 is 20 degrees forwards, not 340 backwards.
		float eased = Geo.approachAngle(170f, -170f, 0.5f);
		assertTrue(eased > 170f && eased < 190f, "expected ~180 but got " + eased);
	}

	@Test
	@DisplayName("portal ratio floors negatives instead of truncating toward zero")
	void portalRatio() {
		assertEquals("100, -100", Geo.toNether(new Waypoint("a", 800, 64, -800, 0)));
		assertEquals("-2, -2", Geo.toNether(new Waypoint("a", -9, 64, -9, 0)));
		assertEquals("800, -800", Geo.toOverworld(new Waypoint("a", 100, 64, -100, 0)));
	}
}
