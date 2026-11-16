package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.DayOutlook;
import com.itcmd.anotherweathermod.api.DayType;
import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * The world's storms. They belong to the world, not to anyone in it: each is rolled from the world seed, a patch of
 * the map and a stretch of time, born where it was rolled and carried off by the steering wind (plus a little drift of
 * its own). Nothing about a storm, where it forms, how it goes, or whether it forms at all, depends on who is online or
 * where they are; players only decide which storms are worth keeping in the {@link StormField} (those that come within
 * sight of someone within the forecast horizon). One put in late, mid-life, is where it would have been all along, so
 * forecasts (and RadioCraft's radio, WEFAX and SKYWARN readers) agree with what comes.
 *
 * <p>Three sources, each a pure function of the seed:
 * <ul>
 * <li>Scattered storms: each {@link #CELL}-block square of the map, every {@link #SLOT} ticks, rolls how many storms
 * form in it, by the sort of day: none on dry days, more and longer-lived ones on rainy days, the severe kinds more
 * often near a wind shift and tornadoes mostly on tornado-watch days. Fog banks, heat waves, cold snaps and high wind
 * are rolled the same way on their own squares.</li>
 * <li>Fronts: lines thousands of blocks long that form across the wind, sweep along with it, and set off a band of
 * cells along their length as they go (a squall line), more severe than scattered storms. More of them on rainy days,
 * most around a wind shift (a cold front).</li>
 * <li>Hurricanes: rolled on huge squares, formed only over the ocean, wandering with the wind for days.</li>
 * </ul>
 * How often each comes is set per place: the settings say how many pass over any one spot in an hour (or day), and the
 * rate per square follows from how much ground each sweeps.
 */
public final class StormGenerator {

	/**
	 * @param horizonTicks        how far ahead storms are put in the field, so the forecast sees them coming
	 * @param stormsPerPlayerHour scattered storms passing over any one place per real hour on a day with a chance of
	 *                            rain (fronts come on top)
	 * @param hurricaneInterval   mean ticks between hurricanes passing over a place by the ocean (0 for none)
	 * @param maxStorms           most storms kept in the field at once (the nearest to players win)
	 * @param weights             relative chance of each rain-bearing storm on the scale, 0 (shower) to 5 (tornado)
	 * @param fogPerPlayerHour    fog banks passing over a place per real hour
	 * @param extremesPerPlayerDay heat waves (over hot ground) or cold snaps (over snowy ground) passing over a place
	 *                            per in-game day
	 * @param highWindPerPlayerDay spells of high wind passing over a place per in-game day, on days that are not rainy
	 */
	public record Settings(long horizonTicks, double stormsPerPlayerHour, double hurricaneInterval, int maxStorms,
			double[] weights, double fogPerPlayerHour, double extremesPerPlayerDay, double highWindPerPlayerDay) {

		public static Settings defaults() {
			return new Settings(20 * 60 * 20, DEFAULT_STORMS_PER_HOUR, 72 * 24000, 160, WEIGHTS.clone(), 0.12, 0.12, 0.2);
		}

		/** These settings with everything that happens made {@code frequency} times as common (1 for no change). */
		public Settings scaled(double frequency) {
			return new Settings(horizonTicks, stormsPerPlayerHour * frequency,
					frequency <= 0 ? 0 : hurricaneInterval / frequency, maxStorms, weights, fogPerPlayerHour * frequency,
					extremesPerPlayerDay * frequency, highWindPerPlayerDay * frequency);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Settings s && horizonTicks == s.horizonTicks
					&& stormsPerPlayerHour == s.stormsPerPlayerHour && hurricaneInterval == s.hurricaneInterval
					&& maxStorms == s.maxStorms && java.util.Arrays.equals(weights, s.weights)
					&& fogPerPlayerHour == s.fogPerPlayerHour && extremesPerPlayerDay == s.extremesPerPlayerDay
					&& highWindPerPlayerDay == s.highWindPerPlayerDay;
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(horizonTicks, stormsPerPlayerHour, hurricaneInterval, maxStorms,
					java.util.Arrays.hashCode(weights), fogPerPlayerHour, extremesPerPlayerDay, highWindPerPlayerDay);
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

	/** Where someone is who needs the storms round them: it decides what is kept in the field, never what forms. */
	public record Viewer(double x, double z) {
	}

	/** Scattered storms on a day with a chance of rain, and on a rainy day, as a share of the setting. */
	static final double CHANCE_RATE = 1.0;
	static final double RAINY_RATE = 3.0;
	/**
	 * Scattered storms on a dry day (2026-11-13: dry days had none of their own, but storms from fronts and hurricanes
	 * ran on into them; now they get only the odd light one): showers and ordinary thunderstorms, never anything strong.
	 */
	static final double DRY_RATE = 0.15;
	/** The kinds a dry day can have, by index into the weights: shower and thunderstorm. */
	static final int DRY_KINDS = 2;
	/**
	 * How long before a dry day the storms of the day before must be gone (2026-11-15, the maintainer: they ran on into
	 * the dry morning; they should dwindle and break up before it). Their whole life is laid out to end by then, so they
	 * spend its last part decaying, shrinking and weakening ({@link StormCell#DECAY_START}). Into other days they run on
	 * a quarter day ({@link #DAY_GRACE}).
	 */
	static final long DRY_CLEAR_BEFORE = 600;
	/** Storms on rainy days live this much longer. */
	static final double RAINY_LIFE = 1.6;
	/** Tornado weight on tornado-watch days, and on other days, as a share of its setting. */
	static final double WATCH_TORNADO = 4;
	static final double NO_WATCH_TORNADO = 0.25;
	/** Supercell weight on tornado-watch days. */
	static final double WATCH_SUPERCELL = 1.5;
	/** A day's storms die out by this long into the next day (an evening storm rains on into the night). */
	static final long DAY_GRACE = DayTypes.DAY / 4;
	/** Severe kinds (severe thunderstorms, supercells, tornadoes) weigh this much more near a wind shift. */
	static final double SHIFT_SEVERE_BOOST = 1.5;

	static final double DEFAULT_STORMS_PER_HOUR = 7.0;
	/** The default weights. */
	private static final double[] WEIGHTS = { 0.42, 0.17, 0.09, 0.21, 0.09, 0.008 };

	/**
	 * Storms are kept in the field while they come within this many blocks (plus their own reach) of someone within the
	 * forecast horizon: what a client is sent ({@code NearbyStormsPayload.RANGE}, 1500) and some room to walk.
	 */
	public static final double VIEW_REACH = 1800;
	/** Kept ones are dropped only once they will not come within this far of anyone: no dropping and taking back. */
	static final double DROP_REACH = VIEW_REACH + 800;

	/** Scattered storms: the square each is rolled on, blocks, and the stretch of time, ticks. */
	static final double CELL = 2048;
	static final long SLOT = 2400;
	/** Fog banks, and high wind, heat waves and cold snaps. */
	static final double FOG_CELL = 4096;
	static final long FOG_SLOT = 2400;
	static final double EVENT_CELL = 8192;
	static final long EVENT_SLOT = 6000;
	/** Hurricanes. */
	static final double HURRICANE_CELL = 16384;
	static final long HURRICANE_SLOT = 24000;

	/** Fronts: the square each is rolled on and the stretch of time. */
	static final double FRONT_CELL = 12000;
	static final long FRONT_SLOT = 6000;
	/** Fronts per square and stretch on a day with a chance of rain; on rainy days, and near a wind shift, times these. */
	static final double FRONT_MEAN = 0.1;
	static final double FRONT_RAINY = 3;
	static final double FRONT_SHIFT = 2;
	static final double FRONT_MAX = FRONT_RAINY * FRONT_SHIFT;
	/** A front's length, blocks, and life, ticks. */
	static final int FRONT_MIN_LENGTH = 2500, FRONT_MAX_LENGTH = 7000;
	static final int FRONT_MIN_LIFE = 12000, FRONT_MAX_LIFE = 24000;
	/** A front sets off cells every {@link #FRONT_STEP} ticks along stretches of {@link #FRONT_SEGMENT} blocks. */
	static final long FRONT_STEP = 1500;
	static final double FRONT_SEGMENT = 500;
	/** The chance a stretch sets off a cell at each step (fewer toward the front's ends). */
	static final double FRONT_CELL_CHANCE = 0.22;
	/** How far either side of the line a front's cells form, blocks. */
	static final double FRONT_WIDTH = 80;
	/** A front's cells live this share of a lone storm's life. */
	static final double FRONT_CELL_LIFE = 0.6;
	/** A front's cells are this much more often each kind on the scale than scattered storms: fewer showers, more severe. */
	static final double[] FRONT_SEVERITY = { 0.3, 1, 1.4, 1.8, 1.5, 1.5 };
	/** Sources, for the seeds. */
	private static final long SCATTERED = 1, FRONT = 2, HURRICANE = 3, FOG = 4, WIND = 5, EXTREME = 6;

	/** Mean steering wind speed, metres per second, and drift, blocks a tick, for the rates. */
	static final double MEAN_WIND = (SteeringWind.MIN_SPEED + SteeringWind.MAX_SPEED) / 2;
	static final double MAX_DRIFT = 0.02;
	static final double HURRICANE_DRIFT = 0.03;
	/** How close a storm must pass to count as passing over a place, as a share of its radius. */
	static final double CROSS = 0.6;
	/**
	 * Measured corrections to the swept-ground rates (WeatherFrequencyTest), so the settings give what they say: storms
	 * passing the edge of the crossing distance, weakening fog banks, and hurricanes kept only over the ocean all bring
	 * less than the plain geometry counts.
	 */
	static final double SCATTERED_TUNE = 0.7, FOG_TUNE = 2.4, HURRICANE_TUNE = 9; // 4 -> 9 (2026-11-13): hurricanes now die out on the first dry day, so a few more form.

	private final long worldSeed;

	public StormGenerator(long worldSeed) {
		this.worldSeed = worldSeed;
	}

	/**
	 * Keeps the field to the world's storms that come within sight of {@code viewers} between {@code now} and the
	 * forecast horizon: puts in the ones that do (where they are by now, if they formed earlier), and drops those that
	 * no longer will. Without outlooks every day is alike.
	 *
	 * @return the storms put in
	 */
	public List<StormCell> topUp(StormField field, long now, List<Viewer> viewers, Climate climate, Settings settings) {
		return topUp(field, now, viewers, climate, settings, null);
	}

	/** {@link #topUp(StormField, long, List, Climate, Settings)} by the sort of day ({@code outlooks}; null for none). */
	public List<StormCell> topUp(StormField field, long now, List<Viewer> viewers, Climate climate, Settings settings,
			@Nullable Outlooks outlooks) {
		field.pruneSuppressed(now);
		long until = now + settings.horizonTicks();
		for(StormCell cell : field.all())
			if(field.isNatural(cell) && !comesNear(cell, viewers, now, until, DROP_REACH))
				field.dematerialize(cell);
		List<StormCell> added = new ArrayList<>();
		if(viewers.isEmpty())
			return added;
		record Candidate(StormCell cell, double distance) {
		}
		List<Candidate> wanted = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		forEachStorm(field.wind(), viewers, VIEW_REACH, now, until, climate, settings, outlooks, cell -> {
			if(cell.deathTick() <= now || cell.birthTick() > until || field.knows(cell.seed()) || !seen.add(cell.seed()))
				return;
			double d = closest(cell, viewers, now, until);
			if(d <= VIEW_REACH + reach(cell))
				wanted.add(new Candidate(cell, d));
		});
		wanted.sort(Comparator.comparingDouble(Candidate::distance));
		for(Candidate c : wanted) {
			if(field.all().size() >= settings.maxStorms())
				break;
			added.add(field.addNatural(c.cell(), now));
		}
		return added;
	}

	/**
	 * Every one of the world's storms alive at some time from {@code from} to {@code to} that comes within
	 * {@code within} blocks (plus its own reach) of ({@code x}, {@code z}) then, by seed: the same whoever is about.
	 */
	public List<StormCell> stormsNear(SteeringWind wind, double x, double z, double within, long from, long to,
			Climate climate, Settings settings, @Nullable Outlooks outlooks) {
		List<Viewer> at = List.of(new Viewer(x, z));
		List<StormCell> near = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		forEachStorm(wind, at, within, from, to, climate, settings, outlooks, cell -> {
			if(cell.deathTick() > from && cell.birthTick() <= to && seen.add(cell.seed())
					&& closest(cell, at, from, to) <= within + reach(cell))
				near.add(cell);
		});
		near.sort(Comparator.comparingLong(StormCell::seed));
		return near;
	}

	/** How far a storm's weather reaches from its centre at most, blocks. */
	static double reach(StormCell cell) {
		return cell.peakRadius() * (cell.kind() == StormKind.HURRICANE ? 1 : StormCell.GUST_FRONT);
	}

	/** Whether {@code cell} comes within {@code within} (plus its reach) of a viewer between {@code from} and {@code to}. */
	static boolean comesNear(StormCell cell, List<Viewer> viewers, long from, long to, double within) {
		return closest(cell, viewers, from, to) <= within + reach(cell);
	}

	/** How close {@code cell}'s centre comes to any viewer while alive between {@code from} and {@code to}. */
	static double closest(StormCell cell, List<Viewer> viewers, long from, long to) {
		long start = Math.max(from, cell.birthTick()), end = Math.min(to, cell.deathTick());
		if(start > end || viewers.isEmpty())
			return Double.MAX_VALUE;
		double best = Double.MAX_VALUE;
		for(long t = start; ; t = Math.min(end, t + 400)) {
			double cx = cell.centerX(t), cz = cell.centerZ(t);
			for(Viewer v : viewers)
				best = Math.min(best, Math.hypot(cx - v.x(), cz - v.z()));
			if(t >= end)
				break;
		}
		return best;
	}

	/** A storm handed out by {@link #forEachStorm}. */
	@FunctionalInterface
	interface Sink {
		void accept(StormCell cell);
	}

	/**
	 * Hands {@code sink} every one of the world's storms that might come within {@code within} of a viewer between
	 * {@code from} and {@code to} (a cheap bound from where it forms and how fast it can go; the caller looks closer).
	 */
	synchronized void forEachStorm(SteeringWind wind, List<Viewer> viewers, double within, long from, long to, Climate climate,
			Settings settings, @Nullable Outlooks outlooks, Sink sink) {
		WindTrack track = new WindTrack(wind, from - maxLife(StormProfile.HURRICANE, StormProfile.EXTREME) - FRONT_MAX_LIFE
				- HURRICANE_SLOT, to + HURRICANE_SLOT);
		Sink near = cell -> {
			long start = Math.max(from, cell.birthTick()), end = Math.min(to, cell.deathTick());
			if(start > end)
				return;
			// Where it is every minute or so, from the wind's path (the same the storm follows, near enough).
			double mf = cell.profile().motionFactor();
			double slack = within + reach(cell) + maxSpeed(cell) * 600 + 16;
			for(long t = start; ; t = Math.min(end, t + 1200)) {
				double x = cell.originX() + mf * track.dx(cell.birthTick(), t) + cell.driftX() * (t - cell.birthTick());
				double z = cell.originZ() + mf * track.dz(cell.birthTick(), t) + cell.driftZ() * (t - cell.birthTick());
				for(Viewer v : viewers)
					if(Math.hypot(x - v.x(), z - v.z()) <= slack) {
						sink.accept(cell);
						return;
					}
				if(t >= end)
					return;
			}
		};
		double[] weights = settings.weights();
		// Scattered storms.
		double scattered = perCellSlot(settings.stormsPerPlayerHour() / 72000.0, rainArea(weights, 1), CELL, SLOT)
				* RAINY_RATE * SCATTERED_TUNE;
		long rainLife = (long)(maxLife(StormProfile.TORNADO, StormProfile.SUPERCELL, StormProfile.SEVERE_THUNDERSTORM,
				StormProfile.STRONG_THUNDERSTORM, StormProfile.THUNDERSTORM, StormProfile.SHOWER) * RAINY_LIFE);
		Maker scatteredMaker = (r, ox, oz, birth) -> scattered(wind, r, ox, oz, birth, weights, outlooks);
		Spacing spacing = new Spacing(SCATTERED, CELL, SLOT, scattered, rainLife, track, scatteredMaker);
		scatter(SCATTERED, CELL, SLOT, scattered, rainLife, new Motion(0.2, 0.25, MAX_DRIFT), 320 * 1.4,
				viewers, within, from, to, track, scatteredMaker, cell -> {
					if(!spacing.crowded(cell))
						near.accept(cell);
				});
		// Fog banks, high wind, heat waves and cold snaps.
		scatter(FOG, FOG_CELL, FOG_SLOT, perCellSlot(settings.fogPerPlayerHour() / 72000.0,
						area(StormProfile.DENSE_FOG, 1, MAX_DRIFT), FOG_CELL, FOG_SLOT) * FOG_TUNE,
				StormProfile.DENSE_FOG.maxLifetime(), new Motion(0.08, 0.08, MAX_DRIFT),
				StormProfile.DENSE_FOG.maxRadius(), viewers, within, from, to, track,
				(r, ox, oz, birth) -> event(wind, r, ox, oz, birth, StormKind.DENSE_FOG), near);
		scatter(WIND, EVENT_CELL, EVENT_SLOT, perCellSlot(settings.highWindPerPlayerDay() / 24000.0,
						area(StormProfile.HIGH_WIND, 1, MAX_DRIFT), EVENT_CELL, EVENT_SLOT),
				StormProfile.HIGH_WIND.maxLifetime(), new Motion(0.15, 0.15, MAX_DRIFT),
				StormProfile.HIGH_WIND.maxRadius(), viewers, within, from, to, track, (r, ox, oz, birth) -> {
					StormCell cell = event(wind, r, ox, oz, birth, StormKind.HIGH_WIND);
					DayOutlook day = outlooks == null ? null : outlooks.at(ox, oz, birth);
					return day != null && day.type() == DayType.RAINY ? null : cell;
				}, near);
		scatter(EXTREME, EVENT_CELL, EVENT_SLOT, perCellSlot(settings.extremesPerPlayerDay() / 24000.0,
						area(StormProfile.EXTREME, 1, MAX_DRIFT), EVENT_CELL, EVENT_SLOT),
				StormProfile.EXTREME.maxLifetime(), new Motion(0.03, 0.03, MAX_DRIFT),
				StormProfile.EXTREME.maxRadius(), viewers, within, from, to, track, (r, ox, oz, birth) -> {
					int y = climate.seaLevel() + 1;
					Precipitation base = climate.basePrecipitation((int)ox, y, (int)oz);
					float t = climate.biomeTemperature((int)ox, y, (int)oz);
					StormKind kind = base == Precipitation.NONE || t >= 1 ? StormKind.HEAT_WAVE
							: base == Precipitation.SNOW || t <= 0.15f ? StormKind.COLD_SNAP : null;
					return kind == null ? null : event(wind, r, ox, oz, birth, kind);
				}, near);
		// Hurricanes, over the ocean only.
		if(settings.hurricaneInterval() > 0)
			scatter(HURRICANE, HURRICANE_CELL, HURRICANE_SLOT, perCellSlot(1 / settings.hurricaneInterval(),
							crossArea(StormProfile.HURRICANE, 1, HURRICANE_DRIFT, 0.5), HURRICANE_CELL, HURRICANE_SLOT)
							* HURRICANE_TUNE,
					StormProfile.HURRICANE.maxLifetime(), new Motion(0.05, 0.05, HURRICANE_DRIFT),
					StormProfile.HURRICANE.maxRadius(), viewers, within, from, to, track,
					(r, ox, oz, birth) -> hurricane(wind, r, ox, oz, birth, climate, outlooks), near);
		// Fronts scale with the storms setting (and weatherFrequency), from its default.
		fronts(wind, viewers, within, from, to, track, weights, outlooks,
				settings.stormsPerPlayerHour() / DEFAULT_STORMS_PER_HOUR, near);
	}

	/**
	 * The steering wind's path, blocks, summed in {@link StormCell#STEP} tick steps from {@code start} to {@code end}: how
	 * far something carried by the whole wind goes between two times, without working out each storm's own path.
	 */
	static final class WindTrack {
		private final long start;
		private final double[] x, z;

		WindTrack(SteeringWind wind, long start, long end) {
			this.start = Math.floorDiv(start, StormCell.STEP) * StormCell.STEP;
			int n = (int)((end - this.start) / StormCell.STEP) + 3;
			x = new double[n];
			z = new double[n];
			for(int i = 1; i < n; i++) {
				long mid = this.start + (long)(i - 1) * StormCell.STEP + StormCell.STEP / 2;
				x[i] = x[i - 1] + wind.x(mid) / 20 * StormCell.STEP;
				z[i] = z[i - 1] + wind.z(mid) / 20 * StormCell.STEP;
			}
		}

		private double at(double[] a, long t) {
			double f = (t - start) / (double)StormCell.STEP;
			int i = (int)Math.max(0, Math.min(a.length - 2, Math.floor(f)));
			return a[i] + (a[i + 1] - a[i]) * Math.max(0, Math.min(1, f - i));
		}

		double dx(long from, long to) {
			return at(x, to) - at(x, from);
		}

		double dz(long from, long to) {
			return at(z, to) - at(z, from);
		}
	}

	/** Makes one storm from its own random stream, formed at ({@code ox}, {@code oz}) at {@code birth}; null for none. */
	@FunctionalInterface
	private interface Maker {
		@Nullable
		StormCell make(SplittableRandom random, double ox, double oz, long birth);
	}

	/** How a source's storms move: the share of the steering wind they ride (least and most) and their largest drift. */
	record Motion(double minFactor, double maxFactor, double drift) {
	}

	/**
	 * Rolls storms on squares of {@code cellSize} blocks and stretches of {@code slotTicks}, {@code mean} each on
	 * average (before {@code maker} turns some down), for every square and stretch whose storms might reach a viewer:
	 * for each stretch, the squares upwind of a viewer by as far as the wind carries them from then to the window.
	 */
	private void scatter(long source, double cellSize, long slotTicks, double mean, long maxLife, Motion motion,
			double maxReach, List<Viewer> viewers, double within, long from, long to, WindTrack track, Maker maker,
			Sink sink) {
		if(mean <= 0)
			return;
		long k0 = Math.floorDiv(from - maxLife, slotTicks), k1 = Math.floorDiv(to, slotTicks);
		double[] factors = { motion.minFactor(), motion.maxFactor() };
		for(long k = k0; k <= k1; k++) {
			long born = k * slotTicks;
			long start = Math.max(from, born), end = Math.min(to, born + slotTicks + maxLife);
			if(start > end)
				continue;
			double slack = within + maxReach + motion.drift() * 1.42 * (end - born)
					+ motion.maxFactor() * SteeringWind.MAX_SPEED / 20 * (slotTicks + 1200) + 16;
			Set<Long> cells = new HashSet<>();
			for(Viewer v : viewers) {
				double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
				for(long t = start; ; t = Math.min(end, t + 1200)) {
					for(double f : factors) {
						double x = v.x() - f * track.dx(born, t), z = v.z() - f * track.dz(born, t);
						minX = Math.min(minX, x);
						maxX = Math.max(maxX, x);
						minZ = Math.min(minZ, z);
						maxZ = Math.max(maxZ, z);
					}
					if(t >= end)
						break;
				}
				long i0 = (long)Math.floor((minX - slack) / cellSize), i1 = (long)Math.floor((maxX + slack) / cellSize);
				long j0 = (long)Math.floor((minZ - slack) / cellSize), j1 = (long)Math.floor((maxZ + slack) / cellSize);
				for(long i = i0; i <= i1; i++)
					for(long j = j0; j <= j1; j++)
						if(cells.add(Noise.mix(i * 0x9E3779B97F4A7C15L ^ Noise.mix(j))))
							roll(source, i, j, k, cellSize, slotTicks, mean, maker, sink);
			}
		}
	}

	/**
	 * How close two scattered storms may form (2026-11-13): a storm does not form where an older one, alive at its birth,
	 * has its centre within this share of their two radii. Scattered storms form at independent random spots, so on a
	 * rainy day several used to pile up on one place (up to nine over one spot, and over twenty within 400 blocks),
	 * each with its own rain curtains. Storms may still brush and overlap at their edges. Which storm stays is decided
	 * from the storms' own rolls only (the older one, the lower seed on a tie), so every server and every top-up agrees.
	 */
	static final double SPACING = 0.8;
	/** How far back, ticks, a forming storm looks for older storms it would crowd (5 minutes: cheap enough to work out). */
	static final long CROWD_WINDOW = 6000;

	/**
	 * Spacing for one source's storms over one top-up: rolls the squares round a storm, as {@link #roll} would, to find
	 * the older storms it would crowd. Rolled squares are kept for the top-up.
	 */
	private final class Spacing {
		private final long source;
		private final double cellSize, mean;
		private final long slotTicks, maxLife;
		private final WindTrack track;
		private final Maker maker;
		private final Map<Long, List<StormCell>> rolled;
		private final Map<Long, Boolean> verdicts;

		Spacing(long source, double cellSize, long slotTicks, double mean, long maxLife, WindTrack track, Maker maker) {
			this.source = source;
			this.cellSize = cellSize;
			this.slotTicks = slotTicks;
			this.mean = mean;
			this.maxLife = maxLife;
			this.track = track;
			this.maker = maker;
			long settings = Noise.mix(Double.doubleToLongBits(mean) ^ Noise.mix(maxLife));
			if(settings != spacingSettings || spacingVerdicts.size() > SPACING_CACHE) {
				spacingVerdicts.clear(); // New settings (or a full cache): start afresh.
				spacingSettings = settings;
			}
			this.rolled = new HashMap<>(); // The rolled storms only for this top-up: they are big, the verdicts small.
			this.verdicts = spacingVerdicts;
		}

		private List<StormCell> square(long i, long j, long k) {
			return rolled.computeIfAbsent(key(source, i, j, k), x -> {
				List<StormCell> out = new ArrayList<>();
				roll(source, i, j, k, cellSize, slotTicks, mean, maker, out::add);
				return out;
			});
		}

		/** True if an older storm, alive when {@code cell} forms, sits too close to where it forms. */
		boolean crowded(StormCell cell) {
			return crowded(cell, 0);
		}

		private boolean crowded(StormCell cell, int depth) {
			Boolean known = verdicts.get(cell.seed());
			if(known != null)
				return known;
			boolean verdict = crowdedNow(cell, depth);
			verdicts.put(cell.seed(), verdict);
			return verdict;
		}

		/**
		 * Only older storms that formed themselves count (so more storms rolled always means more weather, up to how
		 * closely they can pack): a close older one is looked at in turn, as far back as {@link #SPACING_DEPTH}.
		 */
		private boolean crowdedNow(StormCell cell, int depth) {
			long birth = cell.birthTick();
			double x = cell.originX(), z = cell.originZ();
			long look = Math.min(maxLife, CROWD_WINDOW); // Older ones have drifted apart from where they formed anyway.
			long k1 = Math.floorDiv(birth, slotTicks), k0 = Math.floorDiv(birth - look, slotTicks);
			for(long k = k0; k <= k1; k++) {
				long born = Math.max(k * slotTicks, birth - look);
				// Where a storm from this stretch that is here now formed: back up the wind it rides (a fifth to a
				// quarter of the steering wind), give or take the spread of that and its drift.
				double dx = 0.225 * track.dx(born, birth), dz = 0.225 * track.dz(born, birth);
				double slack = 0.03 * Math.hypot(dx, dz) / 0.225 + 2 * MAX_DRIFT * (birth - born) + 2 * MAX_RADIUS + 16;
				long i0 = (long)Math.floor((x - dx - slack) / cellSize), i1 = (long)Math.floor((x - dx + slack) / cellSize);
				long j0 = (long)Math.floor((z - dz - slack) / cellSize), j1 = (long)Math.floor((z - dz + slack) / cellSize);
				for(long i = i0; i <= i1; i++)
					for(long j = j0; j <= j1; j++)
						for(StormCell other : square(i, j, k)) {
							if(other.birthTick() > birth || other.birthTick() == birth && other.seed() >= cell.seed()
									|| other.deathTick() <= birth)
								continue;
							double d = Math.hypot(other.centerX(birth) - x, other.centerZ(birth) - z);
							if(d < SPACING * (other.peakRadius() + cell.peakRadius()) && (depth >= SPACING_DEPTH || !crowded(other, depth + 1)))
								return true;
						}
			}
			return false;
		}
	}

	/** Spacing verdicts by storm seed, kept between top-ups (they never change for a world and its settings). */
	private final Map<Long, Boolean> spacingVerdicts = new HashMap<>();
	private long spacingSettings = 0;
	/** Entries kept before the caches start afresh. */
	static final int SPACING_CACHE = 50_000;
	/** How far a chain of storms, each crowding the next, is followed back before an older one is taken as formed. */
	static final int SPACING_DEPTH = 24;

	/** The largest scattered storm, blocks. */
	private static final double MAX_RADIUS = Math.max(StormProfile.TORNADO.maxRadius(), Math.max(StormProfile.SUPERCELL.maxRadius(),
			Math.max(StormProfile.SEVERE_THUNDERSTORM.maxRadius(), Math.max(StormProfile.STRONG_THUNDERSTORM.maxRadius(),
					Math.max(StormProfile.THUNDERSTORM.maxRadius(), StormProfile.SHOWER.maxRadius())))));

	/** The storms of square ({@code i}, {@code j}) in stretch {@code k}. */
	private void roll(long source, long i, long j, long k, double cellSize, long slotTicks, double mean, Maker maker,
			Sink sink) {
		long base = key(source, i, j, k);
		int n = poisson(new SplittableRandom(base), mean);
		for(int c = 0; c < n; c++) {
			SplittableRandom random = new SplittableRandom(Noise.mix(base + (c + 1) * 0x9E3779B97F4A7C15L));
			long birth = k * slotTicks + random.nextLong(slotTicks);
			double ox = (i + random.nextDouble()) * cellSize, oz = (j + random.nextDouble()) * cellSize;
			StormCell cell = maker.make(random, ox, oz, birth);
			if(cell != null)
				sink.accept(cell);
		}
	}

	private long key(long source, long i, long j, long k) {
		return Noise.mix(worldSeed ^ Noise.mix(source * 0xD1B54A32D192ED03L ^ Noise.mix(i * 0x9E3779B97F4A7C15L
				^ Noise.mix(j * 0xC2B2AE3D27D4EB4FL ^ Noise.mix(k * 0x165667B19E3779F9L)))));
	}

	/**
	 * A scattered rain-bearing storm, shower to tornado, by the sort of day where and when it forms: none on dry days,
	 * more (kept {@code rate / RAINY_RATE} of the time) and longer-lived on rainy days, the severe kinds more often near
	 * a wind shift, tornadoes mostly on watch days. A storm belongs to its day: it dies out a quarter day into the next,
	 * or does not form if its day has too little left.
	 */
	@Nullable
	private StormCell scattered(SteeringWind wind, SplittableRandom r, double ox, double oz, long birth, double[] base,
			@Nullable Outlooks outlooks) {
		double keep = r.nextDouble(), kindRoll = r.nextDouble();
		DayOutlook day = outlooks == null ? null : outlooks.at(ox, oz, birth);
		double rate = day == null ? 1 : day.type() == DayType.DRY ? DRY_RATE : day.type() == DayType.RAINY ? RAINY_RATE : CHANCE_RATE;
		if(keep >= rate / RAINY_RATE)
			return null;
		double[] weights = dayWeights(wind, base, day, birth);
		StormKind kind = pick(kindRoll, weights);
		double life = day != null && day.type() == DayType.RAINY ? RAINY_LIFE : 1;
		return rainStorm(wind, r, kind, ox, oz, birth, life, latestDeath(outlooks, ox, oz, birth));
	}

	/**
	 * When a storm forming at {@code birth} must be gone: a quarter day into the next day, or {@link #DRY_CLEAR_BEFORE}
	 * before it starts when the next day is dry.
	 */
	static long latestDeath(@Nullable Outlooks outlooks, double x, double z, long birth) {
		if(outlooks == null)
			return Long.MAX_VALUE;
		long end = outlooks.dayEnd(birth);
		DayOutlook next = outlooks.at(x, z, end);
		return next != null && next.type() == DayType.DRY ? end - DRY_CLEAR_BEFORE : end + DAY_GRACE;
	}

	/**
	 * The weights for a storm forming at {@code birth} on {@code day}: severe ones near a shift, tornadoes on watch days;
	 * on a dry day only the light kinds.
	 */
	static double[] dayWeights(SteeringWind wind, double[] base, @Nullable DayOutlook day, long birth) {
		double[] weights = base.clone();
		if(day != null && day.type() == DayType.DRY) {
			for(int i = DRY_KINDS; i < weights.length; i++)
				weights[i] = 0;
			return weights;
		}
		if(wind.nearShift(birth))
			for(int i = 3; i < weights.length; i++)
				weights[i] *= SHIFT_SEVERE_BOOST;
		if(day != null && weights.length > 5) {
			weights[5] *= day.tornadoWatch() ? WATCH_TORNADO : NO_WATCH_TORNADO;
			if(day.tornadoWatch())
				weights[4] *= WATCH_SUPERCELL;
		}
		return weights;
	}

	/** A rain-bearing storm of {@code kind}, living {@code lifeScale} times as long but dead by {@code latestDeath}. */
	@Nullable
	private static StormCell rainStorm(SteeringWind wind, SplittableRandom r, StormKind kind, double ox, double oz,
			long birth, double lifeScale, long latestDeath) {
		StormProfile p = StormProfile.of(kind);
		int life = (int)(lerp(r.nextDouble(), p.minLifetime(), p.maxLifetime()) * lifeScale);
		double radius = lerp(r.nextDouble(), p.minRadius(), p.maxRadius());
		float intensity = (float)lerp(r.nextDouble(), p.minIntensity(), p.maxIntensity());
		double driftX = (r.nextDouble() * 2 - 1) * MAX_DRIFT, driftZ = (r.nextDouble() * 2 - 1) * MAX_DRIFT;
		long seed = r.nextLong();
		if(birth + life > latestDeath) {
			if(latestDeath - birth < p.minLifetime() / 2)
				return null;
			life = (int)(latestDeath - birth);
		}
		return new StormCell(0, kind, birth, life, ox, oz, driftX, driftZ, radius, intensity, seed, wind);
	}

	/** A fog bank, heat wave, cold snap or spell of high wind. */
	private static StormCell event(SteeringWind wind, SplittableRandom r, double ox, double oz, long birth, StormKind kind) {
		StormProfile p = StormProfile.of(kind);
		int life = (int)lerp(r.nextDouble(), p.minLifetime(), p.maxLifetime());
		double radius = lerp(r.nextDouble(), p.minRadius(), p.maxRadius());
		float intensity = (float)lerp(r.nextDouble(), p.minIntensity(), p.maxIntensity());
		double driftX = (r.nextDouble() * 2 - 1) * MAX_DRIFT, driftZ = (r.nextDouble() * 2 - 1) * MAX_DRIFT;
		return new StormCell(0, kind, birth, life, ox, oz, driftX, driftZ, radius, intensity, r.nextLong(), wind);
	}

	/** A hurricane, if ({@code ox}, {@code oz}) is ocean: it wanders off with the wind and its own drift for days. */
	@Nullable
	private static StormCell hurricane(SteeringWind wind, SplittableRandom r, double ox, double oz, long birth,
			Climate climate, @Nullable Outlooks outlooks) {
		StormProfile p = StormProfile.HURRICANE;
		int life = (int)lerp(r.nextDouble(), p.minLifetime(), p.maxLifetime());
		// Never on a dry day (2026-11-13): none forms on one, and one dies out before the first dry day it would run into.
		if(outlooks != null) {
			if(outlooks.at(ox, oz, birth).type() == DayType.DRY)
				return null;
			for(long day = outlooks.dayEnd(birth); day < birth + life; day = outlooks.dayEnd(day))
				if(outlooks.at(ox, oz, day).type() == DayType.DRY) {
					life = (int)(day - DRY_CLEAR_BEFORE - birth); // Broken up before the dry day comes.
					break;
				}
			if(life < p.minLifetime() / 2)
				return null;
		}
		double radius = lerp(r.nextDouble(), p.minRadius(), p.maxRadius());
		float intensity = (float)lerp(r.nextDouble(), p.minIntensity(), p.maxIntensity());
		double driftX = (r.nextDouble() * 2 - 1) * HURRICANE_DRIFT, driftZ = (r.nextDouble() * 2 - 1) * HURRICANE_DRIFT;
		long seed = r.nextLong();
		if(!climate.isOcean((int)Math.floor(ox), (int)Math.floor(oz)))
			return null;
		return new StormCell(0, StormKind.HURRICANE, birth, life, ox, oz, driftX, driftZ, radius, intensity, seed, wind);
	}

	/** A front: a line across the wind that sweeps along with it, setting off cells. */
	record Front(long seed, long birth, int life, double length, double dirX, double dirZ, StormCell track) {

		/** Where the point {@code along} blocks from the middle of the line is at {@code gameTime}. */
		double x(long gameTime, double along) {
			return track.centerX(gameTime) + dirX * along;
		}

		double z(long gameTime, double along) {
			return track.centerZ(gameTime) + dirZ * along;
		}
	}

	/** Every front that might set off a cell within reach of a viewer, with its cells handed to {@code sink}. */
	private void fronts(SteeringWind wind, List<Viewer> viewers, double within, long from, long to, WindTrack track,
			double[] base, @Nullable Outlooks outlooks, double scale, Sink sink) {
		long cellLife = (long)(StormProfile.TORNADO.maxLifetime() * FRONT_CELL_LIFE);
		scatter(FRONT, FRONT_CELL, FRONT_SLOT, FRONT_MEAN * FRONT_MAX * scale, FRONT_MAX_LIFE + cellLife, new Motion(0.2, 0.25, MAX_DRIFT),
				FRONT_MAX_LENGTH / 2.0 + 320 * 1.4, viewers, within, from, to, track, (r, ox, oz, birth) -> {
					Front front = front(wind, r, ox, oz, birth, outlooks);
					if(front != null)
						frontCells(wind, front, base, outlooks, sink);
					return null;
				}, cell -> {
				});
	}

	/** The front rolled from {@code r}, formed with its middle at ({@code ox}, {@code oz}); null if the day has none. */
	@Nullable
	static Front front(SteeringWind wind, SplittableRandom r, double ox, double oz, long birth, @Nullable Outlooks outlooks) {
		double keep = r.nextDouble();
		int life = (int)lerp(r.nextDouble(), FRONT_MIN_LIFE, FRONT_MAX_LIFE);
		double length = lerp(r.nextDouble(), FRONT_MIN_LENGTH, FRONT_MAX_LENGTH);
		long seed = r.nextLong();
		DayOutlook day = outlooks == null ? null : outlooks.at(ox, oz, birth);
		double rate = day == null ? 1 : day.type() == DayType.DRY ? 0 : day.type() == DayType.RAINY ? FRONT_RAINY : 1;
		if(wind.nearShift(birth) || wind.nearShift(birth + life / 2))
			rate *= FRONT_SHIFT;
		if(keep >= rate / FRONT_MAX)
			return null;
		// Across the wind it forms in; it rides the wind like a thunderstorm.
		double wx = wind.x(birth), wz = wind.z(birth), w = Math.max(1e-6, Math.hypot(wx, wz));
		StormCell track = new StormCell(0, StormKind.SEVERE_THUNDERSTORM, birth, life, ox, oz, 0, 0, 1, 1f, seed, wind);
		return new Front(seed, birth, life, length, -wz / w, wx / w, track);
	}

	/** The cells {@code front} sets off along its line as it goes. */
	static void frontCells(SteeringWind wind, Front front, double[] base, @Nullable Outlooks outlooks, Sink sink) {
		int segments = (int)Math.ceil(front.length() / FRONT_SEGMENT);
		double wx = front.dirZ(), wz = -front.dirX(); // Along the wind.
		for(long step = 0; step * FRONT_STEP < front.life() * 0.85; step++)
			for(int s = 0; s < segments; s++) {
				SplittableRandom r = new SplittableRandom(Noise.mix(front.seed() ^ Noise.mix(step * 0x9E3779B97F4A7C15L
						^ Noise.mix(s * 0xC2B2AE3D27D4EB4FL))));
				double along = -front.length() / 2 + (s + r.nextDouble()) * FRONT_SEGMENT;
				double end = 2 * along / front.length();
				if(r.nextDouble() >= FRONT_CELL_CHANCE * (1 - end * end * end * end))
					continue;
				long birth = front.birth() + step * FRONT_STEP + r.nextLong(FRONT_STEP);
				double across = (r.nextDouble() * 2 - 1) * FRONT_WIDTH;
				double x = front.x(birth, along) + wx * across, z = front.z(birth, along) + wz * across;
				DayOutlook day = outlooks == null ? null : outlooks.at(x, z, birth);
				if(day != null && day.type() == DayType.DRY)
					continue; // A front runs out of steam on a dry day: it sets nothing off (2026-11-13).
				double[] weights = dayWeights(wind, base, day, birth);
				for(int i = 0; i < weights.length && i < FRONT_SEVERITY.length; i++)
					weights[i] *= FRONT_SEVERITY[i];
				StormKind kind = pick(r.nextDouble(), weights);
				StormCell cell = rainStorm(wind, r, kind, x, z, birth, FRONT_CELL_LIFE, latestDeath(outlooks, x, z, birth));
				if(cell != null)
					sink.accept(cell);
			}
	}

	/** The fastest {@code cell} can go, blocks a tick. */
	private static double maxSpeed(StormCell cell) {
		return cell.profile().motionFactor() * SteeringWind.MAX_SPEED / 20 + Math.hypot(cell.driftX(), cell.driftZ());
	}

	private static long maxLife(StormProfile... profiles) {
		long max = 0;
		for(StormProfile p : profiles)
			max = Math.max(max, p.maxLifetime());
		return max;
	}

	/**
	 * How much ground a storm of {@code p} passes over within {@link #CROSS} of its radius in its life, on average,
	 * blocks squared: a place sees {@code rate * area} of them per tick for {@code rate} storms per block squared and tick.
	 */
	static double area(StormProfile p, double lifeScale, double drift) {
		return crossArea(p, lifeScale, drift, CROSS);
	}

	static double crossArea(StormProfile p, double lifeScale, double drift, double cross) {
		double d = cross * (p.minRadius() + p.maxRadius()) / 2.0;
		double life = (p.minLifetime() + p.maxLifetime()) / 2.0 * lifeScale;
		double speed = p.motionFactor() * MEAN_WIND / 20 + drift * 0.77; // Mean drift speed for a uniform square.
		return 2 * d * speed * life + Math.PI * d * d;
	}

	/** {@link #area} over the rain-bearing kinds by {@code weights}. */
	static double rainArea(double[] weights, double lifeScale) {
		StormKind[] kinds = { StormKind.SHOWER, StormKind.THUNDERSTORM, StormKind.STRONG_THUNDERSTORM,
				StormKind.SEVERE_THUNDERSTORM, StormKind.SUPERCELL, StormKind.TORNADO };
		double sum = 0, total = 0;
		for(int i = 0; i < kinds.length && i < weights.length; i++) {
			sum += weights[i] * area(StormProfile.of(kinds[i]), lifeScale, MAX_DRIFT);
			total += weights[i];
		}
		return total <= 0 ? 1 : sum / total;
	}

	/** Mean storms per square and stretch for {@code perPlace} passing over a place each tick, each sweeping {@code area}. */
	static double perCellSlot(double perPlace, double area, double cellSize, long slotTicks) {
		return perPlace <= 0 ? 0 : perPlace / area * cellSize * cellSize * slotTicks;
	}

	static StormKind pick(double roll, double[] weights) {
		StormKind[] kinds = { StormKind.SHOWER, StormKind.THUNDERSTORM, StormKind.STRONG_THUNDERSTORM,
				StormKind.SEVERE_THUNDERSTORM, StormKind.SUPERCELL, StormKind.TORNADO };
		double total = 0;
		for(double w : weights)
			total += w;
		double r = roll * total;
		for(int i = 0; i < kinds.length && i < weights.length; i++) {
			if(r < weights[i])
				return kinds[i];
			r -= weights[i];
		}
		return StormKind.SHOWER;
	}

	/** A Poisson draw with mean {@code mean}. */
	static int poisson(SplittableRandom random, double mean) {
		if(mean <= 0)
			return 0;
		if(mean > 40)
			return Math.max(0, (int)Math.round(mean + Math.sqrt(mean) * gaussian(random)));
		double limit = Math.exp(-mean), p = 1;
		int n = -1;
		do {
			n++;
			p *= random.nextDouble();
		} while(p > limit);
		return n;
	}

	private static double gaussian(SplittableRandom random) {
		return Math.sqrt(-2 * Math.log(1 - random.nextDouble())) * Math.cos(2 * Math.PI * random.nextDouble());
	}

	private static double lerp(double t, double a, double b) {
		return a + (b - a) * t;
	}
}
