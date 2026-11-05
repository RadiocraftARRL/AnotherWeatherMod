package com.itcmd.anotherweathermod.block;

import com.itcmd.anotherweathermod.world.SirenWires;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A siren controller: a cabinet that sounds every tornado siren its data wire reaches (AWM's data wire, or
 * RadioCraft's: the block tag {@code anotherweathermod:siren_wire}), at the strength of the redstone signal it gets.
 * Sirens touching it count too. The sirens still answer plain redstone as well. See
 * {@link SirenControllerBlockEntity} for when it walks the wire.
 */
public class SirenControllerBlock extends BaseEntityBlock {

	public static final MapCodec<SirenControllerBlock> CODEC = simpleCodec(SirenControllerBlock::new);
	/** Lit while it gets a signal. */
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	@SuppressWarnings("this-escape")
	public SirenControllerBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(POWERED, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWERED);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SirenControllerBlockEntity(pos, state);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
			boolean isMoving) {
		super.neighborChanged(state, level, pos, block, fromPos, isMoving);
		if(!level.isClientSide && level.getBlockEntity(pos) instanceof SirenControllerBlockEntity controller)
			SirenWires.schedule(controller);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if(!state.is(newState.getBlock()) && !level.isClientSide
				&& level.getBlockEntity(pos) instanceof SirenControllerBlockEntity controller)
			controller.release();
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if(!level.isClientSide && level.getBlockEntity(pos) instanceof SirenControllerBlockEntity controller) {
			int sirens = controller.sirens().size();
			player.displayClientMessage(controller.input() > 0
					? Component.translatable("block.anotherweathermod.siren_controller.sounding", sirens,
							Math.round(controller.input() * 100 / 15.0))
					: Component.translatable("block.anotherweathermod.siren_controller.idle"), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}
}
