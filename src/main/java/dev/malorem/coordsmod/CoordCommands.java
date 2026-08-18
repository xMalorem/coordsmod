package dev.malorem.coordsmod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** Registers every client-side command. Nothing here touches the server. */
public final class CoordCommands {
	/** Chat scrollback is finite; past this a listing is noise rather than information. */
	private static final int MAX_LISTED = 20;

	private CoordCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			// /cs [name] - save where you are standing
			dispatcher.register(literal("cs")
					.executes(ctx -> save(ctx, null))
					.then(argument("name", StringArgumentType.greedyString())
							.executes(ctx -> save(ctx, StringArgumentType.getString(ctx, "name")))));

			// /cl [ow|overworld|nether|n|end|e|all] - bare /cl uses the dimension you are in
			LiteralArgumentBuilder<FabricClientCommandSource> list = literal("cl")
					.executes(ctx -> listOne(ctx, currentDimension(ctx)));

			for (Map.Entry<String, String> alias : Dimensions.ALIASES.entrySet()) {
				String dimensionId = alias.getValue();
				list = list.then(literal(alias.getKey()).executes(ctx -> listOne(ctx, dimensionId)));
			}

			dispatcher.register(list.then(literal("all").executes(CoordCommands::listAll)));

			// /cdel <name> - remove a waypoint from the current dimension
			dispatcher.register(literal("cdel")
					.then(argument("name", StringArgumentType.greedyString())
							.suggests((ctx, builder) -> SharedSuggestionProvider
									.suggest(namesIn(currentDimension(ctx)), builder))
							.executes(ctx -> delete(ctx, StringArgumentType.getString(ctx, "name")))));

			// /cundo - put back the last thing /cdel removed
			dispatcher.register(literal("cundo").executes(CoordCommands::undo));

			// /cren <old> <new> - rename in place, keeping the position
			dispatcher.register(literal("cren")
					.then(argument("from", StringArgumentType.string())
							.suggests((ctx, builder) -> SharedSuggestionProvider
									.suggest(namesIn(currentDimension(ctx)), builder))
							.then(argument("to", StringArgumentType.greedyString())
									.executes(CoordCommands::rename))));

			// /sc <player> [name] - whisper a waypoint to one player
			dispatcher.register(literal("sc")
					.then(argument("player", StringArgumentType.word())
							.suggests((ctx, builder) -> SharedSuggestionProvider
									.suggest(ShareHandler.onlinePlayerNames(), builder))
							.executes(ctx -> share(ctx, StringArgumentType.getString(ctx, "player"), null))
							.then(argument("name", StringArgumentType.greedyString())
									.suggests((ctx, builder) -> SharedSuggestionProvider
											.suggest(namesIn(currentDimension(ctx)), builder))
									.executes(ctx -> share(ctx,
											StringArgumentType.getString(ctx, "player"),
											StringArgumentType.getString(ctx, "name"))))));

			// /scall [name] - send a waypoint to public chat
			dispatcher.register(literal("scall")
					.executes(ctx -> share(ctx, null, null))
					.then(argument("name", StringArgumentType.greedyString())
							.suggests((ctx, builder) -> SharedSuggestionProvider
									.suggest(namesIn(currentDimension(ctx)), builder))
							.executes(ctx -> share(ctx, null, StringArgumentType.getString(ctx, "name")))));

			// /cworld - which storage file is in use, and merging between them
			dispatcher.register(literal("cworld")
					.executes(CoordCommands::world)
					.then(literal("list").executes(CoordCommands::worldList))
					.then(literal("merge")
							.then(argument("key", StringArgumentType.greedyString())
									.suggests((ctx, builder) -> SharedSuggestionProvider
											.suggest(CoordStore.worldKeys(), builder))
									.executes(CoordCommands::worldMerge))));

			// /ctrack <name> - pin one waypoint to the HUD; /cuntrack removes it
			dispatcher.register(literal("ctrack")
					.then(argument("name", StringArgumentType.greedyString())
							.suggests((ctx, builder) -> SharedSuggestionProvider
									.suggest(namesIn(currentDimension(ctx)), builder))
							.executes(CoordCommands::track)));

			dispatcher.register(literal("cuntrack").executes(CoordCommands::untrack));

			// screens
			dispatcher.register(literal("ckeybind").executes(ctx -> openScreen(new KeybindScreen(null))));
			dispatcher.register(literal("cgui").executes(ctx -> openScreen(new WaypointScreen(null))));
			dispatcher.register(literal("cconfig").executes(ctx -> openScreen(new ConfigScreen(null))));
			dispatcher.register(literal("clabels").executes(CoordCommands::toggleLabels));

