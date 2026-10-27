package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.network.NearbyStormsPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RainWallTest {

	/** Thick under the cloud base, thinner at the ground, softening into the deck at the very top. */
	@Test
	void shaftThinsTowardTheGround() {
		assertTrue(RainWall.vertical(0.85) > RainWall.vertical(0.2));
		assertTrue(RainWall.vertical(0) > 0.3, "it still reaches the ground");
		assertTrue(RainWall.vertical(1) < RainWall.vertical(0.9), "it softens into the deck");
		assertTrue(RainWall.hazeVertical(0) > RainWall.hazeVertical(0.5));
		assertEquals(0, RainWall.hazeVertical(0.8), 1e-6, "the haze stays low");
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
		assertTrue(RainWall.hazeAmount(500) > RainWall.hazeAmount(30), "far shafts are mistier");
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
}
