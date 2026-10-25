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
 * @param pressureDrop   hectopascals below normal
 * @param eye            inside a hurricane's eye
 * @param storm          the storm with the most rain here, or null
 * @param stormIntensity that storm's overall intensity, 0..1
 */
public record PointWeather(double rain, double thunder, double windX, double windZ, double pressureDrop, boolean eye,
		@Nullable StormCell storm, float stormIntensity) {

	public double windSpeed() {
		return Math.sqrt(windX * windX + windZ * windZ);
	}

	@Nullable
	public StormKind stormKind() {
		return storm == null ? null : storm.kind();
	}

	/** Collects storms' contributions. */
	public static final class Builder {
		private double rain;
		private double thunder;
		private double windX;
		private double windZ;
		private double pressureDrop;
		private boolean eye;
		private StormCell storm;
		private float stormIntensity;

		public Builder(double windX, double windZ) {
			this.windX = windX;
			this.windZ = windZ;
		}

		void add(StormCell cell, double rain, double thunder, double windX, double windZ, double pressureDrop,
				boolean eye, float intensity) {
			if(storm == null || rain > this.rain) {
				storm = cell;
				stormIntensity = intensity;
			}
			this.rain = Math.max(this.rain, rain);
			this.thunder = Math.max(this.thunder, thunder);
			this.windX += windX;
			this.windZ += windZ;
			this.pressureDrop = Math.max(this.pressureDrop, pressureDrop);
			this.eye |= eye;
		}

		public PointWeather build() {
			return new PointWeather(Math.min(1, rain), Math.min(1, thunder), windX, windZ, pressureDrop, eye, storm,
					stormIntensity);
		}
	}
}
