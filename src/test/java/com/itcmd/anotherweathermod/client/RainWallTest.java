package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.network.NearbyStormsPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RainWallTest {

	/** Thick under the cloud base, thinner at the ground, softening into the deck at the very top. */
	@Test
	void shaftThinsTowardTheGround() {
		assertTrue(RainWall.vertical(0.85) > RainWall.vertical(0.2));
		assertTrue(RainWall.vertical(0) > 0.3, "it still reaches the ground");
		assertTrue(RainWall.vertical(1) < RainWall.vertical(0.9), "it softens into the deck");
	}

	/** It fades smoothly away as the camera walks into the rain, and thins with distance, never vanishing in range. */
	@Test
	void fadesInsideAndWithDistance() {
		assertEquals(1, RainWall.distanceFade(0, 900), 1e-6);
		assertEquals(0, RainWall.distanceFade(-RainWall.ENTER_FADE, 900), 1e-6);
		float half = RainWall.distanceFade(-RainWall.ENTER_FADE / 2, 900);
		assertTrue(half > 0.2 && half < 0.8, "smooth, not a cut: " + half);
		assertTrue(RainWall.distanceFade(800, 900) < RainWall.distanceFade(100, 900));
		assertTrue(RainWall.distanceFade(900, 900) > 0.3);
	}

	/** Sheets fall at their own pace and are offset from each other; snow falls far slower than rain, and drifts. */
	@Test
	void sheetsDifferAndSnowIsSlow() {
		assertNotEquals(RainWall.speed(7, 0), RainWall.speed(7, 1));
		assertNotEquals(RainWall.offset(7, 0), RainWall.offset(7, 2));
		for(int layer = 0; layer < RainWall.LAYERS; layer++) {
			assertTrue(RainWall.speed(7, layer) >= 0.85 && RainWall.speed(7, layer) <= 1.25);
			assertTrue(RainWall.scroll(100, true, 7, layer) * 5 < RainWall.scroll(100, false, 7, layer));
		}
		assertEquals(0, RainWall.drift(123, false, 1), 1e-9);
		assertNotEquals(RainWall.drift(123, true, 1), RainWall.drift(456, true, 1));
		for(int layer = 1; layer < RainWall.LAYERS; layer++)
			assertTrue(RainWall.DEPTH[layer] > RainWall.DEPTH[layer - 1], "sheets go back into the rain");
	}

	/**
	 * The sheets are faint and not flat (2026-11-13): each bows in and out along the wall and with height, by no more
	 * than its bound, each sheet its own way; and a full wall is only its streak sheets (no haze sheets).
	 */
	@Test
	void sheetsAreFaintAndBowed() {
		for(float alpha : RainWall.LAYER_ALPHA)
			assertTrue(alpha <= 0.3f, "faint: " + alpha);
		double bound = RainWall.WOBBLE + RainWall.LEAN / 2 + 1e-9, least = 0, most = 0;
		for(double arc = 0; arc < 400; arc += 3)
			for(double f = 0; f <= 1; f += 0.25) {
				double w = RainWall.wobble(11, 2, arc, f);
				assertTrue(Math.abs(w) <= bound, "bowed too far: " + w);
				least = Math.min(least, w);
				most = Math.max(most, w);
			}
		assertTrue(most - least > RainWall.WOBBLE, "it bows: " + least + " to " + most);
		assertNotEquals(RainWall.wobble(11, 0, 50, 0.5), RainWall.wobble(11, 1, 50, 0.5), "each sheet its own way");
		assertNotEquals(RainWall.wobble(11, 0, 50, 0), RainWall.wobble(11, 0, 50, 1), "and it changes with height");
		assertEquals(RainWall.LAYERS * RainWall.SEGMENTS * RainWall.ROWS, RainWall.stormWallQuads());
	}

	/** Rain is a grey-blue as dark as its storm; snow nearly white. */
	@Test
	void tintFollowsTheStorm() {
		float[] slate = RainWall.tint(0.28f, false, 0), shower = RainWall.tint(0.62f, false, 0), snow = RainWall.tint(0.28f, true, 0);
		assertTrue(slate[0] < shower[0], "a darker storm, a darker shaft");
		assertTrue(slate[2] > slate[0], "blue-grey");
		assertTrue(snow[0] > 0.85f && snow[2] > 0.9f, "snow is white");
		assertTrue(RainWall.tint(0.4f, false, 3)[0] < RainWall.tint(0.4f, false, 0)[0], "deeper sheets are darker");
	}

	/** Over snowy ground a storm's deck is snow cloud; over rain ground rain cloud; dust and hail keep their own. */
	@Test
	void deckFollowsTheGroundUnderIt() {
		assertEquals(1, CloudCover.localPrecipitation(0, 1), "rain storm over snow: snow cloud");
		assertEquals(0, CloudCover.localPrecipitation(1, 0), "snow storm over rain ground: rain cloud");
		assertEquals(1, CloudCover.localPrecipitation(1, -1), "unknown ground: the storm's own");
		assertEquals(2, CloudCover.localPrecipitation(2, 0), "hail stays hail");
		assertEquals(1, CloudCover.localPrecipitation(2, 1), "hail over snow is a blizzard");
		assertEquals(3, CloudCover.localPrecipitation(3, 1), "dust stays dust");
		NearbyStormsPayload.Storm storm = new NearbyStormsPayload.Storm(1, StormKind.SEVERE_THUNDERSTORM, 216, 16, 0, 0, 400,
				1, 3, 0, 0, 0, 0, Precipitation.RAIN);
		int key = new CloudCover(new NearbyStormsPayload(0, List.of(storm)), 0, 0, (x, z) -> 1).cell(0, 0);
		assertEquals(1, CloudCover.stormPrecipitation(key));
	}

	/** Snow cloud is paler than any rain cloud of the same storm. */
	@Test
	void snowDecksArePale() {
		for(float dark : CloudCover.DARKNESS) {
			float snow = dark + (0.95f - dark) * CloudCover.PRECIPITATION_TINT[1][0];
			assertTrue(snow > 0.8f, "snow deck shade " + snow);
		}
	}

	/** Indoors (anything but leaves overhead) the rain walls fade away, and come back outside; under a tree they show. */
	@Test
	void hiddenIndoors() {
		assertTrue(RainWall.outdoors(70.6, 70), "open sky or only leaves above");
		assertFalse(RainWall.outdoors(65.6, 70), "under a roof or in a cave");
		float shown = 1;
		for(int frame = 0; frame < 60; frame++)
			shown = RainWall.stepOutdoors(shown, false, 1 / 60f);
		assertEquals(0, shown, "gone within a second of going in");
		shown = RainWall.stepOutdoors(shown, true, 0.1f);
		assertTrue(shown > 0 && shown < 1, "fades back in");
	}

	/** Two storms that touch: each wall hides where it runs inside the other's rain, so they show one curtain. */
	@Test
	void wallsHideInsideAnotherStormsRain() {
		double[][] rain = { { 0, 0, 300 }, { 400, 0, 300 } };
		assertEquals(1, RainWall.outsideOthers(-300, 0, rain, 0), "the far side of the first storm is open");
		assertEquals(0, RainWall.outsideOthers(300, 0, rain, 0), "deep in the second storm's rain");
		float edge = RainWall.outsideOthers(110, 0, rain, 0); // 10 blocks inside the second storm.
		assertTrue(edge > 0 && edge < 1, "fades out at the edge: " + edge);
		assertEquals(1, RainWall.outsideOthers(300, 0, rain, 1), "a storm's own rain does not hide its wall");
	}
}
