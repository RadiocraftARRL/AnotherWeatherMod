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
	public static final ModConfigSpec.DoubleValue SUPERCELL_WEIGHT;
	public static final ModConfigSpec.DoubleValue LIGHTNING_MULTIPLIER;
	public static final ModConfigSpec.BooleanValue HAIL_DAMAGE;
	public static final ModConfigSpec.BooleanValue STORM_WIND_DAMAGE;
	public static final ModConfigSpec.BooleanValue HURRICANE_WIND_DAMAGE;
	public static final ModConfigSpec.DoubleValue RAIN_EASE_PER_TICK;
	public static final ModConfigSpec.BooleanValue REGROWTH;
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
						"player standing still rain about 30 % of the time, hail or damaging wind about 5 %, and a",
						"hurricane about 1 %.")
				.defineInRange("stormsPerPlayerHour", d.stormsPerPlayerHour(), 0, 120);
		MAX_STORMS = b.comment("Most storms alive or forecast at once.")
				.defineInRange("maxStorms", d.maxStorms(), 0, 256);
		SHOWER_WEIGHT = b.comment("Relative chance of each kind of storm.")
				.defineInRange("showerWeight", d.weights()[0], 0, 100);
		THUNDERSTORM_WEIGHT = b.defineInRange("thunderstormWeight", d.weights()[1], 0, 100);
		SUPERCELL_WEIGHT = b.defineInRange("supercellWeight", d.weights()[2], 0, 100);
		HURRICANE_INTERVAL_DAYS = b.comment("Mean in-game days between hurricanes. 0 turns them off.")
				.defineInRange("hurricaneIntervalDays", d.hurricaneInterval() / 24000, 0, 1000);
		b.pop();

		b.push("effects");
		LIGHTNING_MULTIPLIER = b.comment("Lightning under a storm, compared with vanilla's chance while thundering.")
				.defineInRange("lightningMultiplier", 3.0, 0, 100);
		HAIL_DAMAGE = b.comment("Hail hurts players and mobs out in it, and breaks crops and glass panes now and then.")
				.define("hailDamage", true);
		STORM_WIND_DAMAGE = b.comment("Thunderstorms and supercells damage blocks in strong gusts.")
				.define("stormWindDamage", false);
		HURRICANE_WIND_DAMAGE = b.comment("Hurricanes damage blocks. The doWindDamage game rule turns both off.")
				.define("hurricaneWindDamage", true);
		REGROWTH = b.comment("Leaves and plants the wind tore away grow back, and scoured ground grasses over. Glass,",
						"fences, torches and other built things do not come back.")
				.define("regrowth", true);
		REGROWTH_MIN_DAYS = b.comment("Soonest a damaged block grows back, in-game days.")
				.defineInRange("regrowthMinDays", 1.0, 0, 100);
		REGROWTH_MAX_DAYS = b.comment("Latest a damaged block grows back, in-game days.")
				.defineInRange("regrowthMaxDays", 3.0, 0, 100);
		b.pop();

		SPEC = b.build();
	}

	private AwmConfig() {
	}

	public static StormGenerator.Settings generatorSettings() {
		StormGenerator.Settings d = StormGenerator.Settings.defaults();
		return new StormGenerator.Settings(d.slotTicks(), FORECAST_MINUTES.get() * 1200L, STORMS_PER_PLAYER_HOUR.get(),
				HURRICANE_INTERVAL_DAYS.get() * 24000, MAX_STORMS.get(),
				new double[] { SHOWER_WEIGHT.get(), THUNDERSTORM_WEIGHT.get(), SUPERCELL_WEIGHT.get() });
	}
}
