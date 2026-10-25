package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmClientConfig;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.network.ClientWeatherState;
import com.itcmd.anotherweathermod.network.NearbyStormsPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Storms seen from outside: a dark cloud deck under the vanilla clouds over each storm, and a curtain of rain hanging
 * from its edge, drawn on the side facing the camera. Inside a storm the vanilla rain (driven by the rain level the
 * server sends) does the rest.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID, value = Dist.CLIENT)
public final class StormRenderer {

	private static final ResourceLocation CLOUDS = ResourceLocation.withDefaultNamespace("textures/environment/clouds.png");
	private static final ResourceLocation RAIN = ResourceLocation.withDefaultNamespace("textures/environment/rain.png");

	/** Segments around a cloud deck. */
	static final int DECK_SEGMENTS = 40;
	/** Segments along a rain wall. */
	static final int WALL_SEGMENTS = 32;
	/** Longest stretch of rain wall drawn, blocks along the edge. */
	static final double WALL_LENGTH = 700;
	/** Rain walls show from this far inside the edge (looking out) to this far outside. */
	static final double WALL_INSIDE = 64;
	static final double WALL_OUTSIDE = 900;

	private StormRenderer() {
	}

	@SubscribeEvent
	public static void onRenderStage(RenderLevelStageEvent event) {
		if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER)
			return;
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		NearbyStormsPayload payload = ClientWeatherState.storms();
		boolean clouds = AwmClientConfig.STORM_CLOUDS.get();
		boolean walls = AwmClientConfig.RAIN_WALLS.get();
		if(level == null || payload.storms().isEmpty() || !clouds && !walls && !AwmClientConfig.TORNADO_FUNNELS.get())
			return;
		float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
		double age = level.getGameTime() - payload.gameTime() + partial;
		Vec3 camera = event.getCamera().getPosition();
		float cloudY = level.effects().getCloudHeight();
		double viewDistance = mc.options.getEffectiveRenderDistance() * 16.0;

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

