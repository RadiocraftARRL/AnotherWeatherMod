package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.DayOutlook;
import com.itcmd.anotherweathermod.api.DayType;
import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

class StormGeneratorTest {

	/** Plains everywhere, ocean west of x = -1000. */
	static final Climate CLIMATE = new Climate() {
		@Override public Precipitation basePrecipitation(int x, int y, int z) { return Precipitation.RAIN; }
		@Override public float biomeTemperature(int x, int y, int z) { return 0.8f; }
		@Override public boolean isOcean(int x, int z) { return x < -1000; }
		@Override public int seaLevel() { return 63; }
	};

	/** Every day rainy, with a tornado watch: plenty of storms and fronts to look at. */
	static final StormGenerator.Outlooks RAINY = (x, z, t) -> new DayOutlook(DayTypes.dayOf(t), DayType.RAINY, false, true);

	/** The defaults with room for every storm about, so no cap gets in the way of the comparisons. */
	private static StormGenerator.Settings settings() {
		StormGenerator.Settings d = StormGenerator.Settings.defaults();
		return new StormGenerator.Settings(d.horizonTicks(), d.stormsPerPlayerHour(), d.hurricaneInterval(), 100000,
				d.weights(), d.fogPerPlayerHour(), d.extremesPerPlayerDay(), d.highWindPerPlayerDay());
	}

	private static List<StormGenerator.Viewer> at(double... xz) {
		List<StormGenerator.Viewer> viewers = new ArrayList<>();
		for(int i = 0; i < xz.length; i += 2)
			viewers.add(new StormGenerator.Viewer(xz[i], xz[i + 1]));
		return viewers;
	}

	/** The storms in {@code field} that come within {@code within} of ({@code x}, {@code z}) at {@code t}, by seed. */
	private static Map<Long, StormCell> near(StormField field, double x, double z, long t, double within) {
		Map<Long, StormCell> near = new HashMap<>();
		for(StormCell cell : field.all())
			if(cell.isAlive(t) && Math.hypot(cell.centerX(t) - x, cell.centerZ(t) - z) < within)
				near.put(cell.seed(), cell);
		return near;
	}

	private static StormField fill(long seed, long now, List<StormGenerator.Viewer> viewers) {
		StormField field = new StormField(new SteeringWind(seed));
		new StormGenerator(seed).topUp(field, now, viewers, CLIMATE, settings(), RAINY);
		return field;
	}

	/**
	 * The storms over a place are the same whoever is online and wherever they are: one player there, five players
	 * there, or one there and another far off. Their number does not grow with the players.
	 */
	@Test
	void theSameStormsWhoeverIsLooking() {
		long now = 300000, t = now + 6000;
		Map<Long, StormCell> one = near(fill(5, now, at(0, 0)), 0, 0, t, 1200);
		Map<Long, StormCell> five = near(fill(5, now, at(0, 0, 10, 10, -20, 5, 3, -3, 0, 0)), 0, 0, t, 1200);
		Map<Long, StormCell> apart = near(fill(5, now, at(0, 0, 9000, -4000)), 0, 0, t, 1200);
		assertFalse(one.isEmpty(), "a rainy day with no storms near");
		assertEquals(one.keySet(), five.keySet());
		assertEquals(one.keySet(), apart.keySet());
		for(long seed : one.keySet())
			for(Map<Long, StormCell> other : List.of(five, apart)) {
				assertEquals(one.get(seed).centerX(t), other.get(seed).centerX(t), 1e-9);
				assertEquals(one.get(seed).centerZ(t), other.get(seed).centerZ(t), 1e-9);
				assertEquals(one.get(seed).kind(), other.get(seed).kind());
			}
		// And they are the world's storms there, as worked out with nobody about.
		List<StormCell> world = new StormGenerator(5).stormsNear(new SteeringWind(5), 0, 0, 1200, t, t, CLIMATE,
				settings(), RAINY);
		int there = 0;
		for(StormCell cell : world)
			if(cell.isAlive(t) && Math.hypot(cell.centerX(t), cell.centerZ(t)) < 1200) {
				there++;
				assertTrue(one.containsKey(cell.seed()), cell + " is missing");
			}
		assertEquals(one.size(), there);
	}

