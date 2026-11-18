package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AwmClientConfig.RainDetail;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rain detail setting and the frame-rate work of 2026-11-18. */
class RainDetailTest {

	/** ULTRA is the rain as it was; each step down draws less, and the walls keep their front sheet. */
	@Test
	void lowerDetailDrawsLess() {
		assertEquals(10, RainDetail.ULTRA.near());
		assertEquals(18 + 2 * 3, RainDetail.ULTRA.radius(3), "ULTRA's outer ring is the old one");
		assertEquals(RainWall.LAYERS, RainDetail.ULTRA.wallLayers());
		RainDetail[] all = RainDetail.values();
		for(int i = 1; i < all.length; i++) {
			assertTrue(all[i - 1].radius(5) <= all[i].radius(5), all[i - 1] + " reaches further than " + all[i]);
			assertTrue(all[i - 1].wallLayers() <= all[i].wallLayers());
		}
		assertEquals(RainDetail.LOW.near(), RainDetail.LOW.radius(5), "LOW has no outer ring");
	}

	/** The sheets a lighter wall draws: the front one always, spread through the depth. */
	@Test
	void wallLayersKeepFrontAndBack() {
		assertArrayEquals(new int[] { 0, 1, 2, 3, 4 }, RainWall.layersFor(5));
		assertArrayEquals(new int[] { 0, 2, 4 }, RainWall.layersFor(3));
		assertArrayEquals(new int[] { 0, 4 }, RainWall.layersFor(2));
		assertArrayEquals(new int[] { 0 }, RainWall.layersFor(1));
	}

	/** Fewer sheets are each denser, but not as dense as all the missing ones (the tighter streaks make up the rest). */
	@Test
	void fewerSheetsAreDenser() {
		float a = RainWall.LAYER_ALPHA[0];
		assertEquals(a, RainWall.layerAlpha(a, RainWall.LAYERS), 1e-6);
		float two = RainWall.layerAlpha(a, 2);
		assertTrue(two > a, "a sheet of two is no denser than one of five");
		assertTrue(two < 1 - Math.pow(1 - a, RainWall.LAYERS / 2.0), "it stands in for every missing sheet");
	}

	/** Another storm's rain hides a wall only inside it, as before the square-root saving. */
	@Test
	void outsideOthersOnlyInsideTheRain() {
		double[][] rain = { { 0, 0, 100 }, { 200, 0, 100 } };
		assertEquals(1, RainWall.outsideOthers(101, 0, rain, 1), 1e-6, "just outside the first storm's rain");
		assertTrue(RainWall.outsideOthers(60, 0, rain, 1) < 1, "inside the first storm's rain");
		assertEquals(0, RainWall.outsideOthers(0, 0, rain, 1), 1e-6, "deep inside");
	}

	/** The precipitation cache tells columns and heights apart, negative coordinates too. */
	@Test
	void cacheKeysAreDistinct() {
		Set<Long> keys = new HashSet<>();
		for(int x = -3; x <= 3; x++)
			for(int z = -3; z <= 3; z++)
				for(int y : new int[] { -64, 0, 63, 320 })
					assertTrue(keys.add(PrecipitationCache.key(x * 1000, y, z * 1000)), x + " " + y + " " + z);
	}
}
