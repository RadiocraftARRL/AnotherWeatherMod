package com.itcmd.anotherweathermod.api;

/** Where a storm is in its life. */
public enum StormStage {
	/** Decided by the forecast but not formed yet. */
	PENDING,
	/** Growing: intensity and size ramp up. */
	FORMING,
	/** At full strength. */
	MATURE,
	/** Weakening. */
	DECAYING,
	/** Gone. */
	DISSIPATED
}
