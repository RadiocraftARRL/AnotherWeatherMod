package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.StormStage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Every storm in a level, formed and pending, and the steering wind. The lists are safe to read from any thread;
 * changes happen on the server thread.
 */
public final class StormField {

	/**
	 * Out of the storms, the ground feels this share of the steering wind (which blows aloft and moves the storms):
	 * a light breeze of 1 to 3.5 m/s. Storms bring the real wind.
	 */
	public static final double SURFACE_WIND = 0.35;

	private final SteeringWind wind;
	private final List<StormCell> active = new CopyOnWriteArrayList<>();
	private final List<StormCell> pending = new CopyOnWriteArrayList<>();
	/** Where hurricanes weaken over land, or null when they keep their strength. */
	@Nullable
	private Climate land;
	private long nextId = 1;
	/** The first forecast slot not decided yet. */
	private long nextSlotTick = Long.MIN_VALUE;

	public StormField(SteeringWind wind) {
		this.wind = wind;
	}

	public SteeringWind wind() {
		return wind;
	}

	public List<StormCell> active() {
		return List.copyOf(active);
	}

	public List<StormCell> pending() {
		return List.copyOf(pending);
	}

	/** Every storm, formed first, then pending. */
	public List<StormCell> all() {
		List<StormCell> all = new ArrayList<>(active.size() + pending.size());
		all.addAll(active);
		all.addAll(pending);
		return all;
	}

	public long nextSlotTick() {
		return nextSlotTick;
	}

	public void setNextSlotTick(long tick) {
		nextSlotTick = tick;
	}

	public long allocateId() {
		return nextId++;
	}

	/**
	 * Makes hurricanes weaken over the land of {@code climate}, or keep their strength if it is null. Their tracks do
	 * not change; their strength along them does, and is still a pure function of time and the (fixed) terrain.
	 */
	public void setLand(@Nullable Climate climate) {
		if(climate == land)
			return;
		land = climate;
		for(StormCell cell : all())
			cell.setLand(climate);
	}

	/** Adds a storm: active if it has formed by {@code now}, else pending. Gives it a fresh id. */
	public StormCell add(StormCell cell, long now) {
		StormCell placed = cell.withId(allocateId());
		placed.setLand(land);
		placed.reportedStage = placed.stageAt(now);
		if(now >= placed.birthTick())
			active.add(placed);
		else
			pending.add(placed);
		return placed;
	}

	@Nullable
	public StormCell find(long id) {
		for(StormCell cell : all())
			if(cell.id() == id)
				return cell;
		return null;
	}

	/** Removes a storm, active or pending. */
	public boolean remove(long id) {
		return active.removeIf(c -> c.id() == id) | pending.removeIf(c -> c.id() == id);
	}

	/** Removes every storm and forgets the forecast, so it is decided again. */
	public List<StormCell> clear() {
		List<StormCell> removed = all();
		active.clear();
		pending.clear();
		nextSlotTick = Long.MIN_VALUE;
		return removed;
	}

	/** What changed in one {@link #advance}. */
	public record Changes(List<StormCell> formed, List<StormCell> dissipated, List<StageChange> stages) {
		public boolean any() {
			return !formed.isEmpty() || !dissipated.isEmpty() || !stages.isEmpty();
		}
	}

	public record StageChange(StormCell cell, StormStage from, StormStage to) {
	}

	/** Forms pending storms whose time has come and drops dead ones. */
	public Changes advance(long now) {
		List<StormCell> formed = new ArrayList<>();
		List<StormCell> dissipated = new ArrayList<>();
		List<StageChange> stages = new ArrayList<>();
		for(StormCell cell : pending) {
			if(now >= cell.birthTick()) {
				pending.remove(cell);
				if(now >= cell.deathTick())
					continue; // Its whole life passed while nobody was looking.
				active.add(cell);
				formed.add(cell);
				cell.reportedStage = cell.stageAt(now);
			}
		}
		for(StormCell cell : active) {
			StormStage stage = cell.stageAt(now);
			if(stage == StormStage.DISSIPATED) {
				active.remove(cell);
				dissipated.add(cell);
			}
			else if(cell.reportedStage != null && cell.reportedStage != stage && !formed.contains(cell))
				stages.add(new StageChange(cell, cell.reportedStage, stage));
			cell.reportedStage = stage;
		}
		return new Changes(formed, dissipated, stages);
	}

	/** The storm weather at ({@code x}, {@code z}) at {@code gameTime}, counting pending storms that will have formed. */
	public PointWeather sample(double x, double z, long gameTime) {
		PointWeather.Builder acc = new PointWeather.Builder(wind.x(gameTime) * SURFACE_WIND, wind.z(gameTime) * SURFACE_WIND);
		for(StormCell cell : active)
			cell.contribute(x, z, gameTime, acc);
		for(StormCell cell : pending)
			cell.contribute(x, z, gameTime, acc);
		return acc.build();
	}

	/** Whether any storm's rain or wind reaches within {@code margin} blocks of ({@code x}, {@code z}). */
	public boolean anyReaches(double x, double z, long gameTime, double margin) {
		for(StormCell cell : active)
			if(cell.reaches(x, z, gameTime, margin))
				return true;
		return false;
	}

	/** Whether any storm is over ({@code x}, {@code z}) at {@code gameTime}. Cheaper than {@link #sample}. */
	public boolean anyCovers(double x, double z, long gameTime) {
		for(StormCell cell : active)
			if(cell.covers(x, z, gameTime))
				return true;
		return false;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("next_id", nextId);
		tag.putLong("next_slot", nextSlotTick);
		ListTag cells = new ListTag();
		for(StormCell cell : all())
			cells.add(cell.save());
		tag.put("storms", cells);
		return tag;
	}

	/** Restores what {@link #save()} wrote. Each storm goes to active or pending by {@code now}. */
	public void load(CompoundTag tag, long now) {
		active.clear();
		pending.clear();
		nextId = Math.max(1, tag.getLong("next_id"));
		nextSlotTick = tag.contains("next_slot") ? tag.getLong("next_slot") : Long.MIN_VALUE;
		ListTag cells = tag.getList("storms", Tag.TAG_COMPOUND);
		for(int i = 0; i < cells.size(); i++) {
			StormCell cell = StormCell.load(cells.getCompound(i), wind);
			if(cell == null || now >= cell.deathTick())
				continue;
			cell.setLand(land);
			cell.reportedStage = cell.stageAt(now);
			if(now >= cell.birthTick())
				active.add(cell);
			else
				pending.add(cell);
			nextId = Math.max(nextId, cell.id() + 1);
		}
	}
}
