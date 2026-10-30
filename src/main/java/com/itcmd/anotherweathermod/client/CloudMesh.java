package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AwmClientConfig.CloudDetail;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.SplittableRandom;

/**
 * The shapes of the mod's clouds. Each cloud is a few puffs: squashed, lumpy domes with a flat base, overlapping so
 * they merge. A fair-weather cumulus is a big puff with smaller ones round it, white on top and greyer below; storm
 * cloud is wide puffs filling their whole cell so neighbours join into one deck, dark and deep, and over severe
 * storms it towers. Shapes come from the cell, so a cloud looks the same every time it is built.
 */
final class CloudMesh {

	/** Fair-weather cloud's tint: white, a touch blue. */
	private static final float[] FAIR_TINT = CloudCover.PRECIPITATION_TINT[0];
	/**
	 * The vertex alpha says which kind of cloud a vertex is, for the shader (clouds are always opaque): storm cloud
	 * stays solid further out, so a storm is seen on the horizon.
	 */
	static final float FAIR = 1f;
	static final float STORM = 0.5f;
	/** A rainy day's grey deck: drawn like storm cloud, and faded with the fair-weather cloud when the day changes. */
	static final float GREY = 0.25f;
	/** How far above the cloud height the grey deck's base is, blocks: storm decks hang below the cloud height. */
	static final double GREY_BASE = 10;
	/** How tall storm cloud grows at its deepest, blocks. */
	static final double STORM_TOP = 70;
	/** How far a storm puff's base may hang below the deck's, blocks, scaled by half again at full depth. */
	static final double BASE_RAGGED = 10;

	private CloudMesh() {
	}

