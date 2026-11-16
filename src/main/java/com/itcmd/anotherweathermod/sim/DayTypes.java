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

	/** Share of days of each type: dry, chance of rain, rainy (the cuts in {@link #type} give these). */
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

	/**
	 * How much a day leans on the day before (2026-11-15, the maintainer: "some light leaning on previous days"): its
	 * roll is its own normal deviate plus this much of yesterday's, so wet days and dry days come in spells now and then
	 * (after a wet day the next is wet about 63% of the time, not 50%) while the shares of each type stay as set. Still a
	 * pure function of the seed and the day, known days ahead.
	 */
	static final double LEAN = 0.5;
	/** The standard normal's 50th and 80th percentiles: the cuts between dry, chance and rainy at 50%, 30%, 20%. */
	private static final double DRY_CUT = 0.0, CHANCE_CUT = 0.8416212335729143;

	/** The type of {@code day}. */
	DayType type(long day) {
		double z = (deviate(day) + LEAN * deviate(day - 1)) / Math.sqrt(1 + LEAN * LEAN); // Still a standard normal.
		return z < DRY_CUT ? DayType.DRY : z < CHANCE_CUT ? DayType.CHANCE : DayType.RAINY;
	}

	/** Day {@code day}'s own standard normal deviate (Box-Muller from two of its uniforms). */
	private double deviate(long day) {
		double u1 = Math.max(1e-12, Noise.unit(seed ^ 0x7E7EL, dayKey(day)));
		double u2 = Noise.unit(seed ^ 0x7E7FL, dayKey(day));
		return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
	}

	/** Whether the wind shifts during the day that starts at {@code dayStart} (game time). */
	private boolean shiftDuring(long dayStart) {
		return wind.nextShift(dayStart - 1) < dayStart + DAY;
	}

	private static long dayKey(long day) {
		return Noise.mix(day * 0x165667B19E3779F9L);
	}
}
