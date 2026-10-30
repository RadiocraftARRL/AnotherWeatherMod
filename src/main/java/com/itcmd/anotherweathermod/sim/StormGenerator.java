package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.DayOutlook;
import com.itcmd.anotherweathermod.api.DayType;
import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Decides storms ahead of time, so the forecast can see them coming. Time is cut into slots; each slot, up to the
 * forecast horizon, is decided once: for each player a roll for a storm aimed at them, and one roll for a hurricane.
 * A decided storm is placed upwind so that its real path (steering wind included) crosses its target partway through
 * its life. With {@link DayTypes}, the target's sort of day decides how often: none on dry days or on the strip where
 * two regions meet, more and longer-lived ones on rainy days, and tornadoes mostly on tornado-watch days. High wind
 * comes on days that are not rainy.
 */
public final class StormGenerator {

	/**
	 * @param slotTicks           length of a forecast slot
	 * @param horizonTicks        how far ahead storms are decided
	 * @param stormsPerPlayerHour storms aimed at each player per real hour
	 * @param hurricaneInterval   mean ticks between hurricanes (0 for none)
	 * @param maxStorms           most storms alive or pending at once
	 * @param weights             relative chance of each rain-bearing storm on the scale, 0 (shower) to 5 (tornado)
	 * @param fogPerPlayerHour    fog banks aimed at each player per real hour
	 * @param extremesPerPlayerDay heat waves (over hot ground) or cold snaps (over snowy ground) aimed at each player
	 *                            per in-game day
	 * @param highWindPerPlayerDay spells of high wind aimed at each player per in-game day, on days that are not rainy
	 */
	public record Settings(int slotTicks, long horizonTicks, double stormsPerPlayerHour, double hurricaneInterval,
			int maxStorms, double[] weights, double fogPerPlayerHour, double extremesPerPlayerDay,
			double highWindPerPlayerDay) {

		public static Settings defaults() {
			return new Settings(600, 20 * 60 * 20, 7.0, 72 * 24000, 24, WEIGHTS.clone(), 0.12, 0.12, 0.2);
		}

		/** These settings with everything that happens made {@code frequency} times as common (1 for no change). */
		public Settings scaled(double frequency) {
			return new Settings(slotTicks, horizonTicks, stormsPerPlayerHour * frequency,
					frequency <= 0 ? 0 : hurricaneInterval / frequency, maxStorms, weights, fogPerPlayerHour * frequency,
					extremesPerPlayerDay * frequency, highWindPerPlayerDay * frequency);
		}
	}

	/** The sort of day at a place and game time (see {@link DayTypes}). */
	@FunctionalInterface
	public interface Outlooks {
		DayOutlook at(double x, double z, long gameTime);

		/** The game time the day that {@code gameTime} falls in ends at. By default days run with game time. */
		default long dayEnd(long gameTime) {
			return (Math.floorDiv(gameTime, DayTypes.DAY) + 1) * DayTypes.DAY;
		}
	}

	/** Storms on a day with a chance of rain, and on a rainy day, as a share of {@link Settings#stormsPerPlayerHour}. */
	static final double CHANCE_RATE = 1.0;
	static final double RAINY_RATE = 5.0;
	/** Storms on rainy days live this much longer. */
	static final double RAINY_LIFE = 1.6;
	/** Tornado weight on tornado-watch days, and on other days, as a share of its setting. */
	static final double WATCH_TORNADO = 4;
	static final double NO_WATCH_TORNADO = 0.25;
	/** Supercell weight on tornado-watch days. */
	static final double WATCH_SUPERCELL = 1.5;
	/** A day's storms die out by this long into the next day (an evening storm rains on into the night). */
	static final long DAY_GRACE = DayTypes.DAY / 4;

	/** The default weights. */
	private static final double[] WEIGHTS = { 0.42, 0.17, 0.09, 0.21, 0.09, 0.008 };

	/** Where a player is, for aiming storms. */
	public record Target(double x, double z) {
	}

