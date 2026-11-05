package com.itcmd.anotherweathermod.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** How far out a tornado pulls things in and lifts them, and that the reach grew without the core getting stronger. */
class TornadoReachTest {

	/** {@code WindPush.FROM}: the wind speed, m/s, from which the wind pushes (not referenced, to keep this test pure). */
	private static final double PUSH_FROM = 22;
	/** {@code Tornadoes.LIFT} and an entity's gravity: the lift carries things up where LIFT * closeness beats it. */
	private static final double LIFT = 0.14;
	private static final double GRAVITY = 0.08;

	/** The furthest distance, scanning out from the centre, at which {@code speed} still pushes. */
	private static double reach(java.util.function.DoubleUnaryOperator speed, double rt) {
		double furthest = 0;
		for(double d = 0; d < rt * 10; d += rt / 400)
			if(speed.applyAsDouble(d) >= PUSH_FROM)
				furthest = d;
		return furthest;
	}

	/** The pull reach, blocks, of a full-strength tornado of each rating at its middle radius: the maintainer's table. */
	private static final double[] PULL_REACH_BLOCKS = { 14, 32, 64, 109, 130, 149 };

	@Test
	void pullReachMatchesTheTableInRatingOrder() {
		double last = 0;
		for(int ef = 0; ef <= 5; ef++) {
			double rt = StormCell.TORNADO_RADIUS[ef], vmax = StormCell.TORNADO_WIND[ef];
			double before = reach(d -> StormCell.tornadoSpeed(d, rt, vmax), rt);
			double after = reach(d -> StormCell.pullSpeed(d, rt, vmax), rt);
			double ratio = after / before;
			System.out.printf("EF%d: pulled from %.0f blocks (%.2f radii), was %.0f (%.2f radii): x%.2f%n", ef, after,
					after / rt, before, before / rt, ratio);
			assertEquals(PULL_REACH_BLOCKS[ef], after, 2.5, "EF" + ef + " pull reach");
			assertTrue(after > last, "EF" + ef + " reaches further than the rating below");
			if(ef <= 2)
				assertTrue(ratio > 1.6 && ratio < 2.2, "EF" + ef + " pulls from about twice as far, x" + ratio);
			last = after;
		}
		// The widest EF5 (a fifth over its middle radius) still stays near 150.
		double widest = reach(d -> StormCell.pullSpeed(d, 48 * 1.2, 105 * 1.15), 48 * 1.2);
		assertTrue(widest < 165, "the widest EF5 pulls from " + widest);
	}

	@Test
	void theReachIsHeldInSmoothlyPastAHundredBlocks() {
		double from = StormCell.SOFT_REACH_FROM;
		assertEquals(50, StormCell.softReach(50), 1e-12, "untouched below");
		assertEquals(from, StormCell.softReach(from), 1e-12);
		double h = 1e-4;
		assertEquals(1, (StormCell.softReach(from + h) - StormCell.softReach(from)) / h, 1e-3, "no kink where it starts");
		double last = from;
		for(double u = from + 1; u < 600; u += 1) {
			double held = StormCell.softReach(u);
			assertTrue(held > last && held < u, "grows, ever more slowly, at " + u);
			assertEquals(u, StormCell.unsoftReach(held), 1e-6, "the inverse");
			last = held;
		}
	}

	@Test
	void liftStaysWithinThePullForTheBigOnes() {
		for(int ef = 3; ef <= 5; ef++)
			for(double size : new double[] { 0.8, 1, 1.2 }) {
				double rt = StormCell.TORNADO_RADIUS[ef] * size, vmax = StormCell.TORNADO_WIND[ef];
				double pull = reach(d -> StormCell.pullSpeed(d, rt, vmax), rt);
				assertTrue(StormCell.liftReach(rt) <= pull, "EF" + ef + " x" + size + ": lift " + StormCell.liftReach(rt)
						+ " past pull " + pull);
			}
	}

	@Test
	void theCoreAndThePeakAreUnchanged() {
		double rt = 16, vmax = 55;
		double peakBefore = 0, peakAfter = 0;
		for(double d = 0; d <= rt * 9; d += rt / 200) {
			double wind = StormCell.tornadoSpeed(d, rt, vmax), pull = StormCell.pullSpeed(d, rt, vmax);
			if(d <= rt)
				assertEquals(wind, pull, 1e-9, "the same in the core, " + d);
			else
				assertTrue(pull >= wind - 1e-9, "never less than the wind, " + d);
			peakBefore = Math.max(peakBefore, wind);
			peakAfter = Math.max(peakAfter, pull);
		}
		assertEquals(peakBefore, peakAfter, 1e-6, "the strongest pull is the funnel's own wind");
		assertEquals(vmax, peakAfter, 1e-6);
	}

