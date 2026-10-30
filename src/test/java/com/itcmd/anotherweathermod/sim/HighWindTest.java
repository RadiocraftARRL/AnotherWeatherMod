package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.Hazard;
import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.api.WeatherSample;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighWindTest {

	@Test
	void gustyButBreaksNothing() {
		SteeringWind wind = new SteeringWind(7);
		StormField field = new StormField(wind);
		long now = 50000;
		field.add(StormCell.matureAt(StormKind.HIGH_WIND, 0, 0, now, 12000, 1200, 1f, 99, wind), now);
		field.advance(now);
		double lull = Double.MAX_VALUE, gust = 0;
		for(long t = now; t < now + 2000; t += 2) {
			PointWeather point = field.sample(0, 0, t);
			WeatherSample s = WeatherModel.sample(point, Precipitation.RAIN, 0.8f, 0, 0);
			assertTrue(s.has(Hazard.HIGH_WIND), "high wind hazard at " + t);
			assertEquals(0, s.rainLevel(), 1e-9);
			assertTrue(!s.has(Hazard.DAMAGING_WIND), "high wind damaged at " + t);
			lull = Math.min(lull, point.windSpeed());
			gust = Math.max(gust, point.windSpeed());
		}
		assertTrue(gust < com.itcmd.anotherweathermod.world.Gusts.DOOR_WIND, "gust " + gust + " would blow doors open");
		assertTrue(gust > 14, "gust only " + gust);
		assertTrue(gust > lull * 1.6, "no gusts: " + lull + " to " + gust);
	}

	@Test
	void frequencyScalesEverything() {
		StormGenerator.Settings d = StormGenerator.Settings.defaults();
		assertEquals(d, d.scaled(1));
		StormGenerator.Settings twice = d.scaled(2);
		assertEquals(d.stormsPerPlayerHour() * 2, twice.stormsPerPlayerHour(), 1e-9);
		assertEquals(d.fogPerPlayerHour() * 2, twice.fogPerPlayerHour(), 1e-9);
		assertEquals(d.highWindPerPlayerDay() * 2, twice.highWindPerPlayerDay(), 1e-9);
		assertEquals(d.hurricaneInterval() / 2, twice.hurricaneInterval(), 1e-9);
		assertEquals(0, d.scaled(0).hurricaneInterval(), 1e-9);
	}
}
