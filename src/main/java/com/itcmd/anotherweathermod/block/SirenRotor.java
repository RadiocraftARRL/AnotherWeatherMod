package com.itcmd.anotherweathermod.block;

/**
 * A warning siren's rotor and how it sounds (2026-11-13). Pure arithmetic, so it is unit tested.
 * <p>
 * <b>Power:</b> the redstone signal (0 to 15; the strongest of the signal at the siren, its controllers, and 15 for a
 * pole's key switch) sets the speed the rotor makes for: signal / 15. It never jumps there: it winds up at most a
 * full range in {@link #WIND_UP_TICKS} and coasts down slower, in {@link #WIND_DOWN_TICKS}. So a steady 15 winds it
 * up and holds it; cutting the power lets it moan down; a circuit that raises and lowers the signal, or pulses it,
 * makes it rise and fall as players please.
 * <p>
 * <b>Sound:</b> the pitch follows the rotor from {@link #LOWEST} of full (very low) at a crawl up to full, times the
 * mode's {@link SirenMode#pitchScale}; the volume rises faster than the speed (quiet while it is low). The klaxon has
 * no rotor: it sounds as soon as it has a signal, louder for a stronger one.
 */
public final class SirenRotor {

	/** Ticks from still to full speed (a real siren takes several seconds). */
	public static final int WIND_UP_TICKS = 140;
	/** Ticks from full speed to still: it coasts. */
	public static final int WIND_DOWN_TICKS = 300;
	/** The pitch at the slowest turning speed, of full. */
	public static final float LOWEST = 0.3F;
	/** Minecraft plays no pitch under this: below it the sound moves onto the recording an octave down. */
	public static final float ENGINE_LOWEST = 0.5F;
	/** The pitches over which the sound crossfades from the octave-down recording to the normal one. */
	public static final float CROSSFADE_TOP = 0.6F;
	/** How far off, in blocks, the siren has died away. */
	public static final double HEARD = 480.0D;

	private SirenRotor() {}

	/** The speed after one tick, 0 to 1, making for {@code signal} (0 to 15). */
	public static float step(float speed, int signal) {
		float target = Math.max(0, Math.min(15, signal)) / 15.0F;
		float delta = Math.max(-1.0F / WIND_DOWN_TICKS, Math.min(1.0F / WIND_UP_TICKS, target - speed));
		return Math.max(0.0F, Math.min(1.0F, speed + delta));
	}

	/** The pitch the wail is heard at, of the recording's, at {@code speed}. */
	public static float pitch(SirenMode mode, float speed) {
		float bottom = mode.startScale * LOWEST;
		return bottom + (mode.pitchScale - bottom) * Math.max(0.0F, Math.min(1.0F, speed));
	}

	/** How loud the wail is at {@code speed}, 0 to 1: quiet while it is low. */
	public static float loudness(float speed) {
		float s = Math.max(0.0F, Math.min(1.0F, speed));
		return (float)Math.pow(s, 1.6D);
	}

	/** How loud the klaxon is on {@code signal}: nothing at 0, a third at 1, full at 15. */
	public static float klaxonLoudness(int signal) {
		return signal <= 0 ? 0.0F : 0.33F + 0.67F * Math.min(15, signal) / 15.0F;
	}

	/**
	 * The share of the sound on the octave-down recording at {@code pitch}: all of it under
	 * {@link #ENGINE_LOWEST}, none above {@link #CROSSFADE_TOP}. Both recordings play the right pitch in between.
	 */
	public static float lowShare(float pitch) {
		return Math.max(0.0F, Math.min(1.0F, (CROSSFADE_TOP - pitch) / (CROSSFADE_TOP - ENGINE_LOWEST)));
	}

	/** How loud the siren still is {@code distance} blocks off, 1 beside it to 0 at {@link #HEARD}. */
	public static double falloff(double distance) {
		double left = Math.max(0.0D, 1.0D - distance / HEARD);
		return Math.pow(left, 1.5D);
	}

	/**
	 * How strong the echoes are {@code distance} blocks off, 0 to 1: faint close by, where the siren itself drowns
	 * them, strong far off, where the hills and buildings that throw them back are as near as the siren.
	 */
	public static double echo(double distance) {
		return Math.max(0.25D, Math.min(1.0D, (distance - 24.0D) / 150.0D)); // 2026-11-14: more echo close by too (was 0.1).
	}
}
