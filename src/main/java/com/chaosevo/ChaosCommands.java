package com.chaosevo;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** /chaos status | reset | wrath N | lives N | boss 1-5 | event <имя>
 *  Команды не защищены правами: мод для друзей. Если нужно, добавьте .requires(...). */
public final class ChaosCommands {
	private ChaosCommands() { }

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("chaos")
			.then(Commands.literal("status").executes(c -> status(c.getSource())))
			.then(Commands.literal("reset").executes(c -> reset(c.getSource())))
			.then(Commands.literal("wrath").then(Commands.argument("value", IntegerArgumentType.integer(0, 100000))
				.executes(c -> {
					State s = State.get();
					s.wrath = IntegerArgumentType.getInteger(c, "value");
					return msg(c.getSource(), "Гнев мира: " + s.wrath + " (ур. " + s.level() + ")");
				})))
			.then(Commands.literal("lives").then(Commands.argument("value", IntegerArgumentType.integer(0, 1000))
				.executes(c -> {
					State s = State.get();
					s.lives = IntegerArgumentType.getInteger(c, "value");
					return msg(c.getSource(), "Жизней: " + s.lives);
				})))
			.then(Commands.literal("boss").then(Commands.argument("n", IntegerArgumentType.integer(1, Bosses.DEFS.length))
				.executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					String err = Bosses.start(c.getSource().getServer().overworld(), p,
						IntegerArgumentType.getInteger(c, "n") - 1, true);
					return msg(c.getSource(), err == null ? "Босс призван" : err);
				})))
			.then(Commands.literal("event").then(Commands.argument("name", StringArgumentType.word())
				.executes(c -> {
					String n = StringArgumentType.getString(c, "name").toUpperCase();
					try {
						Events.start(c.getSource().getServer(), Events.Type.valueOf(n));
						return 1;
					} catch (IllegalArgumentException e) {
						return msg(c.getSource(), "События: meteor, blood_moon, plague, horde, storm");
					}
				}))));
	}

	private static int msg(CommandSourceStack src, String text) {
		src.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int status(CommandSourceStack src) {
		State s = State.get();
		return msg(src, "Гнев: " + (int) s.wrath + " (ур. " + s.level() + ") | Жизни: " + s.lives
			+ " | Смертей: " + s.deaths + " | Боссов: " + s.bossesDefeated + "/" + Bosses.DEFS.length
			+ (s.failed ? " | ПРОВАЛ" : ""));
	}

	private static int reset(CommandSourceStack src) {
		State s = State.get();
		MinecraftServer server = src.getServer();
		if (!s.activeBoss.isEmpty()) {
			Bosses.removeCage(server.overworld(), new net.minecraft.core.BlockPos(s.arenaX, s.arenaY, s.arenaZ), s.arenaR);
		}
		s.wrath = 0;
		s.deaths = 0;
		s.lives = 10;
		s.bossesDefeated = 0;
		s.activeBoss = "";
		s.activeIndex = -1;
		s.failed = false;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.setGameMode(GameType.SURVIVAL);
		}
		State.save(server);
		return msg(src, "Прогресс сброшен. 10 жизней, гнев 0.");
	}
}
