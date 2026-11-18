package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.StormKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hurricane's category follows its wind (2026-11-18): a tropical storm forming, its peak at its strongest. */
class HurricaneCategoryTest {

	private final SteeringWind wind = new SteeringWind(1234);

	private StormCell storm(int peak) {
		int life = 3 * 24000;
		return StormCell.atLife(StormKind.HURRICANE, 0, 0, 0, 0, life, 900, 1f, 7, wind).withCategory(peak);
	}

	@Test
	void formsAsATropicalStormAndReachesItsPeak() {
		StormCell storm = storm(4);
		long birth = storm.birthTick(), life = storm.deathTick() - birth;
		assertEquals(0, storm.categoryAt(birth + 10), "it was not a tropical storm as it formed");
		int most = 0, last = 0;
		boolean neverDown = true;
		for(long t = birth; t < birth + life / 2; t += 600) {
			int c = storm.categoryAt(t);
			if(c < last)
				neverDown = false;
			last = c;
			most = Math.max(most, c);
		}
		assertTrue(neverDown, "it weakened while still strengthening");
		assertEquals(4, most, "it never reached its peak");
		assertTrue(storm.categoryAt(storm.deathTick() - 200) < 4, "it did not weaken as it died");
	}

	@Test
	void neverPastItsPeak() {
		for(int peak = 0; peak <= 5; peak++) {
			StormCell storm = storm(peak);
			for(long t = storm.birthTick(); t < storm.deathTick(); t += 2400)
				assertTrue(storm.categoryAt(t) <= peak, "category " + storm.categoryAt(t) + " past peak " + peak);
		}
	}

	@Test
	void eachCategorysFullWindIsThatCategory() {
		for(int c = 0; c <= 5; c++)
			assertEquals(c, StormCell.categoryFor(StormCell.HURRICANE_WIND[c], 5));
		assertEquals(2, StormCell.categoryFor(StormCell.HURRICANE_WIND[5], 2), "past the peak");
	}
}
