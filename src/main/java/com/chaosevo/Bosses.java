package com.chaosevo;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** 5 боссов на ванильных мобах: фазы, способности, адаптация, арена-клетка. */
public final class Bosses {
	public record Def(String name, EntityType<? extends Mob> type, double scale, double hp, double dmg) { }

	public static final Def[] DEFS = {
		new Def("Гнилой Колосс", EntityType.ZOMBIE, 3.5, 600, 12),
		new Def("Костяной Палач", EntityType.WITHER_SKELETON, 3.0, 800, 14),
		new Def("Грозовой Крипер", EntityType.CREEPER, 4.0, 1000, 0),
		new Def("Пустотный Страж", EntityType.ENDERMAN, 3.0, 1200, 16),
		new Def("Владыка Хаоса", EntityType.RAVAGER, 3.0, 2500, 22),
	};

	private static final String[] CAT = {"ближний бой", "стрелы/снаряды", "огонь", "взрывы", "магия/прочее"};
	private static final Random R = new Random();
	private static final int ARENA_R = 18;

	private static final int[] adapt = new int[5];
	private static int phase = 1;
	private static int seconds = 0;
	private static int missing = 0;

	private Bosses() { }

	// ---------- призыв ----------

	public static String trySummon(ServerLevel lvl, ServerPlayer p) {
		return start(lvl, p, -1, false);
	}

	/** idx = -1 -> следующий по прогрессу. force = без ключа (для команды). */
	public static String start(ServerLevel lvl, ServerPlayer p, int idx, boolean force) {
		State s = State.get();
		if (s == null) return "Состояние не загружено";
		if (s.failed) return "Игра провалена. /chaos reset";
		if (!s.activeBoss.isEmpty()) return "Босс уже призван";
		if (idx < 0) idx = s.bossesDefeated;
		if (idx >= DEFS.length) return "Все боссы повержены";
		if (!force && idx >= 1 && !consume(p, ModContent.KEYS[idx - 1])) {
			return "Нужен ключ босса №" + idx + " (его даёт предыдущий босс)";
		}
		Def def = DEFS[idx];
		BlockPos c = p.blockPosition();
		buildArena(lvl, c, ARENA_R);
		// всех поблизости в арену
		for (ServerPlayer o : lvl.players()) {
			if (o.isAlive() && o.distanceToSqr(p) < 64 * 64) {
				o.connection.teleport(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, o.getYRot(), o.getXRot());
			}
		}
		Mob boss = def.type().spawn(lvl, c.offset(0, 0, 8), EntitySpawnReason.EVENT);
		if (boss == null) return "Не удалось заспавнить босса (мало места?)";

		int players = Math.max(1, (int) lvl.players().stream().filter(o -> !o.isSpectator()).count());
		double mult = 1.0 + 0.7 * (players - 1);
		boss.addTag("chaos_done");
		boss.addTag("chaos_boss");
		boss.addTag("chaos_boss_" + idx);
		boss.setPersistenceRequired();
		set(boss.getAttribute(Attributes.SCALE), def.scale());
		set(boss.getAttribute(Attributes.MAX_HEALTH), def.hp() * mult);
		if (def.dmg() > 0) set(boss.getAttribute(Attributes.ATTACK_DAMAGE), def.dmg());
		set(boss.getAttribute(Attributes.KNOCKBACK_RESISTANCE), 1.0);
		boss.setHealth(boss.getMaxHealth());
		boss.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		boss.setCustomName(Component.literal("☠ " + def.name()).withStyle(ChatFormatting.DARK_RED));
		boss.setCustomNameVisible(true);

		s.activeBoss = boss.getUUID().toString();
		s.activeIndex = idx;
		s.arenaX = c.getX();
		s.arenaY = c.getY();
		s.arenaZ = c.getZ();
		s.arenaR = ARENA_R;
		adapt[0] = adapt[1] = adapt[2] = adapt[3] = adapt[4] = 0;
		phase = 1;
		seconds = 0;
		missing = 0;
		MinecraftServer server = lvl.getServer();
		ChaosMod.say(server, "§4§l⚔ " + def.name() + " пробуждается! Арена заперта до победы.");
		State.save(server);
		return null;
	}

	private static void set(AttributeInstance a, double v) {
		if (a != null) a.setBaseValue(v);
	}

