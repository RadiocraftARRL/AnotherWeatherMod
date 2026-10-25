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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
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
		if(level.random.nextFloat() >= HIT_CHANCE || StormProtection.protects(level, entity.getX(), entity.getZ()))
			return;
		BlockPos head = BlockPos.containing(entity.getEyePosition());
		if(!level.canSeeSky(head))
			return;
		DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
				.getHolderOrThrow(DAMAGE));
		entity.hurt(source, DAMAGE_AMOUNT);
	}

	/**
	 * Hail on one column, {@code top} being the motion-blocking heightmap position: crops (which do not block motion)
	 * sit at {@code top}, panes just under it. A crop is beaten back a stage or two, or flattened when young; panes
	 * crack now and then.
	 */
	static void strike(ServerLevel level, BlockPos top) {
		if(StormProtection.protects(level, top) || crop(level, top))
			return;
		BlockPos below = top.below();
		if(level.getBlockState(below).is(AwmTags.HAIL_BREAKABLE) && level.random.nextInt(4) == 0) {
			level.destroyBlock(below, false);
			level.playSound(null, below, SoundEvents.GLASS_BREAK, SoundSource.WEATHER, 0.4f, 1.6f);
		}
	}

	/** Damages the crop at {@code pos}, if there is one. */
	public static boolean crop(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if(!state.is(BlockTags.CROPS))
			return false;
		IntegerProperty ageProperty = state.hasProperty(BlockStateProperties.AGE_7) ? BlockStateProperties.AGE_7
				: state.hasProperty(BlockStateProperties.AGE_3) ? BlockStateProperties.AGE_3 : null;
		CropBlock crop = state.getBlock() instanceof CropBlock c ? c : null;
		int age = crop != null ? crop.getAge(state) : ageProperty != null ? state.getValue(ageProperty) : 0;
		if(age >= 2) {
			int younger = Math.max(0, age - 1 - level.random.nextInt(2));
			BlockState beaten = crop != null ? crop.getStateForAge(younger) : state.setValue(ageProperty, younger);
			level.setBlock(pos, beaten, Block.UPDATE_CLIENTS);
			level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
		}
		else if(level.random.nextInt(3) == 0)
			level.destroyBlock(pos, true); // Young plants are flattened.
		return true;
	}
}
