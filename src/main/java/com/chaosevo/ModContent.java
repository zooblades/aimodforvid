package com.chaosevo;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModContent {
	public static Item SIGIL, HEART, VOID_SHARD, BLOOD_CRYSTAL, STORM_CORE;
	public static final Item[] KEYS = new Item[4];
	public static Block VOID_ORE, BLOOD_ORE, STORM_ORE;

	private static Item item(String name, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ChaosMod.id(name));
		Item it = factory.apply(new Item.Properties().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, it);
	}

	private static Block ore(String name, int minXp, int maxXp, float hardness) {
		ResourceKey<Block> bk = ResourceKey.create(Registries.BLOCK, ChaosMod.id(name));
		Block b = new DropExperienceBlock(
			UniformInt.of(minXp, maxXp),
			BlockBehaviour.Properties.of().setId(bk).strength(hardness, 6.0f)
				.requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE));
		Registry.register(BuiltInRegistries.BLOCK, bk, b);
		ResourceKey<Item> ik = ResourceKey.create(Registries.ITEM, ChaosMod.id(name));
		Registry.register(BuiltInRegistries.ITEM, ik,
			new BlockItem(b, new Item.Properties().setId(ik).useBlockDescriptionPrefix()));
		return b;
	}

	public static void init() {
		VOID_SHARD = item("void_shard", p -> new Item(p));
		BLOOD_CRYSTAL = item("blood_crystal", p -> new Item(p));
		STORM_CORE = item("storm_core", p -> new Item(p));
		SIGIL = item("chaos_sigil", p -> new SigilItem(p.stacksTo(16).rarity(Rarity.EPIC)));
		for (int i = 0; i < KEYS.length; i++) {
			KEYS[i] = item("boss_key_" + (i + 1), p -> new Item(p.stacksTo(16).rarity(Rarity.RARE)));
		}
		HEART = item("chaos_heart", p -> new Item(p.stacksTo(1).rarity(Rarity.EPIC)));

		VOID_ORE = ore("void_ore", 3, 7, 4.5f);
		BLOOD_ORE = ore("blood_ore", 4, 8, 5.5f);
		STORM_ORE = ore("storm_ore", 5, 10, 6.5f);
	}
}
