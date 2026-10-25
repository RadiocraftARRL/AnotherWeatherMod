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
 * rain about 30 % of the time, hail and the like about 5 %, a hurricane about 1 %; and (2026-10-25) a severe
 * thunderstorm about 8 %. Out in the plains here, "hail and the like" is hail; elsewhere the same supercells bring
 * blizzards and sandstorms.
 */
class WeatherFrequencyTest {

	/** Plains, ocean far to the west and south so hurricanes can form. */
	private static final Climate CLIMATE = new Climate() {
		@Override public Precipitation basePrecipitation(int x, int y, int z) { return Precipitation.RAIN; }
		@Override public float biomeTemperature(int x, int y, int z) { return 0.8f; }
		@Override public boolean isOcean(int x, int z) { return Math.abs(x) > 1500 || Math.abs(z) > 1500; }
		@Override public int seaLevel() { return 63; }
	};

	record Coverage(double rain, double hail, double severeThunderstorm, double hurricane, double fog, double strong,
			double tornadoStorm) {
	}

	static Coverage measure(long seed, long ticks, int step) {
		StormGenerator.Settings settings = StormGenerator.Settings.defaults();
		StormField field = new StormField(new SteeringWind(seed));
		StormGenerator generator = new StormGenerator(seed);
		List<StormGenerator.Target> targets = List.of(new StormGenerator.Target(0, 0));
		long rain = 0, hail = 0, severe = 0, hurricane = 0, fog = 0, strong = 0, tornado = 0, samples = 0;
		for(long t = 0; t < ticks; t += step) {
			generator.topUp(field, t, targets, CLIMATE, settings);
			field.advance(t);
			PointWeather point = field.sample(0, 0, t);
			WeatherSample s = WeatherModel.sample(point, Precipitation.RAIN, 0.8f, 0, 0);
			samples++;
			if(s.rainLevel() > WeatherModel.RAINING)
				rain++;
			if(s.has(Hazard.HAIL))
				hail++;
			if(point.stormKind() == StormKind.SEVERE_THUNDERSTORM && point.rain() > WeatherModel.RAINING)
				severe++;
			if(point.stormKind() == StormKind.HURRICANE && point.rain() > WeatherModel.RAINING)
				hurricane++;
			if(s.has(Hazard.DENSE_FOG))
				fog++;
			if(point.stormKind() == StormKind.STRONG_THUNDERSTORM && point.rain() > WeatherModel.RAINING)
				strong++;
			if(point.stormKind() == StormKind.TORNADO && point.rain() > WeatherModel.RAINING)
				tornado++;
		}
		double n = samples;
		return new Coverage(rain / n, hail / n, severe / n, hurricane / n, fog / n, strong / n, tornado / n);
	}

	@Test
	void rainAndSevereWeather() {
		double rain = 0, hail = 0, severe = 0, fog = 0, strong = 0, tornado = 0;
		int seeds = 4;
		for(long seed = 1; seed <= seeds; seed++) {
			Coverage c = measure(seed, 72000L * 60, 200); // 60 real hours.
			rain += c.rain() / seeds;
			hail += c.hail() / seeds;
			severe += c.severeThunderstorm() / seeds;
			fog += c.fog() / seeds;
			strong += c.strong() / seeds;
			tornado += c.tornadoStorm() / seeds;
		}
		System.out.printf("rain %.3f hail %.3f severe thunderstorm %.3f strong %.3f tornadic %.4f fog %.3f%n", rain, hail,
				severe, strong, tornado, fog);
		assertTrue(rain > 0.24 && rain < 0.36, "rain " + rain);
		assertTrue(hail > 0.035 && hail < 0.07, "hail " + hail);
		assertTrue(severe > 0.06 && severe < 0.10, "severe thunderstorm " + severe);
		assertTrue(fog > 0.02 && fog < 0.045, "dense fog " + fog);
		assertTrue(tornado > 0.002 && tornado < 0.012, "under a tornadic storm " + tornado);
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
