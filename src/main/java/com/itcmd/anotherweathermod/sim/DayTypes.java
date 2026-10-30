package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.DayOutlook;
import com.itcmd.anotherweathermod.api.DayType;

/**
 * The sort of day each region has. Regions are the cells of a jittered grid ({@link #regionSize} blocks apart), each
 * place belonging to the nearest cell point, so their borders are straight but run every which way. The points move
 * every day, so the regions do too. Where two regions meet, a strip {@link #EDGE_WIDTH} blocks wide is cloudy with no
 * storms. Everything is a pure function of the seed, the place and the day, so it is known days ahead.
 */
public final class DayTypes {

	public static final double DEFAULT_REGION_SIZE = 2000;
	/** Width of the strip where two regions meet, blocks: two chunks. */
	public static final double EDGE_WIDTH = 32;
	/** Share of days of each type: dry, chance of rain, rainy. */
	static final double DRY_SHARE = 0.5;
	static final double CHANCE_SHARE = 0.3;
	/** Share of rainy days with no wind shift that still get a tornado watch. */
	static final double WATCH_SHARE = 0.25;
	public static final long DAY = 24000;

	private final long seed;
	private final SteeringWind wind;
	private final double regionSize;

	public DayTypes(long seed, SteeringWind wind, double regionSize) {
		this.seed = seed ^ 0xDA7DA7L;
		this.wind = wind;
		this.regionSize = Math.max(64, regionSize);
	}

	public double regionSize() {
		return regionSize;
	}

	/** The day {@code dayTime} falls in. */
	public static long dayOf(long dayTime) {
		return Math.floorDiv(dayTime, DAY);
	}

	/**
	 * The outlook at ({@code x}, {@code z}) on {@code day}. {@code dayStartGameTime} is the game time that day starts
	 * at, for the wind shifts that raise tornado watches.
	 */
	public DayOutlook at(double x, double z, long day, long dayStartGameTime) {
		double u = x / regionSize, v = z / regionSize;
		long ci = (long)Math.floor(u), cj = (long)Math.floor(v);
		long bi = 0, bj = 0;
		double bx = 0, bz = 0, best = Double.MAX_VALUE;
		double sx = 0, sz = 0, second = Double.MAX_VALUE;
		for(long i = ci - 1; i <= ci + 1; i++)
			for(long j = cj - 1; j <= cj + 1; j++) {
				long cell = cellKey(i, j, day);
				double px = i + 0.1 + 0.8 * Noise.unit(seed, cell * 2);
				double pz = j + 0.1 + 0.8 * Noise.unit(seed, cell * 2 + 1);
				double d = (px - u) * (px - u) + (pz - v) * (pz - v);
				if(d < best) {
					second = best;
					sx = bx;
					sz = bz;
					best = d;
					bx = px;
					bz = pz;
					bi = i;
					bj = j;
				}
				else if(d < second) {
					second = d;
					sx = px;
					sz = pz;
				}
			}
		// Distance to the border with the second nearest region: (d2^2 - d1^2) / (2 |p2 - p1|).
		double gap = Math.hypot(sx - bx, sz - bz);
		double toEdge = gap < 1e-9 ? 0 : (second - best) / (2 * gap) * regionSize;
		boolean edge = toEdge < EDGE_WIDTH / 2;
		DayType type = type(bi, bj, day);
		boolean watch = type == DayType.RAINY && (shiftDuring(dayStartGameTime)
				|| Noise.unit(seed ^ 0x7047L, cellKey(bi, bj, day)) < WATCH_SHARE);
		return new DayOutlook(day, type, edge, watch && !edge);
	}

	/** The type of region cell ({@code i}, {@code j}) on {@code day}. */
	DayType type(long i, long j, long day) {
		double r = Noise.unit(seed ^ 0x7E7EL, cellKey(i, j, day));
		return r < DRY_SHARE ? DayType.DRY : r < DRY_SHARE + CHANCE_SHARE ? DayType.CHANCE : DayType.RAINY;
	}

	/** Whether the wind shifts during the day that starts at {@code dayStart} (game time). */
	private boolean shiftDuring(long dayStart) {
		return wind.nextShift(dayStart - 1) < dayStart + DAY;
	}

	private static long cellKey(long i, long j, long day) {
		return Noise.mix(i * 0x9E3779B97F4A7C15L ^ Noise.mix(j * 0xC2B2AE3D27D4EB4FL ^ day * 0x165667B19E3779F9L));
	}
}
