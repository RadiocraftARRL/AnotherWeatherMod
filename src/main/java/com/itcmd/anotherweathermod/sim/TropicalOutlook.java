package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.api.TropicalAdvisory;
import com.itcmd.anotherweathermod.api.TropicalAdvisory.Stage;
import com.itcmd.anotherweathermod.api.TropicalAdvisory.TrackPoint;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Early warnings of hurricanes (2026-11-15, the maintainer: "a tropical storm is forming etc, and a chance that this
 * ends up growing into a hurricane. Those warnings go to players that are within the path of the hurricane +/- 200%
 * of the storm's width so that it might be a false alarm ... so players could plan, board up windows"). Hurricanes are
 * known from the seed days ahead, so a place hears of one up to {@link #LEAD} before it may arrive:
 * <ul>
 * <li>{@link Stage#DISTURBANCE}: it has not formed yet. Its track is forecast as if it lived on, with its chance of
 * developing ({@link TropicalDevelopment}): one that fizzles out never comes, a false alarm.</li>
 * <li>{@link Stage#OUTLOOK}: formed, more than {@link #WATCH} away.</li>
 * <li>{@link Stage#WATCH}: formed, within {@link #WATCH}.</li>
 * <li>{@link Stage#WARNING}: within {@link #WARNING}, or here.</li>
 * </ul>
 * A place is in the cone while the forecast track passes within {@link #CONE_WIDTHS} of the storm's width (twice its
 * radius) of it: wide, so many in the cone are missed. Pure (no game classes), so it is unit tested.
 */
public final class TropicalOutlook {

	public static final long LEAD = 2 * DayTypes.DAY, WATCH = DayTypes.DAY, WARNING = DayTypes.DAY / 2;
	/** The cone round the track, in storm widths each side (the maintainer's "+/- 200% of the storm's width"). */
	public static final double CONE_WIDTHS = 2.0;
	/**
	 * How far round a place hurricanes are looked for, blocks: the widest cone (twice the widest storm's width each
	 * side) and as far as the fastest moves in {@link #LEAD}, so a disturbance that will fizzle (its own life short) is
	 * still found while its forecast track comes this way.
	 */
	public static final double SEARCH_REACH = CONE_WIDTHS * 2 * StormProfile.HURRICANE.maxRadius() + 3500;
	/** How finely the track is searched for the arrival, ticks. */
	static final long SEARCH_STEP = 200;

	private TropicalOutlook() {
	}

	/** The cone's half width round the track of {@code cell}, blocks. */
	public static double coneRadius(StormCell cell) {
		return CONE_WIDTHS * 2 * cell.peakRadius();
	}

	/** The advisory for ({@code x}, {@code z}) about hurricane {@code cell} at {@code now}, or null if it is not in the cone. */
	@Nullable
	public static TropicalAdvisory advise(StormCell cell, double x, double z, long now) {
		if(cell.kind() != StormKind.HURRICANE || now >= cell.deathTick())
			return null;
		boolean formed = now >= cell.birthTick();
		// A disturbance's track as it would run if it went on: forecast, not yet known to fizzle.
		StormCell track = formed ? cell : cell.withLifetime((int)Math.max(cell.lifetimeTicks(), now + LEAD - cell.birthTick()));
		double cone = coneRadius(cell);
		long until = Math.min(track.deathTick(), now + LEAD);
		long arrival = -1;
		for(long t = Math.max(now, track.birthTick()); t <= until; t += SEARCH_STEP)
			if(Math.hypot(track.centerX(t) - x, track.centerZ(t) - z) <= cone) {
				arrival = t;
				break;
			}
		if(arrival < 0)
			return null;
		long ahead = arrival - now;
		Stage stage = !formed ? Stage.DISTURBANCE : ahead <= WARNING ? Stage.WARNING : ahead <= WATCH ? Stage.WATCH : Stage.OUTLOOK;
		int category = formed ? cell.category() : -1;
		int chance = formed ? (category >= 1 ? 100 : 0) : TropicalDevelopment.chancePercent(cell.seed());
		List<TrackPoint> points = new ArrayList<>();
		for(long t = Math.max(now, track.birthTick()); t <= until; t += TropicalAdvisory.TRACK_STEP)
			points.add(new TrackPoint(t, track.centerX(t), track.centerZ(t), cell.peakRadius()));
		return new TropicalAdvisory(cell.seed(), stage, formed && category >= 1, category, chance, cell.birthTick(), arrival, cone, List.copyOf(points));
	}
}
