package com.itcmd.anotherweathermod.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * A tornado's roar: a deep, rushing woosh from where the funnel is, heard from far off and loud close by. No
 * attenuation of its own, so it carries; {@link WeatherEffects} sets its loudness by distance every tick, and the
 * position still pans it toward the funnel. Uses the elytra's rushing-air loop, pitched well down, until the mod has
 * its own sounds.
 */
public final class TornadoSound extends AbstractTickableSoundInstance {

	/** How far off, in blocks, a tornado's roar has died away. */
	static final double HEARD = 600;

	private float target;
	private boolean fading;

	TornadoSound(double x, double y, double z) {
		super(SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, SoundInstance.createUnseededRandom());
		looping = true;
		delay = 0;
		attenuation = Attenuation.NONE;
		volume = 0;
		pitch = 0.4f;
		this.x = x;
		this.y = y;
		this.z = z;
	}

	/** Moves it to the funnel and sets how loud it should be, 0 to 1. */
	void follow(double x, double y, double z, float loudness) {
		this.x = x;
		this.y = y;
		this.z = z;
		target = loudness;
	}

	/** Lets it die away, then stop. */
	void fadeOut() {
		fading = true;
		target = 0;
	}

	@Override
	public void tick() {
		volume += (target - volume) * 0.05f;
		// Surging: the pitch wanders a little, deeper when quieter.
		pitch = 0.32f + 0.12f * volume + 0.03f * (float)Math.sin(System.currentTimeMillis() / 900.0);
		if(fading && volume < 0.005f)
			stop();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}
}