			// /chelp - list every command
			dispatcher.register(literal("chelp").executes(CoordCommands::help));

			// /cadd <x> <y> <z> <name> <dimension> - what the [+Add] button runs.
			// Dimension goes last and greedy: ids contain a ':', which word() rejects.
			dispatcher.register(literal("cadd")
					.then(argument("x", IntegerArgumentType.integer())
							.then(argument("y", IntegerArgumentType.integer())
									.then(argument("z", IntegerArgumentType.integer())
											.then(argument("name", StringArgumentType.string())
													.then(argument("dimension", StringArgumentType.greedyString())
															.executes(CoordCommands::add)))))));
		});
	}

	// ----------------------------------------------------------------- commands

	private static int save(CommandContext<FabricClientCommandSource> ctx, String rawName) {
		FabricClientCommandSource source = ctx.getSource();
		String dimensionId = currentDimension(ctx);
		BlockPos pos = source.getPlayer().blockPosition();

		String name = rawName == null
				? CoordStore.autoName(dimensionId)
				: CoordStore.sanitizeName(rawName);

		if (name.isEmpty()) {
			source.sendError(Chat.error("That name is empty after cleanup - pick another."));
			return 0;
		}

		boolean replaced = CoordStore.exists(dimensionId, name);
		Waypoint waypoint = new Waypoint(name, pos.getX(), pos.getY(), pos.getZ(), System.currentTimeMillis());
		CoordStore.put(dimensionId, waypoint);
		source.sendFeedback(Chat.saved(replaced, waypoint, dimensionId));

		return 1;
	}

	private static int listOne(CommandContext<FabricClientCommandSource> ctx, String dimensionId) {
		FabricClientCommandSource source = ctx.getSource();
		List<Waypoint> waypoints = new ArrayList<>(CoordStore.list(dimensionId));

		if (waypoints.isEmpty()) {
			source.sendFeedback(Chat.info("No coords saved in " + Dimensions.displayName(dimensionId) + "."));
			return 0;
		}

		// A distance is only meaningful inside the dimension you are standing in.
		Vec3 viewer = dimensionId.equals(currentDimension(ctx)) ? source.getPlayer().position() : null;

		if (viewer != null) {
			waypoints.sort(Comparator.comparingDouble(w -> Geo.horizontalDistance(viewer, w)));
		}

		source.sendFeedback(Chat.header(dimensionId, waypoints.size()));
		int shown = Math.min(waypoints.size(), MAX_LISTED);

		for (int i = 0; i < shown; i++) {
			source.sendFeedback(Chat.entry(i + 1, waypoints.get(i), dimensionId, viewer));
		}

		if (waypoints.size() > shown) {
			source.sendFeedback(Chat.more(waypoints.size() - shown));
		}

		return waypoints.size();
	}

	private static int listAll(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		Map<String, List<Waypoint>> all = CoordStore.all();

		if (all.isEmpty()) {
			source.sendFeedback(Chat.info("No coords saved in this world yet."));
			return 0;
		}

		int total = 0;

		for (String dimensionId : orderedDimensions(all)) {
			total += listOne(ctx, dimensionId);
		}

		return total;
	}

	private static int delete(CommandContext<FabricClientCommandSource> ctx, String rawName) {
		FabricClientCommandSource source = ctx.getSource();
		String dimensionId = currentDimension(ctx);
		String name = CoordStore.sanitizeName(rawName);

		if (!CoordStore.remove(dimensionId, name)) {
			source.sendError(Chat.error("No coord named \"" + name + "\" in "
					+ Dimensions.displayName(dimensionId) + "."));
			return 0;
		}

		source.sendFeedback(Chat.info("Deleted " + name + ".  /cundo puts it back."));
		return 1;
	}

	private static int undo(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		Waypoint restored = CoordStore.undo();

		if (restored == null) {
			source.sendError(Chat.error("Nothing to undo."));
			return 0;
		}

		source.sendFeedback(Chat.info("Restored " + restored.name + "  " + Chat.coords(restored) + "."));
		return 1;
	}

	private static int rename(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		String dimensionId = currentDimension(ctx);
		String from = CoordStore.sanitizeName(StringArgumentType.getString(ctx, "from"));
		String to = CoordStore.sanitizeName(StringArgumentType.getString(ctx, "to"));

		if (to.isEmpty()) {
			source.sendError(Chat.error("That new name is empty after cleanup - pick another."));
			return 0;
		}

		String result = CoordStore.rename(dimensionId, from, to);

		if (result == null) {
			source.sendError(Chat.error("No coord named \"" + from + "\" in "
					+ Dimensions.displayName(dimensionId) + "."));
			return 0;
		}

		source.sendFeedback(Chat.info("Renamed " + from + " to " + result + "."));
		return 1;
	}

	/** Backs both /sc (player != null) and /scall (player == null). */
	private static int share(CommandContext<FabricClientCommandSource> ctx, String player, String rawName) {
		FabricClientCommandSource source = ctx.getSource();
		String dimensionId = currentDimension(ctx);

		String name;
		int x;
		int y;
		int z;

		if (rawName == null) {
			// No name given: share where the player is standing right now.
			BlockPos pos = source.getPlayer().blockPosition();
			name = "here";
			x = pos.getX();
			y = pos.getY();
			z = pos.getZ();
		} else {
			String wanted = CoordStore.sanitizeName(rawName);
			Waypoint waypoint = CoordStore.find(dimensionId, wanted);

			if (waypoint == null) {
				waypoint = CoordStore.findAnywhere(wanted);
			}

			if (waypoint == null) {
				source.sendError(Chat.error("No coord named \"" + wanted + "\" to share."));
				return 0;
			}

			name = waypoint.name;
			x = waypoint.x;
			y = waypoint.y;
			z = waypoint.z;
		}

		String line = ShareHandler.payload(name, x, y, z, dimensionId);

		if (player == null) {
			ShareHandler.shareAll(line);
		} else {
			ShareHandler.shareWith(player, line);
			source.sendFeedback(Chat.info("Sent " + name + " to " + player + "."));
		}

		return 1;
	}

	private static int add(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		int x = IntegerArgumentType.getInteger(ctx, "x");
		int y = IntegerArgumentType.getInteger(ctx, "y");
		int z = IntegerArgumentType.getInteger(ctx, "z");
		String dimensionId = StringArgumentType.getString(ctx, "dimension").trim();
		String name = CoordStore.sanitizeName(StringArgumentType.getString(ctx, "name"));

		if (name.isEmpty()) {
			source.sendError(Chat.error("That shared coord had no usable name."));
			return 0;
		}

		// Keep both copies rather than silently overwriting someone else's point.
		String unique = CoordStore.uniqueName(dimensionId, name);
		CoordStore.put(dimensionId, new Waypoint(unique, x, y, z, System.currentTimeMillis()));
		source.sendFeedback(Chat.info("Added " + unique + "  " + x + ", " + y + ", " + z
				+ " to " + Dimensions.displayName(dimensionId) + "."));

		return 1;
	}

	// --------------------------------------------------------- tracking, GUIs

	private static int track(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		String dimensionId = currentDimension(ctx);
		String name = CoordStore.sanitizeName(StringArgumentType.getString(ctx, "name"));
		Waypoint waypoint = CoordStore.find(dimensionId, name);

		if (waypoint == null) {
			source.sendError(Chat.error("No coord named \"" + name + "\" in "
					+ Dimensions.displayName(dimensionId) + "."));
			return 0;
		}

		Tracker.track(dimensionId, waypoint);
		source.sendFeedback(Chat.info("Tracking " + waypoint.name + " on your HUD. /cuntrack to stop."));
		return 1;
	}

	private static int untrack(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();

		if (!Tracker.clear()) {
			source.sendError(Chat.error("Nothing is being tracked."));
			return 0;
		}

		source.sendFeedback(Chat.info("Stopped tracking."));
		return 1;
	}

	private static int toggleLabels(CommandContext<FabricClientCommandSource> ctx) {
		Config config = Config.get();
		config.labels = !config.labels;
		Config.save();

		ctx.getSource().sendFeedback(Chat.info("In-world labels " + (config.labels ? "on" : "off") + "."));
		return 1;
	}

	/** Screens cannot open while the command is still executing, so defer a tick. */
	private static int openScreen(Screen screen) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreenAndShow(screen));
		return 1;
	}

	// -------------------------------------------------------------- /cworld

	private static int world(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		source.sendFeedback(Chat.info("Storing coords under \"" + CoordStore.currentKey() + "\" ("
				+ CoordStore.total() + " saved)."));
		source.sendFeedback(Chat.info("/cworld list shows every stored world."));
		return 1;
	}

	private static int worldList(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		List<String> keys = CoordStore.worldKeys();

		if (keys.isEmpty()) {
			source.sendFeedback(Chat.info("No stored worlds yet."));
			return 0;
		}

		String current = CoordStore.currentKey();
		source.sendFeedback(Chat.info("Stored worlds (" + keys.size() + "):"));

		for (String key : keys) {
			source.sendFeedback(Chat.info("  " + key + (key.equals(current) ? "  <- current" : "")));
		}

		return keys.size();
	}

	private static int worldMerge(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		String key = StringArgumentType.getString(ctx, "key").trim();
		int merged = CoordStore.mergeFrom(key);

		if (merged == -1) {
			source.sendError(Chat.error("\"" + key + "\" is the world you are already in."));
			return 0;
		}

		if (merged == -2) {
			source.sendError(Chat.error("No stored world called \"" + key + "\". Try /cworld list."));
			return 0;
		}

		source.sendFeedback(Chat.info("Merged " + merged + " coord" + (merged == 1 ? "" : "s")
				+ " from \"" + key + "\"."));
		return merged;
	}

	// ---------------------------------------------------------------- /chelp

	private static int help(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		source.sendFeedback(Chat.helpHeader());

		source.sendFeedback(Chat.helpLine("/cs", "Save where you are standing, auto-named"));
		source.sendFeedback(Chat.helpLine("/cs <name>", "Save under a name - same name overwrites"));
		source.sendFeedback(Chat.helpLine("/cl", "List this dimension, nearest first"));
		source.sendFeedback(Chat.helpLine("/cl ow|nether|end", "List one dimension"));
		source.sendFeedback(Chat.helpLine("/cl all", "List every dimension"));
		source.sendFeedback(Chat.helpLine("/cdel <name>", "Delete a coord from this dimension"));
		source.sendFeedback(Chat.helpLine("/cundo", "Put back the last deleted coord"));
		source.sendFeedback(Chat.helpLine("/cren <old> <new>", "Rename a coord, keeping its position"));
		source.sendFeedback(Chat.helpLine("/sc <player>", "Whisper your current position to a player"));
		source.sendFeedback(Chat.helpLine("/sc <player> <name>", "Whisper a saved coord to a player"));
		source.sendFeedback(Chat.helpLine("/scall", "Send your current position to public chat"));
		source.sendFeedback(Chat.helpLine("/scall <name>", "Send a saved coord to public chat"));
		source.sendFeedback(Chat.helpLine("/ctrack <name>", "Pin a coord to your HUD"));
		source.sendFeedback(Chat.helpLine("/cuntrack", "Stop tracking"));
		source.sendFeedback(Chat.helpLine("/cgui", "Open the waypoint manager"));
		source.sendFeedback(Chat.helpLine("/clabels", "Toggle in-world waypoint labels"));
		source.sendFeedback(Chat.helpLine("/cconfig", "Settings"));
		source.sendFeedback(Chat.helpLine("/ckeybind", "Bind the quick-save and list keys"));
		source.sendFeedback(Chat.helpLine("/cworld", "Which storage file this world uses"));
		source.sendFeedback(Chat.helpLine("/cworld merge <key>", "Pull coords in from another stored world"));
		source.sendFeedback(Chat.helpLine("/cadd <x> <y> <z> <name> <dim>", "Save a shared coord - the [+Add] button"));
		source.sendFeedback(Chat.helpLine("/chelp", "This list"));
		source.sendFeedback(Chat.info("Click any listed coord to copy it. Deaths are saved automatically."));

		return 1;
	}

	// ------------------------------------------------------------------ helpers

	private static String currentDimension(CommandContext<FabricClientCommandSource> ctx) {
		return ctx.getSource().getLevel().dimension().identifier().toString();
	}

	private static List<String> namesIn(String dimensionId) {
		List<String> names = new ArrayList<>();

		for (Waypoint waypoint : CoordStore.list(dimensionId)) {
			names.add(waypoint.name);
		}

		return names;
	}

	/** Overworld, Nether, End first; anything modded after them. */
	private static List<String> orderedDimensions(Map<String, List<Waypoint>> all) {
		List<String> ordered = new ArrayList<>();

		for (String known : List.of(Dimensions.OVERWORLD, Dimensions.NETHER, Dimensions.END)) {
			if (all.containsKey(known)) {
				ordered.add(known);
			}
		}

		for (String dimensionId : all.keySet()) {
			if (!ordered.contains(dimensionId)) {
				ordered.add(dimensionId);
			}
		}

		return ordered;
	}
}
