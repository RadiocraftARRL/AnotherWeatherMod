package com.itcmd.anotherweathermod.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Level of detail for storms seen from afar, apart from drawing them. Near storms keep their own detailed cloud and
 * rain walls; past a distance each fades ({@link #detail}) into a merged mass: storms whose clouds touch are joined
 * ({@link #clusters}), so a front's cells become one long band along the line and a clump of scattered storms one wide
 * mass, each drawn as a single capsule ({@link Mass}) instead of a storm each.
 *
 * <p>Nothing jumps: a storm's detail eases over {@code blend} blocks, and a mass's shape is a weighted fit of its
 * members, each weighing as much as it has faded, so the mass grows smoothly as storms join it. Which storms make up a
 * mass depends only on the storms (not the camera), and when that changes the old mass fades out and the new one in
 * ({@link Tracker}). Only drawing changes; the storms themselves (rain, wind, lightning) do not.
 */
final class StormLod {

	/** Storms join a mass when their centres are within this many times the sum of their radii: when their clouds touch. */
	static final double LINK = 1.15;
	/** Seconds a mass takes to fade in or out when the storms making it up change. */
	static final float MASS_FADE_SECONDS = 2;

	private StormLod() {
	}

	/**
	 * A storm as far as the LOD cares, where it is now.
	 *
	 * @param kind          {@link CloudCover#kindIndex} index
	 * @param precipitation {@link CloudCover#precipitationIndex} index
	 */
	record Body(long id, double x, double z, double radius, float intensity, int kind, int precipitation, double vx,
			double vz) {
	}

	/**
	 * Merged far storms: a capsule round the segment from {@code halfLength} back to {@code halfLength} ahead along
	 * ({@code ux}, {@code uz}) from its middle, {@code width} round. Kind and precipitation are its strongest member's.
	 *
	 * @param alpha how much it shows, 0 to 1: as far as its members have faded into it, and its own fade in or out
	 */
	record Mass(long key, double x, double z, double ux, double uz, double halfLength, double width, float intensity,
			int kind, int precipitation, double vx, double vz, float alpha, int members) {

		/** How far ({@code px}, {@code pz}) is from its middle line, blocks: 0 on it, its width at its edge. */
		double distance(double px, double pz) {
			double dx = px - x, dz = pz - z;
			double t = Math.max(-halfLength, Math.min(halfLength, dx * ux + dz * uz));
			return Math.hypot(dx - ux * t, dz - uz * t);
		}

		/** How far it reaches toward the direction ({@code nx}, {@code nz}) from its middle (a unit vector), blocks. */
		double support(double nx, double nz) {
			return Math.abs(halfLength * (ux * nx + uz * nz)) + width;
		}

		Mass withAlpha(float alpha) {
			return new Mass(key, x, z, ux, uz, halfLength, width, intensity, kind, precipitation, vx, vz, alpha, members);
		}
	}

	/** What to draw: each body's detail (as listed) and the masses. */
	record View(double[] detail, List<Mass> masses) {

		static final View NONE = new View(new double[0], List.of());

		/** Body {@code i}'s detail: 1 drawn on its own, 0 only as part of a mass. Bodies past the list are near. */
		double detail(int i) {
			return i < detail.length ? detail[i] : 1;
		}
	}

	/**
	 * How much of a storm whose edge is {@code edgeDistance} blocks off is drawn on its own: 1 within {@code near},
	 * easing to 0 over the next {@code blend}.
	 */
	static double detail(double edgeDistance, double near, double blend) {
		if(blend <= 0)
			return edgeDistance <= near ? 1 : 0;
		double t = Math.max(0, Math.min(1, (edgeDistance - near) / blend));
		return 1 - t * t * (3 - 2 * t);
	}

	/** Groups of bodies (indices) whose clouds touch, directly or through others. The same wherever the camera is. */
	static List<int[]> clusters(List<Body> bodies) {
		int n = bodies.size();
		int[] parent = new int[n];
		for(int i = 0; i < n; i++)
			parent[i] = i;
		for(int i = 0; i < n; i++)
			for(int j = i + 1; j < n; j++) {
				Body a = bodies.get(i), b = bodies.get(j);
				double link = (a.radius() + b.radius()) * LINK;
				double dx = a.x() - b.x(), dz = a.z() - b.z();
				if(dx * dx + dz * dz <= link * link)
					parent[find(parent, i)] = find(parent, j);
			}
		Map<Integer, List<Integer>> groups = new HashMap<>();
		for(int i = 0; i < n; i++)
			groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
		List<int[]> out = new ArrayList<>();
		for(List<Integer> g : groups.values())
			out.add(g.stream().mapToInt(Integer::intValue).toArray());
		out.sort((p, q) -> Integer.compare(p[0], q[0]));
		return out;
	}

	private static int find(int[] parent, int i) {
		while(parent[i] != i) {
			parent[i] = parent[parent[i]];
			i = parent[i];
		}
		return i;
	}

	/** A key for a set of members: the same for the same storms, whatever their order. */
	static long key(List<Body> bodies, int[] members) {
		long[] ids = new long[members.length];
		for(int i = 0; i < members.length; i++)
			ids[i] = bodies.get(members[i]).id();
		Arrays.sort(ids);
		long h = 0x9E3779B97F4A7C15L;
		for(long id : ids) {
			h ^= id + 0x9E3779B97F4A7C15L + (h << 6) + (h >>> 2);
			h *= 0xBF58476D1CE4E5B9L;
		}
		return h;
	}

	/**
	 * The mass that {@code members} make, each weighing {@code weights[i]} (how far it has faded into the mass), or null
	 * if none weighs anything. Its line runs along the members' long axis (a front's line); its length and width come
	 * from their spread along and across it ({@code sqrt(3 variance)}, the half length of an even spread) plus their
	 * radius, so every part of it changes smoothly with the weights.
	 */
	static Mass merge(List<Body> bodies, int[] members, double[] weights, long key) {
		double total = 0, mx = 0, mz = 0, radius = 0, vx = 0, vz = 0, intensity = 0, alpha = 0;
		Body strongest = null;
		for(int k = 0; k < members.length; k++) {
			Body b = bodies.get(members[k]);
			double w = weights[k] * b.radius() * b.radius();
			total += w;
			mx += w * b.x();
			mz += w * b.z();
			alpha = Math.max(alpha, weights[k]);
			if(weights[k] > 0 && (strongest == null || b.intensity() * b.radius() > strongest.intensity() * strongest.radius()))
				strongest = b;
		}
		if(total <= 0 || strongest == null)
			return null;
		mx /= total;
		mz /= total;
		double sxx = 0, szz = 0, sxz = 0;
		for(int k = 0; k < members.length; k++) {
			Body b = bodies.get(members[k]);
			double w = weights[k] * b.radius() * b.radius() / total;
			double dx = b.x() - mx, dz = b.z() - mz;
			sxx += w * dx * dx;
			szz += w * dz * dz;
			sxz += w * dx * dz;
			radius += w * b.radius();
			vx += w * b.vx();
			vz += w * b.vz();
			intensity += w * b.intensity();
		}
		// The long axis: the larger eigenvector of the spread.
		double angle = 0.5 * Math.atan2(2 * sxz, sxx - szz);
		double ux = Math.cos(angle), uz = Math.sin(angle);
		double along = sxx * ux * ux + 2 * sxz * ux * uz + szz * uz * uz;
		double across = Math.max(0, sxx + szz - along);
		double halfLength = Math.sqrt(3 * Math.max(0, along));
		double width = radius + Math.sqrt(3 * across);
		return new Mass(key, mx, mz, ux, uz, halfLength, width, (float)Math.max(intensity, strongest.intensity() * 0.85),
				strongest.kind(), strongest.precipitation(), vx, vz, (float)alpha, members.length);
	}

	/**
	 * The view from ({@code camX}, {@code camZ}): each body's detail by how far its edge is, and a mass for every
	 * cluster with something faded into it (before {@link Tracker} fades masses in and out).
	 */
	static View view(List<Body> bodies, double camX, double camZ, double near, double blend) {
		double[] detail = new double[bodies.size()];
		for(int i = 0; i < bodies.size(); i++) {
			Body b = bodies.get(i);
			detail[i] = detail(Math.hypot(b.x() - camX, b.z() - camZ) - b.radius(), near, blend);
		}
		List<Mass> masses = new ArrayList<>();
		for(int[] members : clusters(bodies)) {
			double[] weights = new double[members.length];
			for(int k = 0; k < members.length; k++)
				weights[k] = 1 - detail[members[k]];
			Mass mass = merge(bodies, members, weights, key(bodies, members));
			if(mass != null)
				masses.add(mass);
		}
		return new View(detail, masses);
	}

	/**
	 * Fades masses in and out as the storms making them up change (two storms drifting together or apart), so a mass
	 * never pops; one that goes keeps its last shape while it fades.
	 */
	static final class Tracker {

		private final Map<Long, float[]> fade = new HashMap<>();
		private final Map<Long, Mass> last = new HashMap<>();
		private boolean started;

		/** The masses to draw after {@code seconds} more, with their fades applied. */
		List<Mass> update(List<Mass> current, float seconds) {
			float step = seconds / MASS_FADE_SECONDS;
			Map<Long, Mass> now = new HashMap<>();
			for(Mass m : current)
				now.put(m.key(), m);
			List<Mass> out = new ArrayList<>();
			for(Mass m : current) {
				float[] f = fade.computeIfAbsent(m.key(), k -> new float[] { started ? 0 : 1 });
				f[0] = Math.min(1, f[0] + step);
				last.put(m.key(), m);
				out.add(m.withAlpha(m.alpha() * f[0]));
			}
			for(Iterator<Map.Entry<Long, float[]>> it = fade.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<Long, float[]> e = it.next();
				if(now.containsKey(e.getKey()))
					continue;
				float[] f = e.getValue();
				f[0] -= step;
				Mass gone = last.get(e.getKey());
				if(f[0] <= 0 || gone == null) {
					it.remove();
					last.remove(e.getKey());
				}
				else
					out.add(gone.withAlpha(gone.alpha() * f[0]));
			}
			started = true;
			return out;
		}

		void clear() {
			fade.clear();
			last.clear();
			started = false;
		}
	}
}
