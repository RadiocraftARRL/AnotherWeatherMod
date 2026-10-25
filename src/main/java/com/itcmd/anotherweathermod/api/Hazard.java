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
	HURRICANE_EYE
}
