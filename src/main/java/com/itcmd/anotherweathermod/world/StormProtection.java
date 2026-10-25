package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.block.AwmBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * The areas storm protectors keep safe in a level: a circle round each protector, all the way up and down, where wind
 * (tornadoes, hurricanes, gusts) and hail damage nothing and tornadoes lift nothing. Saved with the level, so an area
 * stays safe while its protector's chunk is unloaded.
 */
public final class StormProtection extends SavedData {

	static final String NAME = "anotherweathermod_protection";
	public static final int MIN_RADIUS = 8;
	public static final int MAX_RADIUS = 256;
	public static final int DEFAULT_RADIUS = 48;

	private final Map<BlockPos, Integer> areas = new HashMap<>();

	public static StormProtection get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(
				new SavedData.Factory<>(StormProtection::new, StormProtection::load, null), NAME);
	}

	/** Whether a protector keeps ({@code x}, {@code z}) safe. */
	public static boolean protects(ServerLevel level, double x, double z) {
		StormProtection data = get(level);
		if(data.areas.isEmpty())
			return false;
		for(Map.Entry<BlockPos, Integer> e : data.areas.entrySet()) {
			double dx = x - (e.getKey().getX() + 0.5), dz = z - (e.getKey().getZ() + 0.5);
			int r = e.getValue();
			if(dx * dx + dz * dz <= r * r)
				return true;
		}
		return false;
	}

	public static boolean protects(ServerLevel level, BlockPos pos) {
		return protects(level, pos.getX() + 0.5, pos.getZ() + 0.5);
	}

	/** The radius of the protector at {@code pos}, or the default if it has none yet. */
	public int radius(BlockPos pos) {
		return areas.getOrDefault(pos, DEFAULT_RADIUS);
	}

	/** Protects round {@code pos}; keeps an existing radius if {@code radius} is negative. */
	public void set(BlockPos pos, int radius) {
		int r = radius < 0 ? radius(pos) : Mth.clamp(radius, MIN_RADIUS, MAX_RADIUS);
		Integer old = areas.put(pos.immutable(), r);
		if(old == null || old != r)
			setDirty();
	}

	public void remove(BlockPos pos) {
		if(areas.remove(pos) != null)
			setDirty();
	}

	/** Forgets protectors that are no longer there, where their chunk is loaded to look. */
	public void prune(ServerLevel level) {
		if(areas.keySet().removeIf(pos -> level.isLoaded(pos) && !level.getBlockState(pos).is(AwmBlocks.STORM_PROTECTOR.get())))
			setDirty();
	}

	private static StormProtection load(CompoundTag tag, HolderLookup.Provider registries) {
		StormProtection data = new StormProtection();
		for(Tag t : tag.getList("areas", Tag.TAG_COMPOUND)) {
			CompoundTag area = (CompoundTag)t;
			data.areas.put(new BlockPos(area.getInt("x"), area.getInt("y"), area.getInt("z")), area.getInt("radius"));
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		areas.forEach((pos, radius) -> {
			CompoundTag area = new CompoundTag();
			area.putInt("x", pos.getX());
			area.putInt("y", pos.getY());
			area.putInt("z", pos.getZ());
			area.putInt("radius", radius);
			list.add(area);
		});
		tag.put("areas", list);
		return tag;
	}
}
