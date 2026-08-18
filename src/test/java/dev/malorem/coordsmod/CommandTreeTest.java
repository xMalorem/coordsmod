package dev.malorem.coordsmod;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Mirrors the argument tree from {@link CoordCommands}. Argument-type choices
 * are where the [+Add] button broke once already: dimension ids contain a ':',
 * which StringArgumentType.word() silently refuses.
 */
class CommandTreeTest {
	private record Source() {
	}

	private CommandDispatcher<Source> dispatcher;

	private static LiteralArgumentBuilder<Source> literal(String name) {
		return LiteralArgumentBuilder.literal(name);
	}

	private static <T> RequiredArgumentBuilder<Source, T> argument(String name, ArgumentType<T> type) {
		return RequiredArgumentBuilder.argument(name, type);
	}

	@BeforeEach
	void buildTree() {
		dispatcher = new CommandDispatcher<>();

		dispatcher.register(literal("cadd")
				.then(argument("x", IntegerArgumentType.integer())
						.then(argument("y", IntegerArgumentType.integer())
								.then(argument("z", IntegerArgumentType.integer())
										.then(argument("name", StringArgumentType.string())
												.then(argument("dimension", StringArgumentType.greedyString())
														.executes(c -> 1)))))));

		dispatcher.register(literal("cren")
				.then(argument("from", StringArgumentType.string())
						.then(argument("to", StringArgumentType.greedyString()).executes(c -> 1))));

		dispatcher.register(literal("cundo").executes(c -> 1));

		dispatcher.register(literal("cworld").executes(c -> 1)
				.then(literal("list").executes(c -> 1))
				.then(literal("merge")
						.then(argument("key", StringArgumentType.greedyString()).executes(c -> 1))));

		LiteralArgumentBuilder<Source> list = literal("cl").executes(c -> 1);

		for (String alias : Dimensions.ALIASES.keySet()) {
			list = list.then(literal(alias).executes(c -> 1));
		}

		dispatcher.register(list.then(literal("all").executes(c -> 1)));
	}

	private boolean parses(String command) {
		var parse = dispatcher.parse(command, new Source());
		return parse.getExceptions().isEmpty()
				&& !parse.getReader().canRead()
				&& parse.getContext().getCommand() != null;
	}

	@Test
	@DisplayName("the [+Add] payload parses, including spaces and modded dimension ids")
	void caddAcceptsSharedPayloads() {
		assertTrue(parses("cadd -180 102 -27 \"Home\" minecraft:overworld"));
		assertTrue(parses("cadd -180 102 -27 \"my base camp\" minecraft:the_nether"));
		assertTrue(parses("cadd -1 -60 -1 \"deep\" someothermod:strange_dim"));
	}

	@Test
	void caddRejectsMissingDimension() {
		assertFalse(parses("cadd 0 64 0 \"x\""));
	}

	@Test
	void renameTakesQuotedAndGreedyNames() {
		assertTrue(parses("cren \"old name\" shiny new name"));
		assertTrue(parses("cren base home"));
		assertFalse(parses("cren base"));
	}

	@Test
	void worldCommands() {
		assertTrue(parses("cworld"));
		assertTrue(parses("cworld list"));
		assertTrue(parses("cworld merge sp_New_World"));
		// Storage keys are server addresses, so dots have to survive.
		assertTrue(parses("cworld merge play.example.com"));
		assertFalse(parses("cworld merge"));
	}

	@Test
	void listAcceptsEveryDimensionAlias() {
		assertTrue(parses("cl"));
		assertTrue(parses("cl all"));

		for (String alias : Dimensions.ALIASES.keySet()) {
			assertTrue(parses("cl " + alias), alias + " should be a valid /cl subcommand");
		}

		assertFalse(parses("cl bogus"));
	}

	@Test
	void undoTakesNoArguments() {
		assertTrue(parses("cundo"));
		assertFalse(parses("cundo something"));
	}
}
