package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.api.DayOutlook;
import com.itcmd.anotherweathermod.api.DayType;
import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.network.NearbyStormsPayload;
import com.itcmd.anotherweathermod.sim.Climate;
import com.itcmd.anotherweathermod.sim.SteeringWind;
import com.itcmd.anotherweathermod.sim.StormField;
import com.itcmd.anotherweathermod.sim.StormGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StormLodTest {

	private static final double NEAR = 600, BLEND = 250;

	private static StormLod.Body body(long id, double x, double z, double r) {
		return new StormLod.Body(id, x, z, r, 0.9f, 3, 0, 0.05, 0.01);
	}

	/** A front's cells, 350 blocks apart along a line at {@code angle}, centred on ({@code cx}, {@code cz}). */
	private static List<StormLod.Body> front(double cx, double cz, double angle, int cells, long firstId) {
		List<StormLod.Body> bodies = new ArrayList<>();
		for(int i = 0; i < cells; i++) {
			double along = (i - (cells - 1) / 2.0) * 350;
			double across = (i % 2 == 0 ? 1 : -1) * 40;
			bodies.add(body(firstId + i, cx + Math.cos(angle) * along - Math.sin(angle) * across,
					cz + Math.sin(angle) * along + Math.cos(angle) * across, 200));
		}
		return bodies;
	}

	/** Far off, a front's cells become one long band along its line. */
	@Test
	void aFrontMergesAlongItsLine() {
		double angle = Math.toRadians(30);
		List<StormLod.Body> bodies = front(3000, 1000, angle, 10, 1);
		StormLod.View view = StormLod.view(bodies, 0, 0, NEAR, BLEND);
		assertEquals(1, view.masses().size());
		StormLod.Mass band = view.masses().get(0);
		assertEquals(10, band.members());
		assertTrue(Math.abs(band.ux() * Math.cos(angle) + band.uz() * Math.sin(angle)) > 0.99, "band not along the line");
		assertTrue(band.halfLength() > 0.8 * 350 * 4.5, "band too short: " + band.halfLength());
		assertTrue(band.width() < band.halfLength() / 3, "band too wide: " + band.width());
		// It covers the line: every cell's middle is inside it.
		for(StormLod.Body b : bodies)
			assertTrue(band.distance(b.x(), b.z()) < band.width());
		for(int i = 0; i < bodies.size(); i++)
			assertEquals(0, view.detail(i), 1e-9);
	}

	/** A clump of touching scattered storms becomes one wide mass; storms far apart stay apart. */
	@Test
	void nearbyCellsCluster() {
		List<StormLod.Body> bodies = new ArrayList<>(List.of(body(1, 3000, 0, 220), body(2, 3300, 150, 200),
				body(3, 3100, -300, 250), body(4, 3350, -200, 180), body(5, 2850, 250, 200)));
		bodies.add(body(6, -3000, 2500, 200)); // Alone, a long way off.
		StormLod.View view = StormLod.view(bodies, 0, 0, NEAR, BLEND);
		assertEquals(2, view.masses().size());
		StormLod.Mass clump = view.masses().stream().filter(m -> m.members() == 5).findFirst().orElseThrow();
		assertTrue(clump.width() > clump.halfLength() * 0.6, "a clump should be wide: " + clump);
		for(int i = 0; i < 5; i++)
			assertTrue(clump.distance(bodies.get(i).x(), bodies.get(i).z()) < clump.width());
	}

	/** Near storms are drawn in full and in no mass. */
	@Test
	void nearStormsStaySeparate() {
		List<StormLod.Body> bodies = List.of(body(1, 300, 0, 200), body(2, 600, 100, 200), body(3, -200, -400, 150));
		StormLod.View view = StormLod.view(bodies, 0, 0, NEAR, BLEND);
		for(int i = 0; i < bodies.size(); i++)
			assertEquals(1, view.detail(i), 1e-9);
		assertTrue(view.masses().isEmpty());
		// A near storm touching a far front stays whole, and does not pull the band toward the camera.
		List<StormLod.Body> mixed = new ArrayList<>(front(1500, 0, Math.PI / 2, 8, 10));
		mixed.add(body(99, 400, 0, 200));
		StormLod.View v = StormLod.view(mixed, 0, 0, NEAR, BLEND);
		assertEquals(1, v.detail(mixed.size() - 1), 1e-9);
		for(StormLod.Mass m : v.masses())
			assertTrue(m.x() > 1300, "band pulled toward the near storm: " + m.x());
	}

	/** Frame to frame (storms drifting a tick, the camera stepping) nothing jumps. */
	@Test
	void stableFrameToFrame() {
		List<StormLod.Body> bodies = front(1200, 300, 0.4, 9, 1);
		StormLod.View before = StormLod.view(bodies, 0, 0, NEAR, BLEND);
		double maxDetailStep = 0;
		for(int step = 1; step <= 400; step++) {
			double cam = step * 2.0; // Walking toward the front: storms cross the threshold one by one.
			List<StormLod.Body> moved = new ArrayList<>();
			for(StormLod.Body b : bodies)
				moved.add(body(b.id(), b.x() + 0.05 * step, b.z() + 0.01 * step, b.radius()));
			StormLod.View after = StormLod.view(moved, cam, 0, NEAR, BLEND);
			for(int i = 0; i < bodies.size(); i++)
				maxDetailStep = Math.max(maxDetailStep, Math.abs(after.detail(i) - before.detail(i)));
			if(!before.masses().isEmpty() && !after.masses().isEmpty()) {
				StormLod.Mass a = before.masses().get(0), b = after.masses().get(0);
				assertEquals(a.key(), b.key(), "the same storms make the same mass");
				assertTrue(Math.hypot(a.x() - b.x(), a.z() - b.z()) < 25, "mass jumped " + Math.hypot(a.x() - b.x(), a.z() - b.z()));
				assertTrue(Math.abs(a.halfLength() - b.halfLength()) < 40, "length jumped");
				assertTrue(Math.abs(a.width() - b.width()) < 25, "width jumped");
				assertTrue(Math.abs(a.alpha() - b.alpha()) < 0.05, "alpha jumped");
			}
			before = after;
		}
		assertTrue(maxDetailStep < 0.02, "a storm's detail jumped " + maxDetailStep);
	}

	/** Masses fade in and out when the storms making them up change; the first frame shows them at once. */
	@Test
	void massesFadeInAndOut() {
		StormLod.Tracker tracker = new StormLod.Tracker();
		StormLod.Mass a = StormLod.view(front(3000, 0, 0, 4, 1), 0, 0, NEAR, BLEND).masses().get(0);
		StormLod.Mass b = StormLod.view(front(3000, 0, 0, 5, 1), 0, 0, NEAR, BLEND).masses().get(0);
		assertEquals(1, tracker.update(List.of(a), 0.05f).get(0).alpha(), 1e-6);
		List<StormLod.Mass> mid = tracker.update(List.of(b), 1f); // A storm joined: a new mass.
		assertEquals(2, mid.size());
		for(StormLod.Mass m : mid)
			assertEquals(0.5f, m.alpha(), 1e-6);
		List<StormLod.Mass> done = tracker.update(List.of(b), 1f);
		assertEquals(1, done.size());
		assertEquals(b.key(), done.get(0).key());
		assertEquals(1, done.get(0).alpha(), 1e-6);
	}

	/**
	 * A busy rainy day with the world's storms round a player: how many storms each cloud cell weighs, and how many
	 * rain-wall quads a frame draws, with and without merging.
	 */
	@Test
	void mergingCutsTheWorkOnABusyRainyDay() {
		Climate plains = new Climate() {
			@Override public Precipitation basePrecipitation(int x, int y, int z) { return Precipitation.RAIN; }
			@Override public float biomeTemperature(int x, int y, int z) { return 0.8f; }
			@Override public boolean isOcean(int x, int z) { return false; }
			@Override public int seaLevel() { return 63; }
		};
		StormGenerator.Outlooks rainy = (x, z, t) -> new DayOutlook(t / 24000, DayType.RAINY, false, true);
		long now = 300000;
		StormField field = new StormField(new SteeringWind(5));
		StormGenerator.Settings d = StormGenerator.Settings.defaults();
		// Four times the default storms (2026-11-13): spaced storms make a default rainy day too thin to need merging.
		StormGenerator.Settings s = new StormGenerator.Settings(d.horizonTicks(), d.stormsPerPlayerHour() * 4, 0, 1000,
				d.weights(), 0, 0, 0);
		new StormGenerator(5).topUp(field, now, List.of(new StormGenerator.Viewer(0, 0)), plains, s, rainy);
		field.advance(now);
		NearbyStormsPayload payload = NearbyStormsPayload.of(field.active(), plains, 0, 0, now);
		int storms = (int)payload.storms().stream().filter(st -> st.kind().bringsRain()).count();
		StormLod.View view = DistantStorms.compute(payload, now, 0, 0, NEAR, BLEND, new StormLod.Tracker(), 0);
		int cellBefore = storms, cellAfter = 0, wallsBefore = storms * RainWall.stormWallQuads(), wallsAfter = 0;
		int callsBefore = storms * 2, callsAfter = 0;
		for(int i = 0; i < payload.storms().size(); i++) {
			if(!payload.storms().get(i).kind().bringsRain())
				continue;
			if(view.detail(i) >= 0.5)
				cellAfter++;
			if(view.detail(i) >= 0.01) {
				wallsAfter += RainWall.stormWallQuads();
				callsAfter += 2;
			}
		}
		for(StormLod.Mass m : view.masses()) {
			cellAfter++;
			wallsAfter += RainWall.massWallQuads();
			callsAfter += 2;
		}
		System.out.printf("LOD: %d storms in sight; per cloud cell %d -> %d storms weighed; rain walls %d -> %d quads,"
				+ " %d -> %d draw calls; %d masses%n", storms, cellBefore, cellAfter, wallsBefore, wallsAfter, callsBefore,
				callsAfter, view.masses().size());
		assertTrue(storms > 30, "not a busy day: " + storms);
		// Spaced storms (2026-11-13) touch less, so fewer merge: the clouds' work falls by a third, the rain walls' by half.
		assertTrue(cellAfter * 3 < cellBefore * 2, "cloud cells still weigh " + cellAfter + " of " + cellBefore);
		assertTrue(wallsAfter * 2 < wallsBefore, "rain walls still " + wallsAfter + " of " + wallsBefore);
	}
}
