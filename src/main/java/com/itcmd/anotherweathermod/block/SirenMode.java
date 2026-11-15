package com.itcmd.anotherweathermod.block;

import java.util.Locale;

/**
 * What a warning siren sounds like (2026-11-13), changed with a sneaking right click on any part of it: the air raid
 * wail, the tornado wail (a little higher), or a klaxon's low, long brrt-brrt-brrt. The wails wind up and down with the rotor
 * ({@link SirenRotor}); the klaxon sounds at once.
 */
public enum SirenMode {
	// 2026-11-14, the maintainer: the top pitch up about 15%, then 15% more (1.32 from 1.0), and the tornado siren the
	// higher of the two (they were the other way round); where each starts from is as it was for that pitch.
	AIR_RAID(1.13F, 0.85F, true),
	TORNADO(1.32F, 1.0F, true),
	KLAXON(1.0F, 1.0F, false);

	/** The wail's pitch at full speed, of the recording's. */
	public final float pitchScale;
	/** Its scale at a crawl: the bottom of its wind-up is {@link SirenRotor#LOWEST} times this. */
	public final float startScale;
	/** True for a wail that winds up and coasts down; false for one that sounds at once. */
	public final boolean winds;

	SirenMode(float pitchScale, float startScale, boolean winds) {
		this.pitchScale = pitchScale;
		this.startScale = startScale;
		this.winds = winds;
	}

	public SirenMode next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static SirenMode byName(String name) {
		for(SirenMode mode : values())
			if(mode.key().equals(name))
				return mode;
		return AIR_RAID;
	}
}
