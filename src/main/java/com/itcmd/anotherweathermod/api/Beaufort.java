package com.itcmd.anotherweathermod.api;

/** The Beaufort wind force scale. */
public final class Beaufort {

	/** Upper wind speed of each force 0..11, metres per second. Force 12 is anything above the last. */
	private static final double[] LIMITS = { 0.5, 1.5, 3.3, 5.5, 7.9, 10.7, 13.8, 17.1, 20.7, 24.4, 28.4, 32.6 };

	private Beaufort() {
	}

	/** The Beaufort force for a wind speed in metres per second. */
	public static int of(double metresPerSecond) {
		for(int force = 0; force < LIMITS.length; force++)
			if(metresPerSecond < LIMITS[force])
				return force;
		return 12;
	}
}
