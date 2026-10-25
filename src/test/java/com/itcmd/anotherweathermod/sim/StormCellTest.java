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
		StormCell h = StormCell.matureAt(StormKind.HURRICANE, 0, 0, 100000, 72000, 800, 1f, 5, wind);
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

	/** Out of the storms only a light breeze blows; the gust front comes before the rain, and is not "in" the storm. */
	@Test
	void windBelongsToStorms() {
		StormField field = new StormField(wind);
		for(long t = 0; t < 2_000_000; t += 7919)
			assertTrue(field.sample(0, 0, t).windSpeed() < 4, "clear-sky breeze");
		field.add(StormCell.matureAt(StormKind.SEVERE_THUNDERSTORM, 0, 0, 50000, 12000, 200, 1f, 1, wind), 50000);
		PointWeather ahead = field.sample(230, 0, 50000);
		assertEquals(0, ahead.rain(), 1e-9);
		assertNull(ahead.storm(), "the gust front is not in the storm");
		assertTrue(ahead.windSpeed() > 8, "but it blows there: " + ahead.windSpeed());
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
}
