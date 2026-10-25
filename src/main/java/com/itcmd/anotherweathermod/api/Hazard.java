package com.itcmd.anotherweathermod.api;

/** Dangerous weather at a position. A position can have several at once. */
public enum Hazard {
	LIGHTNING,
	HAIL,
	/** Heavy snow and strong wind over a snow biome. */
	BLIZZARD,
	/** Strong wind over a biome without precipitation. */
	SANDSTORM,
	/** Wind strong enough to damage blocks. */
	DAMAGING_WIND,
	/** Inside a hurricane's calm eye. */
	HURRICANE_EYE,
	/** In or right by a tornado. */
	TORNADO,
	/** Fog so thick you can see a dozen blocks or so. */
	DENSE_FOG,
	/** A heat wave, out in the day. */
	HEAT_WAVE,
	/** A cold snap, out at night. */
	COLD_SNAP
}
