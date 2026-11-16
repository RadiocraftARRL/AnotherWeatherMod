package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.api.DayType;

/**
 * The flat stratus of a dry day (2026-11-13, the maintainer: "on dry days, can we have non-volumetric stratus
 * clouds?"): a thin sheet high over the day's cumulus, drawn on the sky by {@link StratusRenderer}. Each dry day has
 * its own (2026-11-15, the maintainer: variety and natural-looking days, but not a quarter of them a solid grey sheet
 * with holes): clear blue on {@link #CLEAR_DAYS}, a thin veil of wisps on {@link #VEIL_DAYS}, broken cloud on
 * {@link #BROKEN_DAYS}, and on the rest (one in ten) an overcast: a continuous layer, thicker and thinner in places,
 * not a sheet with holes punched in it. Not on chance or rainy days, which have their own grey. Pure (no game classes),
 * so it is unit tested.
 */
final class Stratus {

	/** Share of dry days with no stratus at all. */
	static final double CLEAR_DAYS = 0.35;
	/** Shares of dry days with a thin veil and with broken cloud (the rest, one in ten, are overcast). */
	static final double VEIL_DAYS = 0.30, BROKEN_DAYS = 0.25;
	/** Coverage ranges: a veil, broken cloud, an overcast. */
	static final double VEIL_MIN = 0.2, VEIL_MAX = 0.35, BROKEN_MIN = 0.4, BROKEN_MAX = 0.6, OVERCAST_MIN = 0.7, OVERCAST_MAX = 0.85;
	/** From this coverage up the sheet turns from patches into a continuous layer ({@link #opacity}). */
	static final double LAYER_FROM = 0.6;
	/** The texture: this many pixels a side, repeating every {@link #BLOCKS} blocks. */
	static final int SIZE = 128;
	static final double BLOCKS = 1536;

	private Stratus() {
	}

	/**
	 * How much of the sky the stratus covers on day {@code day} (world day number) of {@code type}: 0 for none, a veil,
	 * broken cloud or an overcast (see the ranges). Only dry days have any.
	 */
	static double coverage(DayType type, long day) {
		if(type != DayType.DRY)
			return 0;
		double roll = CloudCover.hash((int)day, (int)(day >> 32), 0x57A7L);
		double within = CloudCover.hash((int)day, 7, 0x57A8L);
		if(roll < CLEAR_DAYS)
			return 0;
		if(roll < CLEAR_DAYS + VEIL_DAYS)
			return VEIL_MIN + (VEIL_MAX - VEIL_MIN) * within;
		if(roll < CLEAR_DAYS + VEIL_DAYS + BROKEN_DAYS)
			return BROKEN_MIN + (BROKEN_MAX - BROKEN_MIN) * within;
		return OVERCAST_MIN + (OVERCAST_MAX - OVERCAST_MIN) * within;
	}

	/**
	 * The sheet's opacity at texture pixel ({@code x}, {@code y}) for {@code coverage}, 0..1: the noise there (ranked,
	 * {@link #rank}) thresholded softly, so a veil is wisps and broken cloud patches with sky between. From {@link #LAYER_FROM} up it turns into a
	 * continuous layer whose thickness follows the noise (an overcast: thick and thin, no holes), so the most covered
	 * days are not a solid sheet with holes in it.
	 */
	static double opacity(int x, int y, double coverage) {
		if(coverage <= 0)
			return 0;
		double n = rank(x, y);
		double cut = 1 - coverage;
		double t = Math.max(0, Math.min(1, (n - cut + 0.12) / 0.24));
		double patches = t * t * (3 - 2 * t) * (0.5 + 0.5 * Math.min(1, coverage / LAYER_FROM)); // A thin veil is see-through.
		if(coverage <= LAYER_FROM)
			return patches;
		double k = Math.min(1, (coverage - LAYER_FROM) / (OVERCAST_MIN - LAYER_FROM));
		double layer = 0.35 + 0.5 * n * n * (3 - 2 * n); // Thinner and thicker, never quite clear nor solid.
		return patches + (layer - patches) * k;
	}

	/** The noise's rank over the whole texture, 0..1 (evenly spread), by pixel; made once. */
	private static float[] ranks;

	/**
	 * The noise at ({@code x}, {@code y}) as its rank among all the texture's pixels, 0..1 (2026-11-15): the raw noise
	 * bunches round its middle, so a coverage of 0.3 covered about 1% of the sky and a veil was all but invisible.
	 * Ranked, a coverage of 0.3 covers about 30%.
	 */
	static double rank(int x, int y) {
		float[] r = ranks;
		if(r == null) {
			int n = SIZE * SIZE;
			Integer[] order = new Integer[n];
			double[] values = new double[n];
			for(int i = 0; i < n; i++) {
				order[i] = i;
				values[i] = noise(i % SIZE, i / SIZE);
			}
			java.util.Arrays.sort(order, java.util.Comparator.comparingDouble(i -> values[i]));
			r = new float[n];
			for(int k = 0; k < n; k++)
				r[order[k]] = (k + 0.5f) / n;
			ranks = r;
		}
		return r[Math.floorMod(y, SIZE) * SIZE + Math.floorMod(x, SIZE)];
	}

	/** Smooth noise 0..1 that repeats every {@link #SIZE} pixels: four octaves of value noise. */
	static double noise(int x, int y) {
		double sum = 0, weight = 0, amp = 1;
		for(int octave = 0; octave < 4; octave++) {
			int cells = 4 << octave;
			sum += amp * value(x * cells / (double)SIZE, y * cells / (double)SIZE, cells, octave);
			weight += amp;
			amp *= 0.5;
		}
		return sum / weight;
	}

	private static double value(double x, double y, int cells, int octave) {
		int x0 = (int)Math.floor(x), y0 = (int)Math.floor(y);
		double fx = x - x0, fy = y - y0;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		double a = lattice(x0, y0, cells, octave), b = lattice(x0 + 1, y0, cells, octave);
		double c = lattice(x0, y0 + 1, cells, octave), d = lattice(x0 + 1, y0 + 1, cells, octave);
		return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
	}

	private static double lattice(int x, int y, int cells, int octave) {
		return CloudCover.hash(Math.floorMod(x, cells), Math.floorMod(y, cells), 0x5700L + octave);
	}
}
