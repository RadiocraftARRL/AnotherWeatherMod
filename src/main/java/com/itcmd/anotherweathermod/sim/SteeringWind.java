package com.itcmd.anotherweathermod.sim;

/**
 * The prevailing wind that steers storms. A pure function of the world seed and the game time, so its value at any
 * future tick is known and forecasts can follow it exactly.
 *
 * <p>Directions are compass bearings the wind blows <em>toward</em>, in radians: 0 toward north (-z), pi/2 toward east
 * (+x).
 */
public final class SteeringWind {

	/** Ticks between the slow direction knots (a day and a half). */
	static final double DIRECTION_PERIOD = 36000;
	/** Ticks between the faster wobble knots. */
	static final double WOBBLE_PERIOD = 9000;
	/** Ticks between speed knots. */
	static final double SPEED_PERIOD = 12000;

	public static final double MIN_SPEED = 3;
	public static final double MAX_SPEED = 10;

	private final long seed;
	private final double baseDirection;

	public SteeringWind(long seed) {
		this.seed = seed;
		this.baseDirection = Noise.unit(seed, 0x5eed) * Math.PI * 2;
	}

	/** The bearing the wind blows toward at {@code gameTime}, radians. */
	public double direction(long gameTime) {
		double slow = Noise.smooth(seed ^ 0x1d1d, gameTime / DIRECTION_PERIOD) * Math.PI;
		double wobble = Noise.smooth(seed ^ 0x2e2e, gameTime / WOBBLE_PERIOD) * Math.PI * 0.25;
		return baseDirection + slow + wobble;
	}

	/** Wind speed at {@code gameTime}, metres per second. */
	public double speed(long gameTime) {
		double n = Noise.smooth(seed ^ 0x3f3f, gameTime / SPEED_PERIOD); // -1..1
		return (MIN_SPEED + MAX_SPEED) / 2 + n * (MAX_SPEED - MIN_SPEED) / 2;
	}

	/** East component at {@code gameTime}, metres per second. */
	public double x(long gameTime) {
		return Math.sin(direction(gameTime)) * speed(gameTime);
	}

	/** South component at {@code gameTime}, metres per second. */
	public double z(long gameTime) {
		return -Math.cos(direction(gameTime)) * speed(gameTime);
	}
}
