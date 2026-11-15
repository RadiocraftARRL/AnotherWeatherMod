package com.itcmd.anotherweathermod.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The warning sirens' rotor and sound (2026-11-13). */
class SirenRotorTest {

	private static int ticksTo(float from, int signal, float reach) {
		float speed = from;
		for(int t = 1; t < 10_000; t++) {
			speed = SirenRotor.step(speed, signal);
			if(signal > 0 ? speed >= reach : speed <= reach)
				return t;
		}
		return -1;
	}

	@Test
	void windsUpOverSecondsAndCoastsDownSlower() {
		int up = ticksTo(0.0F, 15, 0.999F), down = ticksTo(1.0F, 0, 0.001F);
		assertTrue(up >= 100 && up <= 160, "wind-up ticks " + up);
		assertTrue(down > up, "coasts down slower: " + down + " vs " + up);
	}

	@Test
	void signalStrengthSetsTheTopSpeed() {
		float speed = 0.0F;
		for(int t = 0; t < 400; t++)
			speed = SirenRotor.step(speed, 7);
		assertEquals(7 / 15.0F, speed, 1e-4);
		for(int t = 0; t < 400; t++)
			speed = SirenRotor.step(speed, 15);
		assertEquals(1.0F, speed, 1e-4);
	}

	@Test
	void pitchStartsVeryLowAndTornadoIsLower() {
		assertEquals(SirenRotor.LOWEST, SirenRotor.pitch(SirenMode.TORNADO, 0.0F), 1e-6);
		assertEquals(SirenMode.TORNADO.pitchScale, SirenRotor.pitch(SirenMode.TORNADO, 1.0F), 1e-6);
		assertTrue(SirenMode.TORNADO.pitchScale > 1.3F, "tops out well above the recording (2026-11-14)");
		assertTrue(SirenRotor.pitch(SirenMode.AIR_RAID, 1.0F) < SirenRotor.pitch(SirenMode.TORNADO, 1.0F), "the tornado siren is the higher");
		assertTrue(SirenRotor.loudness(0.2F) < 0.1F, "quiet while slow");
		assertEquals(1.0F, SirenRotor.loudness(1.0F), 1e-6);
	}

	@Test
	void lowRecordingCoversPitchesTheEngineCannotPlay() {
		assertEquals(1.0F, SirenRotor.lowShare(0.3F), 1e-6);
		assertEquals(1.0F, SirenRotor.lowShare(SirenRotor.ENGINE_LOWEST), 1e-6);
		assertEquals(0.0F, SirenRotor.lowShare(SirenRotor.CROSSFADE_TOP), 1e-6);
		// Wherever the normal recording is heard at all, its pitch is one the engine plays.
		for(float p = 0.2F; p < 1.0F; p += 0.01F)
			if(SirenRotor.lowShare(p) < 1.0F)
				assertTrue(p >= SirenRotor.ENGINE_LOWEST - 1e-6, "pitch " + p);
	}

	@Test
	void heardFarAndEchoesGrowWithDistance() {
		assertEquals(1.0D, SirenRotor.falloff(0.0D), 1e-9);
		assertTrue(SirenRotor.falloff(250.0D) > 0.08D, "still heard at 250 blocks");
		assertEquals(0.0D, SirenRotor.falloff(SirenRotor.HEARD + 1.0D), 1e-9);
		assertTrue(SirenRotor.echo(200.0D) > SirenRotor.echo(20.0D));
	}

	@Test
	void klaxonSoundsAtOnceLouderWithSignal() {
		assertEquals(0.0F, SirenRotor.klaxonLoudness(0), 1e-6);
		assertTrue(SirenRotor.klaxonLoudness(1) > 0.3F);
		assertTrue(SirenRotor.klaxonLoudness(15) > SirenRotor.klaxonLoudness(5));
		assertFalse(SirenMode.KLAXON.winds);
		assertEquals(SirenMode.AIR_RAID, SirenMode.KLAXON.next());
	}
}
