package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.block.SirenRotor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;

import java.lang.reflect.Field;

/**
 * Subtitles for sirens (2026-11-14, the maintainer: subtitles for accessibility). Minecraft shows a sound's subtitle
 * once, when it starts, for three seconds; a siren sounds for minutes. Each siren's main sound calls {@link #tick}
 * every tick, and while it can be heard its subtitle is put up again every {@link #EVERY} ticks, at the siren, saying
 * which siren and what its rotor is doing: winding up, wailing, winding down (the klaxon blares).
 */
public final class SirenSubtitles {

	/** Ticks between refreshes (a subtitle shows for three seconds each time). */
	static final int EVERY = 40;
	/** Quieter than this, a siren gets no subtitle. */
	static final float FLOOR = 0.02F;

	private static Field overlayField;
	private static boolean broken;

	private SirenSubtitles() {}

	/** One siren's countdown to its next refresh. */
	public static final class Clock {
		private int in;
	}

	/**
	 * Called every tick by a siren's main sound, {@code heard} loud (0 to 1, after distance): when it is loud enough and
	 * its clock has run down, puts up the subtitle {@code key} at {@code sound}'s place.
	 */
	public static void tick(Clock clock, SoundInstance sound, String key, float heard) {
		if(broken || heard < FLOOR || --clock.in > 0)
			return;
		clock.in = EVERY;
		try {
			if(overlayField == null) {
				overlayField = net.neoforged.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.client.gui.Gui.class, "subtitleOverlay");
				overlayField.setAccessible(true);
			}
			if(overlayField.get(Minecraft.getInstance().gui) instanceof SubtitleOverlay overlay)
				overlay.onPlaySound(sound, new WeighedSoundEvents(sound.getLocation(), key), (float)SirenRotor.HEARD);
		}
		catch(ReflectiveOperationException | RuntimeException e) {
			broken = true; // No overlay to reach: stop trying.
		}
	}

	/** {@code base} with ".up" or ".down" while the rotor winds up or down ({@code now} against {@code before}). */
	public static String winding(String base, float now, float before) {
		if(now > before + 0.01F)
			return base + ".up";
		if(now < before - 0.01F)
			return base + ".down";
		return base;
	}
}