	/** Severe kinds (severe thunderstorms, supercells, tornadoes) weigh this much more near a wind shift. */
	static final double SHIFT_SEVERE_BOOST = 1.5;

	/** When along its life a storm reaches its target. */
	static final double ARRIVAL = 0.45;
	/** Hurricanes start this far from their target, blocks. */
	static final int HURRICANE_MIN_DISTANCE = 1800;
	static final int HURRICANE_MAX_DISTANCE = 2600;

	private final long worldSeed;

	public StormGenerator(long worldSeed) {
		this.worldSeed = worldSeed;
	}

	/**
	 * Decides every undecided slot up to {@code now + horizon} and adds the storms to {@code field}.
	 *
	 * @return the storms added
	 */
	public List<StormCell> topUp(StormField field, long now, List<Target> targets, Climate climate, Settings settings) {
		return topUp(field, now, targets, climate, settings, null);
	}

	/**
	 * Decides every undecided slot up to {@code now + horizon} and adds the storms to {@code field}, by the targets'
	 * sorts of day ({@code outlooks}; null for none: every day alike).
	 *
	 * @return the storms added
	 */
	public List<StormCell> topUp(StormField field, long now, List<Target> targets, Climate climate, Settings settings,
			@Nullable Outlooks outlooks) {
		List<StormCell> added = new ArrayList<>();
		long slot = field.nextSlotTick();
		if(slot == Long.MIN_VALUE || slot < now - settings.slotTicks())
			slot = Math.floorDiv(now, settings.slotTicks()) * settings.slotTicks();
		long end = now + settings.horizonTicks();
		for(; slot <= end; slot += settings.slotTicks()) {
			SplittableRandom random = new SplittableRandom(Noise.mix(worldSeed ^ Noise.mix(slot)));
			double chance = settings.stormsPerPlayerHour() * settings.slotTicks() / 72000.0;
			long middle = slot + settings.slotTicks() / 2;
			for(Target target : targets) {
				DayOutlook day = outlooks == null ? null : outlooks.at(target.x(), target.z(), middle);
				double rate = day == null ? 1 : day.regionEdge() || day.type() == DayType.DRY ? 0
						: day.type() == DayType.RAINY ? RAINY_RATE : CHANCE_RATE;
				if(random.nextDouble() >= chance * rate || field.all().size() >= settings.maxStorms())
					continue;
				StormCell cell = aimedStorm(field, random, slot, settings, target, day,
						outlooks == null ? Long.MAX_VALUE : outlooks.dayEnd(middle) + DAY_GRACE);
				if(cell != null && spaced(field, cell))
					added.add(field.add(cell, now));
			}
			// Fog, heat waves and cold snaps draw from their own stream, so turning them up or down leaves the storms be.
			SplittableRandom events = new SplittableRandom(Noise.mix(worldSeed ^ Noise.mix(slot) ^ 0xE7E7L));
			double fogChance = settings.fogPerPlayerHour() * settings.slotTicks() / 72000.0;
			double extremeChance = settings.extremesPerPlayerDay() * settings.slotTicks() / 24000.0;
			double windChance = settings.highWindPerPlayerDay() * settings.slotTicks() / 24000.0;
			for(Target target : targets) {
				StormKind event = null;
				DayOutlook day = outlooks == null ? null : outlooks.at(target.x(), target.z(), middle);
				if(events.nextDouble() < fogChance)
					event = StormKind.DENSE_FOG;
				else if(events.nextDouble() < extremeChance) {
					int y = climate.seaLevel() + 1;
					Precipitation base = climate.basePrecipitation((int)target.x(), y, (int)target.z());
					float t = climate.biomeTemperature((int)target.x(), y, (int)target.z());
					if(base == Precipitation.NONE || t >= 1)
						event = StormKind.HEAT_WAVE;
					else if(base == Precipitation.SNOW || t <= 0.15f)
						event = StormKind.COLD_SNAP;
				}
				else if(events.nextDouble() < windChance && (day == null || day.type() != DayType.RAINY))
					event = StormKind.HIGH_WIND;
				if(event != null && field.all().size() < settings.maxStorms())
					added.add(field.add(aimedStorm(field, events, slot, settings, target, event), now));
			}
			if(settings.hurricaneInterval() > 0 && !targets.isEmpty()
					&& random.nextDouble() < settings.slotTicks() / settings.hurricaneInterval()) {
				Target target = targets.get(random.nextInt(targets.size()));
				StormCell cell = hurricane(field, random, slot, settings, target, climate);
				if(cell != null)
					added.add(field.add(cell, now));
			}
		}
		field.setNextSlotTick(slot);
		return added;
	}

