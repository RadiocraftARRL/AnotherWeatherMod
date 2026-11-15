package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Storms do not pile up on one spot (2026-11-13): scattered storms used to form at independent random places, and on
 * rainy days up to nine covered one spot, with twenty within 400 blocks, each with its own rain curtain. With
 * {@link StormGenerator#SPACING} at most a few touch, and a top-up stays cheap.
 */
class StormOverlapTest {

	private static final Climate CLIMATE = new Climate() {
		@Override public Precipitation basePrecipitation(int x, int y, int z) { return Precipitation.RAIN; }
		@Override public float biomeTemperature(int x, int y, int z) { return 0.8f; }
		@Override public boolean isOcean(int x, int z) { return Math.abs(x) > 1500 || Math.abs(z) > 1500; }
		@Override public int seaLevel() { return 63; }
	};

	@Test
	void overlap() {
		int[] hist = new int[40];
		int[] near = new int[200];
		long samples = 0, covered = 0;
		int worst = 0, worstNear = 0;
		long topUps = 0, topUpMs = 0, worstMs = 0;
		for(long seed = 1; seed <= 6; seed++) {
			SteeringWind wind = new SteeringWind(seed);
			StormField field = new StormField(wind);
			StormGenerator generator = new StormGenerator(seed);
			DayTypes days = new DayTypes(seed, wind);
			StormGenerator.Outlooks outlooks = (x, z, t) -> days.at(x, z, DayTypes.dayOf(t), DayTypes.dayOf(t) * DayTypes.DAY);
			List<StormGenerator.Viewer> viewers = List.of(new StormGenerator.Viewer(0, 0));
			for(long t = 0; t < 24000L * 20; t += 200) {
				if(t % 6000 == 0) {
					long n0 = System.nanoTime();
					generator.topUp(field, t, viewers, CLIMATE, StormGenerator.Settings.defaults(), outlooks);
					long ms = (System.nanoTime() - n0) / 1_000_000;
					topUps++;
					topUpMs += ms;
					worstMs = Math.max(worstMs, ms);
				}
				field.advance(t);
				int over = 0, close = 0;
				for(StormCell cell : field.all()) {
					if(!cell.isAlive(t) || !isRain(cell.kind()))
						continue;
					double d = Math.hypot(cell.centerX(t), cell.centerZ(t));
					if(d < cell.radiusAt(t))
						over++;
					if(d < 400)
						close++;
				}
				samples++;
				if(over > 0)
					covered++;
				hist[Math.min(39, over)]++;
				near[Math.min(199, close)]++;
				worst = Math.max(worst, over);
				worstNear = Math.max(worstNear, close);
			}
		}
		StringBuilder out = new StringBuilder("top-ups " + topUps + ", mean " + topUpMs / Math.max(1, topUps) + " ms, worst " + worstMs + " ms; samples " + samples + ", covered " + covered + ", worst overlap " + worst + ", worst within 400: " + worstNear + "\noverlap histogram (covered samples):");
		for(int i = 1; i < 40; i++)
			if(hist[i] > 0)
				out.append(" ").append(i).append(":").append(hist[i]);
		out.append("\nwithin 400 blocks histogram:");
		for(int i = 1; i < 200; i++)
			if(near[i] > 0)
				out.append(" ").append(i).append(":").append(near[i]);
		System.out.println(out);
		long twoOrMore = 0;
		for(int i = 2; i < 40; i++)
			twoOrMore += hist[i];
		org.junit.jupiter.api.Assertions.assertTrue(worst <= 5, "storms over one spot at once: " + worst);
		org.junit.jupiter.api.Assertions.assertTrue(twoOrMore < covered * 0.25, "more than one storm overhead " + twoOrMore + " of " + covered);
		org.junit.jupiter.api.Assertions.assertTrue(worstNear <= 12, "storms within 400 blocks: " + worstNear);
	}

	private static boolean isRain(StormKind kind) {
		return kind != StormKind.DENSE_FOG && kind != StormKind.HIGH_WIND && kind != StormKind.HEAT_WAVE && kind != StormKind.COLD_SNAP;
	}
}
