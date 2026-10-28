package com.chaosevo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/** Случайные события мира + простой планировщик задач. */
public final class Events {
	public enum Type {
		METEOR("Метеоритный дождь"), BLOOD_MOON("Кровавая луна"), PLAGUE("Чумной туман"),
		HORDE("Нашествие орды"), STORM("Грозовой шторм");

		public final String ru;

		Type(String ru) {
			this.ru = ru;
		}
	}

	private static final class Task {
		int left;
		final Runnable r;

		Task(int left, Runnable r) {
			this.left = left;
			this.r = r;
		}
	}

	private static final List<Task> tasks = new ArrayList<>();
	private static final Random R = new Random();
	private static int nextEvent = 6000;

	private Events() { }

	public static void later(int ticks, Runnable r) {
		tasks.add(new Task(ticks, r));
	}

	public static void tick(MinecraftServer server) {
		if (!tasks.isEmpty()) {
			List<Task> run = new ArrayList<>();
			Iterator<Task> it = tasks.iterator();
			while (it.hasNext()) {
				Task t = it.next();
				if (--t.left <= 0) {
					run.add(t);
					it.remove();
				}
			}
			for (Task t : run) {
				try {
					t.r.run();
				} catch (Exception e) {
					ChaosMod.LOGGER.error("Ошибка в задаче события", e);
				}
			}
		}
		State s = State.get();
		if (s == null || s.failed) return;
		if (--nextEvent <= 0) {
			int lvl = s.level();
			nextEvent = Math.max(2400, 9000 - lvl * 600) + R.nextInt(1200);
			if (lvl >= 1 && s.activeBoss.isEmpty()) {
				start(server, Type.values()[R.nextInt(Type.values().length)]);
			}
		}
	}

	private static List<ServerPlayer> players(MinecraftServer server) {
		return server.getPlayerList().getPlayers().stream().filter(p -> p.isAlive() && !p.isSpectator()).toList();
	}

	private static boolean ok(ServerPlayer p) {
		return p.isAlive() && !p.isRemoved() && !p.isSpectator();
	}

	public static void start(MinecraftServer server, Type t) {
		List<ServerPlayer> ps = players(server);
		if (ps.isEmpty()) return;
		ChaosMod.say(server, "§4§l⚠ " + t.ru + "!");
		int lvl = State.get() != null ? State.get().level() : 1;
		switch (t) {
			case METEOR -> {
				for (ServerPlayer p : ps) {
					for (int i = 0; i < 6; i++) later(i * 50 + R.nextInt(40), () -> { if (ok(p)) meteor(p); });
				}
			}
			case BLOOD_MOON -> {
				for (int i = 0; i < 30; i++) {
					later(i * 40, () -> {
						for (ServerPlayer p : players(server)) {
							spawnAround(p, R.nextBoolean() ? EntityType.ZOMBIE : EntityType.SKELETON, 3, 10, 20, true);
						}
					});
				}
			}
			case PLAGUE -> {
				for (int i = 0; i < 30; i++) {
					later(i * 40, () -> {
						for (ServerPlayer p : players(server)) {
							p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 0));
							p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
							((ServerLevel) p.level()).sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1, p.getZ(), 25, 2, 1, 2, 0.01);
						}
					});
				}
			}
			case HORDE -> {
				for (ServerPlayer p : ps) {
					spawnAround(p, EntityType.ZOMBIE, 4 + lvl, 10, 18, false);
					spawnAround(p, EntityType.SKELETON, 2 + lvl / 2, 10, 18, false);
					spawnAround(p, EntityType.SPIDER, 2 + lvl / 2, 10, 18, false);
				}
			}
			case STORM -> {
				for (int i = 0; i < 20; i++) {
					later(i * 30, () -> {
						List<ServerPlayer> now = players(server);
						if (now.isEmpty()) return;
						ServerPlayer p = now.get(R.nextInt(now.size()));
						BlockPos pos = p.blockPosition().offset(R.nextInt(17) - 8, 0, R.nextInt(17) - 8);
						EntityType.LIGHTNING_BOLT.spawn((ServerLevel) p.level(), pos, EntitySpawnReason.EVENT);
					});
				}
			}
		}
	}

	private static void meteor(ServerPlayer p) {
		ServerLevel lvl = (ServerLevel) p.level();
		double x = p.getX() + R.nextInt(31) - 15;
		double z = p.getZ() + R.nextInt(31) - 15;
		double y = lvl.canSeeSky(p.blockPosition())
			? lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z) : p.getY();
		lvl.sendParticles(ParticleTypes.FLAME, x, y + 1, z, 60, 0.6, 0.6, 0.6, 0.03);
		later(40, () -> lvl.explode(null, x, y + 1, z, 3.5f, true, Level.ExplosionInteraction.MOB));
	}

	private static void spawnAround(ServerPlayer p, EntityType<? extends Mob> type, int n, int minR, int maxR, boolean fast) {
		ServerLevel lvl = (ServerLevel) p.level();
		for (int i = 0; i < n; i++) {
			for (int tries = 0; tries < 6; tries++) {
				double a = R.nextDouble() * Math.PI * 2;
				double d = minR + R.nextDouble() * (maxR - minR);
				int x = (int) (p.getX() + Math.cos(a) * d);
				int z = (int) (p.getZ() + Math.sin(a) * d);
				int y = lvl.canSeeSky(p.blockPosition())
					? lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : p.blockPosition().getY();
				Mob m = type.spawn(lvl, new BlockPos(x, y, z), EntitySpawnReason.EVENT);
				if (m != null) {
					if (fast) m.addEffect(new MobEffectInstance(MobEffects.SPEED, 2400, 0));
					break;
				}
			}
		}
	}
}
