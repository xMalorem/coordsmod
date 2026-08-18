package dev.malorem.coordsmod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client-only entrypoint. This mod deliberately renders nothing: no HUD, no
 * overlay, no world markers. Every result goes to chat.
 */
public class CoordsModClient implements ClientModInitializer {
	public static final String MOD_ID = "coordsmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		CoordCommands.register();
		ShareHandler.register();
		DeathTracker.register();
		LOGGER.info("CoordsMod loaded - chat only, nothing is rendered.");
	}
}
