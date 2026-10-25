package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/** What hail does on the server: stings things caught out in it, and breaks crops and glass panes now and then. */
public final class Hail {

	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
			AnotherWeatherMod.id("hail"));

	/** Chance per tick that hail hurts an exposed entity. */
	static final float HIT_CHANCE = 1 / 80f;
	static final float DAMAGE_AMOUNT = 1;

	private Hail() {
	}

	/** Maybe hurts {@code entity}, if nothing is over its head. */
	public static void pelt(ServerLevel level, LivingEntity entity) {
		if(level.random.nextFloat() >= HIT_CHANCE)
			return;
		BlockPos head = BlockPos.containing(entity.getEyePosition());
		if(!level.canSeeSky(head))
			return;
		DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
				.getHolderOrThrow(DAMAGE));
		entity.hurt(source, DAMAGE_AMOUNT);
	}

	/** Hail on one surface block: crops and glass panes break, rarely. */
	static void strike(ServerLevel level, BlockPos surface) {
		BlockState state = level.getBlockState(surface);
		boolean crop = state.getBlock() instanceof CropBlock;
		boolean breakable = state.is(AwmTags.HAIL_BREAKABLE);
		if(crop || (breakable && level.random.nextInt(4) == 0)) {
			level.destroyBlock(surface, crop);
			if(breakable)
				level.playSound(null, surface, SoundEvents.GLASS_BREAK, SoundSource.WEATHER, 0.4f, 1.6f);
		}
	}
}
