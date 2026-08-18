package dev.malorem.coordsmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** Registers every client-side command. Nothing here touches the server. */
public final class CoordCommands {
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
		List<Waypoint> waypoints = CoordStore.list(dimensionId);

		if (waypoints.isEmpty()) {
			source.sendFeedback(Chat.info("No coords saved in " + Dimensions.displayName(dimensionId) + "."));
			return 0;
		}

		source.sendFeedback(Chat.header(dimensionId, waypoints.size()));

		for (int i = 0; i < waypoints.size(); i++) {
			source.sendFeedback(Chat.entry(i + 1, waypoints.get(i)));
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

		source.sendFeedback(Chat.info("Deleted " + name + "."));
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
		String unique = name;
		int suffix = 2;

		while (CoordStore.exists(dimensionId, unique)) {
			unique = name + "-" + suffix++;
		}

		CoordStore.put(dimensionId, new Waypoint(unique, x, y, z, System.currentTimeMillis()));
		source.sendFeedback(Chat.info("Added " + unique + "  " + x + ", " + y + ", " + z
				+ " to " + Dimensions.displayName(dimensionId) + "."));

		return 1;
	}

	private static int help(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		source.sendFeedback(Chat.helpHeader());

		source.sendFeedback(Chat.helpLine("/cs", "Save where you are standing, auto-named"));
		source.sendFeedback(Chat.helpLine("/cs <name>", "Save under a name - same name overwrites"));
		source.sendFeedback(Chat.helpLine("/cl", "List coords in the dimension you are in"));
		source.sendFeedback(Chat.helpLine("/cl ow|nether|end", "List one dimension"));
		source.sendFeedback(Chat.helpLine("/cl all", "List every dimension"));
		source.sendFeedback(Chat.helpLine("/cdel <name>", "Delete a coord from this dimension"));
		source.sendFeedback(Chat.helpLine("/sc <player>", "Whisper your current position to a player"));
		source.sendFeedback(Chat.helpLine("/sc <player> <name>", "Whisper a saved coord to a player"));
		source.sendFeedback(Chat.helpLine("/scall", "Send your current position to public chat"));
		source.sendFeedback(Chat.helpLine("/scall <name>", "Send a saved coord to public chat"));
		source.sendFeedback(Chat.helpLine("/cadd <x> <y> <z> <name> <dim>", "Save a shared coord - the [+Add] button"));
		source.sendFeedback(Chat.helpLine("/chelp", "This list"));

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
