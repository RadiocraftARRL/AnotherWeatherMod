package com.itcmd.anotherweathermod.api;

/**
 * What sort of day a region is having. Every in-game day each region (about {@code dayRegionSize} blocks across, a
 * server setting) rolls one, so the weather belongs to the day instead of seeming to come from nowhere.
 */
public enum DayType {
	/** About half of days: no rain, mostly clear skies with a few puffy clouds. Wind and fog can still come. */
	DRY,
	/** About three days in ten: a chance of rain, now and then a storm. */
	CHANCE,
	/** About one day in five: rain likely. Storms come more often and last longer, under a grey sky all day. */
	RAINY
}