	/**
	 * The clouds of tile ({@code tileX}, {@code tileZ}), {@code tileCells} cells a side, with {@code keys} from
	 * {@link CloudCover}, positioned from the tile's corner at cloud height. Null if it has none.
	 */
	@Nullable
	static MeshData build(int tileX, int tileZ, int tileCells, int[] keys, CloudDetail detail, boolean far) {
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_NORMAL);
		int rings = far ? Math.max(1, detail.rings() / 2) : detail.rings();
		int segments = far ? Math.max(4, detail.segments() / 2) : detail.segments();
		double lumps = far ? 0 : detail.lumps();
		for(int a = 0; a < tileCells; a++)
			for(int b = 0; b < tileCells; b++) {
				int key = keys[a * tileCells + b];
				if(key == CloudCover.NONE)
					continue;
				int i = tileX * tileCells + a, j = tileZ * tileCells + b;
				SplittableRandom random = new SplittableRandom((long)i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL);
				double cx = (a + 0.5) * CloudCover.CELL, cz = (b + 0.5) * CloudCover.CELL;
				if(key == CloudCover.GREY)
					grey(buffer, random, cx, cz);
				else if(CloudCover.isStorm(key))
					storm(buffer, random, cx, cz, key, detail, rings, segments, lumps);
				else
					fair(buffer, random, cx, cz, key - CloudCover.FAIR, detail, rings, segments, lumps);
			}
		return buffer.build();
	}

	private static void fair(BufferBuilder buffer, SplittableRandom random, double cx, double cz, int size,
			CloudDetail detail, int rings, int segments, double lumps) {
		double scale = 0.55 + 0.18 * size + random.nextDouble() * 0.15;
		double r = CloudCover.CELL * 0.32 * scale;
		double base = random.nextDouble() * 6 - 3;
		double x = cx + (random.nextDouble() - 0.5) * CloudCover.CELL * 0.3;
		double z = cz + (random.nextDouble() - 0.5) * CloudCover.CELL * 0.3;
		// Now and then, in the bigger cumulus, a taller heaped-up one.
		double tall = size >= 2 && random.nextDouble() < 0.2 ? 1.8 : 1;
		double ry = r * (0.6 + random.nextDouble() * 0.3) * tall;
		double top = base + ry * 1.4;
		puff(buffer, random, x, base + ry * 0.35, z, r * (1 + random.nextDouble() * 0.3), ry,
				r * (1 + random.nextDouble() * 0.3), base, top, 0.72f, 1f, FAIR_TINT, FAIR, rings, segments, lumps);
		for(int n = 1; n < detail.fairPuffs(); n++) {
			double angle = random.nextDouble() * Math.PI * 2, out = r * (0.6 + random.nextDouble() * 0.45);
			double pr = r * (0.5 + random.nextDouble() * 0.3), py = pr * (0.55 + random.nextDouble() * 0.3);
			puff(buffer, random, x + Math.cos(angle) * out, base + py * 0.3, z + Math.sin(angle) * out, pr, py, pr,
					base, top, 0.72f, 1f, FAIR_TINT, FAIR, rings, segments, lumps);
		}
	}

	/**
	 * A rainy day's grey deck: one wide, flat, smooth puff a cell, low on polygons, overlapping its neighbours into an
	 * even sheet. Each sits a little higher or lower, so where two overlap their faces never lie in one plane (the old
	 * shower-deck grey flickered there).
	 */
	private static void grey(BufferBuilder buffer, SplittableRandom random, double cx, double cz) {
		double base = GREY_BASE + (random.nextDouble() - 0.5) * 2;
		double r = CloudCover.CELL * (0.78 + random.nextDouble() * 0.1);
		double ry = 3 + random.nextDouble() * 1.5;
		double x = cx + (random.nextDouble() - 0.5) * 4, z = cz + (random.nextDouble() - 0.5) * 4;
		float shade = (float)(0.95 + random.nextDouble() * 0.08);
		puff(buffer, random, x, base + ry * 0.3, z, r, ry, r, base, base + ry * 1.3, 0.68f * shade, 0.84f * shade,
				CloudCover.PRECIPITATION_TINT[0], GREY, 2, 10, 0);
	}

	private static void storm(BufferBuilder buffer, SplittableRandom random, double cx, double cz, int key,
			CloudDetail detail, int rings, int segments, double lumps) {
		int kind = CloudCover.stormKind(key);
		double depth = CloudCover.stormDepth(key) / (double)CloudCover.STORM_DEPTHS;
		float[] tint = CloudCover.PRECIPITATION_TINT[CloudCover.stormPrecipitation(key)];
		// A snow deck is lifted toward white, but less the stronger the storm: a severe snowstorm's deck in a near-white
		// sky was invisible (2026-10-27), so from a shower's pale deck it darkens to a slate grey by a supercell's.
		float lift = CloudCover.stormPrecipitation(key) == 1 ? tint[0] * (kind <= 5 ? 1f - 0.6f * kind / 5f : kind == 6 ? 0.4f : 1f) : tint[0];
		float dark = Mth.lerp(lift, CloudCover.DARKNESS[kind], 0.95f);
		double thick = 16 + STORM_TOP * depth * CloudCover.TALL[kind];
		double base = -5 - 8 * depth;
		// A pale (snow) deck is pale underneath too, not just on top.
		float bottom = dark * (0.7f + 0.2f * lift), lit = Math.min(1, dark * 1.6f + 0.15f);
		double top = base + thick * 1.1;
		for(int n = 0; n < detail.stormPuffs(); n++) {
			double x = cx + (random.nextDouble() - 0.5) * CloudCover.CELL * 0.7;
			double z = cz + (random.nextDouble() - 0.5) * CloudCover.CELL * 0.7;
			double r = CloudCover.CELL * (0.55 + random.nextDouble() * 0.2);
			double ry = thick * (0.5 + random.nextDouble() * 0.5);
			// A ragged, mottled base: each puff hangs a little lower or higher, and is a little darker or lighter, so
			// from underneath the deck reads as cloud (and is seen moving) rather than one flat, even ceiling.
			double drop = random.nextDouble() * BASE_RAGGED * (0.5 + depth);
			float mottle = (float)(0.8 + random.nextDouble() * 0.35 - drop / (BASE_RAGGED * 1.5) * 0.2);
			double puffBase = base - drop;
			puff(buffer, random, x, puffBase + ry * 0.4, z, r, ry, r, puffBase, top, bottom * mottle,
					Math.min(1, lit * mottle), tint, STORM, rings, segments, lumps);
		}
		// Severe storms heap up into towers here and there.
		if(kind >= 3 && kind <= 6 && depth >= 0.75 && random.nextDouble() < 0.25) {
			double r = CloudCover.CELL * 0.45, ry = thick * 1.7;
			puff(buffer, random, cx, base + ry * 0.35, cz, r, ry, r, base, base + ry * 1.4, bottom, lit, tint, STORM, rings,
					segments, lumps);
		}
	}

	/**
	 * A squashed, lumpy dome centred at ({@code x}, {@code y}, {@code z}) with radii {@code rx}, {@code ry} and
	 * {@code rz}, cut flat at {@code base}; shaded from {@code bottom} at {@code base} to {@code lit} at {@code top},
	 * tinted by {@code tint} (as {@link CloudCover#PRECIPITATION_TINT}); {@code kind} ({@link #FAIR} or {@link #STORM})
	 * goes to the shader in the vertex alpha.
	 */
	private static void puff(BufferBuilder buffer, SplittableRandom random, double x, double y, double z, double rx,
			double ry, double rz, double base, double top, float bottom, float lit, float[] tint, float kind, int rings,
			int segments, double lumps) {
		double p1 = random.nextDouble() * Math.PI * 2, p2 = random.nextDouble() * Math.PI * 2;
		int bands = rings + 1;
		for(int k = 0; k < bands; k++)
			for(int s = 0; s < segments; s++) {
				vertex(buffer, x, y, z, rx, ry, rz, base, top, bottom, lit, tint, kind, bands, k, s, segments, lumps, p1, p2);
				vertex(buffer, x, y, z, rx, ry, rz, base, top, bottom, lit, tint, kind, bands, k + 1, s, segments, lumps, p1, p2);
				vertex(buffer, x, y, z, rx, ry, rz, base, top, bottom, lit, tint, kind, bands, k + 1, s + 1, segments, lumps, p1, p2);
				vertex(buffer, x, y, z, rx, ry, rz, base, top, bottom, lit, tint, kind, bands, k, s + 1, segments, lumps, p1, p2);
			}
	}

	private static void vertex(BufferBuilder buffer, double x, double y, double z, double rx, double ry, double rz,
			double base, double top, float bottom, float lit, float[] tint, float kind, int bands, int k, int s, int segments,
			double lumps, double p1, double p2) {
		// Latitude: the bottom pole, then from just under the middle up to the top pole.
		double lat = k == 0 ? -Math.PI / 2 : -0.2 + (Math.PI / 2 + 0.2) * (k - 1) / (bands - 1);
		double lon = Math.PI * 2 * s / segments;
		double cosLat = Math.cos(lat), sinLat = Math.sin(lat);
		double cosLon = Math.cos(lon), sinLon = Math.sin(lon);
		double lump = 1 + lumps * cosLat * (0.6 * Math.sin(lon * 3 + p1) + 0.4 * Math.sin(lon * 5 + lat * 3 + p2));
		double px = x + cosLat * cosLon * rx * lump;
		double py = y + sinLat * ry * lump;
		double pz = z + cosLat * sinLon * rz * lump;
		double nx = cosLat * cosLon / rx, ny = sinLat / ry, nz = cosLat * sinLon / rz;
		if(py < base) {
			// Cut flat underneath, facing down.
			py = base;
			nx *= 0.3;
			nz *= 0.3;
			ny = -Math.abs(ny) - 1 / ry;
		}
		double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
		float up = (float)Math.max(0, Math.min(1, (py - base) / Math.max(1, top - base)));
		float shade = bottom + (lit - bottom) * up;
		buffer.addVertex((float)px, (float)py, (float)pz).setColor(Math.min(1, shade * tint[1]), Math.min(1, shade * tint[2]),
						Math.min(1, shade * tint[3]), kind)
				.setNormal((float)(nx / length), (float)(ny / length), (float)(nz / length));
	}
}
