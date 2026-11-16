package com.itcmd.anotherweathermod.sim;

/**
 * Whether a tropical disturbance grows into a hurricane (2026-11-15, the maintainer: early warnings that may be false
 * alarms, "a 70% chance probably"). Every hurricane the generator rolls starts as a disturbance with a chance of
 * developing, given out in the advisories, between {@link #CHANCE_MIN} and {@link #CHANCE_MAX} (70% on average), and
 * it develops just that often: the chance is honest. One that develops becomes a hurricane, category 1 to 5; one that
 * does not stays a tropical storm (category 0) or fizzles out early, weak, as a few showers.
 * <p>
 * All a pure function of the storm's seed, so it is known as soon as the storm is (days before it forms).
 */
public final class TropicalDevelopment {

	/** The stated chances of developing range over this. */
	public static final double CHANCE_MIN = 0.5, CHANCE_MAX = 0.9;
	/** Of the disturbances that do not develop, the share that fizzle out rather than staying a tropical storm. */
	static final double FIZZLE_SHARE = 0.4;
	/** A fizzler's life and strength, as shares of what was rolled. */
	static final double FIZZLE_LIFE = 0.35, FIZZLE_STRENGTH = 0.45;
	/** How often each category comes among those that develop, 1 to 5 (the old odds without category 0). */
	static final double[] DEVELOPED_ODDS = { 0, 0.26 / 0.82, 0.22 / 0.82, 0.17 / 0.82, 0.11 / 0.82, 0.06 / 0.82 };

	private TropicalDevelopment() {
	}

	/** The chance the disturbance with {@code seed} develops into a hurricane, 0..1, as the advisories give it. */
	public static double chance(long seed) {
		return CHANCE_MIN + (CHANCE_MAX - CHANCE_MIN) * Noise.unit(seed, 0x7D01);
	}

	/** The chance in whole percent, as said on the air. */
	public static int chancePercent(long seed) {
		return (int)Math.round(chance(seed) * 100);
	}

	/** Whether it develops into a hurricane: as often as {@link #chance} says. */
	public static boolean develops(long seed) {
		return Noise.unit(seed, 0x7D02) < chance(seed);
	}

	/** Whether, not developing, it fizzles out early rather than staying a tropical storm. */
	public static boolean fizzles(long seed) {
		return !develops(seed) && Noise.unit(seed, 0x7D03) < FIZZLE_SHARE;
	}

	/** Its category at its peak: 1 to 5 if it develops, else 0 (a tropical storm, or less). */
	public static int category(long seed) {
		return develops(seed) ? StormCell.draw(seed, DEVELOPED_ODDS) : 0;
	}
}
