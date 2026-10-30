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
	private final DayTypes days = new DayTypes(SEED, new SteeringWind(SEED), DayTypes.DEFAULT_REGION_SIZE);

	private DayOutlook at(double x, double z, long day) {
		return days.at(x, z, day, day * DayTypes.DAY);
	}

	@Test
	void halfDryThreeTenthsChanceOneFifthRainy() {
		Map<DayType, Integer> counts = new EnumMap<>(DayType.class);
		int n = 0;
		for(long day = 0; day < 400; day++)
			for(int i = 0; i < 25; i++, n++)
				counts.merge(at(i * 7919.0, i * -6271.0, day).type(), 1, Integer::sum);
		assertEquals(0.5, counts.get(DayType.DRY) / (double)n, 0.03);
		assertEquals(0.3, counts.get(DayType.CHANCE) / (double)n, 0.03);
		assertEquals(0.2, counts.get(DayType.RAINY) / (double)n, 0.03);
	}

	@Test
	void sameAnswerEveryTime() {
		for(long day = 0; day < 20; day++)
			assertEquals(at(1234, -5678, day), new DayTypes(SEED, new SteeringWind(SEED), DayTypes.DEFAULT_REGION_SIZE)
					.at(1234, -5678, day, day * DayTypes.DAY));
	}

	@Test
	void regionsAreBigAndEdgesThin() {
		// Along a line, the type changes only a few times per region size, and edges cover a sliver of it.
		int changes = 0, edge = 0, steps = 0;
		DayType last = null;
		for(double x = 0; x < 40000; x += 8, steps++) {
			DayOutlook o = at(x, 777, 5);
			if(o.regionEdge())
				edge++;
			if(last != null && o.type() != last)
				changes++;
			last = o.type();
		}
		assertTrue(changes < 40000 / DayTypes.DEFAULT_REGION_SIZE * 1.5, "changes " + changes);
		double edgeShare = edge / (double)steps;
		assertTrue(edgeShare > 0.005 && edgeShare < 0.06, "edge share " + edgeShare);
	}

	@Test
	void regionsMoveFromDayToDay() {
		int differ = 0;
		for(long day = 0; day < 50; day++)
			if(at(0, 0, day).type() != at(0, 0, day + 1).type())
				differ++;
		assertTrue(differ > 15, "the weather at one place changed on only " + differ + " of 50 days");
	}

	@Test
	void watchesOnlyOnRainyDaysAndNotOnEdges() {
		int watches = 0, total = 0;
		for(long day = 0; day < 300; day++)
			for(int i = 0; i < 10; i++, total++) {
				DayOutlook o = at(i * 3001.0, i * 4999.0, day);
				if(o.tornadoWatch()) {
					watches++;
					assertEquals(DayType.RAINY, o.type());
					assertFalse(o.regionEdge());
				}
			}
		double share = watches / (double)total;
		assertTrue(share > 0.04 && share < 0.14, "watch days " + share);
	}
}
