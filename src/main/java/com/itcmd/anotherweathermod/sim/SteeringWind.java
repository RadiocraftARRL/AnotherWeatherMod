package com.itcmd.anotherweathermod.sim;

/**
 * The prevailing wind that steers storms. A pure function of the world seed and the game time, so its value at any
 * future tick is known and forecasts can follow it exactly.
 *
 * <p>Directions are compass bearings the wind blows <em>toward</em>, in radians: 0 toward north (-z), pi/2 toward east
 * (+x).
 *
 * <p>Besides its slow drift, the wind shifts sharply now and then, as when a front passes: about once every four
 * in-game days it swings 60 to 150 degrees over a couple of in-game hours and holds its new direction. Shifts take
 * turns veering and backing, so the wind keeps to its general direction over the long run. Storms forming near a
 * shift are a little more often severe (see {@link StormGenerator}).
 */
public final class SteeringWind {

	/** Ticks between the slow direction knots (a day and a half). */
	static final double DIRECTION_PERIOD = 36000;
	/** Ticks between the faster wobble knots. */
	static final double WOBBLE_PERIOD = 9000;
	/** Ticks between speed knots. */
	static final double SPEED_PERIOD = 12000;
	/** One wind shift falls somewhere in each stretch of this many ticks (four in-game days). */
	static final long SHIFT_PERIOD = 96000;
	/** Ticks a shift takes to swing the wind round (two in-game hours). */
	static final long SHIFT_TICKS = 2000;
	/** Storms near a shift are more often severe from this many ticks before it starts... */
	public static final long FRONT_BEFORE = 1500;
	/** ...to this many after. */
	public static final long FRONT_AFTER = 3000;

	/** The bearing the wind blows from in general, by default: west. */
	public static final double DEFAULT_FROM = 270;

	public static final double MIN_SPEED = 3;
	public static final double MAX_SPEED = 10;

	private final long seed;
	private final double baseDirection;

	/** A wind that in general blows from the west (westerlies). */
	public SteeringWind(long seed) {
		this(seed, DEFAULT_FROM);
	}

	/**
	 * @param fromDegrees the compass bearing the wind blows from in general (270 west); each world turns it up to 20
	 *                    degrees either way
	 */
	public SteeringWind(long seed, double fromDegrees) {
		this.seed = seed;
		this.baseDirection = Math.toRadians(fromDegrees + 180) + Noise.signed(seed, 0x5eed) * Math.toRadians(20);
	}

	/** The bearing the wind blows toward at {@code gameTime}, radians. */
	public double direction(long gameTime) {
		double slow = Noise.smooth(seed ^ 0x1d1d, gameTime / DIRECTION_PERIOD) * Math.PI;
		double wobble = Noise.smooth(seed ^ 0x2e2e, gameTime / WOBBLE_PERIOD) * Math.PI * 0.25;
		return baseDirection + slow * 0.5 + wobble + shiftOffset(gameTime);
	}

	/** The tick the shift in stretch {@code k} starts: somewhere in its middle 70 %. */
	long shiftTick(long k) {
		return k * SHIFT_PERIOD + (long)(SHIFT_PERIOD * (0.15 + 0.7 * Noise.unit(seed ^ 0x4a4a, k)));
	}

	/**
	 * The direction offset the shift in stretch {@code k} leaves, radians: 30 to 75 degrees either side of the general
	 * direction, the side alternating, so each shift turns the wind 60 to 150 degrees.
	 */
	private double regime(long k) {
		double size = Math.PI / 6 + Noise.unit(seed ^ 0x4b4b, k) * Math.PI / 4;
		return Math.floorMod(k, 2) == 0 ? size : -size;
	}

	/** The latest stretch whose shift has started by {@code gameTime}. */
	private long lastShift(long gameTime) {
		long k = Math.floorDiv(gameTime, SHIFT_PERIOD);
		return gameTime >= shiftTick(k) ? k : k - 1;
	}

	private double shiftOffset(long gameTime) {
		long k = lastShift(gameTime);
		double f = Math.min(1, (gameTime - shiftTick(k)) / (double)SHIFT_TICKS);
		double s = f * f * (3 - 2 * f);
		return regime(k - 1) + (regime(k) - regime(k - 1)) * s;
	}

	/** The first tick after {@code gameTime} that a wind shift starts. */
	public long nextShift(long gameTime) {
		return shiftTick(lastShift(gameTime) + 1);
	}

	/** True around a wind shift, when storms forming are more often severe. */
	public boolean nearShift(long gameTime) {
		long last = shiftTick(lastShift(gameTime));
		return gameTime - last <= FRONT_AFTER || nextShift(gameTime) - gameTime <= FRONT_BEFORE;
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
