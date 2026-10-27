package com.itcmd.anotherweathermod.block;

import com.itcmd.anotherweathermod.api.WeatherSample;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Locale;

/**
 * A wind vane: the arrow points into the wind, the way the wind comes from, on 16 points of the compass. Its signal
 * is the wind force on the Beaufort scale (0 calm to 12 hurricane); a comparator reads the direction, 1 for north
 * round clockwise to 16 for north-northwest. In a calm the arrow stays where it was.
 */
public class WindVaneBlock extends InstrumentBlock {

	/** The way the wind comes from: 0 north, 4 east, 8 south, 12 west. */
	public static final IntegerProperty DIRECTION = IntegerProperty.create("direction", 0, 15);
	private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 13, 12);
	static final String[] POINTS = { "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W",
			"WNW", "NW", "NNW" };

	@SuppressWarnings("this-escape")
	public WindVaneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(DIRECTION, 0).setValue(POWER, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(DIRECTION);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected BlockState read(BlockState state, ServerLevel level, BlockPos pos, WeatherSample sample) {
		state = state.setValue(POWER, Math.min(15, sample.beaufort()));
		if(sample.windSpeed() >= 0.5)
			state = state.setValue(DIRECTION, (int)Math.round(sample.windFromDegrees() / 22.5) % 16);
		return state;
	}

	@Override
	protected Component reading(BlockState state, ServerLevel level, BlockPos pos, WeatherSample sample) {
		if(sample.windSpeed() < 0.5)
			return Component.translatable("block.anotherweathermod.wind_vane.calm");
		return Component.translatable("block.anotherweathermod.wind_vane.reading", POINTS[state.getValue(DIRECTION)],
				String.format(Locale.ROOT, "%.0f", sample.windSpeed()), sample.beaufort());
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return state.getValue(DIRECTION) + 1;
	}
}