	/** Nobody near: the storms there are still the same, worked out twice. */
	@Test
	void stormsExistWithoutPlayers() {
		long now = 500000;
		StormGenerator generator = new StormGenerator(9);
		List<StormCell> a = generator.stormsNear(new SteeringWind(9), 20000, 20000, 500, now, now, CLIMATE, settings(), RAINY);
		List<StormCell> b = generator.stormsNear(new SteeringWind(9), 20000, 20000, 500, now, now, CLIMATE, settings(), RAINY);
		assertFalse(a.isEmpty());
		assertEquals(a.size(), b.size());
		for(int i = 0; i < a.size(); i++) {
			assertEquals(a.get(i).seed(), b.get(i).seed());
			assertEquals(a.get(i).centerX(now), b.get(i).centerX(now), 1e-9);
		}
	}

	/**
	 * Storms go with the wind, not toward anyone: each moves at the steering wind (times its kind's share) plus a small
	 * drift of its own, and a player standing somewhere else does not change the path of any storm.
	 */
	@Test
	void stormsMoveWithTheWindNotTowardPlayers() {
		long now = 300000;
		SteeringWind wind = new SteeringWind(5);
		StormField here = fill(5, now, at(0, 0));
		StormField there = fill(5, now, at(1500, 1500));
		Map<Long, StormCell> moved = new HashMap<>();
		for(StormCell cell : there.all())
			moved.put(cell.seed(), cell);
		int shared = 0;
		for(StormCell cell : here.all()) {
			long t = Math.max(now, cell.birthTick()) + 50;
			double windX = cell.profile().motionFactor() * wind.x(t) / 20, windZ = cell.profile().motionFactor() * wind.z(t) / 20;
			double drift = cell.kind() == StormKind.HURRICANE ? StormGenerator.HURRICANE_DRIFT : StormGenerator.MAX_DRIFT;
			assertEquals(windX, cell.velocityX(t), drift + 1e-9, cell + " is not going with the wind");
			assertEquals(windZ, cell.velocityZ(t), drift + 1e-9, cell + " is not going with the wind");
			StormCell same = moved.get(cell.seed());
			if(same != null) {
				shared++;
				assertEquals(cell.centerX(now + 10000), same.centerX(now + 10000), 1e-9);
				assertEquals(cell.centerZ(now + 10000), same.centerZ(now + 10000), 1e-9);
			}
		}
		assertTrue(shared > 3, "only " + shared + " storms seen from both places");
		// Not drawn in: on average they come no nearer than chance would bring them.
		double sum = 0;
		int n = 0;
		for(StormCell cell : here.all())
			if(cell.kind().bringsRain()) {
				sum += StormGenerator.closest(cell, at(0, 0), now, now + settings().horizonTicks());
				n++;
			}
		assertTrue(n > 5 && sum / n > 500, "storms crowd the player: mean closest " + sum / n + " over " + n);
	}

	/** Put in late, a storm is mid-life exactly where it would have been had someone watched it all along. */
	@Test
	void lateIsTheSameAsAllAlong() {
		long start = 200000, late = start + 30000;
		StormField watched = new StormField(new SteeringWind(13));
		StormGenerator generator = new StormGenerator(13);
		for(long t = start; t <= late; t += 600) {
			generator.topUp(watched, t, at(0, 0), CLIMATE, settings(), RAINY);
			watched.advance(t);
		}
		StormField joined = fill(13, late, at(0, 0));
		Map<Long, StormCell> before = near(watched, 0, 0, late, 1500);
		Map<Long, StormCell> after = near(joined, 0, 0, late, 1500);
		assertFalse(after.isEmpty());
		assertEquals(before.keySet(), after.keySet());
		boolean midLife = false;
		for(long seed : after.keySet()) {
			StormCell a = before.get(seed), b = after.get(seed);
			assertEquals(a.centerX(late), b.centerX(late), 1e-9);
			assertEquals(a.centerZ(late), b.centerZ(late), 1e-9);
			assertEquals(a.intensityAt(late), b.intensityAt(late), 1e-6);
			midLife |= b.birthTick() < late - 2000;
		}
		assertTrue(midLife, "no storm was already under way");
	}

