package com.chaosevo;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChaosMod implements ModInitializer {
	public static final String ID = "chaosevo";
	public static final Logger LOGGER = LoggerFactory.getLogger(ID);

	private static int tick = 0;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(ID, path);
	}

	public static void say(MinecraftServer server, String msg) {
		server.getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
	}

	@Override
	public void onInitialize() {
		ModContent.init();

		// Руды (JSON фич лежат в data/chaosevo/worldgen/...). Работает только в НОВЫХ чанках!
		for (String ore : new String[] {"void_ore", "blood_ore", "storm_ore"}) {
			BiomeModifications.addFeature(
				BiomeSelectors.foundInOverworld(),
				GenerationStep.Decoration.UNDERGROUND_ORES,
				ResourceKey.create(Registries.PLACED_FEATURE, id(ore)));
		}

		ServerLifecycleEvents.SERVER_STARTED.register(State::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(State::save);
		ServerTickEvents.END_SERVER_TICK.register(ChaosMod::onTick);
		ServerEntityEvents.ENTITY_LOAD.register(Mutations::onLoad);

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			Mutations.onDamage(entity, source, taken);
			Bosses.onDamage(entity, source, taken);
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Mutations.onDeath(entity, source);
			Bosses.onDeath(entity, source);
			State s = State.get();
			if (s == null) return;
			if (entity instanceof ServerPlayer p) {
				onPlayerDeath(p, s);
			} else if (entity instanceof Monster && source.getEntity() instanceof ServerPlayer) {
				s.wrath += 0.4;
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, sel) -> ChaosCommands.register(dispatcher));
		LOGGER.info("Хаос: Эволюция загружен");
	}

	private static void onPlayerDeath(ServerPlayer p, State s) {
		MinecraftServer server = p.level().getServer();
		if (server == null || s.failed) return;
		s.deaths++;
		s.lives--;
		s.wrath += 10;
		say(server, "§4☠ " + p.getName().getString() + " погиб. §cСмертей: " + s.deaths
			+ " §7| §aОсталось жизней: " + Math.max(0, s.lives));
		if (s.lives <= 0) {
			fail(server, s);
		}
	}

	public static void fail(MinecraftServer server, State s) {
		s.failed = true;
		say(server, "§4§l=== ПРОВАЛ: жизни команды закончились ===");
		say(server, "§7Мир победил. (/chaos reset чтобы начать заново)");
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.setGameMode(GameType.SPECTATOR);
		}
	}

	private static void onTick(MinecraftServer server) {
		tick++;
		State s = State.get();
		if (s == null) return;
		boolean anyone = !server.getPlayerList().getPlayers().isEmpty();
		if (anyone && !s.failed) {
			if (tick % 600 == 0) s.wrath += 1;
			if (tick % 20 == 0) Bosses.tick(server);
			if (tick % 100 == 0) hud(server, s);
		}
		Events.tick(server);
		if (tick % 1200 == 0) State.save(server);
	}

	private static void hud(MinecraftServer server, State s) {
		String text = "§cГнев: ур." + s.level() + " §7| §aЖизни: " + s.lives
			+ " §7| §4Смертей: " + s.deaths + " §7| §6Боссов: " + s.bossesDefeated + "/" + Bosses.DEFS.length;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.displayClientMessage(Component.literal(text), true);
		}
	}
}
