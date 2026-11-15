package com.itcmd.anotherweathermod.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RainbowShapeTest {

	/** The sun angle (as {@code Level.getSunAngle}) with the sun {@code degrees} above the eastern horizon. */
	private static float sunUp(double degrees) {
		return (float)Math.toRadians(-(90 - degrees));
	}

	/**
	 * The whole ring is drawn, the part below the camera's eye level included, so seen from high up it is a full
	 * halo (it was cut off flat at eye level). From the ground the depth test leaves the terrain to hide that part.
	 */
	@Test
	void aFullRingNotCutAtTheHorizon() {
		float distance = 160;
		float[] q = RainbowShape.quads(sunUp(20), distance, 1);
		assertEquals((RainbowShape.rings() - 1) * RainbowShape.SEGMENTS * 4 * 7, q.length, "every segment drawn");
		double lowest = Double.MAX_VALUE, highest = -Double.MAX_VALUE;
		for(int i = 0; i < q.length; i += 7) {
			lowest = Math.min(lowest, q[i + 1]);
			highest = Math.max(highest, q[i + 1]);
			assertEquals(distance, Math.sqrt(q[i] * q[i] + q[i + 1] * q[i + 1] + q[i + 2] * q[i + 2]), 0.01);
		}
		// Antisolar point 20 degrees down; the ring reaches 20 + 44 = 64 below and 44 - 20 = 24 above.
		assertEquals(-distance * Math.sin(Math.toRadians(64)), lowest, 1);
		assertEquals(distance * Math.sin(Math.toRadians(24)), highest, 1);
	}

	@Test
	void widerAndFainterWithSoftEdges() {
		assertTrue(RainbowShape.OUTER - RainbowShape.INNER >= 5, "wider than the old 3 degrees");
		assertTrue(RainbowShape.STRENGTH < 0.35f, "fainter than before");
		assertEquals(0, RainbowShape.ringAlpha(0));
		assertEquals(0, RainbowShape.ringAlpha(RainbowShape.rings() - 1));
		for(int k = 1; k < RainbowShape.rings(); k++)
			assertTrue(RainbowShape.ringAngle(k) < RainbowShape.ringAngle(k - 1), "red outside, violet in");
	}
}
