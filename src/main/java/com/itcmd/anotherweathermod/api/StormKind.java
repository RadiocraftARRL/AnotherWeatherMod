package com.itcmd.anotherweathermod.api;

/** What sort of storm a cell is. The ground under it decides what falls (see {@link Precipitation}). */
public enum StormKind {
	/** Rain or snow, no thunder. The common one. */
	SHOWER(false),
	/** Heavy rain or snow with lightning and a gust front. */
	THUNDERSTORM(true),
	/**
	 * A thunderstorm with a strong gust front: its winds strip some leaves and blow wooden doors open, but it brings
	 * no hail.
	 */
	SEVERE_THUNDERSTORM(true),
	/** The strongest thunderstorm: hail over rain biomes, blizzards and sandstorms elsewhere. */
	SUPERCELL(true),
	/** A huge, slow, ocean-born storm with an eye and rotating winds that damage blocks. */
	HURRICANE(true);

	private final boolean thunder;

	StormKind(boolean thunder) {
		this.thunder = thunder;
	}

	/** Whether this kind of storm has lightning. */
	public boolean hasThunder() {
		return thunder;
	}
}
