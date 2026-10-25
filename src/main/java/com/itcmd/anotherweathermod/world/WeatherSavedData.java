package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.sim.StormField;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/** A level's storms, saved with it, so a restart keeps the forecast. */
public final class WeatherSavedData extends SavedData {

	static final String NAME = "anotherweathermod_weather";

	/** What was read from disk, until a {@link StormField} takes it over. */
	@Nullable CompoundTag loaded;
	@Nullable StormField field;
	/** Damage waiting to grow back, as read from disk, until a {@link Regrowth} takes it over. */
	@Nullable ListTag loadedRegrowth;
	@Nullable Regrowth regrowth;
	/** Whether the vanilla weather has been cleared once in this level. */
	boolean initialized;

	public static WeatherSavedData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(
				new SavedData.Factory<>(WeatherSavedData::new, WeatherSavedData::load, null), NAME);
	}

	private static WeatherSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
		WeatherSavedData data = new WeatherSavedData();
		data.loaded = tag.getCompound("field");
		data.loadedRegrowth = tag.getList("regrowth", Tag.TAG_COMPOUND);
		data.initialized = tag.getBoolean("initialized");
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		if(field != null)
			tag.put("field", field.save());
		else if(loaded != null)
			tag.put("field", loaded);
		if(regrowth != null)
			tag.put("regrowth", regrowth.save());
		else if(loadedRegrowth != null)
			tag.put("regrowth", loadedRegrowth);
		tag.putBoolean("initialized", initialized);
		return tag;
	}
}
