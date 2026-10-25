package com.itcmd.anotherweathermod.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;

/** Holds the live API. Set once by the mod; other mods only use {@link AnotherWeatherApi#get()}. */
@ApiStatus.Internal
public final class ApiHolder {

	static volatile AnotherWeatherApi instance = new Unmanaged();

	private ApiHolder() {
	}

	@ApiStatus.Internal
	public static void set(AnotherWeatherApi api) {
		instance = api;
	}

	/** Clear weather everywhere, no storms. */
	private static final class Unmanaged implements AnotherWeatherApi {
		@Override public boolean isManaged(Level level) { return false; }
		@Override public WeatherSample sample(ServerLevel level, BlockPos pos) { return WeatherSample.CLEAR; }
		@Override public WeatherSample sampleAt(ServerLevel level, BlockPos pos, long gameTime) { return WeatherSample.CLEAR; }
		@Override public Vec3 wind(ServerLevel level, BlockPos pos) { return Vec3.ZERO; }
		@Override public List<StormView> storms(ServerLevel level) { return List.of(); }
		@Override public List<StormView> pendingStorms(ServerLevel level) { return List.of(); }
		@Override public List<StormView> stormsNear(ServerLevel level, BlockPos pos, double radius) { return List.of(); }
		@Override public Optional<StormArrival> nextArrival(ServerLevel level, BlockPos pos, long horizonTicks) { return Optional.empty(); }
		@Override public long forecastHorizonTicks(ServerLevel level) { return 0; }
	}
}
