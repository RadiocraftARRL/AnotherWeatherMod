package com.itcmd.anotherweathermod;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Graphics settings (one per player, in {@code config/anotherweathermod-client.toml}, and on the mod's config screen).
 * They change only what this client draws and plays; the weather itself is the server's.
 */
public final class AwmClientConfig {

	/** How many weather particles to draw, on top of vanilla's Particles setting. */
	public enum Quality {
		LOW(0.3f), MEDIUM(0.6f), HIGH(1f), ULTRA(1.6f);

		private final float particles;

		Quality(float particles) {
			this.particles = particles;
		}

		/** Particles compared with {@link #HIGH}. */
		public float particles() {
			return particles;
		}
	}

	/**
	 * How detailed the mod's clouds are: how far they reach, how round they are and how many puffs make one up. LOW is
	 * plain low-poly clouds. Vanilla's Clouds setting still applies on top: Fast means LOW, Off means none.
	 */
	public enum CloudDetail {
		LOW(448, 2, 5, 2, 2, 0),
		MEDIUM(576, 3, 7, 3, 3, 0.1),
		HIGH(704, 4, 9, 5, 4, 0.16),
		ULTRA(896, 5, 12, 7, 6, 0.2);

		private final double range;
		private final int rings;
		private final int segments;
		private final int fairPuffs;
		private final int stormPuffs;
		private final double lumps;

		CloudDetail(double range, int rings, int segments, int fairPuffs, int stormPuffs, double lumps) {
			this.range = range;
			this.rings = rings;
			this.segments = segments;
			this.fairPuffs = fairPuffs;
			this.stormPuffs = stormPuffs;
			this.lumps = lumps;
		}

		/** How far off clouds are drawn, blocks. */
		public double range() {
			return range;
		}

		/** Rings of latitude above a puff's flat base, and segments round it. */
		public int rings() {
			return rings;
		}

		public int segments() {
			return segments;
		}

		/** Puffs in a fair-weather cloud, and in each cell of storm cloud. */
		public int fairPuffs() {
			return fairPuffs;
		}

		public int stormPuffs() {
			return stormPuffs;
		}

		/** How lumpy a puff's surface is. */
		public double lumps() {
			return lumps;
		}
	}

	public static final ModConfigSpec SPEC;

	public static final ModConfigSpec.EnumValue<Quality> QUALITY;
	public static final ModConfigSpec.BooleanValue STORM_CLOUDS;
	public static final ModConfigSpec.BooleanValue RAIN_WALLS;
	public static final ModConfigSpec.BooleanValue HAIL;
	public static final ModConfigSpec.BooleanValue WIND_STREAKS;
	public static final ModConfigSpec.BooleanValue WIND_DEBRIS;
	public static final ModConfigSpec.BooleanValue WIND_SOUND;
	public static final ModConfigSpec.BooleanValue STORM_FOG;
	public static final ModConfigSpec.BooleanValue STORM_RAIN;
	public static final ModConfigSpec.BooleanValue PUDDLES;
	public static final ModConfigSpec.BooleanValue WIND_PUSH;
	public static final ModConfigSpec.BooleanValue MIST;
	public static final ModConfigSpec.BooleanValue TORNADO_FUNNELS;
	public static final ModConfigSpec.BooleanValue WHIRLS;
	public static final ModConfigSpec.BooleanValue RAINBOWS;
	public static final ModConfigSpec.BooleanValue CUSTOM_CLOUDS;
	public static final ModConfigSpec.EnumValue<CloudDetail> CLOUD_DETAIL;
	public static final ModConfigSpec.BooleanValue CLOUDS_WITH_SHADER_PACKS;
	public static final ModConfigSpec.BooleanValue STORM_RAIN_WITH_SHADER_PACKS;
	public static final ModConfigSpec.DoubleValue SHADER_RAIN_OPACITY;
	public static final ModConfigSpec.BooleanValue FOG_WITH_SHADER_PACKS;
	public static final ModConfigSpec.DoubleValue STORM_DARKNESS;
	public static final ModConfigSpec.BooleanValue STORMS_AS_THUNDER;
	public static final ModConfigSpec.DoubleValue CLOUD_BRIGHTNESS;
	public static final ModConfigSpec.DoubleValue RAIN_BRIGHTNESS;
	public static final ModConfigSpec.DoubleValue HAIL_BRIGHTNESS;
	public static final ModConfigSpec.DoubleValue WIND_BRIGHTNESS;
	public static final ModConfigSpec.DoubleValue TORNADO_BRIGHTNESS;

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();
		b.push("graphics");
		QUALITY = b.comment("How many weather particles (hail, air streaks, blown sand, leaves): LOW, MEDIUM, HIGH or",
						"ULTRA. Vanilla's Particles setting still applies on top: Minimal turns them off.")
				.defineEnum("particleQuality", Quality.HIGH);
		STORM_CLOUDS = b.comment("A dark cloud deck under the clouds over each storm.")
				.define("stormClouds", true);
		RAIN_WALLS = b.comment("Grey curtains of rain hanging from the edges of storms in the distance.")
				.define("rainWalls", true);
		HAIL = b.comment("Hailstones bouncing round you in hail.")
				.define("hailParticles", true);
		WIND_STREAKS = b.comment("Faint streaks of air in strong wind, and blown sand in sandstorms.")
				.define("windStreaks", true);
		WIND_DEBRIS = b.comment("Leaves torn off and blown past in gales.")
				.define("windDebris", true);
		WIND_SOUND = b.comment("The rushing sound of strong wind.")
				.define("windSound", true);
		STORM_FOG = b.comment("Thick tinted fog in sandstorms and blizzards, and rain cutting the view in severe storms",
						"and hail (most in hurricanes, more at night). Off: they look like ordinary weather.")
				.define("stormFog", true);
		STORM_RAIN = b.comment("In severe thunderstorms, supercells and hurricanes, darker rain driven sideways by the wind.",
						"Off: vanilla's rain.")
				.define("stormRain", true);
		PUDDLES = b.comment("Puddles on open ground in the rain, drying up a few minutes after it stops.")
				.define("puddles", true);
		MIST = b.comment("Light mist in the early morning and after rain, with water dripping from the leaves.")
				.define("mistAndDrips", true);
		TORNADO_FUNNELS = b.comment("Tornado funnels and the debris whirling round their base.")
				.define("tornadoFunnels", true);
		WHIRLS = b.comment("Waterspouts under thunderstorms over open water, and dust devils over hot, dry ground on clear",
						"days. Harmless; for looks only.")
				.define("waterspoutsAndDustDevils", true);
		RAINBOWS = b.comment("Now and then a rainbow when rain clears with the sun low behind you.")
				.define("rainbows", true);
		CUSTOM_CLOUDS = b.comment("The mod's own 3D clouds in place of vanilla's: puffy cumulus in fair weather, a thick dark",
						"deck over storms, a clear eye in a hurricane. Off (or if the server turns them off): vanilla's",
						"clouds, fading out under severe storms.")
				.define("customClouds", true);
		CLOUD_DETAIL = b.comment("How detailed the mod's clouds are, and how far they reach: LOW (plain low-poly), MEDIUM, HIGH",
						"or ULTRA. Vanilla's Clouds setting applies on top: Fast means LOW, Off means no clouds.")
				.defineEnum("cloudDetail", CloudDetail.HIGH);
		b.pop();
		b.push("shaderCompatibility");
		CLOUDS_WITH_SHADER_PACKS = b.comment("With an Iris or Oculus shader pack on, still draw the mod's clouds. Off: the pack's.")
				.define("customCloudsWithShaderPacks", true);
		STORM_RAIN_WITH_SHADER_PACKS = b.comment("With a shader pack on, still draw the mod's sideways storm rain. Off: the pack's",
						"own rain (some packs draw rain their own way and clash).")
				.define("stormRainWithShaderPacks", true);
		SHADER_RAIN_OPACITY = b.comment("With a shader pack on, how much thicker to draw the storm rain: many packs draw rain",
						"fainter than vanilla does. 1 as without a pack.")
				.defineInRange("shaderRainOpacity", 1.6, 1, 3);
		FOG_WITH_SHADER_PACKS = b.comment("With a shader pack on, draw storm and fog-bank murk as layers of fog round you: many",
						"packs draw their own fog and ignore the weather's, so storms would look clear.")
				.define("fogWithShaderPacks", true);
		STORM_DARKNESS = b.comment("Darken the sky, clouds and world light under severe storms and hurricanes like nightfall:",
						"0 none, 1 as dark as night. For shader packs (or tastes) where storms look too bright.")
				.defineInRange("stormDarkness", 0.0, 0, 1);
		STORMS_AS_THUNDER = b.comment("Tell the game (and so shader packs) it is thundering at full strength under severe storms",
						"and hurricanes, so packs that darken and wet the world for thunder do so.")
				.define("stormsAsThunder", false);
		CLOUD_BRIGHTNESS = b.comment("Brightness of the mod's clouds and storm decks (1 as designed).")
				.defineInRange("cloudBrightness", 1.0, 0.2, 2);
		RAIN_BRIGHTNESS = b.comment("Brightness of the mod's storm rain and rain curtains.")
				.defineInRange("rainBrightness", 1.0, 0.2, 2);
		HAIL_BRIGHTNESS = b.comment("Brightness of hailstones.")
				.defineInRange("hailBrightness", 1.0, 0.2, 2);
		WIND_BRIGHTNESS = b.comment("Brightness of wind streaks, gust wisps and blown debris.")
				.defineInRange("windBrightness", 1.0, 0.2, 2);
		TORNADO_BRIGHTNESS = b.comment("Brightness of tornado funnels and their debris.")
				.defineInRange("tornadoBrightness", 1.0, 0.2, 2);
		b.pop();
		b.push("gameplay");
		WIND_PUSH = b.comment("Strong wind (severe gusts, hurricanes) pushes you downwind when you are out in it.")
				.define("windPush", true);
		b.pop();
		SPEC = b.build();
	}

	private AwmClientConfig() {
	}

	/** {@code value} times a brightness setting, kept to 1. */
	public static float bright(float value, ModConfigSpec.DoubleValue brightness) {
		return Math.min(1, value * brightness.get().floatValue());
	}
}
