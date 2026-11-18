package com.itcmd.anotherweathermod.client;

import net.minecraft.util.Mth;

/**
 * The shape and look of a storm's rain shaft seen from outside, apart from drawing it: faint sheets of falling streaks
 * set one behind another into the rain (so they slide past each other as you move, and the shaft has depth), each
 * falling at its own speed, and none of them flat: each bows in and out along the wall and leans a little with height
 * ({@link #wobble}), so the shaft reads as a volume of rain rather than a sheet. Denser up under the cloud base and
 * thinning toward the ground. Snow falls slower and drifts.
 * <p>
 * 2026-11-13: the grey haze sheets along the shaft's foot are gone (the maintainer: a fog sheet ahead of every storm);
 * the streak sheets are fainter, one more of them, and bowed. Near the rain the drops come one by one ({@link RainOnset}).
 */
final class RainWall {

	/** Sheets of streaks, front to back. */
	static final int LAYERS = 5;
	/** How far into the rain each sheet stands, blocks in from the rain's edge. */
	static final double[] DEPTH = { 0, 11, 25, 42, 66 };
	/** How opaque each sheet is, front to back: faint, so it is the sheets together that make the shaft. */
	static final float[] LAYER_ALPHA = { 0.24f, 0.21f, 0.18f, 0.15f, 0.12f };
	/** How far a sheet bows in and out along the wall, blocks. */
	static final double WOBBLE = 7;
	/** How far a sheet leans between the ground and the cloud base, blocks (either way, half above and half below). */
	static final double LEAN = 6;
	/** Rows up a sheet, for its gradient from the ground to the cloud base. */
	static final int ROWS = 5;
	/** Segments along a storm's wall. */
	static final int SEGMENTS = 32;
	/** A merged far mass's wall ({@link StormLod}): this many faint sheets, this many segments by rows. */
	static final int MASS_LAYERS = 2;
	static final int MASS_SEGMENTS = 8;
	static final int MASS_ROWS = 3;
	/** Texture scroll a tick for rain and for snow (the texture repeats every {@link #V_BLOCKS} blocks). */
	static final double RAIN_SPEED = 0.16;
	static final double SNOW_SPEED = 0.018;
	static final double V_BLOCKS = 24;
	/** Blocks of wall per repeat of the streak texture across: smaller is denser. */
	static final double U_BLOCKS = 3.5;
	/** Blocks inside the rain over which the wall fades away as the camera walks into it. */
	static final double ENTER_FADE = 48;
	/** Seconds the walls take to fade out going indoors, and back in going out. */
	static final float INDOOR_FADE_SECONDS = 0.6f;

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

	/**
	 * Whether the camera is outdoors for the rain walls: nothing but leaves over it ({@code openFrom} is the lowest y
	 * with nothing above but air and leaves, the {@code MOTION_BLOCKING_NO_LEAVES} height). Under a roof, in a cave or
	 * a house with windows the walls are hidden (seen indoors, 2026-11-12); under a tree they still show.
	 */
	static boolean outdoors(double cameraY, int openFrom) {
		return cameraY >= openFrom;
	}

	/** Steps the walls' indoor fade, 0 hidden to 1 shown, {@code seconds} toward {@code outdoors}. */
	static float stepOutdoors(float shown, boolean outdoors, float seconds) {
		float step = Math.max(0, seconds) / INDOOR_FADE_SECONDS;
		return outdoors ? Math.min(1, shown + step) : Math.max(0, shown - step);
	}

	/**
	 * The sheets a wall draws with {@code layers} of the {@link #LAYERS} (2026-11-18, the rain detail setting): the front
	 * one always, the rest spread evenly through the depth, so a lighter wall still has front and back.
	 */
	static int[] layersFor(int layers) {
		int n = Mth.clamp(layers, 1, LAYERS);
		int[] out = new int[n];
		for(int i = 0; i < n; i++)
			out[i] = n == 1 ? 0 : (int)Math.round(i * (LAYERS - 1) / (double)(n - 1));
		return out;
	}

	/**
	 * A sheet's opacity when only {@code layers} of the {@link #LAYERS} are drawn: a little denser. The rest of the
	 * missing sheets' rain is made up by tiling their streaks tighter across the sheets drawn (the sheet's density), so
	 * this stands in for only the square root of their number, not all of them, or the wall would come out heavier.
	 */
	static float layerAlpha(float alpha, int layers) {
		int n = Mth.clamp(layers, 1, LAYERS);
		if(n == LAYERS)
			return alpha;
		return (float)(1 - Math.pow(1 - Mth.clamp(alpha, 0, 1), Math.sqrt(LAYERS / (double)n)));
	}

	/** Quads in one storm's full wall: its sheets of streaks. */
	static int stormWallQuads() {
		return LAYERS * SEGMENTS * ROWS;
	}

	/** Quads in a merged mass's wall. */
	static int massWallQuads() {
		return MASS_LAYERS * MASS_SEGMENTS * MASS_ROWS;
	}

	/**
	 * How far a sheet stands out from its place (blocks; positive toward the camera) at {@code arc} blocks along the wall
	 * and {@code f} of the way up: two swells of different lengths along the wall that shift a little with height, and a
	 * lean, each sheet of each storm its own. Never more than {@link #WOBBLE} plus half {@link #LEAN}.
	 */
	static double wobble(long storm, int layer, double arc, double f) {
		double p1 = CloudCover.hash((int)storm, layer, 0x31L) * Math.PI * 2;
		double p2 = CloudCover.hash((int)storm, layer, 0x32L) * Math.PI * 2;
		double lean = (CloudCover.hash((int)storm, layer, 0x33L) - 0.5) * 2 * LEAN;
		double swell = 0.65 * Math.sin(arc / 23 + p1 + f * 1.3) + 0.35 * Math.sin(arc / 9.5 + p2 - f * 2.1);
		return WOBBLE * swell + lean * (Mth.clamp(f, 0, 1) - 0.5);
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

	/** How far inside another storm's rain a wall fades out over, blocks. */
	static final double MERGE_FADE = 40;

	/**
	 * 1 where the point ({@code x}, {@code z}) of a storm's rain wall is out in the open, falling to 0 as it runs
	 * {@link #MERGE_FADE} blocks into another storm's rain (2026-11-13): storms that touch show one curtain round the
	 * lot instead of sheets crossing through each other. {@code rain} holds each storm's centre x, z and rain edge;
	 * {@code self} is the wall's own storm.
	 */
	static float outsideOthers(double x, double z, double[][] rain, int self) {
		float out = 1;
		for(int i = 0; i < rain.length; i++) {
			double edge = rain[i][2];
			if(i == self || edge <= 0)
				continue;
			double dx = x - rain[i][0], dz = z - rain[i][1], d2 = dx * dx + dz * dz;
			if(d2 >= edge * edge)
				continue; // Outside its rain: no square root needed (2026-11-18, it ran for every corner of every wall).
			double inside = edge - Math.sqrt(d2);
			out = Math.min(out, (float)(1 - smoothstep(0, MERGE_FADE, inside)));
		}
		return out;
	}

	private static double smoothstep(double from, double to, double x) {
		double t = Mth.clamp((x - from) / (to - from), 0, 1);
		return t * t * (3 - 2 * t);
	}
}
