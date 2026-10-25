package com.itcmd.anotherweathermod.api;

import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * The weather at one position and time.
 *
 * @param precipitation what is falling or blowing
 * @param rainLevel     0..1, what a player there is sent as the rain level
 * @param thunderLevel  0..1, what a player there is sent as the thunder level
 * @param wind          wind vector in metres per second, the direction it blows toward ({@code y} is always 0)
 * @param temperature   degrees Celsius
 * @param pressure      hectopascals
 * @param humidity      relative humidity, percent
 * @param hazards       dangerous weather here (never null, may be empty)
 * @param stormId       the strongest storm over this position, or {@code -1} for none
 */
public record WeatherSample(Precipitation precipitation, float rainLevel, float thunderLevel, Vec3 wind,
		double temperature, double pressure, double humidity, Set<Hazard> hazards, long stormId) {

	/** Calm, clear weather with no storm. */
	public static final WeatherSample CLEAR = new WeatherSample(Precipitation.NONE, 0, 0, Vec3.ZERO, 15, 1013.25, 50,
			Set.of(), -1);

	/** Wind speed in metres per second. */
	public double windSpeed() {
		return wind.horizontalDistance();
	}

	/** The direction the wind comes from, in compass degrees (0 north, 90 east), as weather reports give it. */
	public double windFromDegrees() {
		if(windSpeed() < 1e-6)
			return 0;
		// Minecraft: +x east, +z south. The wind blows toward (x, z), so it comes from (-x, -z).
		double degrees = Math.toDegrees(Math.atan2(-wind.x, wind.z));
		return (degrees + 360) % 360;
	}

	/** Wind force on the Beaufort scale, 0 (calm) to 12 (hurricane). */
	public int beaufort() {
		return Beaufort.of(windSpeed());
	}

	/** Whether a storm is over this position. */
	public boolean inStorm() {
		return stormId >= 0;
	}

	/** Whether {@code hazard} is present here. */
	public boolean has(Hazard hazard) {
		return hazards.contains(hazard);
	}
}
