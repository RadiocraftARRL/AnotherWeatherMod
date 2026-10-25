package com.itcmd.anotherweathermod.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The roar of the wind around the player, louder and higher in gusts. Uses the elytra's rushing-air loop until the mod
 * has its own wind sounds. {@link WeatherEffects} sets its strength every tick.
 */
public final class WindSound extends AbstractTickableSoundInstance {

	private float strength;

	public WindSound() {
		super(SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, SoundInstance.createUnseededRandom());
		looping = true;
		delay = 0;
		relative = true;
		attenuation = Attenuation.NONE;
		volume = 0;
	}

	/** 0 for still air, 1 for a full gale. */
	void setStrength(float strength) {
		this.strength = strength;
	}

	@Override
	public void tick() {
		volume += (strength * 0.7f - volume) * 0.1f;
		pitch = 0.55f + strength * 0.45f;
		if(strength <= 0 && volume < 0.005f)
			stop();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}
}
