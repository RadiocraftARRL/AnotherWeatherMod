package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.network.NearbyStormsPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RainOnsetTest {

	/**
	 * The maintainer's onset (2026-11-13): a few drops here and there far out, then occasional ones, then frequent, then
	 * rain at the edge; nothing beyond the reach or deep inside the rain.
	 */
	@Test
	void dropsBuildUpAsTheRainNears() {
		assertEquals(0, RainOnset.dropsPerTick(RainOnset.REACH, 1), 1e-9);
		assertEquals(0, RainOnset.dropsPerTick(500, 1), 1e-9);
		assertEquals(0, RainOnset.dropsPerTick(-RainOnset.INSIDE - 1, 1), 1e-9);
		double far = RainOnset.dropsPerTick(110, 1), mid = RainOnset.dropsPerTick(70, 1), near = RainOnset.dropsPerTick(25, 1);
		double edge = RainOnset.dropsPerTick(0, 1);
		assertTrue(far > 0 && far * 20 < 2, "a few drops a second at most far out: " + far * 20);
		assertTrue(mid > far * 4, "occasional, then more: " + mid);
		assertTrue(near > mid * 3, "frequent near the rain: " + near);
		assertEquals(RainOnset.MOST, edge, 1e-9, "raining at the edge");
		double last = 0;
		for(double d = RainOnset.REACH - 1; d >= 0; d -= 5) {
			double n = RainOnset.dropsPerTick(d, 1);
			assertTrue(n >= last, "never fewer nearer");
			last = n;
		}
		assertTrue(RainOnset.dropsPerTick(30, 0.4) < RainOnset.dropsPerTick(30, 1), "a weak storm, fewer drops");
	}

	/** From the storms sent: the nearest rain counts; dust and snow storms bring no drops. */
	@Test
	void nearestRainingStormCounts() {
		NearbyStormsPayload.Storm rain = new NearbyStormsPayload.Storm(1, StormKind.THUNDERSTORM, 0, 0, 0, 0, 400, 1, 1, 0, 0, 0, 0,
				Precipitation.RAIN);
		double edge = rain.rainEdge();
		NearbyStormsPayload one = new NearbyStormsPayload(0, List.of(rain));
		assertEquals(RainOnset.dropsPerTick(40, 1), RainOnset.dropsAt(one, edge + 40, 0, 0), 1e-9);
		NearbyStormsPayload.Storm dust = new NearbyStormsPayload.Storm(2, StormKind.THUNDERSTORM, 0, 0, 0, 0, 400, 1, 1, 0, 0, 0, 0,
				Precipitation.DUST);
		assertEquals(0, RainOnset.dropsAt(new NearbyStormsPayload(0, List.of(dust)), edge + 40, 0, 0), 1e-9);
	}
}
