package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.api.TropicalAdvisory;
import com.itcmd.anotherweathermod.api.TropicalAdvisory.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TropicalOutlookTest {

	private static final long DAY = DayTypes.DAY;

	/** The stated chance is honest: disturbances given about X% develop about X% of the time, 70% on average. */
	@Test
	void chancesAreHonest() {
		int[] said = new int[5], developed = new int[5];
		int all = 0, grew = 0;
		for(long seed = 1; seed <= 40000; seed++) {
			double chance = TropicalDevelopment.chance(seed);
			assertTrue(chance >= TropicalDevelopment.CHANCE_MIN && chance <= TropicalDevelopment.CHANCE_MAX);
			int bucket = Math.min(4, (int)((chance - 0.5) / 0.08));
			said[bucket]++;
			boolean develops = TropicalDevelopment.develops(seed);
			if(develops) {
				developed[bucket]++;
				grew++;
				assertTrue(TropicalDevelopment.category(seed) >= 1, "developed, but category 0");
			}
			else
				assertEquals(0, TropicalDevelopment.category(seed));
			all++;
		}
		assertEquals(0.7, grew / (double)all, 0.01);
		for(int b = 0; b < 5; b++)
			assertEquals(0.54 + 0.08 * b, developed[b] / (double)said[b], 0.02, "bucket " + b);
	}

	/** A hurricane of {@code radius} that lives four days, drifting east fast enough to cross a cone in well under a day. */
	private static StormCell hurricane(long birth, long seed, double radius) {
		return new StormCell(1, StormKind.HURRICANE, birth, (int)(4 * DAY), 0, 0, 0.06, 0.0, radius, 0.9f, seed, 3, new SteeringWind(5));
	}

	/** A place on the track hears of it as a disturbance before it forms, then an outlook, a watch and a warning. */
	@Test
	void stagesAsItComes() {
		long birth = 100 * DAY;
		StormCell cell = hurricane(birth, 77, 300);
		// Beside the track, nine tenths of the cone off it, where the centre passes 1.7 days after it forms: in the cone
		// from a little before, between one and two days after it forms, so every stage comes.
		long hit = birth + DAY + 7 * DAY / 10;
		double x = cell.centerX(hit), z = cell.centerZ(hit) + TropicalOutlook.coneRadius(cell) * 0.9;
		long arrival = TropicalOutlook.advise(cell, x, z, birth).arrivalTick();
		assertTrue(arrival > birth + DAY + 2000 && arrival < birth + 2 * DAY - 2000, "arrives " + (arrival - birth) + " after it forms");
		TropicalAdvisory early = TropicalOutlook.advise(cell, x, z, birth - 1000);
		assertNotNull(early, "no advisory just before it forms");
		assertEquals(Stage.DISTURBANCE, early.stage());
		assertEquals(TropicalDevelopment.chancePercent(77), early.chancePercent());
		assertEquals(-1, early.category());
		assertTrue(early.arrivalTick() <= hit, "the cone reaches it after the centre passes");
		assertFalse(early.track().isEmpty());
		assertEquals(Stage.OUTLOOK, TropicalOutlook.advise(cell, x, z, arrival - DAY - 1000).stage());
		assertEquals(Stage.WATCH, TropicalOutlook.advise(cell, x, z, arrival - DAY / 2 - 1000).stage());
		TropicalAdvisory warning = TropicalOutlook.advise(cell, x, z, arrival - 1000);
		assertEquals(Stage.WARNING, warning.stage());
		assertEquals(3, warning.category());
		assertTrue(warning.hurricane());
	}

	/** Outside the cone (more than twice the storm's width off its track), nothing. Not too early either. */
	@Test
	void onlyInTheCone() {
		long birth = 100 * DAY;
		StormCell cell = hurricane(birth, 78, 1000);
		long hit = birth + DAY;
		double x = cell.centerX(hit), z = cell.centerZ(hit);
		double cone = TropicalOutlook.coneRadius(cell);
		assertEquals(4000, cone, 1e-9); // Twice its width (2000) each side.
		// Across the track: velocity along x mostly, so step off in z.
		assertNotNull(TropicalOutlook.advise(cell, x, z + cone * 0.9, birth));
		assertNull(TropicalOutlook.advise(cell, x, z + cone * 1.6, birth));
		assertNull(TropicalOutlook.advise(cell, x + 20000, z, birth - DAY / 2), "heard of from far down the track, past two days");
	}

	/** A disturbance that fizzles is forecast as if it went on (a false alarm), but once formed and weak, no watch comes. */
	@Test
	void fizzlersAreFalseAlarms() {
		long seed = 1;
		while(!TropicalDevelopment.fizzles(seed))
			seed++;
		long birth = 100 * DAY;
		StormCell full = hurricane(birth, seed, 300);
		StormCell fizzler = full.withLifetime((int)(DAY / 3)).withCategory(0);
		long hit = birth + DAY + 3 * DAY / 4;
		double x = full.centerX(hit), z = full.centerZ(hit) + TropicalOutlook.coneRadius(full) * 0.5;
		TropicalAdvisory before = TropicalOutlook.advise(fizzler, x, z, birth - 1000);
		assertNotNull(before, "a fizzler gives no disturbance notice");
		assertEquals(Stage.DISTURBANCE, before.stage());
		assertNull(TropicalOutlook.advise(fizzler, x, z, birth + DAY / 6), "a fizzled storm still gives a watch");
	}
}
