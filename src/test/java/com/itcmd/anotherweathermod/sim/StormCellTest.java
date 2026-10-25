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
}
