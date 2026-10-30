package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.api.StormStage;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StormCellTest {

	private final SteeringWind wind = new SteeringWind(1234);

	private StormCell cell() {
		return new StormCell(7, StormKind.THUNDERSTORM, 1000, 20000, 50, -80, 0.01, -0.005, 200, 0.9f, 99, wind);
	}

	@Test
	void lifeStages() {
		StormCell c = cell();
		assertEquals(StormStage.PENDING, c.stageAt(999));
		assertEquals(StormStage.FORMING, c.stageAt(1000));
		assertEquals(StormStage.MATURE, c.stageAt(1000 + 10000));
		assertEquals(StormStage.DECAYING, c.stageAt(1000 + 15000));
		assertEquals(StormStage.DISSIPATED, c.stageAt(21000));
	}

	@Test
	void intensityRisesPeaksAndFades() {
		StormCell c = cell();
		assertEquals(0, c.intensityAt(999));
		assertTrue(c.intensityAt(1500) < c.intensityAt(4000));
		assertEquals(0.9f, c.intensityAt(10000), 1e-6);
		assertTrue(c.intensityAt(19000) < 0.9f);
		assertEquals(0, c.intensityAt(21000));
	}

	@Test
	void pathFollowsVelocity() {
		StormCell c = cell();
		assertEquals(50, c.centerX(1000), 1e-9);
		assertEquals(-80, c.centerZ(1000), 1e-9);
		// Over a short span the displacement matches the velocity.
		long t = 5000;
		double dx = c.centerX(t + 200) - c.centerX(t);
		double vx = c.velocityX(t + 100);
		assertEquals(vx * 200, dx, Math.abs(vx * 200) * 0.05 + 1e-3);
		// Asking out of order gives the same answer as in order.
		StormCell fresh = cell();
		double late = fresh.centerX(15000);
		assertEquals(c.centerX(15000), late, 1e-9);
	}

	@Test
	void rainOnlyUnderTheStorm() {
		StormCell c = cell();
		long t = 10000;
		double x = c.centerX(t);
		double z = c.centerZ(t);
		assertEquals(0.9, Forecast.rainAt(c, x, z, t), 1e-6);
		assertEquals(0, Forecast.rainAt(c, x + 201, z, t));
		assertTrue(Forecast.rainAt(c, x + 150, z, t) < 0.9);
	}

	@Test
	void hurricaneHasACalmEyeAndRotates() {
		StormCell h = StormCell.matureAt(StormKind.HURRICANE, 0, 0, 100000, 72000, 800, 1f, 5, wind).withCategory(4);
		PointWeather.Builder eye = new PointWeather.Builder(0, 0);
		h.contribute(5, 0, 100000, eye);
		PointWeather e = eye.build();
		assertTrue(e.eye());
		assertTrue(e.rain() < 0.1);

		// East of the centre at the eyewall the wind blows north (counter-clockwise from above).
		PointWeather.Builder wall = new PointWeather.Builder(0, 0);
		h.contribute(800 * StormCell.EYEWALL, 0, 100000, wall);
		PointWeather w = wall.build();
		assertTrue(w.windZ() < -50, "wind z " + w.windZ());
		assertTrue(w.windSpeed() > 60);
	}

	@Test
	void matureAtIsCentredNow() {
		StormCell c = StormCell.matureAt(StormKind.SHOWER, 300, 400, 50000, 12000, 150, 0.6f, 1, wind);
		assertEquals(300, c.centerX(50000), 1e-6);
		assertEquals(400, c.centerZ(50000), 1e-6);
		assertEquals(StormStage.MATURE, c.stageAt(50000));
	}

	@Test
	void saveRoundTrip() {
		StormCell c = cell();
		CompoundTag tag = c.save();
		StormCell back = StormCell.load(tag, wind);
		assertNotNull(back);
		assertEquals(c.id(), back.id());
		assertEquals(c.kind(), back.kind());
		assertEquals(c.centerX(12345), back.centerX(12345), 1e-9);
		assertEquals(c.intensityAt(12345), back.intensityAt(12345), 1e-9);
	}

	/** West of x = 0 is ocean, east is land. */
	private static final Climate COAST = new Climate() {
		@Override public com.itcmd.anotherweathermod.api.Precipitation basePrecipitation(int x, int y, int z) {
			return com.itcmd.anotherweathermod.api.Precipitation.RAIN;
		}
		@Override public float biomeTemperature(int x, int y, int z) { return 0.8f; }
		@Override public boolean isOcean(int x, int z) { return x < 0; }
		@Override public int seaLevel() { return 63; }
	};

	@Test
	void hurricanesWeakenOverLandOnlyWhenAsked() {
		StormCell sea = StormCell.matureAt(StormKind.HURRICANE, -5000, 0, 100000, 72000, 800, 1f, 5, wind);
		StormCell land = StormCell.matureAt(StormKind.HURRICANE, 5000, 0, 100000, 72000, 800, 1f, 5, wind);
		long later = 100000 + 20000;
		float before = land.intensityAt(later);
		land.setLand(COAST);
		sea.setLand(COAST);
		assertEquals(1, sea.landFactor(later), 1e-6, "no loss at sea");
		float factor = land.landFactor(later);
		assertTrue(factor < 0.6 && factor > 0.05, "about half lost in a day over land, got " + factor);
		assertEquals(before * factor, land.intensityAt(later), 1e-4);
		assertTrue(land.landFactor(later + 10000) < factor, "and still falling");
		land.setLand(null);
		assertEquals(before, land.intensityAt(later), 1e-6, "off again");
	}

	@Test
	void landDoesNotTouchOtherStorms() {
		StormCell c = cell();
		float before = c.intensityAt(5000);
		c.setLand(COAST);
		assertEquals(before, c.intensityAt(5000), 1e-6);
	}

	/** Out of the storms only a light breeze blows; a storm's outflow is in its rain, fading out just past its edge. */
	@Test
	void windBelongsToStorms() {
		StormField field = new StormField(wind);
		for(long t = 0; t < 2_000_000; t += 7919)
			assertTrue(field.sample(0, 0, t).windSpeed() < 4, "clear-sky breeze");
		StormCell storm = field.add(StormCell.matureAt(StormKind.SEVERE_THUNDERSTORM, 0, 0, 50000, 12000, 200, 1f, 1,
				wind), 50000);
		// 230 blocks out along its track, past the rain.
		double vx = storm.velocityX(50000), vz = storm.velocityZ(50000), v = Math.hypot(vx, vz);
		PointWeather ahead = field.sample(storm.centerX(50000) + vx / v * 230, storm.centerZ(50000) + vz / v * 230, 50000);
		assertEquals(0, ahead.rain(), 1e-9);
		assertNull(ahead.storm(), "not in the storm");
		// 54 blocks past the rain's edge the prevailing breeze blows, gusts and all (2026-10-30).
		for(long t = 50000; t < 50000 + 2400; t += 20) {
			PointWeather now = field.sample(storm.centerX(t) + vx / v * 230, storm.centerZ(t) + vz / v * 230, t);
			assertTrue(now.windSpeed() < 4 && now.gust() < 4, "the breeze past the edge: " + now.windSpeed() + ", gusting " + now.gust());
		}
		// In the rain the outflow blows.
		PointWeather in = field.sample(storm.centerX(50000) + vx / v * 120, storm.centerZ(50000) + vz / v * 120, 50000);
		assertTrue(in.gust() > 8, "the outflow in the rain: " + in.gust());
		assertTrue(field.sample(300, 0, 50000).windSpeed() < 4, "and not past it");
	}

	/** Severe thunderstorms gust often, and only their gusts blow hard enough to do damage. */
	@Test
	void severeStormsGustOftenAndDamageOnlyInGusts() {
		StormCell severe = StormCell.matureAt(StormKind.SEVERE_THUNDERSTORM, 0, 0, 50000, 24000, 200, 1f, 3, wind);
		StormCell plain = StormCell.matureAt(StormKind.THUNDERSTORM, 0, 0, 50000, 24000, 200, 1f, 3, wind);
		int severeGusts = 0, plainGusts = 0, damagingInLull = 0, damaging = 0;
		for(long t = 50000; t < 54000; t++) {
			if(severe.gustAt(t) > 0.5)
				severeGusts++;
			if(plain.gustAt(t) > 0.5)
				plainGusts++;
			PointWeather.Builder acc = new PointWeather.Builder(0, 0);
			severe.contribute(severe.centerX(t) + 100, severe.centerZ(t), t, acc); // Halfway out, where it blows hardest.
			PointWeather w = acc.build();
			if(w.windSpeed() > WeatherModel.DAMAGING_WIND) {
				damaging++;
				if(severe.gustAt(t) < 0.5)
					damagingInLull++;
			}
			assertTrue(w.gust() > WeatherModel.DAMAGING_WIND, "the gust peak is always damaging there");
		}
		assertTrue(severeGusts > 2 * plainGusts, "severe " + severeGusts + " plain " + plainGusts);
		assertTrue(damaging > 200, "it does blow hard in the gusts: " + damaging);
		assertEquals(0, damagingInLull, "never in a lull");
	}

	/** Stronger hurricane categories blow harder, from a severe thunderstorm's winds at 0 to over 80 m/s at 5. */
	@Test
	void hurricaneCategoriesScaleTheWind() {
		double last = 0;
		for(int category = 0; category <= 5; category++) {
			StormCell h = StormCell.matureAt(StormKind.HURRICANE, 0, 0, 100000, 72000, 800, 1f, 5, wind)
					.withCategory(category);
			assertEquals(category, h.category());
			PointWeather.Builder acc = new PointWeather.Builder(0, 0);
			h.contribute(800 * StormCell.EYEWALL, 0, 100000, acc);
			double speed = acc.build().windSpeed();
			assertTrue(speed > last, "category " + category + " at " + speed);
			last = speed;
		}
		assertTrue(last > 78, "category 5 eyewall " + last);
		assertEquals(4, StormCell.load(StormCell.matureAt(StormKind.HURRICANE, 0, 0, 0, 72000, 800, 1f, 5, wind)
				.withCategory(4).save(), wind).category(), "the category is saved");
	}

	/** A tornado comes down partway through its storm's life, and bigger ratings are wider and fiercer. */
	@Test
	void tornadoesTouchDownAndScaleWithTheirRating() {
		StormCell early = StormCell.atLife(StormKind.TORNADO, 0, 0, 100000, 0.1, 36000, 240, 1f, 9, wind);
		assertEquals(0, early.tornadoStrength(100000), "not down yet");
		double lastRadius = 0, lastWind = 0;
		for(int ef = 0; ef <= 5; ef++) {
			StormCell cell = StormCell.atLife(StormKind.TORNADO, 0, 0, 100000, 0.5, 36000, 240, 1f, 9, wind).withCategory(ef);
			assertEquals(ef, cell.tornadoRating());
			assertEquals(5, cell.category(), "a tornado is 5 on the storm scale whatever its rating");
			assertTrue(cell.tornadoStrength(100000) > 0.9, "down at mid-life");
			assertTrue(cell.tornadoRadius() > lastRadius && cell.tornadoWind() > lastWind);
			lastRadius = cell.tornadoRadius();
			lastWind = cell.tornadoWind();
			PointWeather.Builder acc = new PointWeather.Builder(0, 0);
			double tx = cell.tornadoX(100000), tz = cell.tornadoZ(100000);
			cell.contribute(tx + cell.tornadoRadius() * 0.9, tz, 100000, acc);
			PointWeather at = acc.build();
			assertTrue(at.tornado() > 0.25, "in the tornado");
			assertTrue(at.windSpeed() > StormCell.TORNADO_WIND[ef] * 0.6, "EF" + ef + " wind " + at.windSpeed());
		}
	}

	/** Fog, heat waves and cold snaps bring no rain, only themselves. */
	@Test
	void eventsBringNoRain() {
		for(StormKind kind : new StormKind[] { StormKind.DENSE_FOG, StormKind.HEAT_WAVE, StormKind.COLD_SNAP }) {
			StormCell cell = StormCell.matureAt(kind, 0, 0, 100000, 24000, 600, 1f, 3, wind);
			PointWeather.Builder acc = new PointWeather.Builder(0, 0);
			cell.contribute(cell.centerX(100000), cell.centerZ(100000), 100000, acc);
			PointWeather p = acc.build();
			assertEquals(0, p.rain(), 1e-9);
			assertNull(p.storm());
			double value = kind == StormKind.DENSE_FOG ? p.fog() : kind == StormKind.HEAT_WAVE ? p.heat() : p.cold();
			assertEquals(1, value, 1e-6, kind + " in its middle");
		}
	}
}
