package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.Precipitation;

/** What the ground is like: the simulation's only view of the world. Implementations must not load chunks. */
public interface Climate {

	/** What falls here in vanilla terms: {@link Precipitation#RAIN}, {@link Precipitation#SNOW} or {@link Precipitation#NONE}. */
	Precipitation basePrecipitation(int x, int y, int z);

	/** The biome's vanilla temperature (0.8 for plains, 2 for desert, 0 or below for snowy biomes). */
	float biomeTemperature(int x, int y, int z);

	/** Whether ({@code x}, {@code z}) is ocean. Used to place hurricanes. */
	boolean isOcean(int x, int z);

	/** The sea level, for temperature by height. */
	int seaLevel();
}