	/** Storms nobody will see are dropped, and come back the same when someone goes there. */
	@Test
	void droppedAndPutBackTheSame() {
		long now = 300000;
		StormGenerator generator = new StormGenerator(5);
		StormField field = fill(5, now, at(0, 0));
		Map<Long, StormCell> first = near(field, 0, 0, now, 1500);
		generator.topUp(field, now, at(40000, 40000), CLIMATE, settings(), RAINY);
		assertTrue(near(field, 0, 0, now, 1500).isEmpty(), "storms kept where nobody is");
		generator.topUp(field, now, at(0, 0), CLIMATE, settings(), RAINY);
		Map<Long, StormCell> back = near(field, 0, 0, now, 1500);
		assertEquals(first.keySet(), back.keySet());
		for(long seed : first.keySet())
			assertEquals(first.get(seed).centerX(now), back.get(seed).centerX(now), 1e-9);
	}

	/** A storm removed on purpose stays gone, even after a reload. */
	@Test
	void removedStaysGone() {
		long now = 300000;
		StormField field = fill(5, now, at(0, 0));
		StormCell gone = field.all().get(0);
		assertTrue(field.remove(gone.id()));
		new StormGenerator(5).topUp(field, now + 40, at(0, 0), CLIMATE, settings(), RAINY);
		for(StormCell cell : field.all())
			assertNotEquals(gone.seed(), cell.seed());
		StormField back = new StormField(new SteeringWind(5));
		back.load(field.save(), now + 40);
		new StormGenerator(5).topUp(back, now + 80, at(0, 0), CLIMATE, settings(), RAINY);
		for(StormCell cell : back.all())
			assertNotEquals(gone.seed(), cell.seed(), "came back after a reload");
	}

	/** A front is a line across the wind: the cells it sets off stand along it, where it has got to by then. */
	@Test
	void frontsAreLinesAcrossTheWind() {
		SteeringWind wind = new SteeringWind(21);
		int checked = 0;
		for(int i = 0; i < 400 && checked < 5; i++) {
			StormGenerator.Front front = StormGenerator.front(wind, new SplittableRandom(i), 0, 0, 100000 + i * 997L, RAINY);
			if(front == null)
				continue;
			List<StormCell> cells = new ArrayList<>();
			StormGenerator.frontCells(wind, front, StormGenerator.Settings.defaults().weights(), RAINY, cells::add);
			assertTrue(cells.size() > 10, "a front set off only " + cells.size() + " cells");
			for(StormCell cell : cells) {
				long t = cell.birthTick();
				double dx = cell.originX() - front.track().centerX(t), dz = cell.originZ() - front.track().centerZ(t);
				double across = Math.abs(dx * front.dirZ() - dz * front.dirX());
				double along = Math.abs(dx * front.dirX() + dz * front.dirZ());
				assertTrue(across <= StormGenerator.FRONT_WIDTH + 1e-6, "cell " + across + " off the line");
				assertTrue(along <= front.length() / 2 + 1e-6);
			}
			// The line is square to the wind it formed in, and moves with it.
			double wx = wind.x(front.birth()), wz = wind.z(front.birth());
			assertEquals(0, (front.dirX() * wx + front.dirZ() * wz) / Math.hypot(wx, wz), 1e-9);
			checked++;
		}
		assertTrue(checked > 0, "no front on rainy days");
	}

	@Test
	void hurricanesFormOverOcean() {
		StormGenerator.Settings s = new StormGenerator.Settings(24000, 0, 2400, 400, new double[] { 1 }, 0, 0, 0);
		List<StormCell> found = new StormGenerator(11).stormsNear(new SteeringWind(11), -5000, 0, 3000, 0, 400000,
				CLIMATE, s, null);
		List<StormCell> hurricanes = found.stream().filter(c -> c.kind() == StormKind.HURRICANE).toList();
		assertFalse(hurricanes.isEmpty());
		for(StormCell h : hurricanes)
			assertTrue(CLIMATE.isOcean((int)Math.floor(h.originX()), (int)Math.floor(h.originZ())));
	}

