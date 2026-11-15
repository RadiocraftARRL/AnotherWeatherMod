package com.itcmd.anotherweathermod;

import com.itcmd.anotherweathermod.sim.StormGenerator;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** Server config (one per world, in {@code serverconfig/anotherweathermod-server.toml}). */
public final class AwmConfig {

	public static final ModConfigSpec SPEC;

	public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS;
	public static final ModConfigSpec.BooleanValue OVERWORLD_LIKE_DIMENSIONS;
	public static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_DIMENSIONS;
	public static final ModConfigSpec.IntValue FORECAST_MINUTES;
	public static final ModConfigSpec.DoubleValue PREVAILING_WIND_FROM;
	public static final ModConfigSpec.DoubleValue STORMS_PER_PLAYER_HOUR;
	public static final ModConfigSpec.DoubleValue HURRICANE_INTERVAL_DAYS;
	public static final ModConfigSpec.IntValue MAX_STORMS;
	public static final ModConfigSpec.DoubleValue SHOWER_WEIGHT;
	public static final ModConfigSpec.DoubleValue THUNDERSTORM_WEIGHT;
	public static final ModConfigSpec.DoubleValue STRONG_THUNDERSTORM_WEIGHT;
	public static final ModConfigSpec.DoubleValue SEVERE_THUNDERSTORM_WEIGHT;
	public static final ModConfigSpec.DoubleValue TORNADO_WEIGHT;
	public static final ModConfigSpec.DoubleValue FOG_PER_PLAYER_HOUR;
	public static final ModConfigSpec.DoubleValue EXTREMES_PER_PLAYER_DAY;
	public static final ModConfigSpec.DoubleValue HIGH_WIND_PER_PLAYER_DAY;
	public static final ModConfigSpec.DoubleValue WEATHER_FREQUENCY;
	public static final ModConfigSpec.BooleanValue TORNADO_DAMAGE;
	public static final ModConfigSpec.BooleanValue EXTREME_TEMPERATURE_EFFECTS;
	public static final ModConfigSpec.DoubleValue SUPERCELL_WEIGHT;
	public static final ModConfigSpec.DoubleValue LIGHTNING_MULTIPLIER;
	public static final ModConfigSpec.BooleanValue HAIL_DAMAGE;
	public static final ModConfigSpec.BooleanValue HURRICANES_WEAKEN_OVER_LAND;
	public static final ModConfigSpec.BooleanValue STORMS_FEEL_TERRAIN;
	public static final ModConfigSpec.BooleanValue SEVERE_WIND_DAMAGE;
	public static final ModConfigSpec.BooleanValue HURRICANE_WIND_DAMAGE;
	public static final ModConfigSpec.BooleanValue GUSTS_OPEN_DOORS;
	public static final ModConfigSpec.BooleanValue WIND_PUSH;
	public static final ModConfigSpec.IntValue TORNADO_SENSOR_RANGE;
	public static final ModConfigSpec.DoubleValue SABLE_SHIP_PUSH;
	public static final ModConfigSpec.DoubleValue RAIN_EASE_PER_TICK;
	public static final ModConfigSpec.BooleanValue REGROWTH;
	public static final ModConfigSpec.BooleanValue CUSTOM_CLOUDS;
	public static final ModConfigSpec.DoubleValue REGROWTH_MIN_DAYS;
	public static final ModConfigSpec.DoubleValue REGROWTH_MAX_DAYS;

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();
		StormGenerator.Settings d = StormGenerator.Settings.defaults();

		b.push("weather");
		DIMENSIONS = b.comment("Dimensions with localized weather (vanilla weather is turned off in them), whatever",
						"overworldLikeDimensions says. Only dimensions with a sky and no ceiling can have it: never the Nether",
						"or the End.")
				.defineListAllowEmpty("dimensions", List.of("minecraft:overworld"), () -> "minecraft:overworld",
						o -> o instanceof String);
		OVERWORLD_LIKE_DIMENSIONS = b.comment("Also give localized weather to every other dimension that looks like an",
						"overworld: sky light, no ceiling, not ultrawarm (so never the Nether or the End), such as a modded",
						"overworld-style world. Off: only the dimensions listed above.")
				.define("overworldLikeDimensions", true);
		EXCLUDED_DIMENSIONS = b.comment("Dimensions that never get localized weather, even if listed above or overworld-like.")
				.defineListAllowEmpty("excludedDimensions", List.of(), () -> "modid:dimension", o -> o instanceof String);
		RAIN_EASE_PER_TICK = b.comment("How fast a player's rain and thunder change, per tick (vanilla uses 0.01).")
				.defineInRange("rainEasePerTick", 0.02, 0.001, 1);
		WEATHER_FREQUENCY = b.comment("How often weather happens, on top of every other setting: 2 makes storms, fog, high",
						"wind, heat waves, cold snaps and hurricanes twice as common, 0.5 half as common, 0 turns them all off.")
				.defineInRange("weatherFrequency", 1.0, 0, 10);
		b.pop();

