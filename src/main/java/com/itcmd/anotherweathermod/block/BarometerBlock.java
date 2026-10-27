package com.itcmd.anotherweathermod.block;

import com.itcmd.anotherweathermod.api.AnotherWeatherApi;
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
 * A wall barometer. Pressure falls ahead of a storm, so it reads the lower of the pressure now and in a few minutes:
 * the needle swings toward rain before the rain comes. The dial reads stormy, rain, change, fair or very dry; the
 * signal is the pressure, 0 at 950 hPa (a hurricane's eye) to 15 at 1025, 13 on a fair day.
 */
public class BarometerBlock extends WallInstrumentBlock {

	/** 0 stormy, 1 rain, 2 change, 3 fair, 4 very dry. */
	public static final IntegerProperty NEEDLE = IntegerProperty.create("needle", 0, 4);
	/** How far ahead the barometer feels the weather coming, ticks. */
	static final long LEAD = 4800;
	private static final String[] DIAL = { "stormy", "rain", "change", "fair", "very_dry" };

	@SuppressWarnings("this-escape")
	public BarometerBlock(Properties properties) {
		super(properties, 3, 3, 0, 13, 14.5, 2.6);
		registerDefaultState(defaultBlockState().setValue(NEEDLE, 3));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(NEEDLE);
	}

	private static double pressure(ServerLevel level, BlockPos pos, WeatherSample now) {
		WeatherSample ahead = AnotherWeatherApi.get().sampleAt(level, pos, level.getGameTime() + LEAD);
		return Math.min(now.pressure(), ahead.pressure());
	}

	@Override
	protected BlockState read(BlockState state, ServerLevel level, BlockPos pos, WeatherSample sample) {
		double p = pressure(level, pos, sample);
		int needle = p < 975 ? 0 : p < 1000 ? 1 : p < 1009 ? 2 : sample.humidity() < 40 ? 4 : 3;
		return state.setValue(NEEDLE, needle).setValue(POWER, clamp15((p - 950) / 5));
	}

	@Override
	protected Component reading(BlockState state, ServerLevel level, BlockPos pos, WeatherSample sample) {
		return Component.translatable("block.anotherweathermod.barometer.reading",
				String.format(Locale.ROOT, "%.0f", sample.pressure()),
				Component.translatable("block.anotherweathermod.barometer." + DIAL[state.getValue(NEEDLE)]));
	}
}
