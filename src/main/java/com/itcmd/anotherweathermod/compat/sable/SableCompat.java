package com.itcmd.anotherweathermod.compat.sable;

import com.itcmd.anotherweathermod.world.LevelWeather;
import com.itcmd.anotherweathermod.world.Tornadoes;
import com.itcmd.anotherweathermod.world.WindPush;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

/**
 * Sable support (https://github.com/ryanhcode/sable), safe to call whether or not Sable is installed. With Sable, the
 * weather reaches its sub-levels (airships, boats): Sable reads our wind wherever something asks for the air's speed
 * (sails, propellers), and strong wind pushes ships downwind and a tornado lifts them ({@link SableWeather}). Nothing
 * here touches a Sable class; {@link SableWeather} does, and only runs when Sable is loaded.
 */
public final class SableCompat {

	private static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("sable");
	/** Ticks between pushes: every other tick, twice as hard, as for mobs. */
	static final int EVERY = 2;
	/** Blocks per tick per tick to metres (blocks) per second per second. */
	static final double PER_TICK_SQUARED = 20 * 20;

	private SableCompat() {
	}

	public static boolean isLoaded() {
		return LOADED;
	}

	/** Gives Sable our wind. Call once, when the mod is constructed. */
	public static void init() {
		if(LOADED)
			SableWeather.register();
	}

	/** Pushes and lifts the ships in {@code level} this tick. */
	public static void tick(ServerLevel level, LevelWeather weather, long now) {
		if(LOADED && now % EVERY == 0)
			SableWeather.tick(level, weather, now);
	}

	/**
	 * How fast wind at {@code speed} metres per second speeds a ship up downwind, metres per second per second:
	 * {@code share} of what it does to a mob of ordinary size ({@link WindPush#force}). Nothing below
	 * {@link WindPush#FROM}.
	 */
	public static double shipAcceleration(double speed, double share) {
		return WindPush.force(speed) * PER_TICK_SQUARED * Math.max(0, share);
	}

	/**
	 * How fast a tornado lifts a ship, metres per second per second, at {@code closeness} (1 at the centre, 0 from twice
	 * the radius out, times its strength; see {@link Tornadoes#closeness}): {@code share} of the lift on a mob.
	 */
	public static double tornadoLift(double closeness, double share) {
		return Tornadoes.LIFT * PER_TICK_SQUARED * Math.max(0, closeness) * Math.max(0, share);
	}
}
