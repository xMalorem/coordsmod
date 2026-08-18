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
import java.util.Map;

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

	/** Which world the in-memory map belongs to; null means nothing is loaded. */
	private static String loadedKey;
	private static Map<String, List<Waypoint>> data = new LinkedHashMap<>();

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

		if (waypoints == null || !waypoints.removeIf(w -> w.name.equalsIgnoreCase(name))) {
			return false;
		}

		if (waypoints.isEmpty()) {
			data.remove(dimensionId);
		}

		save();
		return true;
	}

	/** Builds a unique auto-name like "ow-3" when the player does not supply one. */
	public static String autoName(String dimensionId) {
		int next = list(dimensionId).size() + 1;
		String tag = Dimensions.shortTag(dimensionId);

		while (exists(dimensionId, tag + "-" + next)) {
			next++;
		}

		return tag + "-" + next;
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

	// ------------------------------------------------------------ persistence

	private static void ensureLoaded() {
		String key = worldKey();

		if (key.equals(loadedKey)) {
			return;
		}

		loadedKey = key;
		data = new LinkedHashMap<>();
		Path file = DIR.resolve(key + ".json");

		if (!Files.isRegularFile(file)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(file)) {
			Map<String, List<Waypoint>> loaded = GSON.fromJson(reader, MAP_TYPE);

			if (loaded != null) {
				data = loaded;
			}
		} catch (IOException | RuntimeException e) {
			CoordsModClient.LOGGER.error("Could not read {}, starting empty for this world", file, e);
		}
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
	 * folder name on singleplayer.
	 */
	private static String worldKey() {
		Minecraft minecraft = Minecraft.getInstance();
		ServerData server = minecraft.getCurrentServer();

		if (server != null && server.ip != null && !server.ip.isBlank()) {
			return sanitizeFileName(server.ip);
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
