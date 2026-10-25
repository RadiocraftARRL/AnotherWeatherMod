package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.api.event.WindDamageEvent;
import com.itcmd.anotherweathermod.api.event.WindDamageEvent.DamageClass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;

/** What strong wind does to an exposed block. Every candidate goes through {@link WindDamageEvent} first. */
public final class WindDamage {

	private WindDamage() {
	}

	/**
	 * The wind at {@code windSpeed} metres per second, blowing toward ({@code windX}, {@code windZ}), works on the
	 * block at {@code pos}.
	 *
	 * @return whether anything happened (or a listener took it)
	 */
	public static boolean apply(ServerLevel level, BlockPos pos, double windSpeed, double windX, double windZ) {
		return apply(level, pos, windSpeed, windX, windZ, false);
	}

	/**
	 * {@link #apply(ServerLevel, BlockPos, double, double, double)}; with {@code demolish} (an EF4 or EF5 tornado's
	 * core), anything in no wind damage tag and not {@link AwmTags#TORNADO_PROOF} is torn away too, sheltered or not.
	 */
	public static boolean apply(ServerLevel level, BlockPos pos, double windSpeed, double windX, double windZ,
			boolean demolish) {
		BlockState state = level.getBlockState(pos);
		if(state.isAir() || demolish && !state.getFluidState().isEmpty() || StormProtection.protects(level, pos))
			return false;
		DamageClass damageClass = AwmTags.windClass(state);
		if(demolish && damageClass == DamageClass.OTHER && !state.is(AwmTags.TORNADO_PROOF)
				&& state.getDestroySpeed(level, pos) >= 0)
			damageClass = DamageClass.DEMOLISHED;
		if(windSpeed < AwmTags.threshold(damageClass) || !demolish && sheltered(level, pos, windX, windZ))
			return false;
		WindDamageEvent event = new WindDamageEvent(level, pos, state, windSpeed, damageClass);
		if(NeoForge.EVENT_BUS.post(event).isCanceled())
			return true;
		boolean done = switch(damageClass) {
			case FRAGILE, FOLIAGE, LOOSE, HEAVY -> level.destroyBlock(pos, true);
			// Most of a demolished building is blown away, not left in a heap; containers still spill.
			case DEMOLISHED -> level.destroyBlock(pos, level.getBlockEntity(pos) != null || level.random.nextInt(4) == 0);
			case SCOUR -> level.setBlockAndUpdate(pos, Blocks.COARSE_DIRT.defaultBlockState());
			case BLOWS_OPEN -> Gusts.blowOpen(level, pos, state);
			case OTHER -> false;
		};
		if(done && regrows(state, damageClass)) {
			LevelWeather weather = WeatherManager.get(level);
			if(weather != null) {
				weather.regrowth().record(level, pos, state, level.getBlockState(pos));
				weather.markDirty();
			}
		}
		return done;
	}

	/**
	 * Whether nature puts {@code state} back: foliage and scoured ground, but not crops (farmers replant) or tall
	 * plants (two blocks that only come back together).
	 */
	static boolean regrows(BlockState state, DamageClass damageClass) {
		if(damageClass == DamageClass.SCOUR || damageClass == DamageClass.HEAVY) // Trees come back whole.
			return true;
		return damageClass == DamageClass.FOLIAGE && !state.is(BlockTags.CROPS)
				&& !(state.getBlock() instanceof DoublePlantBlock);
	}

	/** Out of the wind: something solid within two blocks upwind, at the same height. */
	static boolean sheltered(ServerLevel level, BlockPos pos, double windX, double windZ) {
		double speed = Math.sqrt(windX * windX + windZ * windZ);
		if(speed < 1e-6)
			return true;
		for(int step = 1; step <= 2; step++) {
			BlockPos upwind = BlockPos.containing(pos.getX() + 0.5 - windX / speed * step, pos.getY() + 0.5,
					pos.getZ() + 0.5 - windZ / speed * step);
			if(level.isLoaded(upwind) && level.getBlockState(upwind).isSolidRender(level, upwind))
				return true;
		}
		return false;
	}
}
