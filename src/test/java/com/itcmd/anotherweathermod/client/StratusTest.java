package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.api.DayType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StratusTest {

	/** Only dry days, and each its own: some clear, some a veil, some overcast, in about the planned shares. */
	@Test
	void dryDaysVary() {
		int clear = 0, veil = 0, overcast = 0, days = 2000;
		for(long day = 0; day < days; day++) {
			assertEquals(0, Stratus.coverage(DayType.CHANCE, day), 1e-9);
			assertEquals(0, Stratus.coverage(DayType.RAINY, day), 1e-9);
			double c = Stratus.coverage(DayType.DRY, day);
			assertEquals(c, Stratus.coverage(DayType.DRY, day), 1e-12, "the same all day");
			if(c == 0)
				clear++;
			else if(c < 0.6)
				veil++;
			else
				overcast++;
		}
		assertTrue(Math.abs(clear / (double)days - Stratus.CLEAR_DAYS) < 0.05, "clear " + clear);
		assertTrue(Math.abs(veil / (double)days - Stratus.VEIL_DAYS) < 0.05, "veil " + veil);
		assertTrue(overcast > days / 6, "overcast " + overcast);
	}

	/** The sheet repeats seamlessly, and covers about as much of the sky as the day says. */
	@Test
	void textureTilesAndCovers() {
		for(int i = 0; i < Stratus.SIZE; i += 7) {
			assertEquals(Stratus.noise(0, i), Stratus.noise(Stratus.SIZE, i), 1e-9);
			assertEquals(Stratus.noise(i, 0), Stratus.noise(i, Stratus.SIZE), 1e-9);
		}
		double thin = mean(0.35), thick = mean(0.8);
		assertTrue(thin > 0.1 && thin < 0.55, "a veil: " + thin);
		assertTrue(thick > 0.6, "an overcast: " + thick);
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
