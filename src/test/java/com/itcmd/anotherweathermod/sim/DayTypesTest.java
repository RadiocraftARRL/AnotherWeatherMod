package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.DayOutlook;
import com.itcmd.anotherweathermod.api.DayType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DayTypesTest {

	private static final long SEED = 42;
	private final DayTypes days = new DayTypes(SEED, new SteeringWind(SEED));

	private DayOutlook at(double x, double z, long day) {
		return days.at(x, z, day, day * DayTypes.DAY);
	}

	@Test
	void halfDryThreeTenthsChanceOneFifthRainy() {
		Map<DayType, Integer> counts = new EnumMap<>(DayType.class);
		int n = 4000;
		for(long day = 0; day < n; day++)
			counts.merge(at(0, 0, day).type(), 1, Integer::sum);
		assertEquals(0.5, counts.get(DayType.DRY) / (double)n, 0.03);
		assertEquals(0.3, counts.get(DayType.CHANCE) / (double)n, 0.03);
		assertEquals(0.2, counts.get(DayType.RAINY) / (double)n, 0.03);
	}

	/** Days lean lightly on the day before (2026-11-15): a wet day follows a wet one more often than half the time. */
	@Test
	void wetAndDrySpells() {
		int wet = 0, wetAfterWet = 0, n = 8000;
		for(long day = 1; day < n; day++)
			if(at(0, 0, day - 1).type() != DayType.DRY) {
				wet++;
				if(at(0, 0, day).type() != DayType.DRY)
					wetAfterWet++;
			}
		double after = wetAfterWet / (double)wet;
		assertTrue(after > 0.58 && after < 0.68, "a wet day after a wet one " + after + " of the time");
	}

	@Test
	void sameAnswerEveryTime() {
		for(long day = 0; day < 20; day++)
			assertEquals(at(1234, -5678, day), new DayTypes(SEED, new SteeringWind(SEED))
					.at(1234, -5678, day, day * DayTypes.DAY));
	}

	/**
	 * The day is the world's, not the place's: walking anywhere (or along what used to be a region's edge, where the
	 * sky flipped chunk by chunk) never changes it, and there are no edges.
	 */
	@Test
	void theSameDayEverywhere() {
		for(long day = 0; day < 60; day++) {
			DayOutlook here = at(0, 0, day);
			for(double x = -40000; x <= 40000; x += 16)
				assertEquals(here, at(x, x * 0.37 - 777, day), "day " + day + " at x " + x);
			assertFalse(here.regionEdge());
		}
	}

	@Test
	void theDayChangesFromDayToDay() {
		int differ = 0;
		for(long day = 0; day < 50; day++)
			if(at(0, 0, day).type() != at(0, 0, day + 1).type())
				differ++;
		assertTrue(differ > 15, "the weather at one place changed on only " + differ + " of 50 days");
	}

	@Test
	void watchesOnlyOnRainyDays() {
		int watches = 0, total = 3000;
		for(long day = 0; day < total; day++) {
			DayOutlook o = at(0, 0, day);
			if(o.tornadoWatch()) {
				watches++;
				assertEquals(DayType.RAINY, o.type());
			}
		}
		double share = watches / (double)total;
		assertTrue(share > 0.04 && share < 0.14, "watch days " + share);
	}
}
