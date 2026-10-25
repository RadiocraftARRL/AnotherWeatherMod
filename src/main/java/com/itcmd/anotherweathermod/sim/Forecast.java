package com.itcmd.anotherweathermod.sim;

import java.util.Optional;

/** Looks ahead along storms' paths. */
public final class Forecast {

	/** How finely arrivals are searched, ticks. */
	static final int STEP = 100;

	private Forecast() {
	}

	/**
	 * When a storm's rain reaches a point.
	 *
	 * @param cell      the storm
	 * @param arrival   first tick it rains there
	 * @param departure last tick it rains there (or the end of the search)
	 * @param peakTick  when its rain there is heaviest
	 */
	public record Arrival(StormCell cell, long arrival, long departure, long peakTick) {
	}

	/**
	 * The first storm, active or pending, to rain on ({@code x}, {@code z}) between {@code now} and
	 * {@code now + horizon}. A storm raining there now counts, with an arrival of now.
	 */
	public static Optional<Arrival> nextArrival(StormField field, double x, double z, long now, long horizon) {
		Arrival best = null;
		long end = now + horizon;
		for(StormCell cell : field.all()) {
			long from = Math.max(now, cell.birthTick());
			long to = Math.min(end, cell.deathTick());
			if(from >= to || (best != null && from >= best.arrival()))
				continue;
			// Can it reach here at all? Distance now versus how far it can travel plus its radius.
			double dx = cell.centerX(from) - x;
			double dz = cell.centerZ(from) - z;
			double reach = cell.peakRadius() + (to - from) * 0.5; // 10 m/s, faster than any storm moves.
			if(dx * dx + dz * dz > reach * reach)
				continue;
			Arrival arrival = search(cell, x, z, from, to);
			if(arrival != null && (best == null || arrival.arrival() < best.arrival()))
				best = arrival;
		}
		return Optional.ofNullable(best);
	}

	private static Arrival search(StormCell cell, double x, double z, long from, long to) {
		long arrival = -1;
		long peakTick = -1;
		double peak = 0;
		for(long t = from; t <= to; t += STEP) {
			double rain = rainAt(cell, x, z, t);
			if(rain > WeatherModel.RAINING) {
				if(arrival < 0)
					arrival = t;
				if(rain > peak) {
					peak = rain;
					peakTick = t;
				}
			}
			else if(arrival >= 0)
				return new Arrival(cell, arrival, t - STEP, peakTick);
		}
		return arrival < 0 ? null : new Arrival(cell, arrival, to, peakTick);
	}

	/** One storm's rain at a point, ignoring the others. */
	public static double rainAt(StormCell cell, double x, double z, long t) {
		PointWeather.Builder acc = new PointWeather.Builder(0, 0);
		cell.contribute(x, z, t, acc);
		return acc.build().rain();
	}
}
