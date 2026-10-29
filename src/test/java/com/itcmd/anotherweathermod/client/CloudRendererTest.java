package com.itcmd.anotherweathermod.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudRendererTest {

	/**
	 * The cells are read again every few ticks from the first frame on: never-read must not overflow into "not due",
	 * or the clouds stay as they were when each tile was built and a storm moving in never shows overhead.
	 */
	@Test
	void cellsAreReadAgainEveryFewTicks() {
		for(long now : new long[] { 0, 5, 24000, 3_000_000_000L })
			assertTrue(CloudRenderer.resampleDue(now, Long.MIN_VALUE), "never read, at " + now);
		assertFalse(CloudRenderer.resampleDue(1001, 1000), "just read");
		assertTrue(CloudRenderer.resampleDue(1000 + CloudRenderer.RESAMPLE_TICKS, 1000), "due again");
		assertTrue(CloudRenderer.resampleDue(500, 1000), "time went back (a new world, /time set)");
	}
}
