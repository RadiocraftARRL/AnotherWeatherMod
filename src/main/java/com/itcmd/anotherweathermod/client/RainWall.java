package com.itcmd.anotherweathermod.client;

import net.minecraft.util.Mth;

/**
 * The shape and look of a storm's rain shaft seen from outside, apart from drawing it: a few sheets of falling streaks
 * set one behind another into the rain (so they slide past each other as you move, and the shaft has depth), each
 * falling at its own speed; denser and darker up under the cloud base and thinning toward the ground; with a haze
 * round its foot that thickens with distance, so far-off shafts look misty. Snow falls slower and drifts.
 */
final class RainWall {

	/** Sheets of streaks, front to back. */
	static final int LAYERS = 4;
	/** How far into the rain each sheet stands, blocks in from the rain's edge. */
	static final double[] DEPTH = { 0, 14, 32, 58 };
	/** How opaque each sheet is, front to back: the deeper ones are seen through the front ones. */
	static final float[] LAYER_ALPHA = { 0.55f, 0.5f, 0.42f, 0.34f };
	/** Rows up a sheet, for its gradient from the ground to the cloud base. */
	static final int ROWS = 5;
	/** Texture scroll a tick for rain and for snow (the texture repeats every {@link #V_BLOCKS} blocks). */
	static final double RAIN_SPEED = 0.16;
	static final double SNOW_SPEED = 0.018;
	static final double V_BLOCKS = 24;
	/** Blocks of wall per repeat of the streak texture across: smaller is denser. */
	static final double U_BLOCKS = 3.5;
	/** Blocks inside the rain over which the wall fades away as the camera walks into it. */
	static final double ENTER_FADE = 48;

	private RainWall() {
	}

	/**
	 * How strong the shaft is at {@code f} of the way up from the ground (0) to the cloud base (1): thick under the
	 * cloud, thinning toward the ground, and softening into the deck at the very top.
	 */
	static float vertical(double f) {
		f = Mth.clamp(f, 0, 1);
		double body = 0.4 + 0.6 * Math.pow(f, 0.8);
		double top = f > 0.9 ? 1 - (f - 0.9) / 0.1 * 0.5 : 1;
		return (float)(body * top);
	}

	/** The haze at {@code f} of the way up: thickest along the ground, gone by two thirds of the way up. */
	static float hazeVertical(double f) {
		f = Mth.clamp(f, 0, 1);
		return (float)Math.max(0, 1 - f * 1.5);
	}

	/**
	 * How much of the shaft shows from {@code fromEdge} blocks outside the rain's edge (negative inside): fading out
	 * smoothly as the camera walks in, full in the near distance, and thinning with distance beyond.
	 */
	static float distanceFade(double fromEdge, double farthest) {
		if(fromEdge < 0)
			return (float)smoothstep(-ENTER_FADE, 0, fromEdge);
		double far = Math.max(1, farthest);
		return (float)(1 - 0.65 * smoothstep(far * 0.25, far, fromEdge));
	}

	/** How thick the haze at the foot of a shaft is from {@code fromEdge} blocks away: more the farther it is. */
	static float hazeAmount(double fromEdge) {
		if(fromEdge < 0)
			return (float)smoothstep(-ENTER_FADE, 0, fromEdge) * 0.4f;
		return (float)(0.4 + 0.6 * smoothstep(40, 500, fromEdge));
	}

	/** A sheet's speed, as a multiple of the base: each sheet of each storm falls at its own pace. */
	static double speed(long storm, int layer) {
		return 0.85 + 0.4 * CloudCover.hash((int)storm, layer, 0x5A1L);
	}

	/** A sheet's sideways texture offset, so the sheets' streaks never line up. */
	static double offset(long storm, int layer) {
		return CloudCover.hash((int)storm, layer, 0x0FFL) * 7;
	}

	/** How far down the streak texture has scrolled at {@code time} (ticks) for a sheet. */
	static double scroll(double time, boolean snow, long storm, int layer) {
		return time * (snow ? SNOW_SPEED : RAIN_SPEED) * speed(storm, layer);
	}

	/** Snow drifts from side to side as it falls, texture units across. */
	static double drift(double time, boolean snow, int layer) {
		return snow ? 0.35 * Math.sin(time * 0.012 + layer * 1.7) : 0;
	}

	/**
	 * The shaft's colour for a storm of cloud {@code darkness} (as {@link CloudCover#DARKNESS}): rain a grey-blue a
	 * little lighter than its cloud, snow nearly white. Deeper sheets are a touch darker. Red, green, blue.
	 */
	static float[] tint(float darkness, boolean snow, int layer) {
		float depth = 1 - 0.06f * layer;
		if(snow)
			return new float[] { 0.9f * depth, 0.92f * depth, 0.97f * depth };
		float shade = 0.35f + 0.6f * darkness;
		return new float[] { shade * 0.86f * depth, shade * 0.92f * depth, shade * 1.04f * depth };
	}

	private static double smoothstep(double from, double to, double x) {
		double t = Mth.clamp((x - from) / (to - from), 0, 1);
		return t * t * (3 - 2 * t);
	}
}
