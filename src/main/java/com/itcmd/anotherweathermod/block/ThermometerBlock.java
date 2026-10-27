package com.itcmd.anotherweathermod.block;

import com.itcmd.anotherweathermod.api.WeatherSample;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.Locale;

/**
 * A wall thermometer: the red column stands at five heights, below freezing to over 30 °C. The signal is the
 * temperature, 0 at -20 °C to 15 at 40 °C, 10 on a mild 20 °C day.
 */
public class ThermometerBlock extends WallInstrumentBlock {

	public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 4);

	@SuppressWarnings("this-escape")
	public ThermometerBlock(Properties properties) {
		super(properties, 5, 0.5, 0, 11, 15.5, 1.5);
		registerDefaultState(defaultBlockState().setValue(COLUMN, 2));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(COLUMN);
	}

	@Override
	protected BlockState read(BlockState state, ServerLevel level, BlockPos pos, WeatherSample sample) {
		double t = sample.temperature();
		int column = t < 0 ? 0 : t < 10 ? 1 : t < 20 ? 2 : t < 30 ? 3 : 4;
		return state.setValue(COLUMN, column).setValue(POWER, clamp15((t + 20) / 4));
	}

	@Override
	protected Component reading(BlockState state, ServerLevel level, BlockPos pos, WeatherSample sample) {
		double t = sample.temperature();
		return Component.translatable("block.anotherweathermod.thermometer.reading",
				String.format(Locale.ROOT, "%.0f", t), String.format(Locale.ROOT, "%.0f", t * 9 / 5 + 32));
	}
}
