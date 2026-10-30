package com.itcmd.anotherweathermod.api;

/**
 * The outlook for one in-game day at one place: what sort of day it is there, and whether a tornado watch is out.
 * Known days ahead (it is decided from the world seed, the place and the day), unlike the storms themselves, which are
 * only decided {@link AnotherWeatherApi#forecastHorizonTicks forecastHorizonTicks} ahead.
 *
 * @param day          the in-game day, counted like {@code dayTime / 24000} (day 0 is the world's first)
 * @param type         the region's sort of day
 * @param regionEdge   on the strip where two regions meet: cloudy, but no storms
 * @param tornadoWatch conditions favour tornadoes there that day: one is possible, though most watch days never see
 *                     one. Only rainy days have watches.
 */
public record DayOutlook(long day, DayType type, boolean regionEdge, boolean tornadoWatch) {

	/** The outlook in a level the mod does not manage: a dry day, no watch. */
	public static DayOutlook clear(long day) {
		return new DayOutlook(day, DayType.DRY, false, false);
	}

	/** Whether rain is likely enough to say so ("rain likely"): a rainy day off the region's edge. */
	public boolean rainLikely() {
		return type == DayType.RAINY && !regionEdge;
	}
}
