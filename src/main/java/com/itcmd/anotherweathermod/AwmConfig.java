package com.itcmd.anotherweathermod;

import com.itcmd.anotherweathermod.sim.StormGenerator;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** Server config (one per world, in {@code serverconfig/anotherweathermod-server.toml}). */
public final class AwmConfig {

	public static final ModConfigSpec SPEC;

	public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS;
	public static final ModConfigSpec.IntValue FORECAST_MINUTES;
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
	public static final ModConfigSpec.BooleanValue TORNADO_DAMAGE;
	public static final ModConfigSpec.BooleanValue EXTREME_TEMPERATURE_EFFECTS;
	public static final ModConfigSpec.DoubleValue SUPERCELL_WEIGHT;
	public static final ModConfigSpec.DoubleValue LIGHTNING_MULTIPLIER;
	public static final ModConfigSpec.BooleanValue HAIL_DAMAGE;
	public static final ModConfigSpec.BooleanValue HURRICANES_WEAKEN_OVER_LAND;
	public static final ModConfigSpec.BooleanValue SEVERE_WIND_DAMAGE;
	public static final ModConfigSpec.BooleanValue HURRICANE_WIND_DAMAGE;
	public static final ModConfigSpec.BooleanValue GUSTS_OPEN_DOORS;
	public static final ModConfigSpec.BooleanValue WIND_PUSH;
	public static final ModConfigSpec.DoubleValue RAIN_EASE_PER_TICK;
	public static final ModConfigSpec.BooleanValue REGROWTH;
	public static final ModConfigSpec.BooleanValue CUSTOM_CLOUDS;
	public static final ModConfigSpec.DoubleValue REGROWTH_MIN_DAYS;
	public static final ModConfigSpec.DoubleValue REGROWTH_MAX_DAYS;

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();
		StormGenerator.Settings d = StormGenerator.Settings.defaults();

		b.push("weather");
		DIMENSIONS = b.comment("Dimensions with localized weather. Vanilla weather is turned off in them.")
				.defineListAllowEmpty("dimensions", List.of("minecraft:overworld"), () -> "minecraft:overworld",
						o -> o instanceof String);
		RAIN_EASE_PER_TICK = b.comment("How fast a player's rain and thunder change, per tick (vanilla uses 0.01).")
				.defineInRange("rainEasePerTick", 0.02, 0.001, 1);
		b.pop();

		b.push("storms");
		FORECAST_MINUTES = b.comment("How far ahead storms are decided, real minutes. Forecasts see this far.")
				.defineInRange("forecastMinutes", 20, 2, 120);
		STORMS_PER_PLAYER_HOUR = b.comment("Storms aimed at each player per real hour. Some miss. The defaults give a",
						"player standing still rain about 30 % of the time: a severe thunderstorm about 8 %, hail (or",
						"a blizzard or sandstorm) about 5 %, and a hurricane about 1 %.")
				.defineInRange("stormsPerPlayerHour", d.stormsPerPlayerHour(), 0, 120);
		// The comment above and WeatherFrequencyTest also count severe thunderstorms, about 8 % of the time.
		MAX_STORMS = b.comment("Most storms alive or forecast at once.")
				.defineInRange("maxStorms", d.maxStorms(), 0, 256);
		SHOWER_WEIGHT = b.comment("Relative chance of each storm on the scale: 0 shower, 1 thunderstorm, 2 strong",
						"thunderstorm, 3 severe thunderstorm, 4 hailstorm (supercell), 5 tornado.")
				.defineInRange("showerWeight", d.weights()[0], 0, 100);
		THUNDERSTORM_WEIGHT = b.defineInRange("thunderstormWeight", d.weights()[1], 0, 100);
		STRONG_THUNDERSTORM_WEIGHT = b.defineInRange("strongThunderstormWeight", d.weights()[2], 0, 100);
		SEVERE_THUNDERSTORM_WEIGHT = b.defineInRange("severeThunderstormWeight", d.weights()[3], 0, 100);
		SUPERCELL_WEIGHT = b.defineInRange("supercellWeight", d.weights()[4], 0, 100);
		TORNADO_WEIGHT = b.defineInRange("tornadoWeight", d.weights()[5], 0, 100);
		FOG_PER_PLAYER_HOUR = b.comment("Banks of dense fog aimed at each player per real hour (about 3 % of the time).")
				.defineInRange("fogPerPlayerHour", d.fogPerPlayerHour(), 0, 60);
		EXTREMES_PER_PLAYER_DAY = b.comment("Heat waves (over deserts and dry ground, felt by day) or cold snaps (over",
						"snowy ground, felt at night) aimed at each player per in-game day, when they are on such ground.")
				.defineInRange("heatWavesAndColdSnapsPerPlayerDay", d.extremesPerPlayerDay(), 0, 24);
		HURRICANE_INTERVAL_DAYS = b.comment("Mean in-game days between hurricanes. 0 turns them off.")
				.defineInRange("hurricaneIntervalDays", d.hurricaneInterval() / 24000, 0, 1000);
		HURRICANES_WEAKEN_OVER_LAND = b.comment("Hurricanes lose strength over land (about half in half an in-game day)",
						"and slowly regain it over the ocean, so they hit the coast hardest. Off: they keep their",
						"strength wherever they go.")
				.define("hurricanesWeakenOverLand", false);
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
		return new StormGenerator.Settings(d.slotTicks(), FORECAST_MINUTES.get() * 1200L, STORMS_PER_PLAYER_HOUR.get(),
				HURRICANE_INTERVAL_DAYS.get() * 24000, MAX_STORMS.get(),
				new double[] { SHOWER_WEIGHT.get(), THUNDERSTORM_WEIGHT.get(), STRONG_THUNDERSTORM_WEIGHT.get(),
						SEVERE_THUNDERSTORM_WEIGHT.get(), SUPERCELL_WEIGHT.get(), TORNADO_WEIGHT.get() },
				FOG_PER_PLAYER_HOUR.get(), EXTREMES_PER_PLAYER_DAY.get());
	}
}
