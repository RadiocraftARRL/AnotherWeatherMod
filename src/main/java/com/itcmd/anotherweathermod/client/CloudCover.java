package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.api.DayType;
import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.network.LocalWeatherPayload;
import com.itcmd.anotherweathermod.network.NearbyStormsPayload;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Which clouds are where, at one moment: the sky is cut into {@link #CELL}-block cells (in cloud space, which drifts
 * east), and each cell holds a fair-weather cumulus of some size, a stretch of storm cloud of some depth and darkness,
 * or nothing. Fair-weather cover comes from slow noise, patchy and thicker some days than others, and swells round
 * storms; storms fill their cells solid, a hurricane leaving its eye clear, and their cloud leads their rain
 * ({@link #LEAD}). A cell's {@link #cell(int, int) key} only
 * changes in steps, so its cloud is rebuilt only when it visibly changes.
 */
final class CloudCover {

	/** Size of a cloud cell, blocks. */
	static final double CELL = 32;
	/**
	 * Keys: no cloud; a fair-weather cumulus is {@code FAIR + size}; storm cloud is
	 * {@code STORM + ((depth - 1) * 8 + kind) * 4 + precipitation}, the last 0 rain, 1 snow, 2 hail, 3 dust.
	 */
	static final int NONE = -1;
	static final int FAIR = 0;
	static final int FAIR_SIZES = 4;
	static final int STORM = 16;
	static final int STORM_DEPTHS = 4;

	/** How dark storm cloud is by {@link #stormKind(int) kind}: shower, scale 1 to 5, hurricane, other. */
	static final float[] DARKNESS = { 0.62f, 0.48f, 0.42f, 0.36f, 0.33f, 0.28f, 0.38f, 0.55f };
	/**
	 * Storm cloud's colour by what it brings down (rain, snow, hail, dust): how far its shade is lifted toward white,
	 * then a red, green and blue tint. Snow decks are a light grey-white, whatever the storm (a snowstorm's sky is
	 * pale, not a thunderstorm's slate); hail clouds take the green cast of a hail core, dust clouds are tan.
	 */
	static final float[][] PRECIPITATION_TINT = { { 0f, 1f, 1f, 1.03f }, { 0.8f, 0.97f, 0.99f, 1.04f },
			{ 0f, 0.86f, 1f, 0.9f }, { 0.25f, 1.14f, 0.96f, 0.74f } };
	/**
	 * Storm cloud runs out ahead of the rain, as an anvil spreads downwind: its middle is this share of the radius
	 * ahead of the storm's (along its track), and it reaches {@link #REACH} radii from there. So the deck builds
	 * overhead about half a radius before the rain arrives, is at its deepest by the rain's front, and thins out over
	 * the back of the rain as it clears.
	 */
	static final double LEAD = 0.25;
	static final double REACH = 1.35;

	/**
	 * However thick the storm murk or fog, cloud this many times its height above the camera, plus
	 * {@link #OVERHEAD_MARGIN}, is still drawn: under a storm its deck is always seen overhead, closing into the murk
	 * toward the horizon, instead of fading out with everything else and leaving a bare, featureless sky.
	 */
	static final float OVERHEAD_REACH = 1.6f;
	static final float OVERHEAD_MARGIN = 32;

	/** How far off clouds fade out, from the weather's {@code fadeEnd}, never less than reaches the deck overhead. */
	static float fadeEnd(float fadeEnd, double heightAbove, double range) {
		float overhead = (float)(Math.abs(heightAbove) * OVERHEAD_REACH + OVERHEAD_MARGIN);
		return (float)Math.min(range, Math.max(fadeEnd, overhead));
	}

	/** How tall storm cloud grows by kind, as a share of the tallest. */
	static final float[] TALL = { 0.3f, 0.6f, 0.75f, 0.9f, 1f, 1f, 0.8f, 0.4f };

	/**
	 * What the ground under a point would bring down, as a precipitation index (0 rain, 1 snow), or -1 if unknown (not
	 * loaded, or dry ground). A storm's own precipitation is what falls under its middle; its cloud over snowy ground
	 * is snow cloud, and over rain ground rain cloud, so a deck pales where it reaches snow country.
	 */
	@FunctionalInterface
	interface Ground {
		Ground UNKNOWN = (x, z) -> -1;

		int precipitation(double x, double z);
	}

	/** The precipitation index a storm bringing {@code storm} shows over ground that brings {@code ground}. */
	static int localPrecipitation(int storm, int ground) {
		if(storm == 3 || storm == 2 && ground != 1) // Dust stays dust; hail stays hail except over snow (a blizzard).
			return storm;
		return ground == 0 || ground == 1 ? ground : storm;
	}

	private final List<NearbyStormsPayload.Storm> storms;
	private final Ground ground;
	private final double age;
	private final double driftX;
	/** How cloudy fair weather is today, 0 to 1. */
	private final double coverage;
	/**
	 * Rain with no storm behind it: vanilla's {@code /weather rain} override, which rains on everyone everywhere. A
	 * shower's deck then covers the whole sky, so it never rains out of a blue sky (seen 2026-10-27).
	 */
	private final boolean overcast;
	/** The sort of day overhead: a dry day has only a few small clouds, a rainy one (or a region's edge) a grey deck. */
	private final Sky sky;

	/** What the day does to the sky where the player is (see {@code DayType}). */
	enum Sky {
		/** Fair-weather clouds come and go as they always have. */
		USUAL,
		/** A dry day: mostly clear, a few puffy clouds. */
		FEW,
		/** A rainy day, or where two regions meet: a thin grey deck all over, under any storms. */
		GREY;

		static Sky of(LocalWeatherPayload local) {
			if(local.regionEdge() || local.dayType() == DayType.RAINY)
				return GREY;
			return local.dayType() == DayType.DRY ? FEW : USUAL;
		}
	}

	CloudCover(NearbyStormsPayload payload, long gameTime, double driftX) {
		this(payload, gameTime, driftX, Ground.UNKNOWN, false);
	}

	CloudCover(NearbyStormsPayload payload, long gameTime, double driftX, Ground ground) {
		this(payload, gameTime, driftX, ground, false);
	}

	CloudCover(NearbyStormsPayload payload, long gameTime, double driftX, Ground ground, boolean overcast) {
		this(payload, gameTime, driftX, ground, overcast, Sky.USUAL);
	}

	CloudCover(NearbyStormsPayload payload, long gameTime, double driftX, Ground ground, boolean overcast, Sky sky) {
		this.storms = payload.storms();
		this.ground = ground;
		this.age = gameTime - payload.gameTime();
		this.driftX = driftX;
		double noise = smoothNoise(0x5EED, gameTime / 30000.0);
		this.coverage = sky == Sky.FEW ? 0.12 + 0.1 * noise : 0.3 + 0.35 * noise;
		this.overcast = overcast;
		this.sky = sky;
	}

	/** True if the local weather is rain that no storm accounts for (see {@link #overcast}). */
	public static boolean overcast(LocalWeatherPayload local) {
		return local.rain() > 0.05f && local.stormKind() == null;
	}

	/** The key of cloud-space cell ({@code i}, {@code j}). */
	int cell(int i, int j) {
		double x = (i + 0.5) * CELL + driftX, z = (j + 0.5) * CELL;
		double storm = 0, near = 0;
		int kind = 0, precipitation = 0;
		for(NearbyStormsPayload.Storm s : storms) {
			if(!s.kind().bringsRain())
				continue;
			double sx = s.x() + s.velocityX() * age, sz = s.z() + s.velocityZ() * age;
			double r = Math.max(1, s.radius());
			double speed = Math.hypot(s.velocityX(), s.velocityZ());
			// A hurricane's cloud keeps to its eye; a still storm has nowhere to lead.
			double lead = s.kind() == StormKind.HURRICANE || speed < 1e-4 ? 0 : LEAD * r / speed;
			double cx = sx + s.velocityX() * lead, cz = sz + s.velocityZ() * lead;
			double d = Math.hypot(x - cx, z - cz) / r;
			double c = presence(s.intensity()) * (1 - smoothstep(0.75, REACH, d));
			if(s.kind() == StormKind.HURRICANE)
				c *= smoothstep(0.05, 0.13, d); // The clear eye.
			near = Math.max(near, s.intensity() * (1 - smoothstep(REACH, REACH + 1.1, d)));
			if(c > storm) {
				storm = c;
				kind = kindIndex(s.kind());
				precipitation = precipitationIndex(s.precipitation());
			}
		}
		int depth = (int)Math.round(storm * STORM_DEPTHS);
		if(depth == 0 && overcast) { // Rain from nowhere (vanilla's override): a shower's deck over everything.
			depth = 2;
			kind = kindIndex(StormKind.SHOWER);
			precipitation = 0;
		}
		else if(depth == 0 && sky == Sky.GREY) { // A rainy day's grey: the thinnest shower deck, everywhere.
			depth = 1;
			kind = kindIndex(StormKind.SHOWER);
			precipitation = 0;
		}
		if(depth > 0)
			return STORM + ((Math.min(depth, STORM_DEPTHS) - 1) * 8 + kind) * 4
					+ localPrecipitation(precipitation, ground.precipitation(x, z));
		// Fair weather: patches of cumulus where the noise is high, more of them near a storm.
		double n = 0.65 * valueNoise(x / 420, z / 420, 11) + 0.35 * valueNoise(x / 150, z / 150, 23);
		double p = Mth.clamp((n - (1 - coverage)) * 3 + near * 0.8, 0, 1);
		if(hash(i, j, 7) >= p)
			return NONE;
		return FAIR + Math.min(FAIR_SIZES - 1, (int)(p * FAIR_SIZES));
	}

	/**
	 * How much storm cloud a storm of {@code intensity} has: a forming storm already has a low, dark deck that builds
	 * as it strengthens, so a storm coming in is seen coming instead of appearing overhead.
	 */
	static double presence(float intensity) {
		return intensity > 0 ? 0.3 + 0.7 * intensity : 0;
	}

	/** Whether {@code key} is storm cloud, and its depth (1 to {@link #STORM_DEPTHS}) and kind. */
	static boolean isStorm(int key) {
		return key >= STORM;
	}

	static int stormDepth(int key) {
		return (key - STORM) / 32 + 1;
	}

	static int stormKind(int key) {
		return (key - STORM) / 4 % 8;
	}

	/** What {@code key}'s storm cloud brings down: 0 rain, 1 snow, 2 hail, 3 dust (see {@link #PRECIPITATION_TINT}). */
	static int stormPrecipitation(int key) {
		return (key - STORM) % 4;
	}

	static int precipitationIndex(Precipitation precipitation) {
		return switch(precipitation) {
			case SNOW -> 1;
			case HAIL -> 2;
			case DUST -> 3;
			default -> 0;
		};
	}

	static int kindIndex(StormKind kind) {
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
