package com.itcmd.anotherweathermod.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Another Weather Mod's API. Server side only: call it with server levels, on the server thread. The storm lists and
 * {@link StormView} methods are also safe from other threads; the samples look up biomes, so keep them on the server
 * thread. In levels the mod does not manage, every call answers with clear weather and no storms.
 *
 * <pre>{@code
 * AnotherWeatherApi api = AnotherWeatherApi.get();
 * WeatherSample here = api.sample(level, pos);
 * Optional<StormArrival> next = api.nextArrival(level, pos, api.forecastHorizonTicks(level));
 * }</pre>
 *
 * Events are posted on {@code NeoForge.EVENT_BUS}: see the {@code api.event} package.
 */
public interface AnotherWeatherApi {

	/** The API. Never null; before the mod has loaded it answers like an unmanaged level. */
	static AnotherWeatherApi get() {
		return ApiHolder.instance;
	}

	/** Whether localized weather runs in {@code level}. */
	boolean isManaged(Level level);

	/** The weather at {@code pos} now. */
	WeatherSample sample(ServerLevel level, BlockPos pos);

	/**
	 * The weather at {@code pos} at {@code gameTime}. Exact for any time up to {@link #forecastHorizonTicks} ahead, as
	 * far as storms are concerned: storms that are not decided yet are not in it.
	 */
	WeatherSample sampleAt(ServerLevel level, BlockPos pos, long gameTime);

	/** The wind at {@code pos} now, in metres per second, the direction it blows toward. */
	Vec3 wind(ServerLevel level, BlockPos pos);

	/** The storms that have formed and not yet gone. */
	List<StormView> storms(ServerLevel level);

	/** The storms the forecast has decided on that have not formed yet. */
	List<StormView> pendingStorms(ServerLevel level);

	/** Active storms whose edge is within {@code radius} blocks of {@code pos}. */
	List<StormView> stormsNear(ServerLevel level, BlockPos pos, double radius);

	/**
	 * The next storm, active or pending, to be over {@code pos} within {@code horizonTicks}, or empty. If a storm is
	 * over it now, that one (with an arrival tick of now or earlier).
	 */
	Optional<StormArrival> nextArrival(ServerLevel level, BlockPos pos, long horizonTicks);

	/** How far ahead storms are decided, in ticks. */
	long forecastHorizonTicks(ServerLevel level);
}
