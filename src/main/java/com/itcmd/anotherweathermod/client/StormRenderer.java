package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
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
		if(level == null || payload.storms().isEmpty())
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
			if(!Float.isNaN(cloudY))
				deck(storm, sx, sz, cloudY, camera, viewDistance, level.getGameTime() + partial);
			wall(storm, sx, sz, level.getSeaLevel() - 8, Float.isNaN(cloudY) ? 192 : cloudY, camera, viewDistance,
					level.getGameTime() + partial);
		}

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

	private static void draw(BufferBuilder buffer) {
		MeshData mesh = buffer.build();
		if(mesh != null)
			BufferUploader.drawWithShader(mesh);
	}
}
