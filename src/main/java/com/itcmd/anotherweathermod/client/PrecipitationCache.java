package com.itcmd.anotherweathermod.client;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;

/**
 * What falls at a place, remembered (2026-11-18, for frame rate): the storm rain looked up the biome of every rain
 * column every frame, and the clouds the ground under every cell five times a second, and a biome lookup is not cheap
 * (it blends the biomes round the point). What falls at a place depends only on its biome and height, which do not
 * change, so each answer is kept until the level changes or the cache grows past {@link #MAX}. Client only, render
 * thread only.
 */
final class PrecipitationCache {

	static final byte NONE = 0, RAIN = 1, SNOW = 2;
	/** Answers kept before the cache starts over: a few times what a storm and the sky round the camera need. */
	static final int MAX = 200_000;

	private static final Long2ByteOpenHashMap CACHE = new Long2ByteOpenHashMap();
	private static ClientLevel cachedLevel;

	static {
		CACHE.defaultReturnValue((byte)-1);
	}

	private PrecipitationCache() {
	}

	/** What falls at ({@code x}, {@code y}, {@code z}): {@link #NONE}, {@link #RAIN} or {@link #SNOW}. */
	static byte at(ClientLevel level, int x, int y, int z) {
		if(level != cachedLevel || CACHE.size() > MAX) {
			CACHE.clear();
			cachedLevel = level;
		}
		long key = key(x, y, z);
		byte known = CACHE.get(key);
		if(known >= 0)
			return known;
		BlockPos pos = new BlockPos(x, y, z);
		byte found = of(level.getBiome(pos).value().getPrecipitationAt(pos));
		CACHE.put(key, found);
		return found;
	}

	/** Whether rain (not snow, not nothing) falls at the place. */
	static boolean rains(ClientLevel level, int x, int y, int z) {
		return at(level, x, y, z) == RAIN;
	}

	/** A key for the block column at x, z and height y (26 bits each way of x and z, 12 of y). */
	static long key(int x, int y, int z) {
		return ((long)x & 0x3FFFFFFL) << 38 | ((long)z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
	}

	static void clear() {
		CACHE.clear();
		cachedLevel = null;
	}

	/** The byte for {@code precipitation}. */
	static byte of(Biome.Precipitation precipitation) {
		return switch(precipitation) {
			case RAIN -> RAIN;
			case SNOW -> SNOW;
			default -> NONE;
		};
	}
}
