package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.api.event.WindDamageEvent.DamageClass;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Block tags that decide what weather does to blocks. Packs can change them. */
public final class AwmTags {

	public static final TagKey<Block> WIND_FRAGILE = tag("wind_damage/fragile");
	public static final TagKey<Block> WIND_FOLIAGE = tag("wind_damage/foliage");
	public static final TagKey<Block> WIND_LOOSE = tag("wind_damage/loose");
	public static final TagKey<Block> WIND_SCOUR = tag("wind_damage/scour");
	public static final TagKey<Block> WIND_BLOWS_OPEN = tag("wind_damage/blows_open");
	public static final TagKey<Block> WIND_HEAVY = tag("wind_damage/heavy");
	/** Fences, gates, trapdoors: {@link DamageClass#STURDY} (2026-11-13). */
	public static final TagKey<Block> WIND_STURDY = tag("wind_damage/sturdy");
	/** Planks and other light building blocks: {@link DamageClass#STRUCTURE} (2026-11-13). */
	public static final TagKey<Block> WIND_STRUCTURE = tag("wind_damage/structure");
	/** Sirens and mounted antennas: {@link DamageClass#RESILIENT} (2026-11-13; RadioCraft adds its antennas). */
	public static final TagKey<Block> WIND_RESILIENT = tag("wind_damage/resilient");
	/** Only a category 5 hurricane or EF5 tornado: {@link DamageClass#EXTREME} (2026-11-13; RadioCraft adds its coax). */
	public static final TagKey<Block> WIND_RESISTANT_EF5 = tag("wind_resistant_ef5");
	/** Blocks that keep you warm in a cold snap when you are near them. */
	public static final TagKey<Block> WARMS = tag("warms");
	/** What even an EF5 tornado leaves standing: stone, earth, ores and the like. */
	public static final TagKey<Block> TORNADO_PROOF = tag("tornado_proof");
	public static final TagKey<Block> HAIL_BREAKABLE = tag("hail_breakable");
	/**
	 * Wire that links a siren controller to its sirens: AWM's data wire, and RadioCraft's data wire (every colour)
	 * and passthrough when RadioCraft is installed (optional entries).
	 */
	public static final TagKey<Block> SIREN_WIRE = tag("siren_wire");
	/** Siren wire that joins every other kind; the rest joins only its own block (a colour stays apart). */
	public static final TagKey<Block> SIREN_WIRE_JOINS_ALL = tag("siren_wire_joins_all");
	/** Sirens a controller can sound: blocks whose block entity, or the one below, is a {@code WiredSiren}. */
	public static final TagKey<Block> SIRENS = tag("sirens");

	private AwmTags() {
	}

	private static TagKey<Block> tag(String path) {
		return TagKey.create(Registries.BLOCK, AnotherWeatherMod.id(path));
	}

	/**
	 * Which wind damage class {@code state} is in: its tag if it has one (the strongest first), else worked out from what
	 * it is made of and its shape ({@link WindClasses#byProperties}), so every block in the game, any mod's too, has a
	 * place on the ladder; {@link DamageClass#OTHER} for what the wind leaves alone (stone, metal, earth).
	 */
	public static DamageClass windClass(BlockState state) {
		if(state.is(WIND_RESISTANT_EF5))
			return DamageClass.EXTREME;
		if(state.is(WIND_RESILIENT))
			return DamageClass.RESILIENT;
		if(state.is(WIND_FRAGILE))
			return DamageClass.FRAGILE;
		if(state.is(WIND_FOLIAGE))
			return DamageClass.FOLIAGE;
		if(state.is(WIND_LOOSE))
			return DamageClass.LOOSE;
		if(state.is(WIND_SCOUR))
			return DamageClass.SCOUR;
		if(state.is(WIND_BLOWS_OPEN))
			return DamageClass.BLOWS_OPEN;
		if(state.is(WIND_HEAVY))
			return DamageClass.HEAVY;
		if(state.is(WIND_STURDY))
			return DamageClass.STURDY;
		if(state.is(WIND_STRUCTURE))
			return DamageClass.STRUCTURE;
		if(state.is(TORNADO_PROOF))
			return DamageClass.OTHER;
		return WindClasses.byProperties(state);
	}

	/**
	 * Wind speed, metres per second, from which the wind does a damage class's damage (2026-11-13 ladder: a category 1
	 * or 2 hurricane, 33 to 49 m/s, only strips foliage; glass goes from category 3, then loose things, fences, whole
	 * planks in category 5, logs rarer still). Hurricanes: 1 = 33, 2 = 43, 3 = 50, 4 = 58, 5 = 70. EF tornadoes: 0 =
	 * 29, 1 = 38, 2 = 50, 3 = 61, 4 = 75, 5 = 89.
	 */
	public static double threshold(DamageClass damageClass) {
		return switch(damageClass) {
			case FOLIAGE -> 25;
			case FRAGILE -> 50;
			case LOOSE -> 54;
			case SCOUR -> 58;
			case BLOWS_OPEN -> Gusts.DOOR_WIND;
			case STURDY -> 62;
			case STRUCTURE -> 70;
			case RESILIENT -> 70;
			case EXTREME -> 70;
			case HEAVY -> 80;
			case DEMOLISHED -> 74;
			case OTHER -> 25;
		};
	}

	/**
	 * How likely the wind is to do a class's damage each time it picks such a block, at its threshold and well past it
	 * (2026-11-13: everything broke far too fast). Foliage goes readily, logs and storm-built things hardly ever.
	 */
	public static double chance(DamageClass damageClass) {
		return switch(damageClass) {
			case FOLIAGE -> 0.5;
			case FRAGILE -> 0.35;
			case LOOSE, SCOUR -> 0.3;
			case STURDY -> 0.12;
			case STRUCTURE -> 0.06;
			case DEMOLISHED -> 0.3;
			case RESILIENT -> 0.03;
			case EXTREME -> 0.04;
			case HEAVY -> 0.015;
			case BLOWS_OPEN, OTHER -> 1; // Doors roll their own chance; OTHER only tells listeners.
		};
	}

	/** {@link #chance} at {@code windSpeed}: a third of it at the threshold, all of it 25 m/s past. */
	public static double chanceAt(DamageClass damageClass, double windSpeed) {
		double over = (windSpeed - threshold(damageClass)) / 25;
		return over < 0 ? 0 : chance(damageClass) * Math.min(1, 1 / 3.0 + over * 2 / 3.0);
	}
}
