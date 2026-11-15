package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.api.DayType;

/**
 * Morning mist and dew, which come only on rainy days: a dry or partly cloudy morning is clear (playtest 2026-11-12).
 * The client knows only today's type, so the hours before sunrise (day time 22000 on) go by the day that is ending.
 */
final class Dawn {

	private Dawn() {
	}

	/** How strong the dawn mist is, 0..1: strongest at sunrise, gone by mid-morning; 0 unless the day is rainy. */
	static float mist(long dayTime, DayType type) {
		if(type != DayType.RAINY)
			return 0;
		long t = Math.floorMod(dayTime, 24000L);
		return t >= 22000 ? (t - 22000) / 2000f : t < 3000 ? 1 - t / 3000f : 0;
	}

	/** How much dew drips from the leaves at dawn, 0..1 of the full drip; 0 unless the day is rainy. */
	static float dew(long dayTime, DayType type) {
		if(type != DayType.RAINY)
			return 0;
		long t = Math.floorMod(dayTime, 24000L);
		return t >= 22500 || t < 2000 ? 0.4f : 0;
	}
}
