package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.Hazard;
import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.api.WeatherSample;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Set;

/** Turns the raw storm weather at a point into what is actually there, given the ground. */
public final class WeatherModel {

	/** Rain level above which it counts as raining (vanilla's {@code Level.isRaining} threshold). */
	public static final double RAINING = 0.2;
	public static final double HAIL_INTENSITY = 0.7;
	public static final double BLIZZARD_WIND = 15;
	public static final double SANDSTORM_WIND = 12;
	public static final double DUST_RAIN = 0.3;
	public static final double DAMAGING_WIND = 25;
	public static final double LIGHTNING_THUNDER = 0.5;

	public static final double SEA_LEVEL_PRESSURE = 1013.25;

	private WeatherModel() {
	}

	/**
	 * The weather at a point.
	 *
	 * @param point          the storm weather there
	 * @param base           vanilla precipitation for the biome there
	 * @param biomeTemperature vanilla biome temperature
	 * @param heightAboveSea blocks above sea level
	 * @param night          0 at noon, 1 at midnight
	 */
	public static WeatherSample sample(PointWeather point, Precipitation base, float biomeTemperature,
			int heightAboveSea, double night) {
		double rain = point.rain();
		double wind = point.windSpeed();
		Set<Hazard> hazards = EnumSet.noneOf(Hazard.class);

		Precipitation precipitation = Precipitation.NONE;
		if(base == Precipitation.RAIN && rain > 0.05) {
			precipitation = Precipitation.RAIN;
			if(point.stormKind() == StormKind.SUPERCELL && point.stormIntensity() > HAIL_INTENSITY && rain > 0.6) {
				precipitation = Precipitation.HAIL;
				hazards.add(Hazard.HAIL);
			}
		}
		else if(base == Precipitation.SNOW && rain > 0.05) {
			precipitation = Precipitation.SNOW;
			if(wind > BLIZZARD_WIND && rain > 0.5)
				hazards.add(Hazard.BLIZZARD);
		}
		else if(base == Precipitation.NONE && rain > DUST_RAIN) {
			precipitation = Precipitation.DUST;
			if(wind > SANDSTORM_WIND)
				hazards.add(Hazard.SANDSTORM);
		}
		if(point.thunder() > LIGHTNING_THUNDER)
			hazards.add(Hazard.LIGHTNING);
		if(point.gust() > DAMAGING_WIND) // The gusts, not the wind this tick, so it does not flicker.
			hazards.add(Hazard.DAMAGING_WIND);
		if(point.eye())
			hazards.add(Hazard.HURRICANE_EYE);

		double temperature = temperature(biomeTemperature) - heightAboveSea * 0.0065 - 5 * night
				- 8 * point.stormIntensity() * Math.min(1, rain * 1.5);
		double pressure = SEA_LEVEL_PRESSURE - point.pressureDrop();
		double dryHumidity = base == Precipitation.NONE ? 30 : biomeTemperature > 0.9 ? 75 : 60;
		double humidity = dryHumidity + (100 - dryHumidity) * Math.min(1, rain * 1.2);

		StormCell storm = point.storm();
		return new WeatherSample(precipitation, (float)rain, (float)point.thunder(),
				new Vec3(point.windX(), 0, point.windZ()), temperature, pressure, humidity, Set.copyOf(hazards),
				storm == null ? -1 : storm.id());
	}

	/** Vanilla biome temperature to degrees Celsius: plains (0.8) is 20, desert (2) is 40, snowy plains (0) is 0. */
	public static double temperature(float biomeTemperature) {
		if(biomeTemperature <= 1)
			return 25 * biomeTemperature;
		return 25 + 15 * (biomeTemperature - 1);
	}

	/** 0 at noon, 1 at midnight, from the day time (0 is sunrise, 6000 noon). */
	public static double night(long dayTime) {
		double phase = ((dayTime - 6000) % 24000 + 24000) % 24000 / 24000.0;
		return 0.5 * (1 - Math.cos(2 * Math.PI * phase));
	}
}