		for(NearbyStormsPayload.Storm storm : payload.storms()) {
			double sx = storm.x() + storm.velocityX() * age;
			double sz = storm.z() + storm.velocityZ() * age;
			if(clouds && !Float.isNaN(cloudY) && !CloudRenderer.active()) // The mod's clouds draw storms themselves.
				deck(storm, sx, sz, cloudY, camera, viewDistance, level.getGameTime() + partial);
			if(walls)
				wall(storm, sx, sz, level.getSeaLevel() - 8, Float.isNaN(cloudY) ? 192 : cloudY, camera, viewDistance,
					level.getGameTime() + partial);
		}
		if(AwmClientConfig.TORNADO_FUNNELS.get())
			for(TornadoTracker.Funnel funnel : TornadoTracker.funnels(partial))
				funnel(level, funnel, Math.max(20, (Float.isNaN(cloudY) ? 192 : cloudY) - funnel.ground()), Look.TORNADO,
						camera, level.getGameTime() + partial);

		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
	}

	/** Darkness of the deck: thunderstorms and worse are much darker than showers. */
	private static float deckAlpha(NearbyStormsPayload.Storm storm) {
		return storm.intensity() * (storm.kind() == StormKind.SHOWER ? 0.55f : 0.85f);
	}

	/** A dark disc of cloud texture at cloud height, solid in the middle and fading to nothing at the edge. */
	private static void deck(NearbyStormsPayload.Storm storm, double sx, double sz, float cloudY, Vec3 camera,
			double viewDistance, double time) {
		double radius = storm.radius();
		double dist = Math.hypot(sx - camera.x, sz - camera.z) - radius;
		float fade = (float)Mth.clamp(1 - (dist - viewDistance) / viewDistance, 0, 1);
		float alpha = deckAlpha(storm) * fade;
		if(alpha < 0.02f)
			return;
		float shade = AwmClientConfig.bright(storm.kind() == StormKind.SHOWER ? 0.45f : 0.28f, AwmClientConfig.CLOUD_BRIGHTNESS);
		RenderSystem.setShaderTexture(0, CLOUDS);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		double[] rings = { 0, radius * 0.55, radius * 0.85, radius };
		float[] ringAlpha = { alpha, alpha, alpha * 0.6f, 0 };
		double scroll = time * 0.03 / 3072;
		for(int layer = 0; layer < 2; layer++) {
			float y = (float)(cloudY - 3 - layer * 6 - camera.y);
			for(int ring = 0; ring < rings.length - 1; ring++)
				for(int i = 0; i < DECK_SEGMENTS; i++) {
					double a0 = Math.PI * 2 * i / DECK_SEGMENTS;
					double a1 = Math.PI * 2 * (i + 1) / DECK_SEGMENTS;
					deckVertex(buffer, sx, sz, rings[ring], a0, y, camera, scroll, shade, ringAlpha[ring], layer);
					deckVertex(buffer, sx, sz, rings[ring + 1], a0, y, camera, scroll, shade, ringAlpha[ring + 1], layer);
					deckVertex(buffer, sx, sz, rings[ring + 1], a1, y, camera, scroll, shade, ringAlpha[ring + 1], layer);
					deckVertex(buffer, sx, sz, rings[ring], a1, y, camera, scroll, shade, ringAlpha[ring], layer);
				}
		}
		draw(buffer);
	}

	private static void deckVertex(BufferBuilder buffer, double sx, double sz, double r, double angle, float y,
			Vec3 camera, double scroll, float shade, float alpha, int layer) {
		double x = sx + Math.sin(angle) * r;
		double z = sz - Math.cos(angle) * r;
		// The cloud texture at vanilla's scale (12 blocks a pixel, 256 pixels), offset per layer.
		float u = (float)(x / 3072 + scroll + layer * 0.37);
		float v = (float)(z / 3072 + layer * 0.21);
		buffer.addVertex((float)(x - camera.x), y, (float)(z - camera.z)).setUv(u, v).setColor(shade, shade, shade * 1.08f,
				alpha);
	}

	/** A curtain of falling rain from the cloud deck to the ground along the storm's edge, facing the camera. */
	private static void wall(NearbyStormsPayload.Storm storm, double sx, double sz, int bottom, float top, Vec3 camera,
			double viewDistance, double time) {
		double radius = storm.radius();
		double dx = camera.x - sx;
		double dz = camera.z - sz;
		double d = Math.hypot(dx, dz);
		double fromEdge = d - radius;
		if(fromEdge < -WALL_INSIDE || fromEdge > Math.min(WALL_OUTSIDE, viewDistance * 2))
			return;
		float wallR = AwmClientConfig.bright(0.72f, AwmClientConfig.RAIN_BRIGHTNESS);
		float wallG = AwmClientConfig.bright(0.75f, AwmClientConfig.RAIN_BRIGHTNESS);
		float wallB = AwmClientConfig.bright(0.8f, AwmClientConfig.RAIN_BRIGHTNESS);
		float alpha = storm.intensity() * (storm.kind() == StormKind.SHOWER ? 0.35f : 0.5f);
		alpha *= (float)Mth.clamp(1 - Math.max(0, fromEdge - 200) / 700, 0.35, 1);
		if(storm.kind() == StormKind.HURRICANE)
			alpha *= 0.8f;
		if(alpha < 0.02f)
			return;

		double facing = Math.atan2(dx, -dz); // Bearing from the centre to the camera.
		double span = Math.min(Math.PI, WALL_LENGTH / 2 / radius);
		RenderSystem.setShaderTexture(0, RAIN);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		float scroll = (float)(time * 0.05 % 1);
		float y0 = (float)(bottom - camera.y);
		float y1 = (float)(top - 4 - camera.y);
		float v0 = y0 / 16 + scroll;
		float v1 = y1 / 16 + scroll;
		for(int i = 0; i < WALL_SEGMENTS; i++) {
			double a0 = facing - span + 2 * span * i / WALL_SEGMENTS;
			double a1 = facing - span + 2 * span * (i + 1) / WALL_SEGMENTS;
			// Fade out toward both ends of the stretch drawn.
			float e0 = (float)(1 - Math.abs(2.0 * i / WALL_SEGMENTS - 1));
			float e1 = (float)(1 - Math.abs(2.0 * (i + 1) / WALL_SEGMENTS - 1));
			float x0 = (float)(sx + Math.sin(a0) * radius - camera.x);
			float z0 = (float)(sz - Math.cos(a0) * radius - camera.z);
			float x1 = (float)(sx + Math.sin(a1) * radius - camera.x);
			float z1 = (float)(sz - Math.cos(a1) * radius - camera.z);
			float u0 = (float)(a0 * radius / 6);
			float u1 = (float)(a1 * radius / 6);
			float c0 = alpha * Math.min(1, e0 * 3);
			float c1 = alpha * Math.min(1, e1 * 3);
			buffer.addVertex(x0, y0, z0).setUv(u0, v0).setColor(wallR, wallG, wallB, c0);
			buffer.addVertex(x1, y0, z1).setUv(u1, v0).setColor(wallR, wallG, wallB, c1);
			buffer.addVertex(x1, y1, z1).setUv(u1, v1).setColor(wallR, wallG, wallB, c1 * 0.6f);
			buffer.addVertex(x0, y1, z0).setUv(u0, v1).setColor(wallR, wallG, wallB, c0 * 0.6f);
		}
		draw(buffer);
	}

	/**
	 * How a funnel is drawn. A tornado's is near black; a waterspout's and a dust devil's are paler ({@code lighten} of
	 * the way to white) and see-through ({@code alpha}), tinted by {@code r}, {@code g} and {@code b}.
	 */
	public record Look(float lighten, float r, float g, float b, float alpha) {

		public static final Look TORNADO = new Look(0, 1, 1.02f, 1.08f, 1);

		float shade(float base) {
			return base + (1 - base) * lighten;
		}
	}

	/** Rings up a tornado funnel, and segments round it. */
	static final int FUNNEL_RINGS = 22;
	static final int FUNNEL_SEGMENTS = 24;
	/** The funnel's cloud shells round its solid core, inside out: radius, spin speed and opacity. */
	static final double[] SHELL_RADIUS = { 1.0, 1.25 };
	static final double[] SHELL_SPIN = { 1.0, 0.55 };
	static final float[] SHELL_ALPHA = { 0.8f, 0.5f };
	/** The solid core's radius, as a share of the funnel's. */
	static final double CORE = 0.8;

	/** A funnel's radius at height {@code h} (0 at its foot, 1 at the cloud) for a core radius {@code rt}. */
	public static double funnelRadius(double rt, double h) {
		return rt * (0.4 + 2.6 * Math.pow(h, 2.2));
	}

	/** How far a funnel's column has snaked off its foot at height {@code h}, x and z. */
	public static double[] funnelSway(double rt, double h, double time) {
		return new double[] { rt * 0.6 * Math.sin(time * 0.013 + h * 3.1) * h, rt * 0.6 * Math.cos(time * 0.011 + h * 2.7) * h };
	}

	/**
	 * Height above the ground of the point {@code h} up a funnel's shape (0 its tip, 1 the cloud), for a funnel
	 * {@code height} tall from the ground that reaches {@code reach} of the way down.
	 */
	public static double funnelHeight(double h, double height, float reach) {
		return height * (1 - reach * (1 - h));
	}

	/** How much of a funnel's foot is on the ground: its debris cloud shows only as it touches down. */
	static float touchdown(TornadoTracker.Funnel funnel) {
		return Mth.clamp((funnel.reach() - 0.85f) / 0.15f, 0, 1);
	}

	/**
	 * A tornado: a solid, dark core wrapped in grey shells of cloud, lumpy and spinning at different speeds, from the
	 * storm's base down toward the ground, thin at the foot and flaring at the top, the column snaking. A new one
	 * lowers out of the cloud as a thin rope and widens as it comes down. Once down, its foot rides the ground and a
	 * squat cloud of dust and debris churns round it. {@link TornadoCloudParticle}s whirl round it on top of this.
	 */
	static void funnel(ClientLevel level, TornadoTracker.Funnel funnel, double height, Look look, Vec3 camera,
			double time) {
		if(funnel.strength() <= 0.01f || funnel.reach() <= 0.01f)
			return;
		// The core: solid, so nothing shows through, but mottled so it reads as cloud.
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder core = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		for(int ring = 0; ring < FUNNEL_RINGS; ring++) {
			double h0 = ring / (double)FUNNEL_RINGS, h1 = (ring + 1) / (double)FUNNEL_RINGS;
			for(int i = 0; i < FUNNEL_SEGMENTS; i++) {
				double a0 = Math.PI * 2 * i / FUNNEL_SEGMENTS, a1 = Math.PI * 2 * (i + 1) / FUNNEL_SEGMENTS;
				coreVertex(core, level, funnel, height, look, h0, a0, time, camera);
				coreVertex(core, level, funnel, height, look, h1, a0, time, camera);
				coreVertex(core, level, funnel, height, look, h1, a1, time, camera);
				coreVertex(core, level, funnel, height, look, h0, a1, time, camera);
			}
		}
		draw(core);
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, CLOUDS);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		for(int shell = SHELL_RADIUS.length - 1; shell >= 0; shell--)
			for(int ring = 0; ring < FUNNEL_RINGS; ring++) {
				double h0 = ring / (double)FUNNEL_RINGS, h1 = (ring + 1) / (double)FUNNEL_RINGS;
				for(int i = 0; i < FUNNEL_SEGMENTS; i++) {
					double a0 = Math.PI * 2 * i / FUNNEL_SEGMENTS, a1 = Math.PI * 2 * (i + 1) / FUNNEL_SEGMENTS;
					funnelVertex(buffer, level, funnel, height, look, shell, h0, a0, time, camera);
					funnelVertex(buffer, level, funnel, height, look, shell, h1, a0, time, camera);
					funnelVertex(buffer, level, funnel, height, look, shell, h1, a1, time, camera);
					funnelVertex(buffer, level, funnel, height, look, shell, h0, a1, time, camera);
				}
			}
		// The debris cloud: a squat, churning, dark brown-grey ring round the foot, once it is down.
		if(touchdown(funnel) > 0)
			for(int layer = 0; layer < 2; layer++)
				for(int i = 0; i < FUNNEL_SEGMENTS; i++) {
					double a0 = Math.PI * 2 * i / FUNNEL_SEGMENTS, a1 = Math.PI * 2 * (i + 1) / FUNNEL_SEGMENTS;
					skirtVertex(buffer, level, funnel, look, layer, 0, a0, time, camera);
					skirtVertex(buffer, level, funnel, look, layer, 1, a0, time, camera);
					skirtVertex(buffer, level, funnel, look, layer, 1, a1, time, camera);
					skirtVertex(buffer, level, funnel, look, layer, 0, a1, time, camera);
				}
		draw(buffer);
	}

	/** Where on a funnel the point {@code h} up and {@code angle} round, {@code scale} times its radius out, is. */
	private static Vec3 funnelPoint(ClientLevel level, TornadoTracker.Funnel funnel, double height, double scale,
			double h, double angle, double time) {
		double rt = funnel.coreRadius();
		double radius = funnelRadius(rt, h) * scale;
		double[] sway = funnelSway(rt, h, time);
		double x = funnel.x() + sway[0] + Math.sin(angle) * radius;
		double z = funnel.z() + sway[1] - Math.cos(angle) * radius;
		// Once down, the foot follows the ground under it; above, the funnel rises from the smoothed ground.
		double y = h == 0 && funnel.reach() >= 0.99f ? footY(level, x, z, funnel.ground()) - 1
				: funnel.ground() + funnelHeight(h, height, funnel.reach());
		return new Vec3(x, y, z);
	}

	private static void coreVertex(BufferBuilder buffer, ClientLevel level, TornadoTracker.Funnel funnel,
			double height, Look look, double h, double angle, double time, Vec3 camera) {
		double spin = time * 0.05 * 1.7;
		double lump = 1 + 0.15 * Math.sin(angle * 3 + h * 9 - spin * 2) + 0.1 * Math.sin(angle * 5 - h * 14 + spin * 1.3);
		Vec3 at = funnelPoint(level, funnel, height, CORE * lump, h, angle, time);
		// Mottled near-black to dark grey, banded as it turns.
		float mottle = (float)(0.5 + 0.25 * Math.sin(angle * 4 + h * 20 - spin * 2.4) + 0.25 * Math.sin(angle * 7 - h * 11 + spin));
		float shade = AwmClientConfig.bright(look.shade(0.05f + 0.1f * mottle + 0.05f * (float)h),
				AwmClientConfig.TORNADO_BRIGHTNESS);
		float alpha = Math.min(1, funnel.strength() * 2) * (float)(h < 0.9 ? 1 : (1 - h) / 0.1) * look.alpha();
		buffer.addVertex((float)(at.x - camera.x), (float)(at.y - camera.y), (float)(at.z - camera.z))
				.setColor(Math.min(1, shade * look.r()), Math.min(1, shade * look.g()), Math.min(1, shade * look.b()), alpha);
	}

	private static void funnelVertex(BufferBuilder buffer, ClientLevel level, TornadoTracker.Funnel funnel,
			double height, Look look, int shell, double h, double angle, double time, Vec3 camera) {
		double spin = time * 0.05 * SHELL_SPIN[shell];
		// Lumpy: bulges that wind round and up the funnel as it turns, different on each shell.
		double lump = 1 + 0.2 * Math.sin(angle * 3 + h * 9 - spin * 2 + shell)
				+ 0.12 * Math.sin(angle * 5 - h * 14 + spin * 1.3 + shell * 2.1);
		Vec3 at = funnelPoint(level, funnel, height, SHELL_RADIUS[shell] * lump, h, angle, time);
		float u = (float)(angle / (Math.PI * 2) * 2 + spin * (1.6 - h));
		float v = (float)(h * 2.2 + shell * 0.31 - time * 0.004 * SHELL_SPIN[shell]);
		float shade = AwmClientConfig.bright(look.shade((0.2f + 0.12f * (float)h) * (1 + 0.25f * shell)),
				AwmClientConfig.TORNADO_BRIGHTNESS);
		float alpha = funnel.strength() * SHELL_ALPHA[shell] * (float)(h < 0.88 ? 1 : (1 - h) / 0.12) * look.alpha();
		buffer.addVertex((float)(at.x - camera.x), (float)(at.y - camera.y), (float)(at.z - camera.z)).setUv(u, v)
				.setColor(Math.min(1, shade * look.r()), Math.min(1, shade * look.g()), Math.min(1, shade * look.b()), alpha);
	}

	/** The ground under ({@code x}, {@code z}), leaves left out, but never far from the smoothed foot. */
	private static double footY(ClientLevel level, double x, double z, double ground) {
		BlockPos at = BlockPos.containing(x, 0, z);
		if(!level.isLoaded(at))
			return ground;
		double here = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				at.getX(), at.getZ());
		return Mth.clamp(here, ground - 4, ground + 4);
	}

	private static void skirtVertex(BufferBuilder buffer, ClientLevel level, TornadoTracker.Funnel funnel, Look look,
			int layer, int top, double angle, double time, Vec3 camera) {
		double rt = funnel.coreRadius();
		double spin = time * 0.06 * (layer == 0 ? 1.4 : 0.9);
		double lump = 1 + 0.25 * Math.sin(angle * 4 + spin * 2 + layer);
		double radius = rt * (top == 0 ? 1.1 + 0.3 * layer : 1.7 + 0.5 * layer) * lump;
		double x = funnel.x() + Math.sin(angle) * radius;
		double z = funnel.z() - Math.cos(angle) * radius;
		double y = footY(level, x, z, funnel.ground()) + (top == 0 ? -1 : (6 + 5 * layer) * (look == Look.TORNADO ? 1 : Math.min(1, rt / 8)));
		float u = (float)(angle / (Math.PI * 2) * 3 + spin * 1.6 + layer * 0.5);
		float shade = AwmClientConfig.bright(look.shade(0.2f + 0.04f * layer), AwmClientConfig.TORNADO_BRIGHTNESS);
		float alpha = funnel.strength() * touchdown(funnel) * (top == 0 ? 0.7f : 0) * (layer == 0 ? 1 : 0.6f) * look.alpha();
		// A tornado's debris is brown-grey; a waterspout's spray and a dust devil's dust take the funnel's tint.
		float r = look == Look.TORNADO ? 1.1f : look.r(), g = look == Look.TORNADO ? 1 : look.g();
		float b = look == Look.TORNADO ? 0.92f : look.b();
		buffer.addVertex((float)(x - camera.x), (float)(y - camera.y), (float)(z - camera.z))
				.setUv(u, (float)(top * 0.4 + layer * 0.2))
				.setColor(Math.min(1, shade * r), Math.min(1, shade * g), Math.min(1, shade * b), alpha);
	}

	private static void draw(BufferBuilder buffer) {
		MeshData mesh = buffer.build();
		if(mesh != null)
			BufferUploader.drawWithShader(mesh);
	}
}
