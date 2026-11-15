package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.api.DayType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DawnTest {

	/** Morning mist and dew come only on rainy days; dry and partly cloudy mornings are clear. */
	@Test
	void mistAndDewOnlyOnRainyDays() {
		for(long t : new long[] { 22500, 23500, 0, 500, 1500 }) {
			assertEquals(0, Dawn.mist(t, DayType.DRY), "dry at " + t);
			assertEquals(0, Dawn.mist(t, DayType.CHANCE), "partly cloudy at " + t);
			assertEquals(0, Dawn.dew(t, DayType.DRY));
			assertEquals(0, Dawn.dew(t, DayType.CHANCE));
			assertTrue(Dawn.mist(t, DayType.RAINY) > 0, "rainy at " + t);
			assertTrue(Dawn.dew(t, DayType.RAINY) > 0, "dew at " + t);
		}
	}

	@Test
	void strongestAtSunriseAndGoneByMidMorning() {
		assertEquals(1, Dawn.mist(0, DayType.RAINY), 1e-6);
		assertEquals(1, Dawn.mist(24000 * 5, DayType.RAINY), 1e-6);
		assertEquals(0, Dawn.mist(4000, DayType.RAINY));
		assertEquals(0, Dawn.mist(12000, DayType.RAINY));
		assertEquals(0, Dawn.dew(6000, DayType.RAINY));
	}
}
