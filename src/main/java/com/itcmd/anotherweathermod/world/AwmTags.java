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
	public static final TagKey<Block> HAIL_BREAKABLE = tag("hail_breakable");

	private AwmTags() {
	}

	private static TagKey<Block> tag(String path) {
		return TagKey.create(Registries.BLOCK, AnotherWeatherMod.id(path));
	}

	/** Which wind damage class {@code state} is in, or {@link DamageClass#OTHER}. */
	public static DamageClass windClass(BlockState state) {
		if(state.is(WIND_FRAGILE))
			return DamageClass.FRAGILE;
		if(state.is(WIND_FOLIAGE))
			return DamageClass.FOLIAGE;
		if(state.is(WIND_LOOSE))
			return DamageClass.LOOSE;
		if(state.is(WIND_SCOUR))
			return DamageClass.SCOUR;
		return DamageClass.OTHER;
	}

	/** Wind speed, metres per second, from which the wind does a damage class's damage. */
	public static double threshold(DamageClass damageClass) {
		return switch(damageClass) {
			case FOLIAGE -> 25;
			case FRAGILE -> 33;
			case LOOSE -> 40;
			case SCOUR -> 50;
			case OTHER -> 25;
		};
	}
}
