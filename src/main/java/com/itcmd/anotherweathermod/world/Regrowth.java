package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.AwmConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Storm damage to nature heals: leaves the wind stripped grow back, plants come back, scoured ground grasses over. Each
 * damaged block is remembered with what it was, and put back after a random one to three in-game days (config) if
 * nothing else has changed it meanwhile. Player-built things (glass, fences, torches) are not restored.
 */
public final class Regrowth {

	/** The most scars remembered per level; past this, new damage simply stays. */
	static final int MAX_SCARS = 20000;
	/** How many due scars are looked at per second. */
	static final int PER_SECOND = 64;
	/** A scar whose chunk is unloaded, or that is still in the storm, is looked at again this much later. */
	static final int RETRY = 1200;

	record Scar(BlockPos pos, BlockState original, BlockState left, long due) {
	}

	private final PriorityQueue<Scar> scars = new PriorityQueue<>(Comparator.comparingLong(Scar::due));

	/** Remembers that the wind turned {@code original} at {@code pos} into {@code left}. */
	public void record(ServerLevel level, BlockPos pos, BlockState original, BlockState left) {
		if(!AwmConfig.REGROWTH.get() || scars.size() >= MAX_SCARS)
			return;
		long min = (long)(AwmConfig.REGROWTH_MIN_DAYS.get() * 24000);
		long max = Math.max(min, (long)(AwmConfig.REGROWTH_MAX_DAYS.get() * 24000));
		long delay = min + (max > min ? (long)(level.random.nextDouble() * (max - min + 1)) : 0);
		scars.add(new Scar(pos.immutable(), original, left, level.getGameTime() + delay));
	}

	public int size() {
		return scars.size();
	}

	/** Makes every scar due now (the {@code /awm regrow} command). */
	public void hurry(long now) {
		List<Scar> all = new ArrayList<>(scars);
		scars.clear();
		for(Scar scar : all)
			scars.add(new Scar(scar.pos(), scar.original(), scar.left(), now));
	}

	/** Heals what is due. Call once a second. */
	void tick(LevelWeather weather) {
		ServerLevel level = weather.level();
		long now = level.getGameTime();
		List<Scar> retry = new ArrayList<>();
		for(int i = 0; i < PER_SECOND && !scars.isEmpty() && scars.peek().due() <= now; i++) {
			Scar scar = scars.poll();
			if(!level.isLoaded(scar.pos()) || weather.rainLevel(scar.pos().getX() + 0.5, scar.pos().getZ() + 0.5) > 0) {
				retry.add(new Scar(scar.pos(), scar.original(), scar.left(), now + RETRY));
				continue;
			}
			heal(level, scar);
		}
		scars.addAll(retry);
	}

	private static void heal(ServerLevel level, Scar scar) {
		BlockState current = level.getBlockState(scar.pos());
		if(!current.is(scar.left().getBlock()))
			return; // Someone built or planted something there.
		BlockState original = scar.original();
		if(!original.canSurvive(level, scar.pos()))
			return; // The flower's ground is gone, say.
		if(original.getBlock() instanceof LeavesBlock)
			original = Block.updateFromNeighbourShapes(original, level, scar.pos()); // Fresh distance to the trunk.
		level.setBlockAndUpdate(scar.pos(), original);
	}

	public ListTag save() {
		ListTag list = new ListTag();
		for(Scar scar : scars) {
			CompoundTag tag = new CompoundTag();
			tag.put("pos", NbtUtils.writeBlockPos(scar.pos()));
			tag.put("was", NbtUtils.writeBlockState(scar.original()));
			tag.put("now", NbtUtils.writeBlockState(scar.left()));
			tag.putLong("due", scar.due());
			list.add(tag);
		}
		return list;
	}

	public void load(ListTag list, HolderGetter<Block> blocks) {
		scars.clear();
		for(int i = 0; i < list.size(); i++) {
			CompoundTag tag = list.getCompound(i);
			BlockPos pos = NbtUtils.readBlockPos(tag, "pos").orElse(null);
			if(pos == null)
				continue;
			scars.add(new Scar(pos, NbtUtils.readBlockState(blocks, tag.getCompound("was")),
					NbtUtils.readBlockState(blocks, tag.getCompound("now")), tag.getLong("due")));
		}
	}
}
