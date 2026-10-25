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
	private final StormProfile profile;

	private SteeringWind wind;
	private double[] pathX = new double[0];
	private double[] pathZ = new double[0];
	private int pathLength;

	/** The last stage the field reported, for {@code StageChanged} events. Not saved. */
	StormStage reportedStage;

	public StormCell(long id, StormKind kind, long birthTick, int lifetimeTicks, double originX, double originZ,
			double driftX, double driftZ, double peakRadius, float peakIntensity, long seed, SteeringWind wind) {
		this.id = id;
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
		long birth = now - (long)(lifetimeTicks * FORMING_END);
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
				peakIntensity, seed, wind);
	}

	/** The same storm with a new id. */
	StormCell withId(long id) {
		return new StormCell(id, kind, birthTick, lifetimeTicks, originX, originZ, driftX, driftZ, peakRadius,
				peakIntensity, seed, wind);
	}

	synchronized void setWind(SteeringWind wind) {
		this.wind = wind;
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
		if(a < FORMING_END)
			return (float)(peakIntensity * smoothstep(a / FORMING_END));
		if(a < DECAY_START)
			return peakIntensity;
		return (float)(peakIntensity * (1 - smoothstep((a - DECAY_START) / (1 - DECAY_START))));
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
		}
		if(pathLength == 0) {
			pathX[0] = originX;
			pathZ[0] = originZ;
			pathLength = 1;
		}
		for(int i = pathLength; i <= steps; i++) {
			long mid = birthTick + (long)(i - 1) * STEP + STEP / 2;
			pathX[i] = pathX[i - 1] + velocityX(mid) * STEP;
			pathZ[i] = pathZ[i - 1] + velocityZ(mid) * STEP;
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
		if(distSqr >= radius * radius)
			return;
		double d = Math.sqrt(distSqr);
		double u = d / radius;

		double rain;
		double thunder;
		double windX = 0;
		double windZ = 0;
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
			thunder = u > EYE && u < 0.3 ? intensity * edgeFalloff(u, 0.2) : 0;

			// Rankine vortex, counter-clockwise seen from above, tapering to nothing at the edge.
			double vmax = profile.gust() + profile.gustPerIntensity() * intensity;
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
			pressureDrop = profile.pressureDrop() * intensity / (1 + (u / 0.15) * (u / 0.15)) * edgeFalloff(u, 0.6);
		}
		else {
			double fall = edgeFalloff(u, 0.6);
			rain = intensity * fall;
			thunder = kind.hasThunder() ? Math.sqrt(intensity) * edgeFalloff(u, 0.45) : 0;
			// Outflow: wind blows out of the storm, strongest partway out.
			double speed = (profile.gust() + profile.gustPerIntensity() * intensity) * Math.sin(Math.PI * u);
			if(d > 1e-6) {
				windX = speed * dx / d;
				windZ = speed * dz / d;
			}
			pressureDrop = profile.pressureDrop() * intensity * fall;
		}

		acc.add(this, rain, thunder, windX, windZ, pressureDrop, eye, intensity);
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
				tag.getFloat("intensity"), tag.getLong("seed"), wind);
	}

	@Override
	public String toString() {
		return "StormCell[" + id + " " + kind + " born " + birthTick + " for " + lifetimeTicks + "]";
	}
}
