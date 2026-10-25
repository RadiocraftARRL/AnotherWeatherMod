package com.itcmd.anotherweathermod.api.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Wind is about to damage a block. Posted on {@code NeoForge.EVENT_BUS} on the server thread, for every exposed block
 * the wind picks, before anything happens to it. Cancel it to protect the block, or to handle the damage yourself (an
 * antenna taking damage, say).
 *
 * <p>Blocks in none of the mod's wind damage tags come with {@link DamageClass#OTHER}: nothing happens to them unless
 * a listener does something, so mods can damage their own blocks without adding them to a tag.
 */
public class WindDamageEvent extends Event implements ICancellableEvent {

	/** What the wind would do to the block. */
	public enum DamageClass {
		/** Breaks: glass, flower pots, lanterns. */
		FRAGILE,
		/** Torn away: leaves, flowers, crops. */
		FOLIAGE,
		/** Popped off as an item: fences, torches, signs. */
		LOOSE,
		/** Grass scoured to coarse dirt. */
		SCOUR,
		/** Torn down only by the worst winds (tornadoes, category 5 hurricanes): logs. */
		HEAVY,
		/** Torn away by an EF4 or EF5 tornado: anything not in the {@code tornado_proof} tag (stone, earth, ores). */
		DEMOLISHED,
		/** Blown open, nothing broken: wooden doors on outside walls, in gusts. */
		BLOWS_OPEN,
		/** Not in any tag: nothing happens unless a listener does something. */
		OTHER
	}

	private final ServerLevel level;
	private final BlockPos pos;
	private final BlockState state;
	private final double windSpeed;
	private final DamageClass damageClass;

	public WindDamageEvent(ServerLevel level, BlockPos pos, BlockState state, double windSpeed, DamageClass damageClass) {
		this.level = level;
		this.pos = pos;
		this.state = state;
		this.windSpeed = windSpeed;
		this.damageClass = damageClass;
	}

	public ServerLevel getLevel() {
		return level;
	}

	public BlockPos getPos() {
		return pos;
	}

	public BlockState getState() {
		return state;
	}

	/** Metres per second. */
	public double getWindSpeed() {
		return windSpeed;
	}

	public DamageClass getDamageClass() {
		return damageClass;
	}
}
