package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.api.StormStage;
import com.itcmd.anotherweathermod.api.StormView;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * One storm. Everything about it is a function of its birth parameters and the game time: nothing reads the world to
 * move or grow it, which is what makes forecasts exact.
 *
 * <p>Its path follows the {@link SteeringWind} (times its kind's motion factor) plus its own fixed drift, integrated in
 * {@link #STEP} tick steps and cached.
 */
public final class StormCell implements StormView {

	/** Path integration step, ticks. */
	static final int STEP = 100;

	static final double FORMING_END = 0.2;
	static final double DECAY_START = 0.7;
	/** Hurricane eye, as a fraction of the radius. */
	static final double EYE = 0.06;
	/** Hurricane eyewall (strongest wind), as a fraction of the radius. */
	static final double EYEWALL = 0.14;
	/**
	 * A thunderstorm's gust front reaches this far out, as a fraction of its radius: the wind picks up before the
	 * rain comes, and dies down after it has gone.
	 */
	static final double GUST_FRONT = 1.4;
	/**
	 * A hurricane's eyewall wind at full strength, metres per second, by category 0 to 5: a tropical storm (about a
	 * severe thunderstorm's), then up the Saffir-Simpson scale to a monster.
	 */
	static final double[] HURRICANE_WIND = { 28, 38, 47, 58, 68, 82 };
	/** Pressure drop at the eye, hectopascals, by category. */
	static final double[] HURRICANE_PRESSURE = { 10, 22, 32, 45, 60, 78 };
	/** How often each hurricane category comes, 0 to 5. */
	static final double[] CATEGORY_ODDS = { 0.18, 0.26, 0.22, 0.17, 0.11, 0.06 };
	/** How often each tornado rating comes, EF0 to EF5. */
	static final double[] TORNADO_ODDS = { 0.30, 0.30, 0.20, 0.12, 0.06, 0.02 };
	/** A tornado's core radius, blocks, and top wind, metres per second, by rating EF0 to EF5. */
	static final double[] TORNADO_RADIUS = { 6, 10, 16, 24, 34, 48 };
	static final double[] TORNADO_WIND = { 33, 43, 55, 70, 87, 105 };
	/** When in its life a tornadic storm's tornado is down. */
	static final double TOUCHDOWN = 0.32;
	static final double LIFT_OFF = 0.72;
	/** A tornado's winds reach this many of its radii out. */
	static final double TORNADO_REACH = 4;
	/** A thunderstorm's wind in a lull, as a share of its wind in a full gust. */
	static final double LULL = 0.6;
	/** Over land a hurricane's strength falls by e in this many ticks (half in about half a day). */
	static final double LAND_DECAY = 17000;
	/** Over the ocean it wins back what it lost at this pace, ticks. */
	static final double OCEAN_RECOVERY = 48000;

	private final long id;
	private final StormKind kind;
	private final long birthTick;
	private final int lifetimeTicks;
	private final double originX;
	private final double originZ;
	private final double driftX;
	private final double driftZ;
	private final double peakRadius;
	private final float peakIntensity;
	private final long seed;
	/** A hurricane's category or a tornado's rating, 0 to 5; for other storms their scale. */
	private final int category;
	private final StormProfile profile;

	private SteeringWind wind;
	private double[] pathX = new double[0];
	private double[] pathZ = new double[0];
	private int pathLength;
	/** Where hurricanes weaken, or null. */
	private Climate land;
	/** A hurricane's strength left at each path step, 0..1, from its time over land. Only with {@link #land}. */
	private float[] landPath = new float[0];

	/** The last stage the field reported, for {@code StageChanged} events. Not saved. */
	StormStage reportedStage;

	public StormCell(long id, StormKind kind, long birthTick, int lifetimeTicks, double originX, double originZ,
			double driftX, double driftZ, double peakRadius, float peakIntensity, long seed, SteeringWind wind) {
		this(id, kind, birthTick, lifetimeTicks, originX, originZ, driftX, driftZ, peakRadius, peakIntensity, seed,
				kind == StormKind.HURRICANE ? draw(seed, CATEGORY_ODDS)
						: kind == StormKind.TORNADO ? draw(seed, TORNADO_ODDS) : kind.scale(), wind);
	}

	public StormCell(long id, StormKind kind, long birthTick, int lifetimeTicks, double originX, double originZ,
			double driftX, double driftZ, double peakRadius, float peakIntensity, long seed, int category,
			SteeringWind wind) {
		this.id = id;
		this.category = kind == StormKind.HURRICANE || kind == StormKind.TORNADO ? Math.max(0, Math.min(5, category))
				: kind.scale();
		this.kind = kind;
		this.birthTick = birthTick;
		this.lifetimeTicks = Math.max(STEP, lifetimeTicks);
		this.originX = originX;
		this.originZ = originZ;
		this.driftX = driftX;
		this.driftZ = driftZ;
		this.peakRadius = peakRadius;
		this.peakIntensity = peakIntensity;
		this.seed = seed;
		this.profile = StormProfile.of(kind);
		this.wind = wind;
	}

	/**
	 * A storm that is at full strength now, centred on ({@code x}, {@code z}), with no drift of its own: for commands
	 * and tests.
	 */
	public static StormCell matureAt(StormKind kind, double x, double z, long now, int lifetimeTicks, double radius,
			float intensity, long seed, SteeringWind wind) {
		return atLife(kind, x, z, now, FORMING_END, lifetimeTicks, radius, intensity, seed, wind);
	}

	/**
	 * A storm {@code lifeFraction} of the way through its life now, centred on ({@code x}, {@code z}), with no drift of
	 * its own: for commands and tests.
	 */
	public static StormCell atLife(StormKind kind, double x, double z, long now, double lifeFraction,
			int lifetimeTicks, double radius, float intensity, long seed, SteeringWind wind) {
		long birth = now - (long)(lifetimeTicks * lifeFraction);
		StormCell cell = new StormCell(0, kind, birth, lifetimeTicks, 0, 0, 0, 0, radius, intensity, seed, wind);
		return cell.withPath(x - cell.centerX(now), z - cell.centerZ(now), 0, 0);
	}

	/**
	 * A storm forming now that reaches ({@code x}, {@code z}) at full strength at {@code arrival}, coming with the
	 * steering wind: for commands and tests.
	 */
	public static StormCell arrivingAt(StormKind kind, double x, double z, long now, long arrival, int lifetimeTicks,
			double radius, float intensity, long seed, SteeringWind wind) {
		// Arrive while mature: between a quarter and two thirds of the way through its life.
		long travel = Math.max(600, arrival - now);
		int life = (int)Math.max(travel / 0.65, Math.min(lifetimeTicks, travel / 0.25));
		StormCell cell = new StormCell(0, kind, now, life, 0, 0, 0, 0, radius, intensity, seed, wind);
		return cell.withPath(x - cell.centerX(arrival), z - cell.centerZ(arrival), 0, 0);
	}

	/** The same storm with a different origin and drift: used to place a storm so its path hits a target. */
	StormCell withPath(double originX, double originZ, double driftX, double driftZ) {
		return new StormCell(id, kind, birthTick, lifetimeTicks, originX, originZ, driftX, driftZ, peakRadius,
				peakIntensity, seed, category, wind);
	}

	/** The same storm with a new id. */
	StormCell withId(long id) {
		return new StormCell(id, kind, birthTick, lifetimeTicks, originX, originZ, driftX, driftZ, peakRadius,
				peakIntensity, seed, category, wind);
	}

	/** The same hurricane in another category, or tornadic storm with another rating (0 to 5). Others are as they were. */
	public StormCell withCategory(int category) {
		return new StormCell(id, kind, birthTick, lifetimeTicks, originX, originZ, driftX, driftZ, peakRadius,
				peakIntensity, seed, category, wind);
	}

	/** The same storm moved so that a point {@code dx}, {@code dz} from where it would be is where it is. */
	public StormCell shifted(double dx, double dz) {
		return withPath(originX + dx, originZ + dz, driftX, driftZ);
	}

	/** A category or rating, 0 to 5, drawn from {@code seed} by {@code odds}. */
	static int draw(long seed, double[] odds) {
		double r = Noise.unit(seed, 0xCA7);
		for(int c = 0; c < odds.length; c++) {
			if(r < odds[c])
				return c;
			r -= odds[c];
		}
		return 1;
	}

	synchronized void setWind(SteeringWind wind) {
		this.wind = wind;
		pathLength = 0;
	}

	/** Makes a hurricane weaken over the land of {@code climate}, or not if null. Other storms ignore it. */
	synchronized void setLand(Climate climate) {
		if(kind != StormKind.HURRICANE || climate == land)
			return;
		land = climate;
		pathLength = 0;
	}

	@Override public long id() { return id; }
	@Override public StormKind kind() { return kind; }
	@Override public long birthTick() { return birthTick; }
	@Override public int lifetimeTicks() { return lifetimeTicks; }
	public double originX() { return originX; }
	public double originZ() { return originZ; }
	public double driftX() { return driftX; }
	public double driftZ() { return driftZ; }
	public double peakRadius() { return peakRadius; }
	public float peakIntensity() { return peakIntensity; }
	public long seed() { return seed; }
	@Override public int category() { return kind == StormKind.TORNADO ? kind.scale() : category; }

	/** Its tornado's rating on the Enhanced Fujita scale, EF0 to EF5 (0 for storms without one). */
	@Override
	public int tornadoRating() {
		return kind == StormKind.TORNADO ? category : 0;
	}
	public StormProfile profile() { return profile; }

	/** How far through its life it is at {@code gameTime}: below 0 before it forms, above 1 after. */
	public double lifeFraction(long gameTime) {
		return (gameTime - birthTick) / (double)lifetimeTicks;
	}

	@Override
	public StormStage stageAt(long gameTime) {
		double a = lifeFraction(gameTime);
		if(a < 0)
			return StormStage.PENDING;
		if(a < FORMING_END)
			return StormStage.FORMING;
		if(a < DECAY_START)
			return StormStage.MATURE;
		if(a < 1)
			return StormStage.DECAYING;
		return StormStage.DISSIPATED;
	}

	public boolean isAlive(long gameTime) {
		return gameTime >= birthTick && gameTime < deathTick();
	}

	@Override
	public float intensityAt(long gameTime) {
		double a = lifeFraction(gameTime);
		if(a < 0 || a >= 1)
			return 0;
		float life;
		if(a < FORMING_END)
			life = (float)(peakIntensity * smoothstep(a / FORMING_END));
		else if(a < DECAY_START)
			life = peakIntensity;
		else
			life = (float)(peakIntensity * (1 - smoothstep((a - DECAY_START) / (1 - DECAY_START))));
		return life * landFactor(gameTime);
	}

	/** How much of its strength a hurricane has left after its time over land: 1 at sea, or without land weakening. */
	public synchronized float landFactor(long gameTime) {
		if(land == null)
			return 1;
		long t = Math.min(Math.max(gameTime, birthTick), deathTick()) - birthTick;
		int step = (int)(t / STEP);
		double frac = (t - (long)step * STEP) / (double)STEP;
		ensurePath(step + 1);
		return (float)(landPath[step] + (landPath[step + 1] - landPath[step]) * frac);
	}

	@Override
	public double radiusAt(long gameTime) {
		double a = lifeFraction(gameTime);
		if(a < 0 || a >= 1)
			return 0;
		if(a < FORMING_END)
			return peakRadius * (0.4 + 0.6 * smoothstep(a / FORMING_END));
		return peakRadius;
	}

	/** Velocity at {@code gameTime}, blocks per tick: the steering wind times the motion factor, plus the drift. */
	public double velocityX(long gameTime) {
		return profile.motionFactor() * wind.x(gameTime) / 20 + driftX;
	}

	public double velocityZ(long gameTime) {
		return profile.motionFactor() * wind.z(gameTime) / 20 + driftZ;
	}

	@Override
	public Vec3 velocityAt(long gameTime) {
		return new Vec3(velocityX(gameTime), 0, velocityZ(gameTime));
	}

	/** Centre x at {@code gameTime}. Before birth it sits at its origin; after death, where it died. */
	public double centerX(long gameTime) {
		return center(gameTime, true);
	}

	public double centerZ(long gameTime) {
		return center(gameTime, false);
	}

	@Override
	public Vec3 centerAt(long gameTime) {
		return new Vec3(centerX(gameTime), 0, centerZ(gameTime));
	}

	private synchronized double center(long gameTime, boolean x) {
		long t = Math.min(Math.max(gameTime, birthTick), deathTick()) - birthTick;
		int step = (int)(t / STEP);
		double frac = (t - (long)step * STEP) / (double)STEP;
		ensurePath(step + 1);
		double[] path = x ? pathX : pathZ;
		return path[step] + (path[step + 1] - path[step]) * frac;
	}

	/** Integrates the path out to {@code steps} steps (midpoint rule). */
	private void ensurePath(int steps) {
		if(steps < pathLength)
			return;
		if(steps >= pathX.length) {
			int size = Math.max(steps + 1, Math.max(16, pathX.length * 2));
			pathX = java.util.Arrays.copyOf(pathX, size);
			pathZ = java.util.Arrays.copyOf(pathZ, size);
			if(land != null)
				landPath = java.util.Arrays.copyOf(landPath, size);
		}
		if(land != null && landPath.length < pathX.length)
			landPath = java.util.Arrays.copyOf(landPath, pathX.length);
		if(pathLength == 0) {
			pathX[0] = originX;
			pathZ[0] = originZ;
			if(land != null)
				landPath[0] = 1;
			pathLength = 1;
		}
		for(int i = pathLength; i <= steps; i++) {
			long mid = birthTick + (long)(i - 1) * STEP + STEP / 2;
			pathX[i] = pathX[i - 1] + velocityX(mid) * STEP;
			pathZ[i] = pathZ[i - 1] + velocityZ(mid) * STEP;
			if(land != null) {
				float left = landPath[i - 1];
				landPath[i] = land.isOcean((int)Math.floor(pathX[i - 1]), (int)Math.floor(pathZ[i - 1]))
						? (float)(left + (1 - left) * STEP / OCEAN_RECOVERY)
						: (float)(left * Math.exp(-STEP / LAND_DECAY));
			}
		}
		pathLength = steps + 1;
	}

	/**
	 * Adds this storm's weather at ({@code x}, {@code z}) at {@code gameTime} to {@code acc}. Rain and thunder take the
	 * strongest storm, wind adds up.
	 */
	public void contribute(double x, double z, long gameTime, PointWeather.Builder acc) {
		float intensity = intensityAt(gameTime);
		if(intensity <= 0)
			return;
		double radius = radiusAt(gameTime);
		double dx = x - centerX(gameTime);
		double dz = z - centerZ(gameTime);
		double distSqr = dx * dx + dz * dz;
		if(!kind.bringsRain()) { // Fog, heat or cold: thick in the middle, thinning out to the edge.
			if(distSqr < radius * radius)
				acc.addEvent(kind, intensity * edgeFalloff(Math.sqrt(distSqr) / radius, 0.5));
			return;
		}
		if(kind == StormKind.TORNADO)
			tornado(x, z, gameTime, acc);
		double reach = kind == StormKind.HURRICANE ? radius : radius * GUST_FRONT;
		if(distSqr >= reach * reach)
			return;
		double d = Math.sqrt(distSqr);
		double u = d / radius;

		double rain;
		double thunder;
		double windX = 0;
		double windZ = 0;
		double gustX = 0;
		double gustZ = 0;
		double pressureDrop;
		boolean eye = false;

		if(kind == StormKind.HURRICANE) {
			if(u < EYE) {
				rain = 0.05;
				eye = intensity > 0.3;
			}
			else if(u < EYEWALL)
				rain = smoothstep((u - EYE) / (EYEWALL - EYE));
			else if(u < 0.5)
				rain = 1;
			else // Outer rain bands.
				rain = edgeFalloff(u, 0.5) * (0.75 + 0.25 * Math.cos(u * 30));
			rain *= intensity;
			// A category 5 flashes with lightning almost without a break, right out into its rain bands.
			thunder = category >= 5 ? (u > EYE ? intensity * edgeFalloff(u, 0.5) : 0)
					: u > EYE && u < 0.3 ? intensity * edgeFalloff(u, 0.2) : 0;

			// Rankine vortex, counter-clockwise seen from above, tapering to nothing at the edge. Over land it winds
			// down with its strength, to a tropical storm and then a gale.
			double vmax = HURRICANE_WIND[category] * (0.5 + 0.5 * intensity) * landFactor(gameTime);
			double rmax = EYEWALL * radius;
			double speed = d < rmax ? vmax * d / rmax : vmax * Math.pow(rmax / d, 0.6);
			speed *= edgeFalloff(u, 0.8);
			if(d > 1e-6) {
				windX = speed * dz / d;
				windZ = speed * -dx / d;
				// A little inflow toward the eye.
				windX -= 0.2 * speed * dx / d;
				windZ -= 0.2 * speed * dz / d;
			}
			pressureDrop = HURRICANE_PRESSURE[category] * intensity / (1 + (u / 0.15) * (u / 0.15)) * edgeFalloff(u, 0.6);
			gustX = windX;
			gustZ = windZ;
		}
		else {
			double fall = edgeFalloff(u, 0.6);
			rain = intensity * fall;
			// Round a tornado the rain thins out (the clear slot at the back of the storm), so you can see it.
			if(kind == StormKind.TORNADO && tornadoStrength(gameTime) > 0) {
				double clear = Math.hypot(x - tornadoX(gameTime), z - tornadoZ(gameTime)) / (radius * 0.45);
				rain *= 1 - 0.75 * tornadoStrength(gameTime) * (1 - smoothstep(clear));
			}
			thunder = kind.hasThunder() ? Math.sqrt(intensity) * edgeFalloff(u, 0.45) : 0;
			// Outflow: wind blows out of the storm, strongest halfway out, still blowing at the rain's edge and
			// dying away over the gust front beyond it.
			double shape = u < 0.5 ? Math.sin(Math.PI * u)
					: u < 1 ? 1 - 0.3 * (u - 0.5) / 0.5
					: 0.35 * (1 + Math.cos(Math.PI * (u - 1) / (GUST_FRONT - 1)));
			double full = (profile.gust() + profile.gustPerIntensity() * intensity) * shape;
			double speed = full * (LULL + (1 - LULL) * gustAt(gameTime));
			if(d > 1e-6) {
				windX = speed * dx / d;
				windZ = speed * dz / d;
				gustX = full * dx / d;
				gustZ = full * dz / d;
			}
			pressureDrop = profile.pressureDrop() * intensity * fall;
		}

		boolean hail = kind == StormKind.HURRICANE && u > EYE && u < 0.5 && intensity > 0.5 && hailBurstAt(gameTime);
		acc.add(this, rain, thunder, windX, windZ, gustX, gustZ, pressureDrop, eye, intensity, hail);
	}

	/**
	 * How hard a thunderstorm's wind gusts at {@code gameTime}: 0 in a lull, 1 at the height of a gust. Gusts last two
	 * to three seconds; severe thunderstorms and supercells have one most of every four seconds, other storms one
	 * every eight seconds or so. A pure function of the storm's seed and the time, like everything else.
	 */
	public double gustAt(long gameTime) {
		boolean severe = kind.scale() >= 3;
		int window = severe ? 80 : kind == StormKind.STRONG_THUNDERSTORM ? 120 : 160;
		double chance = severe ? 0.8 : kind == StormKind.STRONG_THUNDERSTORM ? 0.55 : 0.35;
		long w = Math.floorDiv(gameTime, window);
		double gust = 0;
		for(long k = w - 1; k <= w; k++) { // A gust late in the last window can run into this one.
			if(Noise.unit(seed, k * 3) >= chance)
				continue;
			double start = k * window + Noise.unit(seed, k * 3 + 1) * window;
			double length = 40 + Noise.unit(seed, k * 3 + 2) * 20;
			double x = (gameTime - start) / length;
			if(x > 0 && x < 1)
				gust = Math.max(gust, Math.pow(Math.sin(Math.PI * x), 2));
		}
		return gust;
	}

	/**
	 * Whether a hurricane is throwing down hail at {@code gameTime}: now and then, in its inner rain (eyewall to half
	 * way out), a burst of half a minute to a minute and a half, about one minute in eight overall. Pure in seed and
	 * time.
	 */
	public boolean hailBurstAt(long gameTime) {
		final int window = 6000;
		long w = Math.floorDiv(gameTime, window);
		for(long k = w - 1; k <= w; k++) {
			long hailSeed = seed ^ 0x4A11L; // Its own draws, apart from the gusts'.
			if(Noise.unit(hailSeed, k * 3) >= 0.55)
				continue;
			double start = k * window + Noise.unit(hailSeed, k * 3 + 1) * window;
			double length = 600 + Noise.unit(hailSeed, k * 3 + 2) * 1200;
			if(gameTime >= start && gameTime < start + length)
				return true;
		}
		return false;
	}

	/**
	 * How much of its strength a tornadic storm's tornado has at {@code gameTime}: 0 while it is up in the cloud, rising
	 * to 1 a minute after it touches down and roping out to nothing before it lifts.
	 */
	public double tornadoStrength(long gameTime) {
		if(kind != StormKind.TORNADO)
			return 0;
		double a = lifeFraction(gameTime);
		if(a <= TOUCHDOWN || a >= LIFT_OFF)
			return 0;
		double ramp = 1200.0 / lifetimeTicks;
		return smoothstep((a - TOUCHDOWN) / ramp) * smoothstep((LIFT_OFF - a) / ramp) * intensityAt(gameTime);
	}

	/** Where its tornado is: behind and to the right of the storm's centre as it moves, wandering a little. */
	public double tornadoX(long gameTime) {
		return tornadoAt(gameTime, true);
	}

	public double tornadoZ(long gameTime) {
		return tornadoAt(gameTime, false);
	}

	private double tornadoAt(long gameTime, boolean x) {
		double vx = velocityX(gameTime), vz = velocityZ(gameTime);
		double v = Math.max(1e-6, Math.sqrt(vx * vx + vz * vz));
		double fx = vx / v, fz = vz / v;
		double r = radiusAt(gameTime);
		// Back 0.25 radius, right 0.2 (right of the motion is -fz, fx), wandering 0.08 radius over a few minutes.
		double back = -0.25 * r + Noise.smooth(seed ^ 0x70A, gameTime / 900.0) * 0.08 * r;
		double right = 0.2 * r + Noise.smooth(seed ^ 0x70B, gameTime / 700.0) * 0.08 * r;
		return x ? centerX(gameTime) + fx * back - fz * right : centerZ(gameTime) + fz * back + fx * right;
	}

	/** Its tornado's core radius at full strength, blocks: by its rating, give or take a fifth. */
	public double tornadoRadius() {
		return TORNADO_RADIUS[tornadoRating()] * (0.8 + 0.4 * Noise.unit(seed, 0x70C));
	}

	/** Its tornado's top wind at full strength, metres per second, within its rating's band. */
	public double tornadoWind() {
		int ef = tornadoRating();
		double next = ef < 5 ? TORNADO_WIND[ef + 1] : TORNADO_WIND[5] * 1.15;
		return TORNADO_WIND[ef] + (next - TORNADO_WIND[ef]) * Noise.unit(seed, 0x70D);
	}

	/** The tornado's winds at ({@code x}, {@code z}): a tight vortex, counter-clockwise, sucking in toward its core. */
	private void tornado(double x, double z, long gameTime, PointWeather.Builder acc) {
		double strength = tornadoStrength(gameTime);
		if(strength <= 0)
			return;
		double rt = tornadoRadius() * (0.6 + 0.4 * strength);
		double dx = x - tornadoX(gameTime);
		double dz = z - tornadoZ(gameTime);
		double d = Math.sqrt(dx * dx + dz * dz);
		if(d >= rt * TORNADO_REACH)
			return;
		double vmax = tornadoWind() * strength;
		double speed = d < rt ? vmax * d / rt : vmax * Math.pow(rt / d, 1.2);
		speed *= edgeFalloff(d / (rt * TORNADO_REACH), 0.5);
		double windX = 0, windZ = 0;
		if(d > 1e-6) {
			windX = speed * dz / d - 0.4 * speed * dx / d;
			windZ = speed * -dx / d - 0.4 * speed * dz / d;
		}
		acc.add(this, 0, 0, windX, windZ, windX, windZ, 0, false, intensityAt(gameTime), false);
		acc.addTornado(Math.max(0, 1 - d / (2 * rt)) * strength);
	}

	/** Whether this storm's rain or wind (its gust front, for a thunderstorm) reaches ({@code x}, {@code z}). */
	public boolean reaches(double x, double z, long gameTime) {
		return reaches(x, z, gameTime, 0);
	}

	/** {@link #reaches(double, double, long)}, counting {@code margin} blocks round it. */
	public boolean reaches(double x, double z, long gameTime, double margin) {
		if(!isAlive(gameTime))
			return false;
		double r = radiusAt(gameTime) * (kind == StormKind.HURRICANE ? 1 : GUST_FRONT) + margin;
		double dx = x - centerX(gameTime);
		double dz = z - centerZ(gameTime);
		return dx * dx + dz * dz < r * r;
	}

	/** Quick reject: whether ({@code x}, {@code z}) could be under this storm at {@code gameTime}. */
	public boolean covers(double x, double z, long gameTime) {
		if(!isAlive(gameTime))
			return false;
		double r = radiusAt(gameTime);
		double dx = x - centerX(gameTime);
		double dz = z - centerZ(gameTime);
		return dx * dx + dz * dz < r * r;
	}

	/** 1 inside {@code inner} (a fraction of the radius), easing to 0 at the edge. */
	static double edgeFalloff(double u, double inner) {
		if(u <= inner)
			return 1;
		if(u >= 1)
			return 0;
		return 0.5 * (1 + Math.cos(Math.PI * (u - inner) / (1 - inner)));
	}

	static double smoothstep(double x) {
		x = Math.max(0, Math.min(1, x));
		return x * x * (3 - 2 * x);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("id", id);
		tag.putString("kind", kind.name());
		tag.putLong("birth", birthTick);
		tag.putInt("life", lifetimeTicks);
		tag.putDouble("ox", originX);
		tag.putDouble("oz", originZ);
		tag.putDouble("dx", driftX);
		tag.putDouble("dz", driftZ);
		tag.putDouble("radius", peakRadius);
		tag.putFloat("intensity", peakIntensity);
		tag.putLong("seed", seed);
		tag.putInt("category", category);
		return tag;
	}

	/** Reads a storm saved by {@link #save()}, or null if its kind is unknown. */
	public static StormCell load(CompoundTag tag, SteeringWind wind) {
		StormKind kind;
		try {
			kind = StormKind.valueOf(tag.getString("kind"));
		}
		catch(IllegalArgumentException e) {
			return null;
		}
		return new StormCell(tag.getLong("id"), kind, tag.getLong("birth"), tag.getInt("life"), tag.getDouble("ox"),
				tag.getDouble("oz"), tag.getDouble("dx"), tag.getDouble("dz"), tag.getDouble("radius"),
				tag.getFloat("intensity"), tag.getLong("seed"),
				tag.contains("category") ? tag.getInt("category")
						: draw(tag.getLong("seed"), kind == StormKind.TORNADO ? TORNADO_ODDS : CATEGORY_ODDS), wind);
	}

	@Override
	public String toString() {
		return "StormCell[" + id + " " + kind + " born " + birthTick + " for " + lifetimeTicks + "]";
	}
}
