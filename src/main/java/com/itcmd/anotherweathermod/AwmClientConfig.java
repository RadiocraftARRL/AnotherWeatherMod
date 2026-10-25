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

	public static final ModConfigSpec SPEC;

	public static final ModConfigSpec.EnumValue<Quality> QUALITY;
	public static final ModConfigSpec.BooleanValue STORM_CLOUDS;
	public static final ModConfigSpec.BooleanValue RAIN_WALLS;
	public static final ModConfigSpec.BooleanValue HAIL;
	public static final ModConfigSpec.BooleanValue WIND_STREAKS;
	public static final ModConfigSpec.BooleanValue WIND_DEBRIS;
	public static final ModConfigSpec.BooleanValue WIND_SOUND;
	public static final ModConfigSpec.BooleanValue STORM_FOG;

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
		STORM_FOG = b.comment("Thick tinted fog in sandstorms and blizzards. Off: they look like ordinary rain and snow.")
				.define("stormFog", true);
		b.pop();
		SPEC = b.build();
	}

	private AwmClientConfig() {
	}
}
