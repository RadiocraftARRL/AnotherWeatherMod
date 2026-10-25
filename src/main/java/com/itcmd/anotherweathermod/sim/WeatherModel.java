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
	/** {@link PointWeather#tornado()} from which you are in a tornado. */
	public static final double TORNADO_NEAR = 0.25;
	/** {@link PointWeather#fog()} from which the fog is dense. */
	public static final double DENSE_FOG = 0.4;
	/** Heat wave or cold snap strength from which it is one. */
	public static final double EXTREME = 0.3;

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
			boolean supercellHail = (point.stormKind() == StormKind.SUPERCELL || point.stormKind() == StormKind.TORNADO)
					&& point.stormIntensity() > HAIL_INTENSITY;
			if((supercellHail || point.hailBurst()) && rain > 0.6) {
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
		if(point.tornado() > TORNADO_NEAR)
			hazards.add(Hazard.TORNADO);
		if(point.fog() > DENSE_FOG)
			hazards.add(Hazard.DENSE_FOG);
		// Heat waves bite over hot, dry ground by day; cold snaps over snowy ground by night.
		boolean hotGround = base == Precipitation.NONE || biomeTemperature >= 1;
		boolean coldGround = base == Precipitation.SNOW || biomeTemperature <= 0.15f;
		double heat = hotGround ? point.heat() * (1 - night) : 0;
		double cold = coldGround ? point.cold() * night : 0;
		if(hotGround && point.heat() > EXTREME && night < 0.5)
			hazards.add(Hazard.HEAT_WAVE);
		if(coldGround && point.cold() > EXTREME && night > 0.5)
			hazards.add(Hazard.COLD_SNAP);

		double temperature = temperature(biomeTemperature) - heightAboveSea * 0.0065 - 5 * night
				- 8 * point.stormIntensity() * Math.min(1, rain * 1.5) + 12 * heat - 16 * cold;
		double pressure = SEA_LEVEL_PRESSURE - point.pressureDrop();
		double dryHumidity = base == Precipitation.NONE ? 30 : biomeTemperature > 0.9 ? 75 : 60;
		double humidity = dryHumidity + (100 - dryHumidity) * Math.min(1, Math.max(rain * 1.2, point.fog()));
		humidity *= 1 - 0.5 * heat;

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
