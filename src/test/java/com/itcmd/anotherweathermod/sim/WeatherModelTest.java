package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.Beaufort;
import com.itcmd.anotherweathermod.api.Hazard;
import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.api.WeatherSample;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeatherModelTest {

	private static final SteeringWind WIND = new SteeringWind(0);

	private static PointWeather point(StormKind kind, double rain, double windX, float intensity) {
		StormCell cell = new StormCell(1, kind, 0, 1000, 0, 0, 0, 0, 100, intensity, 0, WIND);
		return new PointWeather(rain, kind.hasThunder() ? 0.9 : 0, windX, 0, 10, false, cell, intensity);
	}

	@Test
	void groundDecidesWhatFalls() {
		PointWeather shower = point(StormKind.SHOWER, 0.6, 5, 0.6f);
		assertEquals(Precipitation.RAIN, WeatherModel.sample(shower, Precipitation.RAIN, 0.8f, 0, 0).precipitation());
		assertEquals(Precipitation.SNOW, WeatherModel.sample(shower, Precipitation.SNOW, 0f, 0, 0).precipitation());
		assertEquals(Precipitation.DUST, WeatherModel.sample(shower, Precipitation.NONE, 2f, 0, 0).precipitation());
	}

	@Test
	void hailBlizzardSandstorm() {
		PointWeather supercell = point(StormKind.SUPERCELL, 1, 20, 0.95f);
		WeatherSample rain = WeatherModel.sample(supercell, Precipitation.RAIN, 0.8f, 0, 0);
		assertEquals(Precipitation.HAIL, rain.precipitation());
		assertTrue(rain.has(Hazard.HAIL));
		assertTrue(rain.has(Hazard.LIGHTNING));
		assertTrue(WeatherModel.sample(supercell, Precipitation.SNOW, 0f, 0, 0).has(Hazard.BLIZZARD));
		assertTrue(WeatherModel.sample(supercell, Precipitation.NONE, 2f, 0, 0).has(Hazard.SANDSTORM));
		// A shower over plains does none of that.
		WeatherSample calm = WeatherModel.sample(point(StormKind.SHOWER, 0.5, 4, 0.5f), Precipitation.RAIN, 0.8f, 0, 0);
		assertTrue(calm.hazards().isEmpty());
	}

	@Test
	void temperatureAndPressure() {
		assertEquals(20, WeatherModel.temperature(0.8f), 1e-6);
		assertEquals(40, WeatherModel.temperature(2f), 1e-6);
		assertEquals(0, WeatherModel.temperature(0f), 1e-6);
		WeatherSample s = WeatherModel.sample(point(StormKind.SHOWER, 0, 0, 0), Precipitation.RAIN, 0.8f, 1000, 0);
		assertEquals(20 - 6.5, s.temperature(), 0.5);
		assertEquals(WeatherModel.SEA_LEVEL_PRESSURE - 10, s.pressure(), 1e-6);
		assertEquals(0, WeatherModel.night(6000), 1e-9);
		assertEquals(1, WeatherModel.night(18000), 1e-9);
	}

	@Test
	void windReports() {
		assertEquals(0, Beaufort.of(0.2));
		assertEquals(4, Beaufort.of(6));
		assertEquals(12, Beaufort.of(40));
		// Blowing toward the south (+z) means a north wind: from 0 degrees.
		WeatherSample north = new WeatherSample(Precipitation.NONE, 0, 0, new net.minecraft.world.phys.Vec3(0, 0, 5),
				15, 1013, 50, java.util.Set.of(), -1);
		assertEquals(0, north.windFromDegrees(), 1e-6);
		// Blowing toward the west (-x) means an east wind: from 90 degrees.
		WeatherSample east = new WeatherSample(Precipitation.NONE, 0, 0, new net.minecraft.world.phys.Vec3(-5, 0, 0),
				15, 1013, 50, java.util.Set.of(), -1);
		assertEquals(90, east.windFromDegrees(), 1e-6);
	}

	@Test
	void steeringWindStaysInRange() {
		SteeringWind wind = new SteeringWind(99);
		for(long t = 0; t < 2_000_000; t += 777) {
			double speed = wind.speed(t);
			assertTrue(speed >= SteeringWind.MIN_SPEED - 1e-9 && speed <= SteeringWind.MAX_SPEED + 1e-9);
			assertEquals(speed, Math.hypot(wind.x(t), wind.z(t)), 1e-9);
		}
		assertEquals(wind.direction(123456), new SteeringWind(99).direction(123456));
	}
}
