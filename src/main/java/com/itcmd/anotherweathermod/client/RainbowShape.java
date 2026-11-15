package com.itcmd.anotherweathermod.client;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A rainbow's shape apart from drawing it: a whole ring round the point opposite the sun, red outside and violet in,
 * the colours blending into each other and the ring fading out at both edges. It is drawn as a full circle and left to
 * the depth test: from the ground the ground hides the lower part, so it reads as an arc, and from high up you see the
 * whole halo. (It used to drop every piece below the camera's eye level, which cut it off flat when seen from high
 * up, 2026-11-12.)
 */
final class RainbowShape {

	/** The ring's angle from the point opposite the sun, degrees, outer (red) edge to inner (violet). */
	static final float OUTER = 44f, INNER = 37.5f;
	static final int SEGMENTS = 128;
	/** Colours across the ring, outer to inner, at evenly spaced angles between the two faded edges. */
	static final float[][] COLOURS = { { 1, 0.1f, 0.1f }, { 1, 0.5f, 0.05f }, { 1, 0.95f, 0.15f },
			{ 0.2f, 0.9f, 0.25f }, { 0.15f, 0.45f, 1 }, { 0.35f, 0.2f, 0.85f }, { 0.55f, 0.2f, 0.75f } };
	/** How strong it is at full show: faint, as a real rainbow is (it was 0.35 with hard-edged bands). */
	static final float STRENGTH = 0.22f;

	private RainbowShape() {
	}

	/** Rings of vertices across the bow, outer to inner: the faded outer edge, each colour, the faded inner edge. */
	static int rings() {
		return COLOURS.length + 2;
	}

	/** The angle of ring {@code k} from the antisolar point, degrees. */
	static float ringAngle(int k) {
		return Mth.lerp(k / (float)(rings() - 1), OUTER, INNER);
	}

	/** The colour of ring {@code k}, red, green, blue. */
	static float[] ringColour(int k) {
		return COLOURS[Mth.clamp(k - 1, 0, COLOURS.length - 1)];
	}

	/** How opaque ring {@code k} is, as a share of the bow's strength: 0 at both edges, so the bow has no hard rim. */
	static float ringAlpha(int k) {
		return k == 0 || k == rings() - 1 ? 0 : k == 1 || k == rings() - 2 ? 0.7f : 1;
	}

	/** The point opposite the sun at {@code sunAngle} (as {@code Level.getSunAngle}): the sun runs east to west over x. */
	static Vec3 antisolar(float sunAngle) {
		return new Vec3(Mth.sin(sunAngle), -Mth.cos(sunAngle), 0);
	}

	/**
	 * The bow's quads for the sun at {@code sunAngle}, {@code distance} blocks from the camera, at {@code strength}
	 * (0..1): four vertices each of x, y, z (relative to the camera), red, green, blue, alpha. The whole ring, the part
	 * below the horizon included.
	 */
	static float[] quads(float sunAngle, float distance, float strength) {
		Vec3 anti = antisolar(sunAngle);
		Vec3 u = new Vec3(0, 0, 1);
		Vec3 v = anti.cross(u).normalize();
		int strips = rings() - 1;
		float[] out = new float[strips * SEGMENTS * 4 * 7];
		int n = 0;
		for(int k = 0; k < strips; k++) {
			float[] c0 = ringColour(k), c1 = ringColour(k + 1);
			float a0 = strength * STRENGTH * ringAlpha(k), a1 = strength * STRENGTH * ringAlpha(k + 1);
			for(int s = 0; s < SEGMENTS; s++) {
				double p0 = Math.PI * 2 * s / SEGMENTS, p1 = Math.PI * 2 * (s + 1) / SEGMENTS;
				n = put(out, n, point(anti, u, v, ringAngle(k), p0, distance), c0, a0);
				n = put(out, n, point(anti, u, v, ringAngle(k), p1, distance), c0, a0);
				n = put(out, n, point(anti, u, v, ringAngle(k + 1), p1, distance), c1, a1);
				n = put(out, n, point(anti, u, v, ringAngle(k + 1), p0, distance), c1, a1);
			}
		}
		return out;
	}

	private static int put(float[] out, int n, Vec3 at, float[] c, float a) {
		out[n++] = (float)at.x;
		out[n++] = (float)at.y;
		out[n++] = (float)at.z;
		out[n++] = c[0];
		out[n++] = c[1];
		out[n++] = c[2];
		out[n++] = a;
		return n;
	}

	/** The direction {@code degrees} from {@code anti}, {@code phase} round it, out at {@code distance}. */
	static Vec3 point(Vec3 anti, Vec3 u, Vec3 v, float degrees, double phase, float distance) {
		double t = Math.toRadians(degrees);
		return anti.scale(Math.cos(t)).add(u.scale(Math.cos(phase) * Math.sin(t))).add(v.scale(Math.sin(phase) * Math.sin(t)))
				.scale(distance);
	}
}
