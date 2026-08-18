package dev.malorem.coordsmod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client-only entrypoint. Chat is still where everything lives by default; the
 * only thing drawn is a single HUD line, and only once you pin a waypoint with
 * /ctrack. Nothing is ever drawn into the world.
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
		LOGGER.info("CoordsMod loaded - chat first, HUD only while tracking.");
	}
}
