package com.itcmd.anotherweathermod.block;

import com.itcmd.anotherweathermod.AwmConfig;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.api.WeatherSample;
import com.itcmd.anotherweathermod.sim.StormCell;
import com.itcmd.anotherweathermod.world.LevelWeather;
import com.itcmd.anotherweathermod.world.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A tornado acoustic sensor: a cabinet with a radome that listens for the deep rumble a tornado makes, out to its
 * range ({@code tornadoSensorRange}). Its signal grows as the tornado comes closer, 15 when it is on top of you; a
 * tornado not down yet but due within range in a couple of minutes gives 1. Wire it to a tornado siren.
 */
public class TornadoSensorBlock extends InstrumentBlock {

	/** How far ahead it warns of a tornado, ticks. */
	static final long WARNING_TICKS = 2400;
	private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 8, 16), Block.box(2.5, 8, 2.5, 13.5, 16, 13.5));

	@SuppressWarnings("this-escape")
	public TornadoSensorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(POWER, 0));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/**
	 * Blocks to the nearest tornado that is down now; {@code Double.NaN} if one is only coming (down within range in
	 * {@link #WARNING_TICKS}); infinity if none.
	 */
	static double nearestTornado(ServerLevel level, BlockPos pos) {
		LevelWeather weather = WeatherManager.get(level);
		if(weather == null)
			return Double.POSITIVE_INFINITY;
		long now = level.getGameTime();
		double range = AwmConfig.TORNADO_SENSOR_RANGE.get();
		double nearest = Double.POSITIVE_INFINITY;
		boolean coming = false;
		for(StormCell cell : weather.field().active()) {
			if(cell.kind() != StormKind.TORNADO)
				continue;
			if(cell.tornadoStrength(now) > 0)
				nearest = Math.min(nearest, Math.hypot(cell.tornadoX(now) - pos.getX(), cell.tornadoZ(now) - pos.getZ()));
			for(long t = now + 200; t <= now + WARNING_TICKS && !coming; t += 200)
				coming = cell.tornadoStrength(t) > 0
						&& Math.hypot(cell.tornadoX(t) - pos.getX(), cell.tornadoZ(t) - pos.getZ()) < range;
		}
		return nearest < range ? nearest : coming ? Double.NaN : Double.POSITIVE_INFINITY;
	}

	/** The signal for a tornado {@code distance} away (see {@link #nearestTornado}). */
	static int signal(double distance, double range) {
		if(Double.isNaN(distance))
			return 1;
		if(distance >= range)
			return 0;
		return Math.max(2, clamp15(15 * (1 - distance / range)));
	}

	@Override
	protected BlockState read(BlockState state, ServerLevel level, BlockPos pos, WeatherSample sample) {
		return state.setValue(POWER, signal(nearestTornado(level, pos), AwmConfig.TORNADO_SENSOR_RANGE.get()));
	}

	@Override
	protected Component reading(BlockState state, ServerLevel level, BlockPos pos, WeatherSample sample) {
		double distance = nearestTornado(level, pos);
		if(Double.isNaN(distance))
			return Component.translatable("block.anotherweathermod.tornado_sensor.coming");
		if(Double.isInfinite(distance))
			return Component.translatable("block.anotherweathermod.tornado_sensor.quiet", AwmConfig.TORNADO_SENSOR_RANGE.get());
		return Component.translatable("block.anotherweathermod.tornado_sensor.heard", Math.round(distance));
	}
}
