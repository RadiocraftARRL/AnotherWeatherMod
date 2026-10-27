package com.itcmd.anotherweathermod.block;

import com.itcmd.anotherweathermod.api.AnotherWeatherApi;
import com.itcmd.anotherweathermod.api.WeatherSample;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A weather instrument: once a second it reads the weather where it stands, shows it (a needle, a column, an arrow)
 * and gives a redstone signal, 0 to 15, from every side. Using it tells you the reading. Outside the dimensions with
 * localized weather it reads calm, clear weather.
 */
public abstract class InstrumentBlock extends Block {

	public static final IntegerProperty POWER = BlockStateProperties.POWER;
	/** Ticks between readings. */
	static final int INTERVAL = 20;

	protected InstrumentBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWER);
	}

	/** The state showing the weather {@code sample} at {@code pos}. */
	protected abstract BlockState read(BlockState state, ServerLevel level, BlockPos pos, WeatherSample sample);

	/** What using the instrument tells you. */
	protected abstract Component reading(BlockState state, ServerLevel level, BlockPos pos, WeatherSample sample);

	protected static WeatherSample sample(ServerLevel level, BlockPos pos) {
		return AnotherWeatherApi.get().sample(level, pos);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if(!oldState.is(this))
			level.scheduleTick(pos, this, 1);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		BlockState read = read(state, level, pos, sample(level, pos));
		if(read != state)
			level.setBlock(pos, read, Block.UPDATE_ALL);
		level.scheduleTick(pos, this, INTERVAL);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if(level instanceof ServerLevel server)
			player.displayClientMessage(reading(state, server, pos, sample(server, pos)), true);
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return state.getValue(POWER);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return state.getValue(POWER);
	}

	static int clamp15(double value) {
		return (int)Math.max(0, Math.min(15, Math.round(value)));
	}
}