	private StormKind rollKind(SplittableRandom random, double[] weights) {
		double total = 0;
		for(double w : weights)
			total += w;
		double r = random.nextDouble() * total;
		StormKind[] kinds = { StormKind.SHOWER, StormKind.THUNDERSTORM, StormKind.STRONG_THUNDERSTORM,
				StormKind.SEVERE_THUNDERSTORM, StormKind.SUPERCELL, StormKind.TORNADO };
		for(int i = 0; i < kinds.length && i < weights.length; i++) {
			if(r < weights[i])
				return kinds[i];
			r -= weights[i];
		}
		return StormKind.SHOWER;
	}

	/**
	 * A rain-bearing storm, shower to tornado, whose path crosses near {@code target}; the severe kinds weigh more
	 * near a wind shift.
	 */
	StormCell aimedStorm(StormField field, SplittableRandom random, long slot, Settings settings, Target target) {
		return aimedStorm(field, random, slot, settings, target, null, Long.MAX_VALUE);
	}

	/**
	 * A rain-bearing storm, shower to tornado, whose path crosses near {@code target} on {@code day} (null for no day
	 * types): tornadoes mostly on watch days, and rainy days' storms longer-lived. A storm belongs to its day: it dies
	 * out by {@code dayEnd}, or is not made at all (null) if the day has too little left for it.
	 */
	@Nullable
	StormCell aimedStorm(StormField field, SplittableRandom random, long slot, Settings settings, Target target,
			@Nullable DayOutlook day, long dayEnd) {
		double[] weights = settings.weights().clone();
		if(field.wind().nearShift(slot + settings.slotTicks() / 2))
			for(int i = 3; i < weights.length; i++)
				weights[i] *= SHIFT_SEVERE_BOOST;
		if(day != null && weights.length > 5) {
			weights[5] *= day.tornadoWatch() ? WATCH_TORNADO : NO_WATCH_TORNADO;
			if(day.tornadoWatch())
				weights[4] *= WATCH_SUPERCELL;
		}
		double life = day != null && day.type() == DayType.RAINY ? RAINY_LIFE : 1;
		return aimedStorm(field, random, slot, settings, target, rollKind(random, weights), life, dayEnd);
	}

	/** A storm of {@code kind} whose path crosses near {@code target}. */
	StormCell aimedStorm(StormField field, SplittableRandom random, long slot, Settings settings, Target target,
			StormKind kind) {
		return aimedStorm(field, random, slot, settings, target, kind, 1, Long.MAX_VALUE);
	}