		b.push("storms");
		FORECAST_MINUTES = b.comment("How far ahead storms are worked out round each player, real minutes. Forecasts see this far.")
				.defineInRange("forecastMinutes", 20, 2, 120);
		PREVAILING_WIND_FROM = b.comment("The compass bearing the prevailing wind blows from in general, degrees (0 north,",
						"90 east, 180 south, 270 west). Storms come from this side. The wind drifts up to 90 degrees",
						"either way and shifts sharply about every four in-game days. Takes effect on world load.")
				.defineInRange("prevailingWindFrom", com.itcmd.anotherweathermod.sim.SteeringWind.DEFAULT_FROM, 0, 360);
		STORMS_PER_PLAYER_HOUR = b.comment("Scattered storms passing over any one place per real hour on a day with a chance",
						"of rain (none on dry days, more on rainy ones; fronts come on top). Storms form all over the world",
						"and go with the wind, never aimed at anyone. The defaults give a player standing still rain about",
						"8 % of the time (about 15 % on days with a chance of rain, 20 % on rainy days; storms keep apart), a severe thunderstorm",
						"about 2 %, hail (or a blizzard or sandstorm) about 1 %, and a hurricane about 1 %.")
				.defineInRange("stormsPerPlayerHour", d.stormsPerPlayerHour(), 0, 120);
		// The comment above and WeatherFrequencyTest also count severe thunderstorms, about 8 % of the time.
		MAX_STORMS = b.comment("Most storms kept going at once, round all the players (the nearest to someone first).",
						"A world-wide busy rainy day can have well over a hundred within sight of one player.")
				.defineInRange("maxStorms", d.maxStorms(), 0, 1024);
		SHOWER_WEIGHT = b.comment("Relative chance of each storm on the scale: 0 shower, 1 thunderstorm, 2 strong",
						"thunderstorm, 3 severe thunderstorm, 4 hailstorm (supercell), 5 tornado.")
				.defineInRange("showerWeight", d.weights()[0], 0, 100);
		THUNDERSTORM_WEIGHT = b.defineInRange("thunderstormWeight", d.weights()[1], 0, 100);
		STRONG_THUNDERSTORM_WEIGHT = b.defineInRange("strongThunderstormWeight", d.weights()[2], 0, 100);
		SEVERE_THUNDERSTORM_WEIGHT = b.defineInRange("severeThunderstormWeight", d.weights()[3], 0, 100);
		SUPERCELL_WEIGHT = b.defineInRange("supercellWeight", d.weights()[4], 0, 100);
		TORNADO_WEIGHT = b.defineInRange("tornadoWeight", d.weights()[5], 0, 100);
		FOG_PER_PLAYER_HOUR = b.comment("Banks of dense fog passing over any one place per real hour (about 3 % of the time).")
				.defineInRange("fogPerPlayerHour", d.fogPerPlayerHour(), 0, 60);
		EXTREMES_PER_PLAYER_DAY = b.comment("Heat waves (over deserts and dry ground, felt by day) or cold snaps (over",
						"snowy ground, felt at night) passing over any one place on such ground per in-game day.")
				.defineInRange("heatWavesAndColdSnapsPerPlayerDay", d.extremesPerPlayerDay(), 0, 24);
		HIGH_WIND_PER_PLAYER_DAY = b.comment("Spells of high wind (clear weather, gusts that push you about half a block)",
						"passing over any one place per in-game day, on days that are not rainy.")
				.defineInRange("highWindPerPlayerDay", d.highWindPerPlayerDay(), 0, 24);
		HURRICANE_INTERVAL_DAYS = b.comment("Mean in-game days between hurricanes passing over a place by the ocean (they form",
						"only over the ocean and wander off with the wind, so inland places see fewer). 0 turns them off.")
				.defineInRange("hurricaneIntervalDays", d.hurricaneInterval() / 24000, 0, 1000);
		HURRICANES_WEAKEN_OVER_LAND = b.comment("Hurricanes lose strength over land (about half in half an in-game day)",
						"and slowly regain it over the ocean, so they hit the coast hardest. Off: they keep their",
						"strength wherever they go.")
				.define("hurricanesWeakenOverLand", false);
		STORMS_FEEL_TERRAIN = b.comment("Storms grow a little over the ocean and shrink a little over desert, easing into it over",
						"half a day or so as they cross a coast. Off: their size follows only their life and a slow swell.")
				.define("stormsFeelTerrain", true);
		b.pop();

