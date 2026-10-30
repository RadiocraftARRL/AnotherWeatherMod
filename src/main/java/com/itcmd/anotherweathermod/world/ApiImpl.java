package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.api.AnotherWeatherApi;
import com.itcmd.anotherweathermod.api.DayOutlook;
import com.itcmd.anotherweathermod.api.StormArrival;
import com.itcmd.anotherweathermod.api.StormView;
import com.itcmd.anotherweathermod.api.WeatherSample;
import com.itcmd.anotherweathermod.sim.Forecast;
import com.itcmd.anotherweathermod.sim.StormCell;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The live {@link AnotherWeatherApi}. */
public final class ApiImpl implements AnotherWeatherApi {

	@Override
	public boolean isManaged(Level level) {
		return WeatherManager.get(level) != null;
	}

	@Override
	public WeatherSample sample(ServerLevel level, BlockPos pos) {
		return sampleAt(level, pos, level.getGameTime());
	}

	@Override
	public WeatherSample sampleAt(ServerLevel level, BlockPos pos, long gameTime) {
		LevelWeather weather = WeatherManager.get(level);
		return weather == null ? WeatherSample.CLEAR : weather.sample(pos, gameTime);
	}

	@Override
	public double rainLevelAt(ServerLevel level, double x, double z, long gameTime) {
		LevelWeather weather = WeatherManager.get(level);
		return weather == null ? 0 : weather.field().sample(x, z, gameTime).rain();
	}

	@Override
	public double thunderLevelAt(ServerLevel level, double x, double z, long gameTime) {
		LevelWeather weather = WeatherManager.get(level);
		return weather == null ? 0 : weather.field().sample(x, z, gameTime).thunder();
	}

	@Override
	public Vec3 wind(ServerLevel level, BlockPos pos) {
		LevelWeather weather = WeatherManager.get(level);
		if(weather == null)
			return Vec3.ZERO;
		var point = weather.field().sample(pos.getX() + 0.5, pos.getZ() + 0.5, level.getGameTime());
		return new Vec3(point.windX(), 0, point.windZ());
	}

	@Override
	public List<StormView> storms(ServerLevel level) {
		LevelWeather weather = WeatherManager.get(level);
		return weather == null ? List.of() : List.copyOf(weather.field().active());
	}

	@Override
	public List<StormView> pendingStorms(ServerLevel level) {
		LevelWeather weather = WeatherManager.get(level);
		return weather == null ? List.of() : List.copyOf(weather.field().pending());
	}

	@Override
	public List<StormView> stormsNear(ServerLevel level, BlockPos pos, double radius) {
		LevelWeather weather = WeatherManager.get(level);
		if(weather == null)
			return List.of();
		long now = level.getGameTime();
		List<StormView> near = new ArrayList<>();
		for(StormCell cell : weather.field().active()) {
			double dx = cell.centerX(now) - pos.getX();
			double dz = cell.centerZ(now) - pos.getZ();
			double reach = cell.radiusAt(now) + radius;
			if(dx * dx + dz * dz <= reach * reach)
				near.add(cell);
		}
		return near;
	}

	@Override
	public Optional<StormArrival> nextArrival(ServerLevel level, BlockPos pos, long horizonTicks) {
		LevelWeather weather = WeatherManager.get(level);
		if(weather == null)
			return Optional.empty();
		long now = level.getGameTime();
		return Forecast.nextArrival(weather.field(), pos.getX() + 0.5, pos.getZ() + 0.5, now, horizonTicks)
				.map(a -> new StormArrival(a.cell(), a.arrival(), a.departure(), weather.sample(pos, a.peakTick()),
						a.peakTick()));
	}

	@Override
	public DayOutlook outlook(ServerLevel level, BlockPos pos, int daysAhead) {
		LevelWeather weather = WeatherManager.get(level);
		if(weather == null)
			return DayOutlook.clear(Math.floorDiv(level.getDayTime(), 24000L) + daysAhead);
		return weather.outlook(pos.getX() + 0.5, pos.getZ() + 0.5, weather.today() + daysAhead);
	}

	@Override
	public long forecastHorizonTicks(ServerLevel level) {
		return isManaged(level) ? com.itcmd.anotherweathermod.AwmConfig.FORECAST_MINUTES.get() * 1200L : 0;
	}
}
