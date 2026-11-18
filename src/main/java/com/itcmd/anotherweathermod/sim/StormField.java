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
 *
 * <p>Most storms are the world's own ({@link StormGenerator}): known by their seed, put here only while someone is near
 * enough to need them, and dropped again when nobody is ({@link #dematerialize}), to be put back the same when needed.
 * One removed on purpose ({@link #remove}, {@link #clear}) is remembered until it would have died, so it stays gone.
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
	@Nullable private Climate terrain;
	private long nextId = 1;
	/** The seeds of the storms here that are the world's own (not from commands). */
	private final java.util.Set<Long> natural = java.util.concurrent.ConcurrentHashMap.newKeySet();
	/** The world's storms removed on purpose, by seed, with when they would have died: they are not put back. */
	private final java.util.Map<Long, Long> suppressed = new java.util.concurrent.ConcurrentHashMap<>();

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

	/** Whether {@code cell} is one of the world's own storms (see {@link StormGenerator}). */
	public boolean isNatural(StormCell cell) {
		return natural.contains(cell.seed());
	}

	/** Whether the world's storm with {@code seed} is here, or was removed on purpose and must stay gone. */
	boolean knows(long seed) {
		return natural.contains(seed) || suppressed.containsKey(seed);
	}

	/** Adds one of the world's own storms (see {@link #add}). */
	public StormCell addNatural(StormCell cell, long now) {
		natural.add(cell.seed());
		return add(cell, now);
	}

	/** Drops one of the world's storms nobody needs now, without forgetting it: it comes back the same when needed. */
	public void dematerialize(StormCell cell) {
		if(active.removeIf(c -> c.id() == cell.id()) | pending.removeIf(c -> c.id() == cell.id()))
			natural.remove(cell.seed());
	}

	/** Forgets removed storms that would have died by {@code now}. */
	void pruneSuppressed(long now) {
		suppressed.values().removeIf(death -> death <= now);
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

	/** Makes every storm feel the ground of {@code climate} (bigger over the sea, smaller over desert), or not if null. */
	public void setTerrain(@Nullable Climate climate) {
		if(climate == terrain)
			return;
		terrain = climate;
		for(StormCell cell : all())
			cell.setTerrain(climate);
	}

	/** Adds a storm: active if it has formed by {@code now}, else pending. Gives it a fresh id. */
	public StormCell add(StormCell cell, long now) {
		StormCell placed = cell.withId(allocateId());
		placed.setLand(land);
		placed.setTerrain(terrain);
		placed.reportedStage = placed.stageAt(now);
		if(now >= placed.birthTick())
			active.add(placed);
		else
			pending.add(placed);
		return placed;
	}

	/**
	 * Puts {@code replacement} in the place of the storm with its id (2026-11-18: {@code /awm pin}): same id, same
	 * place in the list, same stage. False if there is none.
	 */
	public boolean replace(StormCell replacement) {
		for(List<StormCell> list : List.of(active, pending))
			for(int i = 0; i < list.size(); i++)
				if(list.get(i).id() == replacement.id()) {
					replacement.setLand(land);
					replacement.setTerrain(terrain);
					replacement.reportedStage = list.get(i).reportedStage;
					list.set(i, replacement);
					return true;
				}
		return false;
	}

	@Nullable
	public StormCell find(long id) {
		for(StormCell cell : all())
			if(cell.id() == id)
				return cell;
		return null;
	}

	/** Removes a storm, active or pending. One of the world's own stays gone (it is not put back). */
	public boolean remove(long id) {
		StormCell cell = find(id);
		if(cell == null)
			return false;
		forget(cell);
		return active.removeIf(c -> c.id() == id) | pending.removeIf(c -> c.id() == id);
	}

	/**
	 * Removes every storm here. The world's own stay gone; storms further off, or yet to form, still come when their
	 * time comes.
	 */
	public List<StormCell> clear() {
		List<StormCell> removed = all();
		for(StormCell cell : removed)
			forget(cell);
		active.clear();
		pending.clear();
		return removed;
	}

	private void forget(StormCell cell) {
		if(natural.remove(cell.seed()))
			suppressed.put(cell.seed(), cell.deathTick());
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
				if(now >= cell.deathTick()) {
					natural.remove(cell.seed());
					continue; // Its whole life passed while nobody was looking.
				}
				active.add(cell);
				formed.add(cell);
				cell.reportedStage = cell.stageAt(now);
			}
		}
		for(StormCell cell : active) {
			StormStage stage = cell.stageAt(now);
			if(stage == StormStage.DISSIPATED) {
				active.remove(cell);
				natural.remove(cell.seed());
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

	/** Every tornado on the ground at {@code gameTime}; usually none. */
	public List<StormCell.Tornado> tornadoesDown(long gameTime) {
		List<StormCell.Tornado> down = null;
		for(StormCell cell : active) {
			StormCell.Tornado tornado = cell.tornadoDown(gameTime);
			if(tornado != null) {
				if(down == null)
					down = new ArrayList<>(2);
				down.add(tornado);
			}
		}
		return down == null ? List.of() : down;
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
		ListTag cells = new ListTag();
		for(StormCell cell : all()) {
			CompoundTag c = cell.save();
			if(natural.contains(cell.seed()))
				c.putBoolean("natural", true);
			cells.add(c);
		}
		tag.put("storms", cells);
		ListTag gone = new ListTag();
		suppressed.forEach((seed, death) -> {
			CompoundTag g = new CompoundTag();
			g.putLong("seed", seed);
			g.putLong("death", death);
			gone.add(g);
		});
		tag.put("removed", gone);
		return tag;
	}

	/** Restores what {@link #save()} wrote. Each storm goes to active or pending by {@code now}. */
	public void load(CompoundTag tag, long now) {
		active.clear();
		pending.clear();
		natural.clear();
		suppressed.clear();
		nextId = Math.max(1, tag.getLong("next_id"));
		ListTag gone = tag.getList("removed", Tag.TAG_COMPOUND);
		for(int i = 0; i < gone.size(); i++)
			if(gone.getCompound(i).getLong("death") > now)
				suppressed.put(gone.getCompound(i).getLong("seed"), gone.getCompound(i).getLong("death"));
		ListTag cells = tag.getList("storms", Tag.TAG_COMPOUND);
		for(int i = 0; i < cells.size(); i++) {
			StormCell cell = StormCell.load(cells.getCompound(i), wind);
			if(cell == null || now >= cell.deathTick())
				continue;
			cell.setLand(land);
			cell.setTerrain(terrain);
			cell.reportedStage = cell.stageAt(now);
			if(cells.getCompound(i).getBoolean("natural"))
				natural.add(cell.seed());
			if(now >= cell.birthTick())
				active.add(cell);
			else
				pending.add(cell);
			nextId = Math.max(nextId, cell.id() + 1);
		}
	}
}