	@Test
	void sameSeedSameStorms() {
		StormField a = fill(7, 100000, at(0, 0));
		StormField b = fill(7, 100000, at(0, 0));
		assertEquals(a.all().size(), b.all().size());
		for(int i = 0; i < a.all().size(); i++)
			assertEquals(a.all().get(i).centerX(120000), b.all().get(i).centerX(120000), 1e-9);
	}

	/** Dry days (2026-11-13): only the odd shower or ordinary thunderstorm, far fewer than a day with a chance of rain. */
	@Test
	void dryDaysHaveOnlyTheOddLightStorm() {
		StormGenerator.Outlooks dry = (x, z, t) -> new DayOutlook(DayTypes.dayOf(t), DayType.DRY, false, false);
		StormGenerator.Outlooks chance = (x, z, t) -> new DayOutlook(DayTypes.dayOf(t), DayType.CHANCE, false, false);
		StormGenerator.Settings s = new StormGenerator.Settings(24000, 7, 0, 400, StormGenerator.Settings.defaults().weights(),
				0, 0, 0);
		int dryCount = 0, chanceCount = 0;
		for(long seed = 1; seed <= 6; seed++) {
			StormField dryField = new StormField(new SteeringWind(seed)), chanceField = new StormField(new SteeringWind(seed));
			new StormGenerator(seed).topUp(dryField, 300000, at(0, 0), CLIMATE, s, dry);
			new StormGenerator(seed).topUp(chanceField, 300000, at(0, 0), CLIMATE, s, chance);
			for(StormCell c : dryField.all())
				assertTrue(c.kind() == StormKind.SHOWER || c.kind() == StormKind.THUNDERSTORM, "on a dry day: " + c.kind());
			dryCount += dryField.all().size();
			chanceCount += chanceField.all().size();
		}
		assertTrue(dryCount * 4 < chanceCount, "dry " + dryCount + " against chance " + chanceCount);
	}

	/**
	 * When the days move under the storms (sleep, /time set, /awm day; 2026-11-15), the world's own storms are dropped
	 * and rolled again for the days as they are now (LevelWeather#reroll): a rainy day's severe storms do not run on
	 * into what is now a dry day, and the light ones that still belong come back the same.
	 */
	@Test
	void rolledAgainWhenTheDaysMove() {
		StormGenerator.Outlooks dry = (x, z, t) -> new DayOutlook(DayTypes.dayOf(t), DayType.DRY, false, false);
		StormGenerator.Outlooks rainy = (x, z, t) -> new DayOutlook(DayTypes.dayOf(t), DayType.RAINY, false, false);
		StormGenerator.Settings s = new StormGenerator.Settings(24000, 7, 0, 400, StormGenerator.Settings.defaults().weights(),
				0, 0, 0);
		long now = 300000;
		int severeBefore = 0;
		for(long seed = 1; seed <= 6; seed++) {
			StormField field = new StormField(new SteeringWind(seed));
			StormGenerator generator = new StormGenerator(seed);
			generator.topUp(field, now, at(0, 0), CLIMATE, s, rainy);
			for(StormCell c : field.all())
				if(c.kind() != StormKind.SHOWER && c.kind() != StormKind.THUNDERSTORM)
					severeBefore++;
			for(StormCell c : field.all())
				if(field.isNatural(c))
					field.dematerialize(c);
			generator.topUp(field, now, at(0, 0), CLIMATE, s, dry);
			for(StormCell c : field.all())
				assertTrue(c.kind() == StormKind.SHOWER || c.kind() == StormKind.THUNDERSTORM, "after the days moved to dry: " + c.kind());
		}
		assertTrue(severeBefore > 0, "no severe storms on the rainy days to begin with");
	}