	private static boolean consume(ServerPlayer p, Item key) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(key)) {
				st.shrink(1);
				return true;
			}
		}
		return false;
	}

	// ---------- арена ----------

	private static void buildArena(ServerLevel lvl, BlockPos c, int r) {
		double inner = (r - 1.5) * (r - 1.5);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d2 = dx * dx + dz * dz;
				if (d2 > r * r) continue;
				lvl.setBlock(c.offset(dx, -1, dz), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
				lvl.setBlock(c.offset(dx, 15, dz), Blocks.BARRIER.defaultBlockState(), 2);
				if (d2 > inner) {
					for (int dy = 0; dy < 15; dy++) {
						lvl.setBlock(c.offset(dx, dy, dz), Blocks.BARRIER.defaultBlockState(), 2);
					}
				}
			}
		}
	}

	public static void removeCage(ServerLevel lvl, BlockPos c, int r) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r) continue;
				for (int dy = 0; dy <= 15; dy++) {
					BlockPos pos = c.offset(dx, dy, dz);
					if (lvl.getBlockState(pos).is(Blocks.BARRIER)) {
						lvl.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
	}

	// ---------- тик боссов (раз в секунду) ----------

	public static void tick(MinecraftServer server) {
		State s = State.get();
		if (s == null || s.activeBoss.isEmpty()) return;
		ServerLevel lvl = server.overworld();
		Entity e = lvl.getEntity(UUID.fromString(s.activeBoss));
		if (!(e instanceof Mob boss) || !boss.isAlive()) {
			if (++missing > 45) {
				removeCage(lvl, new BlockPos(s.arenaX, s.arenaY, s.arenaZ), s.arenaR);
				s.activeBoss = "";
				ChaosMod.say(server, "§7Босс исчез. Арена открыта, Сигил можно использовать снова.");
			}
			return;
		}
		missing = 0;
		seconds++;
		List<ServerPlayer> ps = lvl.players().stream()
			.filter(p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(boss) < 70 * 70).toList();
		if (ps.isEmpty()) return;

		int idx = s.activeIndex;
		Def def = DEFS[idx];
		boss.setCustomName(Component.literal("☠ " + def.name() + "  ❤ " + (int) boss.getHealth() + "/"
			+ (int) boss.getMaxHealth()).withStyle(ChatFormatting.DARK_RED));

		ServerPlayer nearest = ps.stream().min(Comparator.comparingDouble(p -> p.distanceToSqr(boss))).get();
		boss.setTarget(nearest);

		float ratio = boss.getHealth() / boss.getMaxHealth();
		int ph = ratio > 0.66f ? 1 : ratio > 0.33f ? 2 : 3;
		if (ph != phase) {
			phase = ph;
			onPhase(server, boss, def, ph);
		}
		abilities(lvl, boss, idx, ph, ps);
	}

	private static void onPhase(MinecraftServer server, Mob boss, Def def, int ph) {
		ChaosMod.say(server, "§c§l" + def.name() + " входит в фазу " + ph + "!");
		boss.addEffect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, ph - 1, false, false));
		if (ph == 3) {
			boss.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MobEffectInstance.INFINITE_DURATION, 1, false, false));
		}
	}

	private static void abilities(ServerLevel lvl, Mob boss, int idx, int ph, List<ServerPlayer> ps) {
		int sec = seconds;
		ServerPlayer rnd = ps.get(R.nextInt(ps.size()));
		switch (idx) {
			case 0 -> {
				if (sec % 12 == 0) horde(lvl, boss, EntityType.ZOMBIE, 2 + ps.size());
				if (ph >= 2 && sec % 8 == 0) slam(lvl, boss, ps, 8, 8f);
				if (ph == 3 && sec % 6 == 0) slam(lvl, boss, ps, 10, 12f);
			}
			case 1 -> {
				if (sec % 6 == 0) fire(ps);
				if (sec % 10 == 0) slam(lvl, boss, ps, 8, 8f);
				if (ph >= 2 && sec % 14 == 0) horde(lvl, boss, EntityType.SKELETON, 2 + ps.size());
				if (ph == 3 && sec % 8 == 0) witherCurse(ps);
			}
			case 2 -> {
				if (sec % 5 == 0) lightning(lvl, ps, ph);
				if (sec % 10 == 0) blast(lvl, boss, rnd, 3f);
				if (ph == 3 && sec % 7 == 0) horde(lvl, boss, EntityType.CREEPER, 2);
			}
			case 3 -> {
				if (sec % 8 == 0) voidPull(boss, rnd);
				if (sec % 6 == 0) blind(ps, 60);
				if (ph >= 2 && sec % 5 == 0) blink(boss, rnd);
				if (ph == 3 && sec % 10 == 0) levitate(ps);
			}
			default -> { // Владыка Хаоса: комбо всего
				if (sec % 8 == 0) slam(lvl, boss, ps, 9, 14f);
				if (sec % 5 == 0) {
					switch (R.nextInt(6)) {
						case 0 -> horde(lvl, boss, EntityType.ZOMBIE, 2 + ps.size());
						case 1 -> fire(ps);
						case 2 -> lightning(lvl, ps, ph + 1);
						case 3 -> voidPull(boss, rnd);
						case 4 -> witherCurse(ps);
						default -> blast(lvl, boss, rnd, 4f);
					}
				}
				if (ph >= 2 && sec % 9 == 0) blink(boss, rnd);
				if (ph == 3 && sec % 12 == 0) { blind(ps, 80); levitate(ps); }
			}
		}
	}

	// ---------- способности ----------

	private static void slam(ServerLevel lvl, Mob boss, List<ServerPlayer> ps, double radius, float dmg) {
		lvl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 0.5, boss.getZ(), 1, 0, 0, 0, 0);
		for (ServerPlayer p : ps) {
			if (p.distanceToSqr(boss) < radius * radius) {
				p.hurtServer(lvl, lvl.damageSources().mobAttack(boss), dmg);
				p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
			}
		}
	}

	private static void horde(ServerLevel lvl, Mob boss, EntityType<? extends Mob> type, int n) {
		for (int i = 0; i < n; i++) {
			BlockPos pos = boss.blockPosition().offset(R.nextInt(11) - 5, 0, R.nextInt(11) - 5);
			Mob m = type.spawn(lvl, pos, EntitySpawnReason.EVENT);
			if (m != null) m.addTag("chaos_done");
		}
	}

	private static void fire(List<ServerPlayer> ps) {
		for (ServerPlayer p : ps) p.igniteForSeconds(4);
	}

	private static void witherCurse(List<ServerPlayer> ps) {
		for (ServerPlayer p : ps) p.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
	}

	private static void lightning(ServerLevel lvl, List<ServerPlayer> ps, int count) {
		for (int i = 0; i < count; i++) {
			ServerPlayer p = ps.get(R.nextInt(ps.size()));
			BlockPos pos = p.blockPosition().offset(R.nextInt(5) - 2, 0, R.nextInt(5) - 2);
			EntityType.LIGHTNING_BOLT.spawn(lvl, pos, EntitySpawnReason.EVENT);
		}
	}

	private static void blast(ServerLevel lvl, Mob boss, ServerPlayer target, float power) {
		lvl.explode(boss, target.getX(), target.getY(), target.getZ(), power, Level.ExplosionInteraction.NONE);
	}

	private static void voidPull(Mob boss, ServerPlayer target) {
		target.connection.teleport(boss.getX(), boss.getY(), boss.getZ(), target.getYRot(), target.getXRot());
	}

	private static void blind(List<ServerPlayer> ps, int ticks) {
		for (ServerPlayer p : ps) p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0));
	}

	private static void blink(Mob boss, ServerPlayer target) {
		boss.setPos(target.getX() + 1.5, target.getY(), target.getZ());
	}

	private static void levitate(List<ServerPlayer> ps) {
		for (ServerPlayer p : ps) p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 1));
	}

	// ---------- адаптация ----------

	private static int category(DamageSource src) {
		if (src.is(DamageTypeTags.IS_PROJECTILE)) return 1;
		if (src.is(DamageTypeTags.IS_FIRE)) return 2;
		if (src.is(DamageTypeTags.IS_EXPLOSION)) return 3;
		if (src.getDirectEntity() instanceof LivingEntity) return 0;
		return 4;
	}

	public static void onDamage(LivingEntity victim, DamageSource src, float dmg) {
		if (!victim.getTags().contains("chaos_boss") || !victim.isAlive()) return;
		int cat = category(src);
		adapt[cat]++;
		for (int c = 0; c < adapt.length; c++) {
			if (c != cat && adapt[c] > 0 && R.nextBoolean()) adapt[c]--;
		}
		int n = adapt[cat];
		float pct = Math.min(0.75f, n * 0.03f);
		victim.heal(dmg * pct);
		if (n == 10 || n == 25 || n == 45) {
			MinecraftServer server = victim.level().getServer();
			if (server != null) {
				ChaosMod.say(server, "§e⚠ Босс адаптируется к урону: " + CAT[cat]
					+ " §7(лечится на " + (int) (pct * 100) + "%). Меняйте тактику!");
			}
		}
	}

	// ---------- смерть ----------

	public static void onDeath(LivingEntity entity, DamageSource src) {
		if (!(entity instanceof Mob boss) || !boss.getTags().contains("chaos_boss")) return;
		if (!(boss.level() instanceof ServerLevel lvl)) return;
		State s = State.get();
		if (s == null) return;
		MinecraftServer server = lvl.getServer();
		int idx = s.activeIndex;
		removeCage(lvl, new BlockPos(s.arenaX, s.arenaY, s.arenaZ), s.arenaR);
		s.activeBoss = "";
		s.bossesDefeated = Math.max(s.bossesDefeated, idx + 1);
		s.wrath += 25;
		s.lives += 2;
		ChaosMod.say(server, "§a§l✔ " + DEFS[idx].name() + " повержен! §7+2 жизни команде.");

		for (ServerPlayer p : lvl.players()) {
			if (!p.isAlive() || p.isSpectator() || p.distanceToSqr(boss) > 80 * 80) continue;
			give(p, new ItemStack(idx < ModContent.KEYS.length ? ModContent.KEYS[idx] : ModContent.HEART));
			give(p, new ItemStack(ModContent.VOID_SHARD, 3 + R.nextInt(4)));
			give(p, new ItemStack(ModContent.BLOOD_CRYSTAL, 2 + R.nextInt(3)));
			give(p, new ItemStack(ModContent.STORM_CORE, 1 + R.nextInt(3)));
		}
		if (idx >= DEFS.length - 1) {
			ChaosMod.say(server, "§6§l★★★ ХАОС ПОБЕЖДЁН! Вы прошли мод! ★★★");
		}
		State.save(server);
	}

	private static void give(ServerPlayer p, ItemStack st) {
		if (!p.getInventory().add(st)) p.drop(st, false);
	}
}
