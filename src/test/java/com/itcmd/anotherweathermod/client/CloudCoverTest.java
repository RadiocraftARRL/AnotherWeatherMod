package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.network.NearbyStormsPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudCoverTest {

	private static int keyUnder(StormKind kind, Precipitation precipitation) {
		NearbyStormsPayload.Storm storm = new NearbyStormsPayload.Storm(1, kind, 216, 16, 0, 0, 400, 1, kind.scale(), 0, 0,
				0, 0, precipitation);
		return new CloudCover(new NearbyStormsPayload(0, List.of(storm)), 0, 0).cell(0, 0);
	}

	/** A storm cell's key carries its depth, kind and what it brings down, and they read back (half way out, clear
	 * of a hurricane's eye). */
	@Test
	void keysCarryPrecipitation() {
		Precipitation[] all = { Precipitation.RAIN, Precipitation.SNOW, Precipitation.HAIL, Precipitation.DUST };
		for(int p = 0; p < all.length; p++)
			for(StormKind kind : List.of(StormKind.SHOWER, StormKind.SEVERE_THUNDERSTORM, StormKind.SUPERCELL,
					StormKind.HURRICANE)) {
				int key = keyUnder(kind, all[p]);
				assertTrue(CloudCover.isStorm(key), kind + " " + all[p]);
				assertEquals(p, CloudCover.stormPrecipitation(key), kind + " " + all[p]);
				assertEquals(CloudCover.STORM_DEPTHS, CloudCover.stormDepth(key), kind + " " + all[p]);
				assertEquals(kind == StormKind.HURRICANE ? 6 : kind.scale(), CloudCover.stormKind(key), kind + " " + all[p]);
			}
	}

	/** A storm's cloud runs out ahead of it along its track and thins out behind: it builds before the rain arrives. */
	@Test
	void cloudLeadsTheStorm() {
		// Moving east at 0.3 blocks a tick, centred on cell (0, 0), 400 blocks across its radius.
		NearbyStormsPayload.Storm storm = new NearbyStormsPayload.Storm(1, StormKind.SEVERE_THUNDERSTORM, 16, 16, 0.3f, 0,
				400, 1, 3, 0, 0, 0, 0, Precipitation.RAIN);
		CloudCover cover = new CloudCover(new NearbyStormsPayload(0, List.of(storm)), 0, 0);
		assertTrue(CloudCover.isStorm(cover.cell(16, 0)), "1.28 radii ahead: the deck has come over already");
		assertTrue(!CloudCover.isStorm(cover.cell(-16, 0)), "1.28 radii behind: clear");
		double edge = storm.rainEdge();
		int front = (int)Math.floor(edge / CloudCover.CELL);
		assertEquals(CloudCover.STORM_DEPTHS, CloudCover.stormDepth(cover.cell(front, 0)), "deepest where the rain begins");
	}

	/** The rain starts where it crosses the raining threshold, inside the radius, further in for weaker storms. */
	@Test
	void rainEdgeIsInsideTheRadius() {
		NearbyStormsPayload.Storm strong = new NearbyStormsPayload.Storm(1, StormKind.THUNDERSTORM, 0, 0, 0, 0, 200, 1, 1, 0,
				0, 0, 0, Precipitation.RAIN);
		NearbyStormsPayload.Storm weak = new NearbyStormsPayload.Storm(2, StormKind.SHOWER, 0, 0, 0, 0, 200, 0.5f, 0, 0, 0,
				0, 0, Precipitation.RAIN);
		NearbyStormsPayload.Storm drizzle = new NearbyStormsPayload.Storm(3, StormKind.SHOWER, 0, 0, 0, 0, 200, 0.15f, 0, 0,
				0, 0, 0, Precipitation.RAIN);
		assertEquals(0.88 * 200, strong.rainEdge(), 2);
		assertTrue(weak.rainEdge() < strong.rainEdge() && weak.rainEdge() > 0.6 * 200, "weak: " + weak.rainEdge());
		assertEquals(0, drizzle.rainEdge(), "too weak to rain: no wall");
	}

	/** Under the storm, wherever it rains, the deck is overhead: at the back and the flanks of the rain too. */
	@Test
	void deckIsOverheadWhereverItRains() {
		NearbyStormsPayload.Storm storm = new NearbyStormsPayload.Storm(1, StormKind.SEVERE_THUNDERSTORM, 16, 16, 0.3f, 0,
				400, 0.6f, 3, 0, 0, 0, 0, Precipitation.RAIN);
		CloudCover cover = new CloudCover(new NearbyStormsPayload(0, List.of(storm)), 0, 0);
		int edge = (int)Math.floor(storm.rainEdge() / CloudCover.CELL);
		assertTrue(CloudCover.isStorm(cover.cell(0, 0)), "the middle");
		assertTrue(CloudCover.isStorm(cover.cell(-edge, 0)), "the back of the rain");
		assertTrue(CloudCover.isStorm(cover.cell(0, edge)), "the flank of the rain");
	}

	/** Storm murk draws the clouds' fade in close, but never so close the deck overhead fades out. */
	@Test
	void murkNeverHidesTheDeckOverhead() {
		float end = CloudCover.fadeEnd(64, 100, 704);
		assertTrue(end * 0.85f > 100 * 1.4f, "the deck 100 blocks up stays solid well past straight up: " + end);
		assertEquals(500, CloudCover.fadeEnd(500, 100, 704), 1e-3, "clear air keeps its own fade");
		assertEquals(200, CloudCover.fadeEnd(64, 400, 200), 1e-3, "never past the cloud range");
	}
}