	/**
	 * A storm of {@code kind} whose path crosses near {@code target}, living {@code lifeScale} times as long but gone
	 * by {@code latestDeath}; null if that leaves it under half its shortest life.
	 */
	@Nullable
	StormCell aimedStorm(StormField field, SplittableRandom random, long slot, Settings settings, Target target,
			StormKind kind, double lifeScale, long latestDeath) {
		StormProfile p = StormProfile.of(kind);
		long birth = slot + random.nextInt(settings.slotTicks());
		int life = (int)(between(random, p.minLifetime(), p.maxLifetime()) * lifeScale);
		if(birth + life > latestDeath) {
			if(latestDeath - birth < p.minLifetime() / 2)
				return null;
			life = (int)(latestDeath - birth);
		}
		double radius = between(random, p.minRadius(), p.maxRadius());
		float intensity = (float)(p.minIntensity() + random.nextDouble() * (p.maxIntensity() - p.minIntensity()));
		double driftX = (random.nextDouble() * 2 - 1) * 0.02;
		double driftZ = (random.nextDouble() * 2 - 1) * 0.02;
		StormCell cell = new StormCell(0, kind, birth, life, 0, 0, driftX, driftZ, radius, intensity,
				random.nextLong(), field.wind());

		long arrival = birth + (long)(life * ARRIVAL);
		// Miss the target sideways by up to 60 % of the radius, so not every storm is a direct hit.
		double vx = cell.velocityX(arrival);
		double vz = cell.velocityZ(arrival);
		double v = Math.max(1e-6, Math.sqrt(vx * vx + vz * vz));
		double side = (random.nextDouble() * 2 - 1) * 0.6 * radius;
		double aimX = target.x() + -vz / v * side;
		double aimZ = target.z() + vx / v * side;
		// The path from an origin of 0 is the displacement; start that far back from the aim point.
		return cell.withPath(aimX - cell.centerX(arrival), aimZ - cell.centerZ(arrival), driftX, driftZ);
	}

	/** A hurricane born over the ocean that comes to {@code target}, or null if no ocean was found nearby. */
	StormCell hurricane(StormField field, SplittableRandom random, long slot, Settings settings, Target target,
			Climate climate) {
		StormProfile p = StormProfile.HURRICANE;
		long birth = slot + random.nextInt(settings.slotTicks());
		int life = between(random, p.minLifetime(), p.maxLifetime());
		double radius = between(random, p.minRadius(), p.maxRadius());
		float intensity = (float)(p.minIntensity() + random.nextDouble() * (p.maxIntensity() - p.minIntensity()));
		double startAngle = random.nextDouble() * Math.PI * 2;
		int distance = between(random, HURRICANE_MIN_DISTANCE, HURRICANE_MAX_DISTANCE);
		for(int attempt = 0; attempt < 8; attempt++) {
			double angle = startAngle + attempt * Math.PI / 4;
			double ox = target.x() + Math.sin(angle) * distance;
			double oz = target.z() - Math.cos(angle) * distance;
			if(!climate.isOcean((int)ox, (int)oz))
				continue;
			StormCell still = new StormCell(0, StormKind.HURRICANE, birth, life, ox, oz, 0, 0, radius, intensity,
					random.nextLong(), field.wind());
			long arrival = birth + life / 2;
			double side = (random.nextDouble() * 2 - 1) * 0.3 * radius;
			double aimX = target.x() + Math.cos(angle) * side;
			double aimZ = target.z() + Math.sin(angle) * side;
			// Drift adds linearly, so pick the drift that closes the gap the steering wind leaves.
			long travel = arrival - birth;
			double driftX = (aimX - still.centerX(arrival)) / travel;
			double driftZ = (aimZ - still.centerZ(arrival)) / travel;
			return still.withPath(ox, oz, driftX, driftZ);
		}
		return null;
	}

	/** Keeps storms from piling up: no two may be over nearly the same place at the newcomer's peak. */
	private static boolean spaced(StormField field, StormCell cell) {
		long check = cell.birthTick() + (long)(cell.lifetimeTicks() * ARRIVAL);
		double x = cell.centerX(check);
		double z = cell.centerZ(check);
		for(StormCell other : field.all()) {
			if(!other.isAlive(check) || other.kind() == StormKind.HURRICANE || !other.kind().bringsRain()
					|| !cell.kind().bringsRain())
				continue;
			double dx = other.centerX(check) - x;
			double dz = other.centerZ(check) - z;
			double min = (other.radiusAt(check) + cell.peakRadius()) * 0.5;
			if(dx * dx + dz * dz < min * min)
				return false;
		}
		return true;
	}

	private static int between(SplittableRandom random, int min, int max) {
		return max <= min ? min : min + random.nextInt(max - min + 1);
	}
}
