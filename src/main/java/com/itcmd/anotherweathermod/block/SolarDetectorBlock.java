package com.itcmd.anotherweathermod.block;

import com.itcmd.anotherweathermod.api.Hazard;
import com.itcmd.anotherweathermod.api.WeatherSample;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A pyranometer: measures the sunshine falling on it, about 1000 W/m² with the sun high in a clear sky. Unlike a
 * daylight detector it sees the clouds: a storm overhead or dense fog cuts the sun to a fraction. The signal is the
 * sunshine, 15 at 1000 W/m².
 */
public class SolarDetectorBlock extends InstrumentBlock {

	private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12.5, 9, 12);

	@SuppressWarnings("this-escape")
	public SolarDetectorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(POWER, 0));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Watts per square metre falling on the detector now. */
	public static double irradiance(ServerLevel level, BlockPos pos, WeatherSample sample) {
		if(!level.dimensionType().hasSkyLight() || !level.canSeeSky(pos.above()))
			return 0;
		double sun = Math.cos(level.getSunAngle(1));
		if(sun <= 0)
			return 0;
		double cloud = Math.max(sample.rainLevel(), sample.inStorm() ? 0.4 : 0);
		if(sample.has(Hazard.DENSE_FOG))
			cloud = Math.max(cloud, 0.7);
		return 1000 * sun * (1 - 0.85 * cloud);
	}

	@Override
	protected BlockState read(BlockState state, ServerLevel level, BlockPos pos, WeatherSample sample) {
		return state.setValue(POWER, clamp15(irradiance(level, pos, sample) / 1000 * 15));
	}

	@Override
	protected Component reading(BlockState state, ServerLevel level, BlockPos pos, WeatherSample sample) {
		return Component.translatable("block.anotherweathermod.solar_detector.reading",
				Math.round(irradiance(level, pos, sample)));
	}
}
