package dev.malorem.coordsmod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Everything that used to be a constant somebody else had to live with.
 * Stored as JSON next to the waypoint files.
 */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir()
			.resolve("coordsmod").resolve("config.json");

	/** Needle colours offered by the settings screen, as ARGB. */
	public static final int[] COLORS = {
			0xFFFFDD55, 0xFF55FF55, 0xFF55DDFF, 0xFFFF7777, 0xFFFFFFFF
	};

	public static final String[] COLOR_NAMES = { "Amber", "Green", "Cyan", "Red", "White" };

	public static final double[] ARRIVED_RADII = { 1.0, 2.0, 3.0, 5.0, 8.0 };

	/** 0 means never auto-untrack. */
	public static final double[] AUTO_UNTRACK_RADII = { 0.0, 1.0, 2.0, 3.0, 5.0, 8.0, 16.0 };
	public static final double[] LABEL_DISTANCES = { 128.0, 256.0, 512.0, 1024.0 };

	private static Config instance;

	// ------------------------------------------------------------ persisted

	/** Master switch: hides every drawn element at once, leaving the chat commands alone. */
	public boolean hidden = false;

	public boolean labels = true;
	public double labelMaxDistance = 512.0;
	public double arrivedRadius = 1.0;

	/** Drop the pin automatically once this close. 0 keeps it pinned until told otherwise. */
	public double autoUntrack = 0.0;
	/** TOP or BOTTOM. */
	public String hudPosition = "TOP";
	public int needleColor = 0xFFFFDD55;

	/** World storage key -> "dimensionId|waypointName", so tracking survives a restart. */
	public Map<String, String> tracked = new LinkedHashMap<>();

	// --------------------------------------------------------------- access

	public static Config get() {
		if (instance == null) {
			instance = read();
		}

		return instance;
	}

	public boolean hudAtTop() {
		return !"BOTTOM".equalsIgnoreCase(hudPosition);
	}

	public static void save() {
		Config config = get();

		try {
			Files.createDirectories(FILE.getParent());

			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			CoordsModClient.LOGGER.error("Could not write {}", FILE, e);
		}
	}

	private static Config read() {
		if (!Files.isRegularFile(FILE)) {
			return new Config();
		}

		try (Reader reader = Files.newBufferedReader(FILE)) {
			Config loaded = GSON.fromJson(reader, Config.class);

			if (loaded != null) {
				if (loaded.tracked == null) {
					loaded.tracked = new LinkedHashMap<>();
				}

				return loaded;
			}
		} catch (IOException | RuntimeException e) {
			CoordsModClient.LOGGER.error("Could not read {}, using defaults", FILE, e);
		}

		return new Config();
	}

	/** Steps a value through a preset list, wrapping at the end. */
	public static double cycle(double[] options, double current) {
		for (int i = 0; i < options.length; i++) {
			if (options[i] == current) {
				return options[(i + 1) % options.length];
			}
		}

		return options[0];
	}

	public static int cycleColor(int current) {
		for (int i = 0; i < COLORS.length; i++) {
			if (COLORS[i] == current) {
				return COLORS[(i + 1) % COLORS.length];
			}
		}

		return COLORS[0];
	}

	public static String colorName(int color) {
		for (int i = 0; i < COLORS.length; i++) {
			if (COLORS[i] == color) {
				return COLOR_NAMES[i];
			}
		}

		return "Custom";
	}
}
