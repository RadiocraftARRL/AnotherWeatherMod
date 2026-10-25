package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmClientConfig;
import com.itcmd.anotherweathermod.AwmClientConfig.CloudDetail;
import com.itcmd.anotherweathermod.network.ClientWeatherState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The mod's 3D clouds, drawn in place of vanilla's. The sky round the camera is cut into tiles of
 * {@link #TILE} by {@link #TILE} {@link CloudCover} cells, each tile kept as its own mesh on the GPU; a tile is
 * rebuilt only when its cells' clouds change (a storm moves in, cumulus build) or it moves between near and far
 * detail, and only a couple a frame, nearest first, so the clouds never stall a frame. The whole sky drifts slowly
 * east, as vanilla's does. Lighting (sun, sky, time of day, rain and lightning) is done by the shader, so it never
 * needs a rebuild.
 */
public final class CloudRenderer {

	/** Cells a side in a tile. */
	static final int TILE = 8;
	static final double TILE_SIZE = TILE * CloudCover.CELL;
	/** How fast the clouds drift east, blocks a tick. */
	static final double DRIFT = 0.05;
	/** Most tiles rebuilt in a frame. */
	static final int REBUILDS_PER_FRAME = 2;
	/** Ticks between looking again at what each cell holds. */
	static final int RESAMPLE_TICKS = 4;
	/** Tiles beyond this share of the range are built in less detail. */
	static final double NEAR = 0.45;

	private static ShaderInstance shader;
	private static final Map<Long, Tile> tiles = new HashMap<>();
	private static long lastSample = Long.MIN_VALUE;
	private static CloudDetail lastDetail;
	private static ClientLevel lastLevel;

	private static final class Tile {
		final int x, z;
		int[] keys;
		boolean far;
		boolean dirty = true;
		double distance;
		VertexBuffer buffer;

		Tile(int x, int z) {
			this.x = x;
			this.z = z;
		}

		void close() {
			if(buffer != null)
				buffer.close();
			buffer = null;
		}
	}

	private CloudRenderer() {
	}

	/** Loads the clouds' shader. Listens on the mod bus. Without it, vanilla's clouds are drawn. */
	static void onRegisterShaders(RegisterShadersEvent event) {
		try {
			event.registerShader(new ShaderInstance(event.getResourceProvider(), AnotherWeatherMod.id("clouds"),
					DefaultVertexFormat.POSITION_COLOR_NORMAL), s -> shader = s);
		} catch(IOException e) {
			AnotherWeatherMod.LOGGER.error("Could not load the cloud shader; drawing vanilla clouds", e);
		}
	}

	/** Whether the mod draws the clouds now, not vanilla. */
	public static boolean active() {
		return shader != null && AwmClientConfig.CUSTOM_CLOUDS.get() && ClientWeatherState.settings().customClouds()
				&& (AwmClientConfig.CLOUDS_WITH_SHADER_PACKS.get() || !ShaderPacks.inUse());
	}

	/** Draws the clouds, from where vanilla would; false if vanilla should draw them instead. */
	public static boolean render(Matrix4f frustum, Matrix4f projection, float partialTick, double camX, double camY,
			double camZ) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if(!active() || level == null) {
			clear();
			return false;
		}
		float cloudY = level.effects().getCloudHeight();
		if(Float.isNaN(cloudY))
			return true;
		CloudDetail detail = mc.options.getCloudsType() == CloudStatus.FAST ? CloudDetail.LOW
				: AwmClientConfig.CLOUD_DETAIL.get();
		if(detail != lastDetail || level != lastLevel) {
			clear();
			lastDetail = detail;
			lastLevel = level;
		}
		long gameTime = level.getGameTime();
		double driftX = (gameTime + partialTick) * DRIFT;
		update(level, detail, gameTime, driftX, camX - driftX, camZ);
		draw(level, detail, frustum, projection, partialTick, cloudY, camX - driftX, camY, camZ);
		return true;
	}

	/** Finds the tiles in range, rereads their cells every few ticks, and rebuilds a few that changed. */
	private static void update(ClientLevel level, CloudDetail detail, long gameTime, double driftX, double cx, double cz) {
		double range = detail.range();
		boolean resample = gameTime - lastSample >= RESAMPLE_TICKS || gameTime < lastSample;
		if(resample)
			lastSample = gameTime;
		CloudCover cover = new CloudCover(ClientWeatherState.storms(), gameTime, driftX);
		int x0 = Mth.floor((cx - range) / TILE_SIZE), x1 = Mth.floor((cx + range) / TILE_SIZE);
		int z0 = Mth.floor((cz - range) / TILE_SIZE), z1 = Mth.floor((cz + range) / TILE_SIZE);
		java.util.Set<Long> seen = new java.util.HashSet<>();
		List<Tile> dirty = new ArrayList<>();
		for(int tx = x0; tx <= x1; tx++)
			for(int tz = z0; tz <= z1; tz++) {
				double dx = Math.max(0, Math.max(tx * TILE_SIZE - cx, cx - (tx + 1) * TILE_SIZE));
				double dz = Math.max(0, Math.max(tz * TILE_SIZE - cz, cz - (tz + 1) * TILE_SIZE));
				double distance = Math.hypot(dx, dz);
				if(distance > range)
					continue;
				long id = (long)tx << 32 ^ (tz & 0xFFFFFFFFL);
				seen.add(id);
				final int fx = tx, fz = tz;
				Tile tile = tiles.computeIfAbsent(id, k -> new Tile(fx, fz));
				tile.distance = distance;
				if(resample || tile.keys == null) {
					int[] keys = new int[TILE * TILE];
					for(int a = 0; a < TILE; a++)
						for(int b = 0; b < TILE; b++)
							keys[a * TILE + b] = cover.cell(tx * TILE + a, tz * TILE + b);
					if(!Arrays.equals(keys, tile.keys)) {
						tile.keys = keys;
						tile.dirty = true;
					}
				}
				boolean far = distance > range * NEAR;
				if(far != tile.far) {
					tile.far = far;
					tile.dirty = true;
				}
				if(tile.dirty)
					dirty.add(tile);
			}
		for(Iterator<Map.Entry<Long, Tile>> it = tiles.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, Tile> e = it.next();
			if(!seen.contains(e.getKey())) {
				e.getValue().close();
				it.remove();
			}
		}
		// Empty tiles first (nothing to show yet), then nearest first.
		dirty.sort(Comparator.<Tile>comparingInt(t -> t.buffer == null ? 0 : 1).thenComparingDouble(t -> t.distance));
		for(int n = 0; n < Math.min(REBUILDS_PER_FRAME, dirty.size()); n++)
			rebuild(dirty.get(n), detail);
	}

	private static void rebuild(Tile tile, CloudDetail detail) {
		tile.dirty = false;
		MeshData mesh = CloudMesh.build(tile.x, tile.z, TILE, tile.keys, detail, tile.far);
		if(mesh == null) {
			tile.close();
			return;
		}
		if(tile.buffer == null)
			tile.buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
		tile.buffer.bind();
		tile.buffer.upload(mesh);
		VertexBuffer.unbind();
	}

	private static void draw(ClientLevel level, CloudDetail detail, Matrix4f frustum, Matrix4f projection,
			float partialTick, float cloudY, double cx, double camY, double cz) {
		double range = detail.range();
		float end = WeatherEffects.cloudFadeEnd((float)range);
		shader.safeGetUniform("FadeStart").set(end * 0.55f);
		shader.safeGetUniform("FadeEnd").set(end);
		float angle = level.getSunAngle(partialTick);
		float sunX = -Mth.sin(angle), sunY = Mth.cos(angle);
		shader.safeGetUniform("SunDir").set(sunX, sunY, 0f);
		shader.safeGetUniform("SunLight").set(Mth.clamp(sunY * 2.5f + 0.25f, 0, 1) * (1 - level.getRainLevel(partialTick) * 0.6f));
		Vec3 color = level.getCloudColor(partialTick);
		float bright = AwmClientConfig.CLOUD_BRIGHTNESS.get().floatValue();
		RenderSystem.setShaderColor((float)Math.min(1, color.x * bright), (float)Math.min(1, color.y * bright),
				(float)Math.min(1, color.z * bright), 1);
		FogRenderer.levelFogColor();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		// Both sides: from inside a cloud you see its inside, and no face order to get wrong.
		RenderSystem.disableCull();
		for(Tile tile : tiles.values()) {
			if(tile.buffer == null)
				continue;
			Matrix4f modelView = new Matrix4f(frustum).translate((float)(tile.x * TILE_SIZE - cx), (float)(cloudY - camY),
					(float)(tile.z * TILE_SIZE - cz));
			tile.buffer.bind();
			tile.buffer.drawWithShader(modelView, projection, shader);
		}
		VertexBuffer.unbind();
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
		RenderSystem.setShaderColor(1, 1, 1, 1);
	}

	/** Drops every tile (leaving a world, a change of settings). */
	static void clear() {
		if(tiles.isEmpty())
			return;
		tiles.values().forEach(Tile::close);
		tiles.clear();
		lastSample = Long.MIN_VALUE;
	}
}
