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
			if(clouds && !Float.isNaN(cloudY))
				deck(storm, sx, sz, cloudY, camera, viewDistance, level.getGameTime() + partial);
			if(walls)
				wall(storm, sx, sz, level.getSeaLevel() - 8, Float.isNaN(cloudY) ? 192 : cloudY, camera, viewDistance,
					level.getGameTime() + partial);
		}
		if(AwmClientConfig.TORNADO_FUNNELS.get())
			for(TornadoTracker.Funnel funnel : TornadoTracker.funnels(partial))
				funnel(level, funnel, Float.isNaN(cloudY) ? 192 : cloudY, camera, level.getGameTime() + partial);

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
		float shade = storm.kind() == StormKind.SHOWER ? 0.45f : 0.28f;
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
			buffer.addVertex(x0, y0, z0).setUv(u0, v0).setColor(0.72f, 0.75f, 0.8f, c0);
			buffer.addVertex(x1, y0, z1).setUv(u1, v0).setColor(0.72f, 0.75f, 0.8f, c1);
			buffer.addVertex(x1, y1, z1).setUv(u1, v1).setColor(0.72f, 0.75f, 0.8f, c1 * 0.6f);
			buffer.addVertex(x0, y1, z0).setUv(u0, v1).setColor(0.72f, 0.75f, 0.8f, c0 * 0.6f);
		}
		draw(buffer);
	}

	/** Rings up a tornado funnel, and segments round it. */
	static final int FUNNEL_RINGS = 22;
	static final int FUNNEL_SEGMENTS = 24;
	/** The funnel's shells, inside out: radius, spin speed and opacity. Spinning at different speeds gives depth. */
	static final double[] SHELL_RADIUS = { 0.75, 1.0, 1.3 };
	static final double[] SHELL_SPIN = { 1.7, 1.0, 0.55 };
	static final float[] SHELL_ALPHA = { 0.95f, 0.7f, 0.4f };

	/** A funnel's radius at height {@code h} (0 at its foot, 1 at the cloud) for a core radius {@code rt}. */
	public static double funnelRadius(double rt, double h) {
		return rt * (0.4 + 2.6 * Math.pow(h, 2.2));
	}

	/** How far a funnel's column has snaked off its foot at height {@code h}, x and z. */
	public static double[] funnelSway(double rt, double h, double time) {
		return new double[] { rt * 0.6 * Math.sin(time * 0.013 + h * 3.1) * h, rt * 0.6 * Math.cos(time * 0.011 + h * 2.7) * h };
	}

	/**
	 * A tornado: dark grey shells of cloud, lumpy and spinning at different speeds, from the ground up to the storm's
	 * base, thin at the foot and flaring at the top, the column snaking. Its foot rides the ground, and a squat cloud of
	 * dust and debris churns round it. {@link TornadoCloudParticle}s whirl round it on top of this.
	 */
	private static void funnel(ClientLevel level, TornadoTracker.Funnel funnel, float cloudY, Vec3 camera, double time) {
		if(funnel.strength() <= 0.01f)
			return;
		double height = Math.max(20, cloudY - funnel.ground());
		RenderSystem.setShaderTexture(0, CLOUDS);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		// Outer shells first, so the inner, darker ones show through.
		for(int shell = SHELL_RADIUS.length - 1; shell >= 0; shell--)
			for(int ring = 0; ring < FUNNEL_RINGS; ring++) {
				double h0 = ring / (double)FUNNEL_RINGS, h1 = (ring + 1) / (double)FUNNEL_RINGS;
				for(int i = 0; i < FUNNEL_SEGMENTS; i++) {
					double a0 = Math.PI * 2 * i / FUNNEL_SEGMENTS, a1 = Math.PI * 2 * (i + 1) / FUNNEL_SEGMENTS;
					funnelVertex(buffer, level, funnel, height, shell, h0, a0, time, camera);
					funnelVertex(buffer, level, funnel, height, shell, h1, a0, time, camera);
					funnelVertex(buffer, level, funnel, height, shell, h1, a1, time, camera);
					funnelVertex(buffer, level, funnel, height, shell, h0, a1, time, camera);
				}
			}
		// The debris cloud: a squat, churning, dark brown-grey ring round the foot.
		for(int layer = 0; layer < 2; layer++)
			for(int i = 0; i < FUNNEL_SEGMENTS; i++) {
				double a0 = Math.PI * 2 * i / FUNNEL_SEGMENTS, a1 = Math.PI * 2 * (i + 1) / FUNNEL_SEGMENTS;
				skirtVertex(buffer, level, funnel, layer, 0, a0, time, camera);
				skirtVertex(buffer, level, funnel, layer, 1, a0, time, camera);
				skirtVertex(buffer, level, funnel, layer, 1, a1, time, camera);
				skirtVertex(buffer, level, funnel, layer, 0, a1, time, camera);
			}
		draw(buffer);
	}

	private static void funnelVertex(BufferBuilder buffer, ClientLevel level, TornadoTracker.Funnel funnel,
			double height, int shell, double h, double angle, double time, Vec3 camera) {
		double rt = funnel.radius();
		double spin = time * 0.05 * SHELL_SPIN[shell];
		// Lumpy: bulges that wind round and up the funnel as it turns, different on each shell.
		double lump = 1 + 0.2 * Math.sin(angle * 3 + h * 9 - spin * 2 + shell)
				+ 0.12 * Math.sin(angle * 5 - h * 14 + spin * 1.3 + shell * 2.1);
		double radius = funnelRadius(rt, h) * SHELL_RADIUS[shell] * lump;
		double[] sway = funnelSway(rt, h, time);
		double x = funnel.x() + sway[0] + Math.sin(angle) * radius;
		double z = funnel.z() + sway[1] - Math.cos(angle) * radius;
		// The foot follows the ground under it; above, the funnel rises from the smoothed ground.
		double y = h == 0 ? footY(level, x, z, funnel.ground()) - 1 : funnel.ground() + h * height;
		float u = (float)(angle / (Math.PI * 2) * 2 + spin * (1.6 - h));
		float v = (float)(h * 2.2 + shell * 0.31 - time * 0.004 * SHELL_SPIN[shell]);
		float shade = (0.15f + 0.08f * (float)h) * (1 - 0.12f * shell);
		float alpha = funnel.strength() * SHELL_ALPHA[shell] * (float)(h < 0.88 ? 1 : (1 - h) / 0.12);
		buffer.addVertex((float)(x - camera.x), (float)(y - camera.y), (float)(z - camera.z)).setUv(u, v)
				.setColor(shade, shade * 1.02f, shade * 1.08f, alpha);
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

	private static void skirtVertex(BufferBuilder buffer, ClientLevel level, TornadoTracker.Funnel funnel, int layer,
			int top, double angle, double time, Vec3 camera) {
		double rt = funnel.radius();
		double spin = time * 0.06 * (layer == 0 ? 1.4 : 0.9);
		double lump = 1 + 0.25 * Math.sin(angle * 4 + spin * 2 + layer);
		double radius = rt * (top == 0 ? 1.1 + 0.3 * layer : 1.7 + 0.5 * layer) * lump;
		double x = funnel.x() + Math.sin(angle) * radius;
		double z = funnel.z() - Math.cos(angle) * radius;
		double y = footY(level, x, z, funnel.ground()) + (top == 0 ? -1 : 6 + 5 * layer);
		float u = (float)(angle / (Math.PI * 2) * 3 + spin * 1.6 + layer * 0.5);
		float shade = 0.2f + 0.04f * layer;
		float alpha = funnel.strength() * (top == 0 ? 0.7f : 0) * (layer == 0 ? 1 : 0.6f);
		buffer.addVertex((float)(x - camera.x), (float)(y - camera.y), (float)(z - camera.z))
				.setUv(u, (float)(top * 0.4 + layer * 0.2)).setColor(shade * 1.1f, shade, shade * 0.92f, alpha);
	}

	private static void draw(BufferBuilder buffer) {
		MeshData mesh = buffer.build();
		if(mesh != null)
			BufferUploader.drawWithShader(mesh);
	}
}
