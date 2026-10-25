package com.itcmd.anotherweathermod.api;

/** What is falling (or blowing) at a position. */
public enum Precipitation {
	NONE,
	RAIN,
	SNOW,
	/** Hail: a strong supercell over a rain biome. */
	HAIL,
	/** Dust or sand blown by a storm over a biome without precipitation. */
	DUST
}
