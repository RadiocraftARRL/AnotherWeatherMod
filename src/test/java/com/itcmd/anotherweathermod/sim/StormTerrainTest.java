package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Storms that change size (2026-11-14): bigger over the ocean, smaller over desert, and a slow swell of their own. */
class StormTerrainTest {

	private final SteeringWind wind = new SteeringWind(1234);

	/** Everywhere the same ground. */
	private static Climate everywhere(boolean ocean, boolean desert) {
		return new Climate() {
			@Override public Precipitation basePrecipitation(int x, int y, int z) { return desert ? Precipitation.NONE : Precipitation.RAIN; }
			@Override public float biomeTemperature(int x, int y, int z) { return desert ? 2.0f : 0.8f; }
			@Override public boolean isOcean(int x, int z) { return ocean; }
			@Override public int seaLevel() { return 63; }
		};
	}

	private StormCell cell(Climate ground) {
		StormCell c = new StormCell(7, StormKind.THUNDERSTORM, 1000, 40000, 50, -80, 0.01, -0.005, 200, 0.9f, 99, wind);
		c.setTerrain(ground);
		return c;
	}

	@Test
	void biggerOverTheSeaSmallerOverDesert() {
		long late = 1000 + 30000; // Long enough to have settled.
		double sea = cell(everywhere(true, false)).radiusAt(late), land = cell(everywhere(false, false)).radiusAt(late),
				desert = cell(everywhere(false, true)).radiusAt(late);
		assertTrue(sea > land * 1.15, "over the sea " + sea + " vs land " + land);
		assertTrue(desert < land * 0.85, "over desert " + desert + " vs land " + land);
		assertTrue(cell(everywhere(true, false)).intensityAt(late) > cell(everywhere(false, true)).intensityAt(late), "stronger at sea than in the desert");
	}

	@Test
	void easesInRatherThanJumping() {
		StormCell c = cell(everywhere(true, false));
		double last = c.terrainFactor(1000);
		for(long t = 1000; t < 41000; t += 100) {
			double f = c.terrainFactor(t);
			assertTrue(Math.abs(f - last) < 0.01, "jumped at " + t + ": " + last + " to " + f);
			last = f;
		}
		assertTrue(last > 1.2 && last <= StormCell.OCEAN_SIZE + 1e-6, "settled at " + last);
	}

	@Test
	void swellsWithinBoundsTheSameEveryTime() {
		StormCell a = cell(null), b = cell(null);
		double min = 9, max = 0;
		for(long t = 1000; t < 41000; t += 250) {
			double w = a.wobble(t);
			assertEquals(w, b.wobble(t), 1e-12, "the same storm swells the same way");
			min = Math.min(min, w);
			max = Math.max(max, w);
		}
		assertTrue(min >= 1 - StormCell.WOBBLE - 1e-9 && max <= 1 + StormCell.WOBBLE + 1e-9, min + ".." + max);
		assertTrue(max - min > StormCell.WOBBLE * 0.5, "it does swell: " + min + ".." + max);
	}
}
