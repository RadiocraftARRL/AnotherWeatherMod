package com.itcmd.anotherweathermod;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The weather's own sounds (the instruments' sounds are with their blocks, in {@code AwmBlocks}). */
public final class AwmSounds {

	private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT,
			AnotherWeatherMod.MOD_ID);

	/** Far-off thunder: a low roll that swells in and grumbles away, no crack (tools/thunder_sound.py). */
	public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_THUNDER = SOUNDS.register("distant_thunder",
			SoundEvent::createVariableRangeEvent);
	/** Thunder from a strike close by: a crack, then the roll. */
	public static final DeferredHolder<SoundEvent, SoundEvent> NEAR_THUNDER = SOUNDS.register("near_thunder",
			SoundEvent::createVariableRangeEvent);

	/**
	 * Wood creaking in a strong wind (2026-11-13): played by planks, logs and other wooden blocks round the player in a
	 * gale. For now sounds.json points it at vanilla's door and chest sounds pitched down; real recordings go in
	 * {@code assets/anotherweathermod/sounds/wood_creak/}.
	 */
	public static final DeferredHolder<SoundEvent, SoundEvent> WOOD_CREAK = SOUNDS.register("wood_creak",
			SoundEvent::createVariableRangeEvent);

	private AwmSounds() {
	}

	static void register(IEventBus modBus) {
		SOUNDS.register(modBus);
	}
}
