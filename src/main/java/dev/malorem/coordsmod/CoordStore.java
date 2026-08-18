package dev.malorem.coordsmod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;

/**
 * Waypoint storage, keyed by dimension id and kept in one JSON file per world /
 * server so coordinates from different saves never mix.
 *
 * Files live in {@code .minecraft/config/coordsmod/<world>.json}.
 */
public final class CoordStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type MAP_TYPE = new TypeToken<Map<String, List<Waypoint>>>() {
	}.getType();

	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("coordsmod");
	private static final String DEFAULT_PORT = ":25565";

	/** Which world the in-memory map belongs to; null means nothing is loaded. */
	private static String loadedKey;
	private static Map<String, List<Waypoint>> data = new LinkedHashMap<>();

	/** One-slot undo buffer, so an accidental /cdel is not permanent. */
	private static String undoDimension;
	private static Waypoint undoWaypoint;

	private CoordStore() {
	}

	// ---------------------------------------------------------------- queries

	public static List<Waypoint> list(String dimensionId) {
		ensureLoaded();
		return data.getOrDefault(dimensionId, List.of());
	}

	/** Every dimension that currently holds at least one waypoint. */
	public static Map<String, List<Waypoint>> all() {
		ensureLoaded();
		return data;
	}

	public static boolean exists(String dimensionId, String name) {
		return find(dimensionId, name) != null;
	}

	public static Waypoint find(String dimensionId, String name) {
		for (Waypoint w : list(dimensionId)) {
			if (w.name.equalsIgnoreCase(name)) {
				return w;
			}
		}

		return null;
	}

	/** Searches every dimension, for commands where the dimension is not implied. */
	public static Waypoint findAnywhere(String name) {
		ensureLoaded();

		for (List<Waypoint> waypoints : data.values()) {
			for (Waypoint w : waypoints) {
				if (w.name.equalsIgnoreCase(name)) {
					return w;
				}
			}
		}

		return null;
	}

	public static int total() {
		ensureLoaded();
		int count = 0;

		for (List<Waypoint> waypoints : data.values()) {
			count += waypoints.size();
		}

		return count;
	}

	// ---------------------------------------------------------------- mutation

	/** Adds a waypoint, replacing any existing one with the same name in that dimension. */
	public static void put(String dimensionId, Waypoint waypoint) {
		ensureLoaded();
		List<Waypoint> waypoints = data.computeIfAbsent(dimensionId, key -> new ArrayList<>());
		waypoints.removeIf(w -> w.name.equalsIgnoreCase(waypoint.name));
		waypoints.add(waypoint);
		save();
	}

	public static boolean remove(String dimensionId, String name) {
		ensureLoaded();
		List<Waypoint> waypoints = data.get(dimensionId);

		if (waypoints == null) {
			return false;
		}

		Waypoint doomed = find(dimensionId, name);

		if (doomed == null) {
			return false;
		}

		waypoints.remove(doomed);
		undoDimension = dimensionId;
		undoWaypoint = doomed;

		if (waypoints.isEmpty()) {
			data.remove(dimensionId);
		}

		save();
		return true;
	}

	/** Writes the current state out after a waypoint has been edited in place. */
	public static void flush() {
		save();
	}

	/** Restores the last deleted waypoint. Returns it, or null if there is nothing to undo. */
	public static Waypoint undo() {
		if (undoWaypoint == null || undoDimension == null) {
			return null;
		}

		Waypoint restored = undoWaypoint;
		restored.name = uniqueName(undoDimension, restored.name);
		put(undoDimension, restored);

		undoWaypoint = null;
		undoDimension = null;
		return restored;
	}

	/** @return the new name, or null when {@code from} does not exist. */
	public static String rename(String dimensionId, String from, String to) {
		Waypoint waypoint = find(dimensionId, from);

		if (waypoint == null) {
			return null;
		}

		// Renaming onto its own name (different case only) must not self-collide.
		List<Waypoint> waypoints = data.get(dimensionId);
		waypoints.remove(waypoint);
		String unique = uniqueName(dimensionId, to);
		waypoint.name = unique;
		waypoints.add(waypoint);
		save();

		return unique;
	}

	/** Builds a unique auto-name like "ow-3" or "death-2". */
	public static String autoName(String dimensionId, String tag) {
		int next = 1;

		while (exists(dimensionId, tag + "-" + next)) {
			next++;
		}

		return tag + "-" + next;
	}

	public static String autoName(String dimensionId) {
		return autoName(dimensionId, Dimensions.shortTag(dimensionId));
	}

	/** Returns {@code base}, or base-2, base-3... so nothing is silently overwritten. */
	public static String uniqueName(String dimensionId, String base) {
		String unique = base;
		int suffix = 2;

		while (exists(dimensionId, unique)) {
			unique = base + "-" + suffix++;
		}

		return unique;
	}

	/** Strips characters that would break the share wire format. */
	public static String sanitizeName(String raw) {
		// '|' would break the share wire format; quotes and backslashes would break the
		// quoted name argument in the /cadd command behind the [+Add] button.
		String name = raw.replace('|', '/').replace('\n', ' ')
				.replace("\\", "").replace("\"", "").trim();

		if (name.length() > 32) {
			name = name.substring(0, 32).trim();
		}

		return name;
	}

	// ------------------------------------------------------------- world files

	public static String currentKey() {
		ensureLoaded();
		return loadedKey;
	}

	/** Every stored world file, newest-looking name order aside, just sorted. */
	public static List<String> worldKeys() {
		List<String> keys = new ArrayList<>();

		if (!Files.isDirectory(DIR)) {
			return keys;
		}

		try (Stream<Path> files = Files.list(DIR)) {
			files.filter(p -> p.getFileName().toString().endsWith(".json"))
					.forEach(p -> {
						String name = p.getFileName().toString();
						keys.add(name.substring(0, name.length() - ".json".length()));
					});
		} catch (IOException e) {
			CoordsModClient.LOGGER.error("Could not list {}", DIR, e);
		}

		keys.sort(String::compareToIgnoreCase);
		return keys;
	}

	/**
	 * Copies every waypoint from another world file into this one, renaming on
	 * collision. Returns the number merged, -1 if it is the current world, -2 if
	 * no such file exists.
	 */
	public static int mergeFrom(String otherKey) {
		ensureLoaded();

		if (otherKey.equals(loadedKey)) {
			return -1;
		}

		Path file = DIR.resolve(otherKey + ".json");

		if (!Files.isRegularFile(file)) {
			return -2;
		}

		Map<String, List<Waypoint>> other = read(file);
		int merged = 0;

		for (Map.Entry<String, List<Waypoint>> entry : other.entrySet()) {
			for (Waypoint w : entry.getValue()) {
				String name = uniqueName(entry.getKey(), w.name);
				data.computeIfAbsent(entry.getKey(), key -> new ArrayList<>())
						.add(new Waypoint(name, w.x, w.y, w.z, w.created));
				merged++;
			}
		}

		if (merged > 0) {
			save();
		}

		return merged;
	}

	// ------------------------------------------------------------ persistence

	private static void ensureLoaded() {
		String key = worldKey();

		if (key.equals(loadedKey)) {
			return;
		}

		loadedKey = key;
		data = read(DIR.resolve(key + ".json"));
	}

	private static Map<String, List<Waypoint>> read(Path file) {
		if (!Files.isRegularFile(file)) {
			return new LinkedHashMap<>();
		}

		try (Reader reader = Files.newBufferedReader(file)) {
			Map<String, List<Waypoint>> loaded = GSON.fromJson(reader, MAP_TYPE);

			if (loaded != null) {
				return loaded;
			}
		} catch (IOException | RuntimeException e) {
			CoordsModClient.LOGGER.error("Could not read {}, starting empty", file, e);
		}

		return new LinkedHashMap<>();
	}

	private static void save() {
		if (loadedKey == null) {
			return;
		}

		Path file = DIR.resolve(loadedKey + ".json");

		try {
			Files.createDirectories(DIR);

			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(data, MAP_TYPE, writer);
			}
		} catch (IOException e) {
			CoordsModClient.LOGGER.error("Could not write {}", file, e);
		}
	}

	/**
	 * Identifies the current world: the server address on multiplayer, the save
	 * name on singleplayer.
	 *
	 * The address is normalised, because joining the same server as
	 * "Play.Example.com", "play.example.com" and "play.example.com:25565" would
	 * otherwise produce three separate files and look like lost coordinates.
	 */
	private static String worldKey() {
		Minecraft minecraft = Minecraft.getInstance();
		ServerData server = minecraft.getCurrentServer();

		if (server != null && server.ip != null && !server.ip.isBlank()) {
			String address = server.ip.trim().toLowerCase(Locale.ROOT);

			if (address.endsWith(DEFAULT_PORT)) {
				address = address.substring(0, address.length() - DEFAULT_PORT.length());
			}

			return sanitizeFileName(address);
		}

		IntegratedServer singleplayer = minecraft.getSingleplayerServer();

		if (singleplayer != null) {
			return "sp_" + sanitizeFileName(singleplayer.getWorldData().getLevelName());
		}

		return "unknown";
	}

	private static String sanitizeFileName(String raw) {
		String cleaned = raw.replaceAll("[^a-zA-Z0-9._-]", "_");
		return cleaned.isBlank() ? "unknown" : cleaned;
	}
}