		b.push("effects");
		LIGHTNING_MULTIPLIER = b.comment("Lightning under a storm, compared with vanilla's chance while thundering.")
				.defineInRange("lightningMultiplier", 3.0, 0, 100);
		HAIL_DAMAGE = b.comment("Hail hurts players and mobs out in it, and breaks crops and glass panes now and then.")
				.define("hailDamage", true);
		SEVERE_WIND_DAMAGE = b.comment("Severe thunderstorms and supercells strip some leaves in their strongest gusts,",
						"far fewer than a hurricane. Plain thunderstorms never blow hard enough to damage anything.")
				.define("severeStormWindDamage", true);
		HURRICANE_WIND_DAMAGE = b.comment("Hurricanes damage blocks. The doWindDamage game rule turns both off.")
				.define("hurricaneWindDamage", true);
		TORNADO_DAMAGE = b.comment("Tornadoes tear up what they cross: leaves, crops, glass, fences, scoured ground and logs.",
						"Trees and scoured ground grow back like other wind damage. The doWindDamage game rule turns it off.")
				.define("tornadoDamage", true);
		EXTREME_TEMPERATURE_EFFECTS = b.comment("Out in a heat wave's sun you get hungry faster and, after a minute or",
						"more, overheat (slowed, weakened, then hurt; shade, water, rain or a drink of water cool you). Out",
						"in a cold snap or a blizzard you freeze (leather armor or a fire, campfire, lava or magma nearby",
						"keeps you warm). Heat waves also slow crops on dry, unwatered farmland.")
				.define("heatAndColdEffects", true);
		GUSTS_OPEN_DOORS = b.comment("Gusts in severe thunderstorms, supercells and hurricanes blow open wooden doors",
						"on outside walls near players (the anotherweathermod:wind_damage/blows_open tag).")
				.define("gustsOpenDoors", true);
		WIND_PUSH = b.comment("Strong wind pushes mobs, animals and items downwind (really big ones stand firm; gold",
						"armor weighs its wearer down). Players are pushed by their own client's windPush setting.")
				.define("windPushesEntities", true);
		SABLE_SHIP_PUSH = b.comment("With Sable installed: how hard strong wind pushes airships and boats downwind, and a",
						"tornado lifts them, as a share of what it does to a mob (0 turns it off). Sable also reads the wind for",
						"anything that asks it, such as sails and propellers.")
				.defineInRange("sableShipPush", 0.25, 0, 4);
		TORNADO_SENSOR_RANGE = b.comment("Tornado acoustic sensors hear a tornado down within this many blocks (or one that",
						"will be within two minutes).")
				.defineInRange("tornadoSensorRange", 320, 16, 4096);
		REGROWTH = b.comment("Leaves and plants the wind tore away grow back, and scoured ground grasses over. Glass,",
						"fences, torches and other built things do not come back.")
				.define("regrowth", true);
		REGROWTH_MIN_DAYS = b.comment("Soonest a damaged block grows back, in-game days.")
				.defineInRange("regrowthMinDays", 1.0, 0, 100);
		REGROWTH_MAX_DAYS = b.comment("Latest a damaged block grows back, in-game days.")
				.defineInRange("regrowthMaxDays", 3.0, 0, 100);
		b.pop();

		b.push("visuals");
		CUSTOM_CLOUDS = b.comment("Let players draw the mod's 3D clouds (each can still turn them off). Off: everyone gets",
						"vanilla's clouds, for servers that want a lighter or more vanilla look.")
				.define("allowCustomClouds", true);
		b.pop();

		SPEC = b.build();
	}

	private AwmConfig() {
	}

	public static StormGenerator.Settings generatorSettings() {
		StormGenerator.Settings d = StormGenerator.Settings.defaults();
		return new StormGenerator.Settings(FORECAST_MINUTES.get() * 1200L, STORMS_PER_PLAYER_HOUR.get(),
				HURRICANE_INTERVAL_DAYS.get() * 24000, MAX_STORMS.get(),
				new double[] { SHOWER_WEIGHT.get(), THUNDERSTORM_WEIGHT.get(), STRONG_THUNDERSTORM_WEIGHT.get(),
						SEVERE_THUNDERSTORM_WEIGHT.get(), SUPERCELL_WEIGHT.get(), TORNADO_WEIGHT.get() },
				FOG_PER_PLAYER_HOUR.get(), EXTREMES_PER_PLAYER_DAY.get(), HIGH_WIND_PER_PLAYER_DAY.get())
				.scaled(WEATHER_FREQUENCY.get());
	}
}
