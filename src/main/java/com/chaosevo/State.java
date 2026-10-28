package com.chaosevo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Общее состояние команды. Хранится в JSON-файле в папке мира. */
public class State {
	public double wrath = 0;
	public int deaths = 0;
	public int lives = 10;
	public int bossesDefeated = 0;
	public String activeBoss = "";
	public int activeIndex = -1;
	public int arenaX, arenaY, arenaZ, arenaR;
	public boolean failed = false;

	private static State INSTANCE;

	public static State get() {
		return INSTANCE;
	}

	private static Path path(MinecraftServer s) {
		return s.getWorldPath(LevelResource.ROOT).resolve("chaosevo_state.json");
	}

	public static void load(MinecraftServer s) {
		State st = null;
		try {
			Path p = path(s);
			if (Files.exists(p)) {
				st = new Gson().fromJson(Files.readString(p), State.class);
			}
		} catch (Exception e) {
			ChaosMod.LOGGER.error("Не удалось прочитать состояние", e);
		}
		INSTANCE = st != null ? st : new State();
	}

	public static void save(MinecraftServer s) {
		if (INSTANCE == null) return;
		try {
			Files.writeString(path(s), new GsonBuilder().setPrettyPrinting().create().toJson(INSTANCE));
		} catch (IOException e) {
			ChaosMod.LOGGER.error("Не удалось сохранить состояние", e);
		}
	}

	/** Уровень гнева мира 0..10 */
	public int level() {
		return (int) Math.min(10, wrath / 50.0);
	}
}
