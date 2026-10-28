package com.chaosevo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 15 мутаций, которые вешаются на ванильных монстров. Без своих моделей. */
public final class Mutations {
	public enum Trait {
		BRUTE("Громила"), TITAN("Титан"), PHANTOM("Призрак"), THIEF("Вор"), EXPLOSIVE("Взрывной"),
		VAMPIRE("Вампир"), TOXIC("Ядовитый"), LEAPER("Прыгун"), REGEN("Регенератор"), FROST("Ледяной"),
		FIERY("Огненный"), STORM("Грозовой"), SHIELDED("Щитовой"), SWARM("Роевой"), BLINK("Телепортёр");

		public final String ru;

		Trait(String ru) {
			this.ru = ru;
		}

		public String tag() {
			return "chaos_t_" + name();
		}
	}

	private static boolean has(Entity e, Trait t) {
		return e.entityTags().contains(t.tag());
	}

	private static void mod(LivingEntity e, Holder<Attribute> attr, String id, double amount,
			AttributeModifier.Operation op) {
		AttributeInstance inst = e.getAttribute(attr);
		if (inst == null) return;
		Identifier mid = ChaosMod.id(id);
		if (inst.getModifier(mid) != null) inst.removeModifier(mid);
		inst.addPermanentModifier(new AttributeModifier(mid, amount, op));
	}

	private static void effect(LivingEntity e, Holder<MobEffect> eff, int amp) {
		e.addEffect(new MobEffectInstance(eff, MobEffectInstance.INFINITE_DURATION, amp, false, false));
	}

	public static void onLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof Monster m)) return;
		State s = State.get();
		if (s == null) return;
		if (m.entityTags().contains("chaos_done")) return;
		m.addTag("chaos_done");

		int lvl = s.level();
		if (lvl <= 0) return;
		EntityType<?> type = m.getType();
		if (type == EntityTypes.WITHER || type == EntityTypes.WARDEN || type == EntityTypes.ELDER_GUARDIAN) return;

		ThreadLocalRandom r = ThreadLocalRandom.current();
		double chance = 0.10 + 0.07 * lvl;
		if (r.nextDouble() > chance) return;

		int count = 1 + (lvl >= 5 ? 1 : 0) + (lvl >= 8 ? 1 : 0);
		List<Trait> pool = new ArrayList<>(List.of(Trait.values()));
		Collections.shuffle(pool);
		List<Trait> picked = pool.subList(0, count);

		// базовое усиление от уровня гнева
		mod(m, Attributes.MAX_HEALTH, "base_hp", 0.08 * lvl, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		mod(m, Attributes.ATTACK_DAMAGE, "base_dmg", 0.05 * lvl, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

		StringBuilder names = new StringBuilder();
		for (Trait t : picked) {
			m.addTag(t.tag());
			apply(m, t);
			if (names.length() > 0) names.append(" ");
			names.append(t.ru);
		}
		m.setHealth(m.getMaxHealth());

		MutableComponent name = Component.literal("[" + names + "] ").withStyle(ChatFormatting.RED);
		name.append(m.getType().getDescription());
		m.setCustomName(name);
		m.setCustomNameVisible(true);
	}

	private static void apply(Monster m, Trait t) {
		switch (t) {
			case BRUTE -> {
				mod(m, Attributes.SCALE, "t_scale", 0.6, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				mod(m, Attributes.MAX_HEALTH, "t_hp", 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				mod(m, Attributes.ATTACK_DAMAGE, "t_dmg", 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				mod(m, Attributes.KNOCKBACK_RESISTANCE, "t_kb", 0.5, AttributeModifier.Operation.ADD_VALUE);
			}
			case TITAN -> {
				mod(m, Attributes.SCALE, "t_scale", 1.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				mod(m, Attributes.MAX_HEALTH, "t_hp", 3.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				mod(m, Attributes.ATTACK_DAMAGE, "t_dmg", 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				mod(m, Attributes.MOVEMENT_SPEED, "t_spd", -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			}
			case PHANTOM -> {
				effect(m, MobEffects.INVISIBILITY, 0);
				mod(m, Attributes.MOVEMENT_SPEED, "t_spd", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			}
			case THIEF -> mod(m, Attributes.MOVEMENT_SPEED, "t_spd", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			case VAMPIRE -> mod(m, Attributes.ATTACK_DAMAGE, "t_dmg", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			case LEAPER -> {
				effect(m, MobEffects.JUMP_BOOST, 3);
				mod(m, Attributes.MOVEMENT_SPEED, "t_spd", 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			}
			case REGEN -> effect(m, MobEffects.REGENERATION, 1);
			case FIERY -> effect(m, MobEffects.FIRE_RESISTANCE, 0);
			case SHIELDED -> effect(m, MobEffects.RESISTANCE, 1);
			case SWARM -> mod(m, Attributes.SCALE, "t_scale", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			default -> { }
		}
	}

	/** Эффекты при ударе мутанта по игроку. */
	public static void onDamage(LivingEntity victim, DamageSource source, float dmg) {
		if (!(victim instanceof ServerPlayer p)) return;
		if (!(source.getEntity() instanceof LivingEntity attacker)) return;
		if (attacker.entityTags().isEmpty()) return;
		ServerLevel level = (ServerLevel) p.level();
		ThreadLocalRandom r = ThreadLocalRandom.current();

		if (has(attacker, Trait.TOXIC)) {
			p.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
		}
		if (has(attacker, Trait.FROST)) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1));
			p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 100, 0));
		}
		if (has(attacker, Trait.FIERY)) {
			p.igniteForSeconds(5);
		}
		if (has(attacker, Trait.VAMPIRE)) {
			attacker.heal(dmg * 0.5f);
		}
		if (has(attacker, Trait.STORM) && r.nextDouble() < 0.25) {
			EntityTypes.LIGHTNING_BOLT.spawn(level, p.blockPosition(), EntitySpawnReason.EVENT);
		}
		if (has(attacker, Trait.BLINK) && r.nextDouble() < 0.35) {
			Vec3 look = p.getLookAngle();
			attacker.setPos(p.getX() - look.x * 2.0, p.getY(), p.getZ() - look.z * 2.0);
		}
		if (has(attacker, Trait.THIEF) && attacker instanceof Mob mob) {
			ItemStack held = p.getMainHandItem();
			if (!held.isEmpty() && mob.getMainHandItem().isEmpty()) {
				mob.setItemInHand(InteractionHand.MAIN_HAND, held.copy());
				mob.setDropChance(EquipmentSlot.MAINHAND, 1.0f);
				p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				p.sendOverlayMessage(Component.literal("§cВор украл твой предмет! Убей его, чтобы вернуть."));
				mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 2));
			}
		}
	}

	/** Эффекты при смерти мутанта. */
	public static void onDeath(LivingEntity entity, DamageSource source) {
		if (entity.entityTags().isEmpty() || !(entity.level() instanceof ServerLevel level)) return;
		if (has(entity, Trait.EXPLOSIVE)) {
			level.explode(entity, entity.getX(), entity.getY() + 0.5, entity.getZ(), 3.0f,
				Level.ExplosionInteraction.MOB);
		}
		if (has(entity, Trait.SWARM)) {
			EntityType<?> type = entity.getType();
			for (int i = 0; i < 2; i++) {
				Entity e = type.spawn(level, entity.blockPosition().offset(i * 2 - 1, 0, 0), EntitySpawnReason.EVENT);
				if (e != null) e.addTag("chaos_done");
			}
		}
	}
}
