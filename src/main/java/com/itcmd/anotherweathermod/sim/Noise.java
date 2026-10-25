package com.itcmd.anotherweathermod.sim;

/** Seeded hash noise. Deterministic: the same seed and input always give the same value. */
final class Noise {

	private Noise() {
	}

	/** A well-mixed 64 bit hash (SplitMix64's finalizer). */
	static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
		z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
		return z ^ (z >>> 31);
	}

	/** A value in [0, 1) from {@code seed} and {@code i}. */
	static double unit(long seed, long i) {
		return (mix(seed * 0x9e3779b97f4a7c15L + i) >>> 11) * 0x1.0p-53;
	}

	/** A value in [-1, 1) from {@code seed} and {@code i}. */
	static double signed(long seed, long i) {
		return unit(seed, i) * 2 - 1;
	}

	/** Smooth value noise in [-1, 1]: random knots at whole numbers, eased between. */
	static double smooth(long seed, double u) {
		long i = (long)Math.floor(u);
		double f = u - i;
		double s = f * f * (3 - 2 * f);
		double a = signed(seed, i);
		double b = signed(seed, i + 1);
		return a + (b - a) * s;
	}
}
