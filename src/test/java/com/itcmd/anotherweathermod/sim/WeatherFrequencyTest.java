package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.DayOutlook;
import com.itcmd.anotherweathermod.api.DayType;
import com.itcmd.anotherweathermod.api.Hazard;
import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.api.WeatherSample;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How often one player standing still sees weather with the default settings, day types included. Targets
 * (maintainer, 2026-10-24): some rain about 30 % of the time, hail and the like about 5 %, a hurricane about 1 %; and
 * (2026-10-25) a severe thunderstorm about 8 %. Day types (2026-10-30: half the days dry, three in ten a chance of
 * rain, one in five rainy) put the rain on the half of days that are not dry, so it all comes down by about a third:
 * rain about 20 % of the time (a little over a quarter on days with a chance of rain, half on rainy days, a few
 * percent on dry days from storms running on past the day before), a severe thunderstorm about 5 %, hail about 3 %.
 * The weatherFrequency setting at 1.5 gives back roughly the old amounts. Since 2026-11-13 the default is lower again
 * (maintainer: rain about 8 % of the time; hail and severe storms fall with it) and scattered storms keep apart. Out in the plains here, "hail and the like"
 * is hail; elsewhere the same supercells bring blizzards and sandstorms. Since 2026-11-12 storms are the world's,
 * not aimed at the player: these are what passes over one spot (scattered storms, fronts, hurricanes from the ocean).
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
			double tornadoStorm, double highWind, Map<DayType, Double> rainByDay, double watchDays) {
	}

	static Coverage measure(long seed, long ticks, int step) {
		return measure(seed, ticks, step, StormGenerator.Settings.defaults());
	}

	static Coverage measure(long seed, long ticks, int step, StormGenerator.Settings settings) {
		SteeringWind wind = new SteeringWind(seed);
		StormField field = new StormField(wind);
		StormGenerator generator = new StormGenerator(seed);
		DayTypes days = new DayTypes(seed, wind);
		// Day time runs with game time here, as it does in a world nobody sleeps in.
		StormGenerator.Outlooks outlooks = (x, z, t) -> days.at(x, z, DayTypes.dayOf(t), DayTypes.dayOf(t) * DayTypes.DAY);
		List<StormGenerator.Viewer> viewers = List.of(new StormGenerator.Viewer(0, 0));
		long rain = 0, hail = 0, severe = 0, hurricane = 0, fog = 0, strong = 0, tornado = 0, highWind = 0, samples = 0;
		long watch = 0;
		Map<DayType, long[]> byDay = new EnumMap<>(DayType.class);
		for(DayType type : DayType.values())
			byDay.put(type, new long[2]);
		for(long t = 0; t < ticks; t += step) {
			// Standing still, the storms coming within the horizon need working out only now and then.
			if(t % 6000 == 0)
				generator.topUp(field, t, viewers, CLIMATE, settings, outlooks);
			field.advance(t);
			PointWeather point = field.sample(0, 0, t);
			WeatherSample s = WeatherModel.sample(point, Precipitation.RAIN, 0.8f, 0, 0);
			DayOutlook day = outlooks.at(0, 0, t);
			samples++;
			long[] counts = byDay.get(day.type());
			counts[0]++;
			if(day.tornadoWatch())
				watch++;
			if(s.rainLevel() > WeatherModel.RAINING) {
				rain++;
				counts[1]++;
			}
			if(s.has(Hazard.HAIL))
				hail++;
			if(s.has(Hazard.HIGH_WIND))
				highWind++;
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
		Map<DayType, Double> rainByDay = new EnumMap<>(DayType.class);
		for(DayType type : DayType.values()) {
			long[] c = byDay.get(type);
			rainByDay.put(type, c[0] == 0 ? 0 : c[1] / (double)c[0]);
		}
		return new Coverage(rain / n, hail / n, severe / n, hurricane / n, fog / n, strong / n, tornado / n,
				highWind / n, rainByDay, watch / n);
	}

	@Test
	void rainAndSevereWeather() {
		double rain = 0, hail = 0, severe = 0, fog = 0, strong = 0, tornado = 0, highWind = 0, watch = 0;
		double dryRain = 0, chanceRain = 0, rainyRain = 0;
		int seeds = 4;
		for(long seed = 1; seed <= seeds; seed++) {
			Coverage c = measure(seed, 72000L * 60, 600); // 60 real hours, 180 in-game days.
			rain += c.rain() / seeds;
			hail += c.hail() / seeds;
			severe += c.severeThunderstorm() / seeds;
			fog += c.fog() / seeds;
			strong += c.strong() / seeds;
			tornado += c.tornadoStorm() / seeds;
			highWind += c.highWind() / seeds;
			watch += c.watchDays() / seeds;
			dryRain += c.rainByDay().get(DayType.DRY) / seeds;
			chanceRain += c.rainByDay().get(DayType.CHANCE) / seeds;
			rainyRain += c.rainByDay().get(DayType.RAINY) / seeds;
		}
		System.out.printf("rain %.3f (dry days %.3f, chance %.3f, rainy %.3f) hail %.3f severe thunderstorm %.3f strong %.3f"
						+ " tornadic %.4f fog %.3f high wind %.3f watch days %.3f%n", rain, dryRain, chanceRain, rainyRain,
				hail, severe, strong, tornado, fog, highWind, watch);
		// Rain about 8 % of the time since 2026-11-13 (the maintainer: 20 % was a little high), the severe kinds with it.
		assertTrue(rain > 0.065 && rain < 0.10, "rain " + rain);
		// Dry days (2026-11-13): the odd light shower of their own, and the end of the day before's storms.
		assertTrue(dryRain > 0.002 && dryRain < 0.03, "rain on dry days " + dryRain);
		// Spaced storms cannot crowd as thickly, so rainy days are not as far ahead of chance days as they were.
		assertTrue(rainyRain > chanceRain * 1.2, "rainy days " + rainyRain + " against chance days " + chanceRain);
		assertTrue(hail > 0.004 && hail < 0.02, "hail " + hail);
		assertTrue(severe > 0.01 && severe < 0.035, "severe thunderstorm " + severe);
		assertTrue(fog > 0.02 && fog < 0.045, "dense fog " + fog);
		assertTrue(tornado > 0.0008 && tornado < 0.008, "under a tornadic storm " + tornado);
		assertTrue(highWind > 0.02 && highWind < 0.08, "high wind " + highWind);
	}

	@Test
	void hurricanes() {
		double hurricane = 0;
		int seeds = 4;
		StormGenerator.Settings d = StormGenerator.Settings.defaults();
		StormGenerator.Settings hurricanesOnly = new StormGenerator.Settings(d.horizonTicks(), 0, d.hurricaneInterval(),
				d.maxStorms(), d.weights(), 0, 0, 0);
		for(long seed = 1; seed <= seeds; seed++)
			hurricane += measure(seed, 24000L * 1500, 1200, hurricanesOnly).hurricane() / seeds; // 1500 in-game days.
		System.out.printf("hurricane %.4f%n", hurricane);
		// 2026-11-13: a hurricane now dies out on the first dry day it meets, so less time under one than before.
		assertTrue(hurricane > 0.002 && hurricane < 0.02, "hurricane " + hurricane);
	}
}
