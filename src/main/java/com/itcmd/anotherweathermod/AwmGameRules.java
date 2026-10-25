package com.itcmd.anotherweathermod;

import net.minecraft.world.level.GameRules;

/** The mod's game rules. */
public final class AwmGameRules {

	/** Whether wind can damage blocks at all (the config picks which storms do). */
	public static final GameRules.Key<GameRules.BooleanValue> DO_WIND_DAMAGE = GameRules.register("doWindDamage",
			GameRules.Category.UPDATES, GameRules.BooleanValue.create(true));

	private AwmGameRules() {
	}

	/** Loads the class, which registers the rules. Call from the mod constructor. */
	static void init() {
	}
}
