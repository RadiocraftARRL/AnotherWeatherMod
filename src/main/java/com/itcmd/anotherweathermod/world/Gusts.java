package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.AwmConfig;
import com.itcmd.anotherweathermod.api.WeatherSample;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Gusts blow wooden doors open. Doors are in walls, under roofs, where the chunk wind (which works on the surface)
 * never reaches, so every {@link #INTERVAL} ticks the wind around each player looks for closed doors on outside walls
 * and blows some of them open. Each goes through {@code WindDamageEvent} as {@code BLOWS_OPEN}, so listeners can keep
 * a door shut.
 */
public final class Gusts {

	static final int INTERVAL = 40;
	/** Wind speed, metres per second, from which gusts blow doors open: past a thunderstorm's, inside a severe one's. */
	public static final double DOOR_WIND = 20;
	/** Wind speed at which a door is most likely to go each time. */
	static final double FULL = 50;
	/** The most likely a door is to blow open each time. */
	static final double MAX_CHANCE = 0.25;
	static final int RADIUS = 12;
	static final int HEIGHT = 6;

	private Gusts() {
	}

	/** Looks for doors around {@code player}, out in weather {@code sample}. */
	static void tick(ServerLevel level, ServerPlayer player, WeatherSample sample) {
		double speed = sample.windSpeed();
		if(speed < DOOR_WIND || !AwmConfig.GUSTS_OPEN_DOORS.get())
			return;
		double chance = Math.min(1, (speed - DOOR_WIND) / (FULL - DOOR_WIND)) * MAX_CHANCE;
		BlockPos center = player.blockPosition();
		for(BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIUS, -HEIGHT, -RADIUS), center.offset(RADIUS, HEIGHT, RADIUS))) {
			BlockState state = level.getBlockState(pos);
			if(!state.is(AwmTags.WIND_BLOWS_OPEN) || !closed(state) || level.random.nextDouble() >= chance)
				continue;
			if(outside(level, pos))
				WindDamage.apply(level, pos.immutable(), speed, sample.wind().x, sample.wind().z);
		}
	}

	/** A closed door (its lower half, so a door is one candidate), trapdoor or gate. */
	private static boolean closed(BlockState state) {
		if(!state.hasProperty(BlockStateProperties.OPEN) || state.getValue(BlockStateProperties.OPEN))
			return false;
		return !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
				|| state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
	}

	/** On an outside wall: open sky on one side of it. */
	private static boolean outside(ServerLevel level, BlockPos pos) {
		for(Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos next = pos.relative(side);
			if(level.getBlockState(next).getCollisionShape(level, next).isEmpty() && level.canSeeSky(next))
				return true;
		}
		return false;
	}

	/** Swings the door at {@code pos} open, with its sound. */
	static boolean blowOpen(ServerLevel level, BlockPos pos, BlockState state) {
		if(!state.hasProperty(BlockStateProperties.OPEN) || state.getValue(BlockStateProperties.OPEN))
			return false;
		if(state.getBlock() instanceof DoorBlock door) {
			door.setOpen(null, level, state, pos, true);
			return true;
		}
		level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 10);
		level.gameEvent(null, GameEvent.BLOCK_OPEN, pos);
		return true;
	}
}
