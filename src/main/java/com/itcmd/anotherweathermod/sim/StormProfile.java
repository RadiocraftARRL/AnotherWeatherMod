package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.StormKind;

/**
 * The typical size, life and strength of each kind of storm, used when the forecast decides on one, and how it moves
 * and blows once it exists.
 *
 * @param minRadius     blocks
 * @param maxRadius     blocks
 * @param minLifetime   ticks
 * @param maxLifetime   ticks
 * @param minIntensity  0..1
 * @param maxIntensity  0..1
 * @param motionFactor  how much of the steering wind moves the cell
 * @param gust          peak outflow (or, for hurricanes, the eyewall wind at intensity 0) in metres per second
 * @param gustPerIntensity extra wind per unit of intensity, metres per second
 * @param pressureDrop  hectopascals at the centre at full intensity
 */
public record StormProfile(int minRadius, int maxRadius, int minLifetime, int maxLifetime, float minIntensity,
		float maxIntensity, double motionFactor, double gust, double gustPerIntensity, double pressureDrop) {

	private static final int MINUTE = 20 * 60;
	private static final int DAY = 24000;

	public static final StormProfile SHOWER = new StormProfile(96, 192, 8 * MINUTE, 20 * MINUTE, 0.4f, 0.8f, 0.25,
			2, 6, 4);
	public static final StormProfile THUNDERSTORM = new StormProfile(128, 256, 10 * MINUTE, 25 * MINUTE, 0.7f, 1f,
			0.25, 4, 14, 12);
	/** Peak gusts 26 to 30 m/s: just over {@link WeatherModel#DAMAGING_WIND}, so leaves but nothing built. */
	public static final StormProfile SEVERE_THUNDERSTORM = new StormProfile(144, 288, 12 * MINUTE, 28 * MINUTE, 0.8f,
			1f, 0.25, 10, 20, 15);
	public static final StormProfile SUPERCELL = new StormProfile(160, 320, 15 * MINUTE, 30 * MINUTE, 0.85f, 1f, 0.2,
			6, 24, 18);
	public static final StormProfile HURRICANE = new StormProfile(600, 1200, 2 * DAY, 4 * DAY, 0.6f, 1f, 0.05, 33, 37,
			63);

	public static StormProfile of(StormKind kind) {
		return switch(kind) {
			case SHOWER -> SHOWER;
			case THUNDERSTORM -> THUNDERSTORM;
			case SEVERE_THUNDERSTORM -> SEVERE_THUNDERSTORM;
			case SUPERCELL -> SUPERCELL;
			case HURRICANE -> HURRICANE;
		};
	}
}
