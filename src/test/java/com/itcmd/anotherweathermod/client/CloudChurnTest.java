package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.network.NearbyStormsPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

/** Counts how fair-weather cells flip near a passing storm, and why. */
class CloudChurnTest {

	static NearbyStormsPayload payload(long t, float intensity) {
		// Storm moving east at 0.3 b/t, radius 400, starting 900 blocks west of the camera at origin.
		double x = -900 + 0.3 * t;
		return new NearbyStormsPayload(t, List.of(new NearbyStormsPayload.Storm(1, StormKind.SEVERE_THUNDERSTORM, x, 0,
				0.3f, 0, 400, intensity, 3, 0, 0, 0, 0, Precipitation.RAIN)));
	}

	/** Fair-weather cells that changed over the run; the rest is printed. */
	int run(String label, boolean stormOn, boolean steppedIntensity, boolean drift) {
		int range = 12; // cells each way (~400 blocks)
		int[][] prev = null;
		int vanish = 0, appear = 0, resize = 0, edge = 0;
		int atPayload = 0, between = 0;
		NearbyStormsPayload p = null;
		for(long t = 0; t <= 2400; t += CloudRenderer.RESAMPLE_TICKS) {
			if(t % 40 == 0) {
				float intensity = steppedIntensity ? (float)(0.9 - 0.4 * t / 2400.0) : 0.7f;
				p = stormOn ? payload(t, intensity) : new NearbyStormsPayload(t, List.of());
			}
			double driftX = drift ? t * CloudRenderer.DRIFT : 0;
			CloudCover cover = new CloudCover(p, t, driftX);
			int ci = 0; // fixed cloud cells; the camera only drifts ~4 cells over the run
			int[][] now = new int[2 * range + 1][2 * range + 1];
			for(int a = -range; a <= range; a++)
				for(int b = -range; b <= range; b++)
					now[a + range][b + range] = cover.cell(ci + a, b);
			if(prev != null) {
				int flips = 0;
				for(int a = 0; a < now.length; a++)
					for(int b = 0; b < now.length; b++) {
						int o = prev[a][b], n = now[a][b];
						if(o == n)
							continue;
						boolean os = CloudCover.isStorm(o), ns = CloudCover.isStorm(n);
						if(os || ns) {
							if(os != ns) edge++;
							continue;
						}
						flips++;
						if(n == CloudCover.NONE) vanish++;
						else if(o == CloudCover.NONE) appear++;
						else resize++;
					}
				if(t % 40 == 0) atPayload += flips;
				else between += flips;
			}
			prev = now;
			// Cell index shifts as the camera's cloud-space cell changes; ignore that by comparing by cloud cell only
			// when ci is stable (drift is 0.2 blocks per 4 ticks, so ci moves rarely; small error accepted).
		}
		System.out.printf("%-34s fair vanish %4d appear %4d resize %4d | storm<->fair %4d | flips on payload tick %4d, between %4d%n",
				label, vanish, appear, resize, edge, atPayload, between);
		return vanish + appear + resize;
	}

	/**
	 * In fair weather the clouds drift with their noise, so over two minutes hardly any cumulus changes (the noise was
	 * read at world x, and about three a second popped in and out, 2026-11-04).
	 */
	@Test
	void probe() {
		int clear = run("no storm, drift", false, false, true);
		org.junit.jupiter.api.Assertions.assertTrue(clear < 40, "clear-sky cumulus changed " + clear + " times");
		run("storm, steady intensity, no drift", true, false, false);
		run("storm, steady intensity, drift", true, false, true);
		run("storm, intensity stepping, drift", true, true, true);
	}
}
