package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.api.DayType;

/**
 * The flat stratus of a dry day (2026-11-13, the maintainer: "on dry days, can we have non-volumetric stratus
 * clouds?"): a thin grey sheet high over the day's cumulus, drawn by {@link StratusRenderer}. Each dry day has its own:
 * clear blue on some, a thin broken veil on others, a high grey overcast on a few. Not on chance or rainy days, which
 * have their own grey. Pure (no game classes), so it is unit tested.
 */
final class Stratus {

	/** Share of dry days with no stratus at all. */
	static final double CLEAR_DAYS = 0.4;
	/** Share of dry days with a thin, broken veil (the rest are overcast). */
	static final double VEIL_DAYS = 0.35;
	/** The texture: this many pixels a side, repeating every {@link #BLOCKS} blocks. */
	static final int SIZE = 128;
	static final double BLOCKS = 1536;

	private Stratus() {
	}

	/**
	 * How much of the sky the stratus covers on day {@code day} (world day number) of {@code type}: 0 for none, about
	 * 0.3 to 0.5 for a veil, 0.65 to 0.85 for an overcast. Only dry days have any.
	 */
	static double coverage(DayType type, long day) {
		if(type != DayType.DRY)
			return 0;
		double roll = CloudCover.hash((int)day, (int)(day >> 32), 0x57A7L);
		double within = CloudCover.hash((int)day, 7, 0x57A8L);
		if(roll < CLEAR_DAYS)
			return 0;
		if(roll < CLEAR_DAYS + VEIL_DAYS)
			return 0.3 + 0.2 * within;
		return 0.65 + 0.2 * within;
	}

	/**
	 * The sheet's opacity at texture pixel ({@code x}, {@code y}) for {@code coverage}: the noise there thresholded softly,
	 * so a veil is patchy wisps and an overcast nearly solid with thin places. 0..1.
	 */
	static double opacity(int x, int y, double coverage) {
		if(coverage <= 0)
			return 0;
		double n = noise(x, y);
		double cut = 1 - coverage;
		double t = Math.max(0, Math.min(1, (n - cut + 0.18) / 0.36));
		return t * t * (3 - 2 * t);
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
