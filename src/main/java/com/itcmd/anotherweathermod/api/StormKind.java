package com.itcmd.anotherweathermod.api;

/**
 * What sort of weather a cell is. The ground under it decides what falls (see {@link Precipitation}).
 *
 * <p>Rain-bearing storms run up a scale of 0 to 5 ({@link #scale()}): a rainstorm, a thunderstorm, a strong
 * thunderstorm, a severe thunderstorm, a hailstorm and a tornado. Hurricanes have their own category, 0 to 5, per
 * storm ({@link StormView#category()}). The last three kinds bring no rain: thick fog, and heat waves and cold snaps
 * over deserts and snowy ground. High wind (added after them) is clear weather with a strong, gusty wind.
 */
public enum StormKind {
	/** 0: rain or snow, no thunder. The common one. */
	SHOWER(0, true),
	/** 1: heavy rain or snow with lightning and a gust front. */
	THUNDERSTORM(1, true),
	/** 2: a stronger thunderstorm: heavier rain, more lightning, stronger gusts that still break nothing. */
	STRONG_THUNDERSTORM(2, true),
	/**
	 * 3: a thunderstorm with a strong gust front: its winds strip some leaves and blow wooden doors open, but it brings
	 * no hail.
	 */
	SEVERE_THUNDERSTORM(3, true),
	/** 4: the hailstorm: hail over rain biomes, blizzards and sandstorms elsewhere. */
	SUPERCELL(4, true),
	/** 5: a supercell that puts a tornado down for part of its life. */
	TORNADO(5, true),
	/** A huge, slow, ocean-born storm with an eye and rotating winds that damage blocks. Category 0 to 5 per storm. */
	HURRICANE(-1, true),
	/** A bank of thick fog. No rain. */
	DENSE_FOG(-1, false),
	/** Fierce heat over deserts and other dry ground, by day. */
	HEAT_WAVE(-1, false),
	/** Bitter cold over snowy ground, at night. */
	COLD_SNAP(-1, false),
	/** Clear weather with a strong, gusty wind that can push you about. No rain, nothing broken. */
	HIGH_WIND(-1, false);

	private final int scale;
	private final boolean rain;

	StormKind(int scale, boolean rain) {
		this.scale = scale;
		this.rain = rain;
	}

	/** Whether this kind of storm has lightning. */
	public boolean hasThunder() {
		return scale >= 1 || this == HURRICANE;
	}

	/** Where a rain-bearing storm is on the 0 to 5 scale, or -1 for hurricanes and the kinds that bring no rain. */
	public int scale() {
		return scale;
	}

	/** Whether it brings rain (or snow, hail or dust); fog, heat waves, cold snaps and high wind do not. */
	public boolean bringsRain() {
		return rain;
	}

	/** The rain-bearing storm kind at {@code scale} on the 0 to 5 scale. */
	public static StormKind ofScale(int scale) {
		return switch(Math.max(0, Math.min(5, scale))) {
			case 0 -> SHOWER;
			case 1 -> THUNDERSTORM;
			case 2 -> STRONG_THUNDERSTORM;
			case 3 -> SEVERE_THUNDERSTORM;
			case 4 -> SUPERCELL;
			default -> TORNADO;
		};
	}
}
