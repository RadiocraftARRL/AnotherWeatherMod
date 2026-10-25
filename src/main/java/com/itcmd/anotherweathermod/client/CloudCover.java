package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.network.NearbyStormsPayload;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Which clouds are where, at one moment: the sky is cut into {@link #CELL}-block cells (in cloud space, which drifts
 * east), and each cell holds a fair-weather cumulus of some size, a stretch of storm cloud of some depth and darkness,
 * or nothing. Fair-weather cover comes from slow noise, patchy and thicker some days than others, and swells round
 * storms; storms fill their cells solid, a hurricane leaving its eye clear. A cell's {@link #cell(int, int) key} only
 * changes in steps, so its cloud is rebuilt only when it visibly changes.
 */
final class CloudCover {

	/** Size of a cloud cell, blocks. */
	static final double CELL = 32;
	/** Keys: no cloud; a fair-weather cumulus is {@code FAIR + size}; storm cloud is {@code STORM + depth * 8 + kind}. */
	static final int NONE = -1;
	static final int FAIR = 0;
	static final int FAIR_SIZES = 4;
	static final int STORM = 16;
	static final int STORM_DEPTHS = 4;

	/** How dark storm cloud is by {@link #stormKind(int) kind}: shower, scale 1 to 5, hurricane, other. */
	static final float[] DARKNESS = { 0.62f, 0.48f, 0.42f, 0.36f, 0.33f, 0.28f, 0.38f, 0.55f };
	/** How tall storm cloud grows by kind, as a share of the tallest. */
	static final float[] TALL = { 0.3f, 0.6f, 0.75f, 0.9f, 1f, 1f, 0.8f, 0.4f };

	private final List<NearbyStormsPayload.Storm> storms;
	private final double age;
	private final double driftX;
	/** How cloudy fair weather is today, 0 to 1. */
	private final double coverage;

	CloudCover(NearbyStormsPayload payload, long gameTime, double driftX) {
		this.storms = payload.storms();
		this.age = gameTime - payload.gameTime();
		this.driftX = driftX;
		this.coverage = 0.3 + 0.35 * smoothNoise(0x5EED, gameTime / 30000.0);
	}

	/** The key of cloud-space cell ({@code i}, {@code j}). */
	int cell(int i, int j) {
		double x = (i + 0.5) * CELL + driftX, z = (j + 0.5) * CELL;
		double storm = 0, near = 0;
		int kind = 0;
		for(NearbyStormsPayload.Storm s : storms) {
			if(!s.kind().bringsRain())
				continue;
			double sx = s.x() + s.velocityX() * age, sz = s.z() + s.velocityZ() * age;
			double d = Math.hypot(x - sx, z - sz) / Math.max(1, s.radius());
			double c = s.intensity() * (1 - smoothstep(0.7, 1.15, d));
			if(s.kind() == StormKind.HURRICANE)
				c *= smoothstep(0.05, 0.13, d); // The clear eye.
			near = Math.max(near, s.intensity() * (1 - smoothstep(1.1, 2.2, d)));
			if(c > storm) {
				storm = c;
				kind = kindIndex(s.kind());
			}
		}
		int depth = (int)Math.round(storm * STORM_DEPTHS);
		if(depth > 0)
			return STORM + (Math.min(depth, STORM_DEPTHS) - 1) * 8 + kind;
		// Fair weather: patches of cumulus where the noise is high, more of them near a storm.
		double n = 0.65 * valueNoise(x / 420, z / 420, 11) + 0.35 * valueNoise(x / 150, z / 150, 23);
		double p = Mth.clamp((n - (1 - coverage)) * 3 + near * 0.8, 0, 1);
		if(hash(i, j, 7) >= p)
			return NONE;
		return FAIR + Math.min(FAIR_SIZES - 1, (int)(p * FAIR_SIZES));
	}

	/** Whether {@code key} is storm cloud, and its depth (1 to {@link #STORM_DEPTHS}) and kind. */
	static boolean isStorm(int key) {
		return key >= STORM;
	}

	static int stormDepth(int key) {
		return (key - STORM) / 8 + 1;
	}

	static int stormKind(int key) {
		return (key - STORM) % 8;
	}

	private static int kindIndex(StormKind kind) {
		if(kind == StormKind.HURRICANE)
			return 6;
		return kind.scale() >= 0 ? kind.scale() : 7;
	}

	/** A number in [0, 1) for cell ({@code i}, {@code j}) and {@code salt}, the same every time. */
	static double hash(int i, int j, long salt) {
		long h = i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 11) * 0x1.0p-53;
	}

	/** Smooth 2D value noise in [0, 1]. */
	static double valueNoise(double x, double z, long salt) {
		int ix = Mth.floor(x), iz = Mth.floor(z);
		double fx = x - ix, fz = z - iz;
		fx = fx * fx * (3 - 2 * fx);
		fz = fz * fz * (3 - 2 * fz);
		double a = hash(ix, iz, salt), b = hash(ix + 1, iz, salt);
		double c = hash(ix, iz + 1, salt), d = hash(ix + 1, iz + 1, salt);
		return Mth.lerp(fz, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
	}

	/** Smooth 1D noise in [0, 1]. */
	private static double smoothNoise(long salt, double u) {
		return valueNoise(u, 0, salt);
	}

	private static double smoothstep(double from, double to, double x) {
		double t = Mth.clamp((x - from) / (to - from), 0, 1);
		return t * t * (3 - 2 * t);
	}
}
