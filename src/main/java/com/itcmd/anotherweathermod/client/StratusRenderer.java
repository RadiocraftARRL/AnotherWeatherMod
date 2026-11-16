package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmClientConfig;
import com.itcmd.anotherweathermod.network.ClientWeatherState;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws a dry day's {@link Stratus} on the sky itself (2026-11-15, the maintainer: the flat sheet read as one disk of
 * cloud; it should be a stratus skybox): a dome round the camera, drawn as part of the sky so the land and the day's
 * cumulus stand in front of it. Each point of the dome shows the layer where a high, endless sheet {@link #LAYER} blocks
 * up would be seen in that direction, so the clouds reach all round to the horizon, growing smaller and flatter toward
 * it as a real layer does, and fade into the haze low down. It drifts with the wind, eases in and out over a few
 * seconds when the day changes, and thins away under rain.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID, value = Dist.CLIENT)
public final class StratusRenderer {

	static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AnotherWeatherMod.MOD_ID, "dynamic/stratus");
	/** The height the layer is drawn as if at, blocks over the camera: sets how fast its clouds shrink toward the horizon. */
	static final double LAYER = 900;
	/** The dome's radius (as the vanilla sky's, inside the far plane). */
	static final float DOME = 100;
	/** The dome's rings, degrees above the horizon, and its segments round. */
	static final float[] RINGS = { 3, 5, 8, 12, 17, 23, 32, 45, 60, 75, 90 };
	static final int SEGMENTS = 32;
	/** Degrees above the horizon over which the layer fades in from the haze. */
	static final float FADE_FROM = 4, FADE_TO = 22;
	/** Seconds to ease in or out. */
	static final float FADE_SECONDS = 4;

	private static DynamicTexture texture;
	/** The coverage the texture was made for, to the nearest 1/50; -1 for none yet. */
	private static int madeFor = -1;
	private static float shown;
	private static double driftX, driftZ;
	private static long lastFrame;

	private StratusRenderer() {
	}

	@SubscribeEvent
	public static void onRenderStage(RenderLevelStageEvent event) {
		if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY)
			return;
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		long now = System.nanoTime();
		float seconds = lastFrame == 0 ? 0 : Math.min(0.25f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		if(level == null)
			return;
		float cloudY = level.effects().getCloudHeight();
		boolean allowed = AwmClientConfig.STRATUS.get() && !Float.isNaN(cloudY) && mc.options.getCloudsType() != CloudStatus.OFF
				&& (!ShaderPacks.inUse() || AwmClientConfig.CLOUDS_WITH_SHADER_PACKS.get());
		double coverage = allowed ? Stratus.coverage(ClientWeatherState.get().dayType(), level.getDayTime() / 24000L) : 0;
		shown = coverage > 0 ? Math.min(1, shown + seconds / FADE_SECONDS) : Math.max(0, shown - seconds / FADE_SECONDS);
		driftX += (CloudRenderer.DRIFT * 20 + WeatherEffects.windX() * 0.3) * seconds;
		driftZ += WeatherEffects.windZ() * 0.3 * seconds;
		if(shown <= 0.01f || Float.isNaN(cloudY))
			return;
		if(coverage > 0)
			make(mc, coverage);
		if(texture == null)
			return;
		float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
		float alpha = shown * 0.8f * (1 - level.getRainLevel(partial));
		if(alpha <= 0.01f)
			return;

		Vec3 camera = event.getCamera().getPosition();
		Vec3 colour = level.getCloudColor(partial);
		float bright = AwmClientConfig.bright(0.92f, AwmClientConfig.CLOUD_BRIGHTNESS);
		float r = (float)Math.min(1, colour.x * bright), g = (float)Math.min(1, colour.y * bright), b = (float)Math.min(1, colour.z * bright);
		double baseU = frac((camera.x - driftX) / Stratus.BLOCKS), baseV = frac((camera.z - driftZ) / Stratus.BLOCKS);

		// After the sky the view matrix is the sky's own no longer: put the camera's turn on, or the sheet sits fixed
		// on the screen and turns with the player's head (2026-11-13).
		org.joml.Matrix4fStack view = RenderSystem.getModelViewStack();
		view.pushMatrix();
		view.mul(event.getModelViewMatrix());
		RenderSystem.applyModelViewMatrix();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, TEXTURE);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		for(int ring = 0; ring + 1 < RINGS.length; ring++)
			for(int seg = 0; seg < SEGMENTS; seg++) {
				dome(buffer, RINGS[ring], seg, baseU, baseV, r, g, b, alpha);
				dome(buffer, RINGS[ring], seg + 1, baseU, baseV, r, g, b, alpha);
				dome(buffer, RINGS[ring + 1], seg + 1, baseU, baseV, r, g, b, alpha);
				dome(buffer, RINGS[ring + 1], seg, baseU, baseV, r, g, b, alpha);
			}
		MeshData mesh = buffer.build();
		if(mesh != null)
			BufferUploader.drawWithShader(mesh);
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
		RenderSystem.disableBlend();
		view.popMatrix();
		RenderSystem.applyModelViewMatrix();
	}

	/**
	 * A point of the dome {@code elevation} degrees up at segment {@code seg}: textured where the high layer is seen in
	 * that direction, and fading into the haze toward the horizon.
	 */
	private static void dome(BufferBuilder buffer, float elevation, int seg, double baseU, double baseV, float r, float g, float b, float alpha) {
		double e = Math.toRadians(elevation), a = 2 * Math.PI * seg / SEGMENTS;
		double dx = Math.cos(e) * Math.cos(a), dy = Math.sin(e), dz = Math.cos(e) * Math.sin(a);
		double reach = LAYER / Math.max(dy, 0.01); // Along the ground to where the layer is seen.
		double t = Math.max(0, Math.min(1, (elevation - FADE_FROM) / (FADE_TO - FADE_FROM)));
		float fade = (float)(t * t * (3 - 2 * t));
		buffer.addVertex((float)(dx * DOME), (float)(dy * DOME), (float)(dz * DOME))
				.setUv((float)(baseU + dx * reach / Stratus.BLOCKS), (float)(baseV + dz * reach / Stratus.BLOCKS))
				.setColor(r, g, b, alpha * fade);
	}

	private static double frac(double v) {
		return v - Math.floor(v);
	}

	/** Makes (or remakes, when the day's coverage differs) the sheet's texture: white, its opacity from the noise. */
	private static void make(Minecraft mc, double coverage) {
		int key = (int)Math.round(coverage * 50);
		if(texture != null && key == madeFor)
			return;
		if(texture == null) {
			texture = new DynamicTexture(Stratus.SIZE, Stratus.SIZE, false);
			mc.getTextureManager().register(TEXTURE, texture);
		}
		NativeImage image = texture.getPixels();
		if(image == null)
			return;
		for(int x = 0; x < Stratus.SIZE; x++)
			for(int y = 0; y < Stratus.SIZE; y++) {
				int a = (int)Math.round(Stratus.opacity(x, y, coverage) * 255);
				image.setPixelRGBA(x, y, a << 24 | 0xFFFFFF);
			}
		texture.upload();
		madeFor = key;
	}
}
