package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The weather's fog for shader packs, which mostly draw their own fog and ignore the distances the game sets: nested
 * see-through shells of fog colour round the camera, out to where the fog closes in. Whatever is behind more of them
 * is hidden more, so the view fades out with distance as it does in vanilla's fog. Only drawn with a pack on.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID, value = Dist.CLIENT)
public final class FogShells {

	/** Shells out to the fog's distance. */
	static final int SHELLS = 8;
	/** How much of the view the shells hide at the fog's distance, at full thickness. */
	static final double HIDDEN = 0.95;
	static final int RINGS = 8;
	static final int SEGMENTS = 16;

	private FogShells() {
	}

	@SubscribeEvent
	public static void onRenderStage(RenderLevelStageEvent event) {
		if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || !AwmClientConfig.FOG_WITH_SHADER_PACKS.get()
				|| !AwmClientConfig.STORM_FOG.get() || !ShaderPacks.inUse())
			return;
		Minecraft mc = Minecraft.getInstance();
		if(mc.level == null || event.getCamera().getFluidInCamera() != FogType.NONE)
			return;
		float thick = WeatherEffects.fogThickness();
		if(thick < 0.01f)
			return;
		float view = mc.options.getEffectiveRenderDistance() * 16f;
		float distance = WeatherEffects.fogDistance(view);
		if(distance >= view * 0.95f)
			return;
		float[] fog = RenderSystem.getShaderFogColor();
		float alpha = (float)(1 - Math.pow(1 - HIDDEN, 1.0 / SHELLS)) * thick;

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		for(int n = 1; n <= SHELLS; n++)
			shell(buffer, distance * n / SHELLS, fog[0], fog[1], fog[2], alpha);
		MeshData mesh = buffer.build();
		if(mesh != null)
			BufferUploader.drawWithShader(mesh);
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
	}

	/** A sphere of {@code radius} round the camera. */
	private static void shell(BufferBuilder buffer, float radius, float r, float g, float b, float a) {
		for(int k = 0; k < RINGS; k++)
			for(int s = 0; s < SEGMENTS; s++) {
				vertex(buffer, radius, k, s, r, g, b, a);
				vertex(buffer, radius, k + 1, s, r, g, b, a);
				vertex(buffer, radius, k + 1, s + 1, r, g, b, a);
				vertex(buffer, radius, k, s + 1, r, g, b, a);
			}
	}

	private static void vertex(BufferBuilder buffer, float radius, int k, int s, float r, float g, float b, float a) {
		float lat = Mth.PI * k / RINGS - Mth.HALF_PI;
		float lon = Mth.TWO_PI * s / SEGMENTS;
		float flat = Mth.cos(lat) * radius;
		buffer.addVertex(flat * Mth.cos(lon), Mth.sin(lat) * radius, flat * Mth.sin(lon)).setColor(r, g, b, a);
	}
}
