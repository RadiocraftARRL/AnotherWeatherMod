package com.itcmd.anotherweathermod.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteeringWindTest {

	/** The angle between two bearings, degrees, 0 to 180. */
	private static double turn(double a, double b) {
		double d = Math.toDegrees(b - a) % 360;
		if(d < 0)
			d += 360;
		return d > 180 ? 360 - d : d;
	}

	@Test
	void shiftsAboutEveryFourDays() {
		SteeringWind wind = new SteeringWind(42);
		long t = 0;
		int shifts = 0;
		while(true) {
			long next = wind.nextShift(t);
			if(next > 96000L * 50)
				break;
			assertTrue(next > t);
			t = next;
			shifts++;
		}
		assertEquals(50, shifts, 1);
	}

	@Test
	void aShiftTurnsTheWindSharply() {
		for(long seed = 1; seed <= 20; seed++) {
			SteeringWind wind = new SteeringWind(seed);
			long shift = wind.nextShift(500_000);
			// Across the shift the slow drift and wobble move a little; the shift itself is 60 to 150 degrees.
			double turned = turn(wind.direction(shift), wind.direction(shift + SteeringWind.SHIFT_TICKS));
			assertTrue(turned > 35 && turned < 175, "seed " + seed + " turned " + turned);
			assertTrue(wind.nearShift(shift - 1000) && wind.nearShift(shift + 2500));
			assertTrue(!wind.nearShift(shift + 20000) || wind.nextShift(shift + 20000) - (shift + 20000) <= SteeringWind.FRONT_BEFORE);
		}
	}

	@Test
	void directionIsSmooth() {
		SteeringWind wind = new SteeringWind(7);
		for(long t = 0; t < 96000L * 10; t += 20)
			assertTrue(turn(wind.direction(t), wind.direction(t + 20)) < 3, "jump at " + t);
	}
}
