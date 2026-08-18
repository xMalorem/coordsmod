package dev.malorem.coordsmod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client-only entrypoint. Chat is still the primary surface: every command
 * answers there, and the drawn parts - the tracker needle and the in-world
 * labels - are both optional and configurable via /cconfig.
 */
public class CoordsModClient implements ClientModInitializer {
	public static final String MOD_ID = "coordsmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		CoordCommands.register();
		ShareHandler.register();
		DeathTracker.register();
		PortalTracker.register();
		Keybinds.register();
		Tracker.register();
		Labels.register();
		LOGGER.info("CoordsMod loaded - chat first, with an optional tracker and in-world labels.");
	}
}
