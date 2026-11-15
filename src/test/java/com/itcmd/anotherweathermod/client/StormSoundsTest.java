package com.itcmd.anotherweathermod.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A hurricane's look and sound by category, the first drops' sound and wood creaking in a gale (2026-11-13). */
class StormSoundsTest {

	@Test
	void weakHurricanesGetTheHeavyRainLook() {
		// 2026-11-13, toned down: a 1 or 2 is steady heavy rain, 3 clearly worse, 4 and 5 the worst.
		assertTrue(StormRain.severity(true, 1) <= 3, "a category 1 is heavy rain, not the worst");
		assertTrue(StormRain.severity(true, 3) > StormRain.severity(true, 2), "a 3 is clearly worse than a 2");
		assertEquals(5, StormRain.severity(true, 5));
		assertTrue(StormRain.severity(true, 2) > StormRain.severity(false, 2), "a hurricane looks worse than a storm of its scale");
		assertEquals(StormRain.sheets(false, 0), StormRain.sheets(true, 2), "no extra sheet in a 1 or 2");
		assertTrue(StormRain.sheets(true, 3) > StormRain.sheets(false, 0));
		assertTrue(StormRain.hurricaneSlant(5) > StormRain.hurricaneSlant(1));
		assertTrue(StormRain.hurricaneSlant(2) < 0.7, "a category 2's rain is not driven flat");
	}

	@Test
	void hurricaneRainIsLoudAndRises() {
		assertTrue(StormSounds.rainVolume(1, 20) >= 0.3f, "a category 1 is loud, over vanilla's 0.2");
		assertTrue(StormSounds.rainVolume(2, 45) < 0.7f, "but a category 2 is not a roar");
		assertTrue(StormSounds.rainVolume(5, 60) <= 1.0f);
		assertTrue(StormSounds.rainVolume(2, 35) > StormSounds.rainVolume(1, 25));
		assertTrue(StormSounds.rainVolume(4, 50) > StormSounds.rainVolume(2, 35));
	}

	@Test
	void woodCreaksOnlyInAGaleAndMoreAsItBlows() {
		assertEquals(0, StormSounds.creakChance(10));
		assertTrue(StormSounds.creakChance(20) > 0);
		assertTrue(StormSounds.creakChance(40) > StormSounds.creakChance(20) * 3);
		assertTrue(StormSounds.creakChance(100) <= StormSounds.CREAKS_PER_SECOND / 20 + 1e-9, "never more than the cap");
	}

	@Test
	void firstDropsDripThenPatter() {
		assertEquals(0, RainOnset.patterVolume(0.3), "a lone drop is a drip, no patter");
		assertTrue(RainOnset.patterVolume(3) > 0);
		assertTrue(RainOnset.patterVolume(RainOnset.MOST) >= RainOnset.patterVolume(3));
		assertTrue(RainOnset.patterVolume(RainOnset.MOST) <= RainOnset.PATTER_VOLUME + 1e-6);
	}

	@Test
	void categoryFourAndFiveStackMoreRain() {
		assertEquals(StormSounds.RAIN_SOUNDS, StormSounds.rainSounds(1));
		assertEquals(StormSounds.RAIN_SOUNDS, StormSounds.rainSounds(3));
		assertTrue(StormSounds.rainSounds(4) > StormSounds.rainSounds(3));
		assertTrue(StormSounds.rainSounds(5) > StormSounds.rainSounds(4));
	}

	@Test
	void hurricanesAndSevereStormsCloseTheSkyAtDuskToo() {
		assertTrue(WeatherEffects.darkensSky(com.itcmd.anotherweathermod.api.StormKind.HURRICANE, 0.8f));
		for(com.itcmd.anotherweathermod.api.StormKind kind : com.itcmd.anotherweathermod.api.StormKind.values())
			if(kind != com.itcmd.anotherweathermod.api.StormKind.HURRICANE)
				assertEquals(kind.scale() >= 3, WeatherEffects.darkensSky(kind, 0.8f), kind.name());
		assertFalse(WeatherEffects.darkensSky(com.itcmd.anotherweathermod.api.StormKind.HURRICANE, 0.1f), "not before its rain");
		assertFalse(WeatherEffects.darkensSky(null, 1));
	}
}
