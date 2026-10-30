package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StormGeneratorTest {

	/** Plains everywhere, ocean west of x = -1000. */
	static final Climate CLIMATE = new Climate() {
		@Override public Precipitation basePrecipitation(int x, int y, int z) { return Precipitation.RAIN; }
		@Override public float biomeTemperature(int x, int y, int z) { return 0.8f; }
		@Override public boolean isOcean(int x, int z) { return x < -1000; }
		@Override public int seaLevel() { return 63; }
	};

	private static StormGenerator.Settings settings(double perHour, double hurricaneInterval) {
		return new StormGenerator.Settings(600, 24000, perHour, hurricaneInterval, 64, new double[] { 0.55, 0.33, 0.12 }, 0, 0, 0);
	}

	@Test
	void stormsAreDecidedAheadAndAimedAtTheTarget() {
		StormField field = new StormField(new SteeringWind(42));
		StormGenerator generator = new StormGenerator(42);
		List<StormCell> added = generator.topUp(field, 100000, List.of(new StormGenerator.Target(500, -300)), CLIMATE,
				settings(30, 0));
		assertFalse(added.isEmpty());
		for(StormCell cell : added) {
			assertTrue(cell.birthTick() >= 100000 - 600 && cell.birthTick() <= 100000 + 24000 + 600);
			long arrival = cell.birthTick() + (long)(cell.lifetimeTicks() * StormGenerator.ARRIVAL);
			double dx = cell.centerX(arrival) - 500;
			double dz = cell.centerZ(arrival) - -300;
			assertTrue(Math.sqrt(dx * dx + dz * dz) <= cell.peakRadius() * 0.6 + 1, cell + " misses its target");
		}
	}

	@Test
	void sameSeedSameForecast() {
		StormField a = new StormField(new SteeringWind(7));
		StormField b = new StormField(new SteeringWind(7));
		List<StormGenerator.Target> targets = List.of(new StormGenerator.Target(0, 0));
		new StormGenerator(7).topUp(a, 5000, targets, CLIMATE, settings(30, 0));
		new StormGenerator(7).topUp(b, 5000, targets, CLIMATE, settings(30, 0));
		assertEquals(a.all().size(), b.all().size());
		for(int i = 0; i < a.all().size(); i++)
			assertEquals(a.all().get(i).centerX(20000), b.all().get(i).centerX(20000), 1e-9);
	}

	@Test
	void slotsAreDecidedOnce() {
		StormField field = new StormField(new SteeringWind(3));
		StormGenerator generator = new StormGenerator(3);
		List<StormGenerator.Target> targets = List.of(new StormGenerator.Target(0, 0));
		generator.topUp(field, 0, targets, CLIMATE, settings(30, 0));
		int first = field.all().size();
		assertTrue(generator.topUp(field, 0, targets, CLIMATE, settings(30, 0)).isEmpty());
		assertEquals(first, field.all().size());
	}

	@Test
	void hurricanesFormOverOceanAndReachTheTarget() {
		StormField field = new StormField(new SteeringWind(11));
		StormGenerator generator = new StormGenerator(11);
		List<StormCell> added = generator.topUp(field, 0, List.of(new StormGenerator.Target(0, 0)), CLIMATE,
				settings(0, 600)); // A hurricane every slot.
		List<StormCell> hurricanes = added.stream().filter(c -> c.kind() == StormKind.HURRICANE).toList();
		assertFalse(hurricanes.isEmpty());
		for(StormCell h : hurricanes) {
			assertTrue(CLIMATE.isOcean((int)h.originX(), (int)h.originZ()));
			long arrival = h.birthTick() + h.lifetimeTicks() / 2;
			double d = Math.hypot(h.centerX(arrival), h.centerZ(arrival));
			assertTrue(d <= h.peakRadius() * 0.3 + 1, "hurricane ends up " + d + " from its target");
		}
	}

	@Test
	void forecastFindsTheArrival() {
		StormField field = new StormField(new SteeringWind(42));
		StormGenerator generator = new StormGenerator(42);
		generator.topUp(field, 100000, List.of(new StormGenerator.Target(500, -300)), CLIMATE, settings(30, 0));
		Optional<Forecast.Arrival> arrival = Forecast.nextArrival(field, 500, -300, 100000, 24000 + 40000);
		assertTrue(arrival.isPresent());
		Forecast.Arrival a = arrival.get();
		assertTrue(a.arrival() <= a.peakTick() && a.peakTick() <= a.departure());
		// It really rains there then.
		assertTrue(field.sample(500, -300, a.peakTick()).rain() > WeatherModel.RAINING);
	}

	@Test
	void fieldSavesAndLoads() {
		StormField field = new StormField(new SteeringWind(42));
		new StormGenerator(42).topUp(field, 100000, List.of(new StormGenerator.Target(0, 0)), CLIMATE, settings(30, 0));
		var tag = field.save();
		StormField back = new StormField(new SteeringWind(42));
		back.load(tag, 100000);
		assertEquals(field.all().size(), back.all().size());
		assertEquals(field.nextSlotTick(), back.nextSlotTick());
		assertEquals(field.sample(100, 100, 110000).rain(), back.sample(100, 100, 110000).rain(), 1e-9);
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
}
