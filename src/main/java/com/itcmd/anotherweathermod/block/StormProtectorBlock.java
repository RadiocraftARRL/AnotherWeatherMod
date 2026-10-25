package com.itcmd.anotherweathermod.block;

import com.itcmd.anotherweathermod.network.ProtectorPayload;
import com.itcmd.anotherweathermod.world.StormProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Keeps the ground round it safe from storms: within its radius (set by using it, 8 to 256 blocks), wind and hail
 * break nothing and tornadoes lift nothing. Only operators in creative can place, use or break it, as with a command
 * block. The areas live in {@link StormProtection}.
 */
public class StormProtectorBlock extends Block {

	public StormProtectorBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if(level instanceof ServerLevel server && !oldState.is(this))
			StormProtection.get(server).set(pos, -1);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if(level instanceof ServerLevel server && !newState.is(this))
			StormProtection.get(server).remove(pos);
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if(!player.canUseGameMasterBlocks())
			return InteractionResult.PASS;
		if(level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer
				&& serverPlayer.connection.hasChannel(ProtectorPayload.TYPE))
			PacketDistributor.sendToPlayer(serverPlayer, new ProtectorPayload(pos, StormProtection.get(server).radius(pos)));
		return InteractionResult.sidedSuccess(level.isClientSide);
	}
}