	/** No hurricane forms on a dry day, and one runs out early in the first dry day it meets. */
	@Test
	void noHurricanesOnDryDays() {
		StormGenerator.Settings d = StormGenerator.Settings.defaults();
		StormGenerator.Settings hurricanesOnly = new StormGenerator.Settings(d.horizonTicks(), 0, d.hurricaneInterval() / 40, d.maxStorms(), d.weights(), 0, 0, 0);
		StormGenerator.Outlooks dry = (x, z, t) -> new DayOutlook(DayTypes.dayOf(t), DayType.DRY, false, false);
		List<StormCell> none = new StormGenerator(11).stormsNear(new SteeringWind(11), -5000, 0, 3000, 0, 400000, CLIMATE,
				hurricanesOnly, dry);
		assertTrue(none.stream().noneMatch(c -> c.kind() == StormKind.HURRICANE), "a hurricane on dry days");
		// Every other day dry: each dies within a twelfth of a day into the dry one.
		StormGenerator.Outlooks alternate = (x, z, t) -> new DayOutlook(DayTypes.dayOf(t),
				Math.floorMod(DayTypes.dayOf(t), 2) == 0 ? DayType.DRY : DayType.RAINY, false, false);
		List<StormCell> some = new StormGenerator(11).stormsNear(new SteeringWind(11), -5000, 0, 3000, 0, 400000, CLIMATE,
				hurricanesOnly, alternate);
		List<StormCell> hurricanes = some.stream().filter(c -> c.kind() == StormKind.HURRICANE).toList();
		assertFalse(hurricanes.isEmpty());
		for(StormCell h : hurricanes) {
			long death = h.deathTick();
			long day = DayTypes.dayOf(death);
			boolean deathOnDry = Math.floorMod(day, 2) == 0;
			assertTrue(!deathOnDry || death - day * DayTypes.DAY <= StormGenerator.DRY_GRACE, "hurricane runs on into a dry day");
		}
	}

	@Test
	void forecastFindsTheArrival() {
		long now = 300000;
		StormField field = fill(42, now, at(500, -300));
		Optional<Forecast.Arrival> arrival = Optional.empty();
		for(StormCell cell : field.all()) // Somewhere a storm will pass over.
			if(cell.kind().bringsRain() && cell.kind() != StormKind.HURRICANE && cell.birthTick() > now) {
				long mid = cell.birthTick() + cell.lifetimeTicks() / 2;
				double x = cell.centerX(mid), z = cell.centerZ(mid);
				arrival = Forecast.nextArrival(field, x, z, now, mid - now + 2000);
				if(arrival.isPresent()) {
					Forecast.Arrival a = arrival.get();
					assertTrue(a.arrival() <= a.peakTick() && a.peakTick() <= a.departure());
					assertTrue(field.sample(x, z, a.peakTick()).rain() > WeatherModel.RAINING);
					break;
				}
			}
		assertTrue(arrival.isPresent());
	}

	@Test
	void fieldSavesAndLoads() {
		StormField field = fill(42, 100000, at(0, 0));
		var tag = field.save();
		StormField back = new StormField(new SteeringWind(42));
		back.load(tag, 100000);
		assertEquals(field.all().size(), back.all().size());
		assertEquals(field.sample(100, 100, 110000).rain(), back.sample(100, 100, 110000).rain(), 1e-9);
		for(StormCell cell : back.all())
			assertTrue(back.isNatural(cell));
	}

	@Test
	void advanceFormsAndDissipates() {
		StormField field = new StormField(new SteeringWind(1));
		StormCell cell = new StormCell(0, StormKind.SHOWER, 1000, 5000, 0, 0, 0, 0, 100, 0.5f, 1, field.wind());
		field.add(cell, 0);
		assertEquals(1, field.pending().size());
		var formed = field.advance(1000);
		assertEquals(1, formed.formed().size());
		assertEquals(1, field.active().size());
		var mature = field.advance(2500);
		assertEquals(1, mature.stages().size());
		var gone = field.advance(6000);
		assertEquals(1, gone.dissipated().size());
		assertTrue(field.all().isEmpty());
	}

	@Test
	void poissonMean() {
		SplittableRandom r = new SplittableRandom(1);
		double sum = 0;
		for(int i = 0; i < 20000; i++)
			sum += StormGenerator.poisson(r, 2.5);
		assertEquals(2.5, sum / 20000, 0.05);
	}
}
