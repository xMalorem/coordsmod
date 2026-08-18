package dev.malorem.coordsmod;

/**
 * A single saved coordinate. Plain class with a no-arg constructor so Gson can
 * round-trip it without any custom adapters.
 */
public class Waypoint {
	public String name;
	public int x;
	public int y;
	public int z;
	public long created;

	/**
	 * Hides just this waypoint's in-world label. It stays saved, listed and
	 * trackable - only the floating label is suppressed. Absent from older files,
	 * which Gson leaves as false.
	 */
	public boolean hidden;

	public Waypoint() {
	}

	public Waypoint(String name, int x, int y, int z, long created) {
		this.name = name;
		this.x = x;
		this.y = y;
		this.z = z;
		this.created = created;
	}
}