	@Test
	void pullFallsOffSmoothlyWithNoJump() {
		for(double rt : new double[] { 16, 48 })
			fallsOffSmoothly(rt, 105);
	}

	private static void fallsOffSmoothly(double rt, double vmax) {
		double step = rt / 200, last = StormCell.pullSpeed(rt, rt, vmax);
		for(double d = rt + step; d <= rt * 9; d += step) {
			double pull = StormCell.pullSpeed(d, rt, vmax);
			assertTrue(pull <= last + 1e-9, "falls off outside the wall, " + d);
			assertTrue(last - pull < vmax * 0.01, "no jump at " + d + ": " + last + " to " + pull);
			last = pull;
		}
		double reach = StormCell.softReach(rt * StormCell.PULL_REACH);
		assertEquals(0, StormCell.pullSpeed(reach, rt, vmax), 1e-9, "nothing at its reach");
		assertTrue(StormCell.pullSpeed(reach - 0.5, rt, vmax) < 0.5, "next to nothing just inside");
	}

	@Test
	void liftCarriesThingsUpTwiceAsFarWithTheSameCore() {
		double rt = 20;
		assertEquals(1, StormCell.liftCloseness(0, rt), 1e-12, "full lift at the centre");
		// The distance out to which the lift beats gravity, before (0 from two radii) and now.
		double before = 0, after = 0;
		for(double d = 0; d < rt * 5; d += rt / 1000) {
			if(LIFT * Math.max(0, 1 - d / (2 * rt)) > GRAVITY)
				before = d;
			if(LIFT * StormCell.liftCloseness(d, rt) > GRAVITY)
				after = d;
		}
		assertEquals(2, after / before, 0.01, "carried up from twice as far: " + before + " -> " + after);
		assertEquals(0, StormCell.liftCloseness(StormCell.liftReach(rt), rt), 1e-12, "none at its reach");
		assertTrue(StormCell.liftCloseness(StormCell.liftReach(rt) - 0.1, rt) < 0.01, "and next to none just inside");
		// The biggest: carried up from twice as far still (the hold starts further out), the edge held in.
		double big = 48 * 1.2;
		assertTrue(LIFT * StormCell.liftCloseness(big * 1.7, big) > GRAVITY, "an EF5 lifts at 1.7 radii");
		assertTrue(StormCell.liftReach(big) < big * StormCell.LIFT_REACH, "its edge held in");
		assertEquals(0, StormCell.liftCloseness(StormCell.liftReach(big), big), 1e-9);
	}

	@Test
	void aTornadoAddsPullOnlyOutsideItsCoreTowardItsWind() {
		StormCell.Tornado tornado = new StormCell.Tornado(100, -50, 16, 55, 1);
		double[] pull = new double[2];
		tornado.addPull(100 + 10, -50, pull);
		assertArrayEquals(new double[] { 0, 0 }, pull, "nothing in the core: the wind does it there");
		tornado.addPull(100 + tornado.pullReach() + 1, -50, pull);
		assertArrayEquals(new double[] { 0, 0 }, pull, "nothing past its reach");
		// East of it, the counter-clockwise inflow blows north-west-ish: in (west, -x) and round (north, -z).
		tornado.addPull(100 + 48, -50, pull);
		assertTrue(pull[0] < 0 && pull[1] < 0, "in and round: " + pull[0] + ", " + pull[1]);
		double speed = Math.hypot(pull[0], pull[1]) / Math.hypot(1, 0.4);
		assertEquals(StormCell.pullSpeed(48, 16, 55) - StormCell.tornadoSpeed(48, 16, 55), speed, 1e-9);
		assertEquals(0.75, new StormCell.Tornado(0, 0, 16, 55, 1).lift(16, 0), 1e-12, "lift at the wall");
		assertEquals(0.375, new StormCell.Tornado(0, 0, 16, 55, 0.5).lift(16, 0), 1e-12, "times its strength");
	}
}
