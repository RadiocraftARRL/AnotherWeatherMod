package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.DayOutlook;
import com.itcmd.anotherweathermod.api.DayType;

/**
 * The sort of day the world is having. Each day rolls one type for the whole dimension, so the sky does not change
 * as you walk (it used to vary by region, and walking along a region's edge flipped the sky between a grey deck and
 * fair weather chunk by chunk, 2026-11-12). Storms are still local: the day only decides how many come and how long
 * they last. Everything is a pure function of the seed and the day, so it is known days ahead.
 */
public final class DayTypes {

	/** Share of days of each type: dry, chance of rain, rainy. */
	static final double DRY_SHARE = 0.5;
	static final double CHANCE_SHARE = 0.3;
	/** Share of rainy days with no wind shift that still get a tornado watch. */
	static final double WATCH_SHARE = 0.25;
	public static final long DAY = 24000;

	private final long seed;
	private final SteeringWind wind;

	public DayTypes(long seed, SteeringWind wind) {
		this.seed = seed ^ 0xDA7DA7L;
		this.wind = wind;
	}

	/** The day {@code dayTime} falls in. */
	public static long dayOf(long dayTime) {
		return Math.floorDiv(dayTime, DAY);
	}

	/**
	 * The outlook on {@code day}, the same everywhere. {@code dayStartGameTime} is the game time that day starts at,
	 * for the wind shifts that raise tornado watches.
	 */
	public DayOutlook on(long day, long dayStartGameTime) {
		DayType type = type(day);
		boolean watch = type == DayType.RAINY && (shiftDuring(dayStartGameTime)
				|| Noise.unit(seed ^ 0x7047L, dayKey(day)) < WATCH_SHARE);
		return new DayOutlook(day, type, false, watch);
	}

	/** The outlook at ({@code x}, {@code z}) on {@code day}: the same as anywhere else (see {@link #on}). */
	public DayOutlook at(double x, double z, long day, long dayStartGameTime) {
		return on(day, dayStartGameTime);
	}

	/** The type of {@code day}. */
	DayType type(long day) {
		double r = Noise.unit(seed ^ 0x7E7EL, dayKey(day));
		return r < DRY_SHARE ? DayType.DRY : r < DRY_SHARE + CHANCE_SHARE ? DayType.CHANCE : DayType.RAINY;
	}

	/** Whether the wind shifts during the day that starts at {@code dayStart} (game time). */
	private boolean shiftDuring(long dayStart) {
		return wind.nextShift(dayStart - 1) < dayStart + DAY;
	}

	private static long dayKey(long day) {
		return Noise.mix(day * 0x165667B19E3779F9L);
	}
}
