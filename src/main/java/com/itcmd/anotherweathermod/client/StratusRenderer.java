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
 * Draws a dry day's {@link Stratus}: one flat textured sheet {@link #ABOVE} blocks over the cloud height, as part of the
 * sky (so the land and the day's cumulus stand in front of it), drifting with the wind and fading out toward the
 * horizon. It eases in and out over a few seconds when the day changes, and thins away under rain.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID, value = Dist.CLIENT)
public final class StratusRenderer {

	static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AnotherWeatherMod.MOD_ID, "dynamic/stratus");
	/** Blocks over the cloud height (and the cumulus) the sheet hangs. */
	static final double ABOVE = 48;
	/** Quads a side of the sheet. */
	static final int GRID = 16;
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
		double reach = Math.max(384, mc.options.getEffectiveRenderDistance() * 16.0 * 2.5);
		float y = (float)(cloudY + ABOVE - camera.y);
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
		for(int i = 0; i < GRID; i++)
			for(int j = 0; j < GRID; j++) {
				double x0 = -reach + 2 * reach * i / GRID, x1 = -reach + 2 * reach * (i + 1) / GRID;
				double z0 = -reach + 2 * reach * j / GRID, z1 = -reach + 2 * reach * (j + 1) / GRID;
				corner(buffer, x0, y, z0, reach, baseU, baseV, r, g, b, alpha);
				corner(buffer, x1, y, z0, reach, baseU, baseV, r, g, b, alpha);
				corner(buffer, x1, y, z1, reach, baseU, baseV, r, g, b, alpha);
				corner(buffer, x0, y, z1, reach, baseU, baseV, r, g, b, alpha);
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

	/** A corner of the sheet, fading out toward its rim (the horizon). */
	private static void corner(BufferBuilder buffer, double x, float y, double z, double reach, double baseU, double baseV,
			float r, float g, float b, float alpha) {
		double d = Math.min(1, Math.hypot(x, z) / reach);
		float fade = (float)(1 - d * d);
		buffer.addVertex((float)x, y, (float)z).setUv((float)(baseU + x / Stratus.BLOCKS), (float)(baseV + z / Stratus.BLOCKS))
				.setColor(r, g, b, alpha * fade * fade);
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
