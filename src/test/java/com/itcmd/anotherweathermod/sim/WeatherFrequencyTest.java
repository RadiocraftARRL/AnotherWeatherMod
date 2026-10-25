package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.Hazard;
import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.api.WeatherSample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How often one player standing still sees weather with the default settings. Targets (maintainer, 2026-10-24): some
 * rain about 30 % of the time, hail and the like about 5 %, a hurricane about 1 %.
 */
class WeatherFrequencyTest {

	/** Plains, ocean far to the west and south so hurricanes can form. */
	private static final Climate CLIMATE = new Climate() {
		@Override public Precipitation basePrecipitation(int x, int y, int z) { return Precipitation.RAIN; }
		@Override public float biomeTemperature(int x, int y, int z) { return 0.8f; }
		@Override public boolean isOcean(int x, int z) { return Math.abs(x) > 1500 || Math.abs(z) > 1500; }
		@Override public int seaLevel() { return 63; }
	};

	record Coverage(double rain, double severe, double hurricane) {
	}

	static Coverage measure(long seed, long ticks, int step) {
		StormGenerator.Settings settings = StormGenerator.Settings.defaults();
		StormField field = new StormField(new SteeringWind(seed));
		StormGenerator generator = new StormGenerator(seed);
		List<StormGenerator.Target> targets = List.of(new StormGenerator.Target(0, 0));
		long rain = 0, severe = 0, hurricane = 0, samples = 0;
		for(long t = 0; t < ticks; t += step) {
			generator.topUp(field, t, targets, CLIMATE, settings);
			field.advance(t);
			PointWeather point = field.sample(0, 0, t);
			WeatherSample s = WeatherModel.sample(point, Precipitation.RAIN, 0.8f, 0, 0);
			samples++;
			if(s.rainLevel() > WeatherModel.RAINING)
				rain++;
			if(s.has(Hazard.HAIL) || s.has(Hazard.DAMAGING_WIND))
				severe++;
			if(point.stormKind() == StormKind.HURRICANE && point.rain() > WeatherModel.RAINING)
				hurricane++;
		}
		return new Coverage(rain / (double)samples, severe / (double)samples, hurricane / (double)samples);
	}

	@Test
	void rainAndSevereWeather() {
		double rain = 0, severe = 0;
		int seeds = 4;
		for(long seed = 1; seed <= seeds; seed++) {
			Coverage c = measure(seed, 72000L * 60, 200); // 60 real hours.
			rain += c.rain() / seeds;
			severe += c.severe() / seeds;
		}
		System.out.printf("rain %.3f severe %.3f%n", rain, severe);
		assertTrue(rain > 0.24 && rain < 0.36, "rain " + rain);
		assertTrue(severe > 0.035 && severe < 0.07, "severe " + severe);
	}

	@Test
	void hurricanes() {
		double hurricane = 0;
		int seeds = 4;
		for(long seed = 1; seed <= seeds; seed++)
			hurricane += measure(seed, 24000L * 1500, 1200).hurricane() / seeds; // 1500 in-game days.
		System.out.printf("hurricane %.4f%n", hurricane);
		assertTrue(hurricane > 0.005 && hurricane < 0.02, "hurricane " + hurricane);
	}
}
