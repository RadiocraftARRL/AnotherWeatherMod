package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.api.DayType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StratusTest {

	/** Only dry days, and each its own: clear, a veil, broken cloud, or (one in ten) an overcast, in the planned shares. */
	@Test
	void dryDaysVary() {
		int clear = 0, veil = 0, broken = 0, overcast = 0, days = 4000;
		for(long day = 0; day < days; day++) {
			assertEquals(0, Stratus.coverage(DayType.CHANCE, day), 1e-9);
			assertEquals(0, Stratus.coverage(DayType.RAINY, day), 1e-9);
			double c = Stratus.coverage(DayType.DRY, day);
			assertEquals(c, Stratus.coverage(DayType.DRY, day), 1e-12, "the same all day");
			if(c == 0)
				clear++;
			else if(c <= Stratus.VEIL_MAX)
				veil++;
			else if(c <= Stratus.BROKEN_MAX)
				broken++;
			else
				overcast++;
		}
		assertEquals(Stratus.CLEAR_DAYS, clear / (double)days, 0.03, "clear");
		assertEquals(Stratus.VEIL_DAYS, veil / (double)days, 0.03, "veil");
		assertEquals(Stratus.BROKEN_DAYS, broken / (double)days, 0.03, "broken");
		assertEquals(0.1, overcast / (double)days, 0.03, "overcast");
	}

	/** An overcast is a continuous layer, thick and thin: no clear holes and no solid stretches (2026-11-15). */
	@Test
	void overcastHasNoHoles() {
		double least = 1, most = 0;
		for(int x = 0; x < Stratus.SIZE; x++)
			for(int y = 0; y < Stratus.SIZE; y++) {
				double o = Stratus.opacity(x, y, Stratus.OVERCAST_MIN);
				least = Math.min(least, o);
				most = Math.max(most, o);
			}
		assertTrue(least >= 0.3, "a hole in the overcast: " + least);
		assertTrue(most <= 0.9 && most - least > 0.2, "an even sheet: " + least + " to " + most);
	}

	/** The sheet repeats seamlessly, and covers about as much of the sky as the day says. */
	@Test
	void textureTilesAndCovers() {
		for(int i = 0; i < Stratus.SIZE; i += 7) {
			assertEquals(Stratus.noise(0, i), Stratus.noise(Stratus.SIZE, i), 1e-9);
			assertEquals(Stratus.noise(i, 0), Stratus.noise(i, Stratus.SIZE), 1e-9);
		}
		double veil = mean(Stratus.VEIL_MIN), broken = mean(Stratus.BROKEN_MAX), overcast = mean(Stratus.OVERCAST_MAX);
		assertTrue(veil > 0.08 && veil < Stratus.VEIL_MIN, "a veil covers about as much as it says, see-through: " + veil);
		assertTrue(broken > veil && broken < 0.7, "broken cloud: " + broken);
		assertTrue(overcast > 0.5, "an overcast: " + overcast);
		assertEquals(0, mean(0), 1e-9);
	}

	private static double mean(double coverage) {
		double sum = 0;
		for(int x = 0; x < Stratus.SIZE; x++)
			for(int y = 0; y < Stratus.SIZE; y++)
				sum += Stratus.opacity(x, y, coverage);
		return sum / (Stratus.SIZE * Stratus.SIZE);
	}
}
