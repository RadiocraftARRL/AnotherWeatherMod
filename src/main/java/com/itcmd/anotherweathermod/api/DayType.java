package com.itcmd.anotherweathermod.api;

/**
 * What sort of day it is. Every in-game day rolls one for the whole dimension, so the weather belongs to the day
 * instead of seeming to come from nowhere, and the sky does not change as you walk.
 */
public enum DayType {
	/** About half of days: no rain, mostly clear skies with a few puffy clouds. Wind and fog can still come. */
	DRY,
	/** About three days in ten: a chance of rain, now and then a storm. */
	CHANCE,
	/** About one day in five: rain likely. Storms come more often and last longer, under a grey sky all day. */
	RAINY
}
