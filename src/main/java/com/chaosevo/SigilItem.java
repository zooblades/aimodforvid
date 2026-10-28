package com.chaosevo;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Сигил Хаоса: ПКМ призывает следующего босса. */
public class SigilItem extends Item {
	public SigilItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
			if (sl.dimension() != Level.OVERWORLD) {
				sp.sendOverlayMessage(Component.literal("§cСигил работает только в Верхнем мире"));
				return InteractionResult.FAIL;
			}
			String err = Bosses.trySummon(sl, sp);
			if (err != null) {
				sp.sendOverlayMessage(Component.literal("§c" + err));
				return InteractionResult.FAIL;
			}
			player.getItemInHand(hand).shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
