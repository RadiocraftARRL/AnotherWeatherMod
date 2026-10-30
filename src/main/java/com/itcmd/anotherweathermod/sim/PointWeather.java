package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.StormKind;
import org.jetbrains.annotations.Nullable;

/**
 * The raw storm weather at one point and time, before the ground (biome, height) is taken into account.
 *
 * @param rain           0..1, the strongest storm's rain
 * @param thunder        0..1, the strongest storm's thunder
 * @param windX          east wind, metres per second (steering wind plus every storm's)
 * @param windZ          south wind, metres per second
 * @param gust           the wind speed here in a full gust, metres per second (the wind itself rises and falls
 *                       between a lull and this)
 * @param pressureDrop   hectopascals below normal
 * @param eye            inside a hurricane's eye
 * @param storm          the storm with the most rain here, or null where none rains
 * @param stormIntensity that storm's overall intensity, 0..1
 * @param hailBurst      that storm is a hurricane throwing down a burst of hail here
 * @param fog            0..1, how thick a fog bank is here
 * @param heat           0..1, how strong a heat wave is here (whatever the time of day or the ground)
 * @param cold           0..1, how strong a cold snap is here (likewise)
 * @param tornado        0..1, how close a tornado is: 1 in its core, 0 from twice its radius out
 * @param highWind       0..1, how strong a spell of high wind is here
 */
public record PointWeather(double rain, double thunder, double windX, double windZ, double gust, double pressureDrop,
		boolean eye, @Nullable StormCell storm, float stormIntensity, boolean hailBurst, double fog, double heat,
		double cold, double tornado, double highWind) {

	/** Weather with no high wind. */
	public PointWeather(double rain, double thunder, double windX, double windZ, double gust, double pressureDrop,
			boolean eye, @Nullable StormCell storm, float stormIntensity, boolean hailBurst, double fog, double heat,
			double cold, double tornado) {
		this(rain, thunder, windX, windZ, gust, pressureDrop, eye, storm, stormIntensity, hailBurst, fog, heat, cold,
				tornado, 0);
	}

	public double windSpeed() {
		return Math.sqrt(windX * windX + windZ * windZ);
	}

	@Nullable
	public StormKind stormKind() {
		return storm == null ? null : storm.kind();
	}

	/** The storm's category ({@link StormCell#category()}), or -1 where none rains. */
	public int stormCategory() {
		return storm == null ? -1 : storm.category();
	}

	/** Collects storms' contributions. */
	public static final class Builder {
		private double rain;
		private double thunder;
		private double windX;
		private double windZ;
		private double gustX;
		private double gustZ;
		private double pressureDrop;
		private boolean eye;
		private StormCell storm;
		private float stormIntensity;
		private boolean hailBurst;
		private double fog;
		private double heat;
		private double cold;
		private double tornado;
		private double highWind;

		public Builder(double windX, double windZ) {
			this.windX = windX;
			this.windZ = windZ;
			this.gustX = windX;
			this.gustZ = windZ;
		}

		void add(StormCell cell, double rain, double thunder, double windX, double windZ, double gustX, double gustZ,
				double pressureDrop, boolean eye, float intensity, boolean hailBurst) {
			if(rain > this.rain) { // Not a gust front with no rain yet.
				storm = cell;
				stormIntensity = intensity;
				this.hailBurst = hailBurst;
			}
			this.rain = Math.max(this.rain, rain);
			this.thunder = Math.max(this.thunder, thunder);
			this.windX += windX;
			this.windZ += windZ;
			this.gustX += gustX;
			this.gustZ += gustZ;
			this.pressureDrop = Math.max(this.pressureDrop, pressureDrop);
			this.eye |= eye;
		}

		/** A fog bank, heat wave, cold snap or high wind of strength {@code value} here. */
		void addEvent(StormKind kind, double value) {
			switch(kind) {
				case DENSE_FOG -> fog = Math.max(fog, value);
				case HEAT_WAVE -> heat = Math.max(heat, value);
				case COLD_SNAP -> cold = Math.max(cold, value);
				case HIGH_WIND -> highWind = Math.max(highWind, value);
				default -> {
				}
			}
		}

		void addTornado(double closeness) {
			tornado = Math.max(tornado, closeness);
		}

		public PointWeather build() {
			double gust = Math.max(Math.sqrt(windX * windX + windZ * windZ), Math.sqrt(gustX * gustX + gustZ * gustZ));
			return new PointWeather(Math.min(1, rain), Math.min(1, thunder), windX, windZ, gust, pressureDrop, eye,
					storm, stormIntensity, hailBurst, fog, heat, cold, tornado, highWind);
		}
	}
}
