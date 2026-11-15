package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmClientConfig;
import com.mojang.blaze3d.platform.GlStateManager;
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
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Now and then, when rain clears with the sun low behind you, a rainbow: a faint ring about 42 degrees round the point
 * opposite the sun, red outside and violet in ({@link RainbowShape}), fading in as the rain stops and out a couple of
 * minutes later or as the sun climbs or sets. It is a whole circle left to the depth test: the ground and hills in
 * front of it hide its lower part, so from the ground it is an arc, and from high up a full halo.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID, value = Dist.CLIENT)
public final class Rainbows {

	/** Chance that rain clearing brings a rainbow, when the sun is right. */
	static final double CHANCE = 0.5;
	/** How long it rained, ticks, for its clearing to count. */
	static final int RAINED = 400;
	/** How long a rainbow lasts, ticks. */
	static final int LASTS = 2400;
	/**
	 * How far off it is drawn, blocks: terrain nearer than this hides it. Close enough that, from high up, the ground
	 * under its lower half is further off, so the whole ring shows.
	 */
	static final float DISTANCE = 160;

	private static int rainedTicks;
	private static long until = Long.MIN_VALUE;
	private static float alpha;

	private Rainbows() {
	}

	/** Sine of the sun's height above the horizon. */
	private static float sunHeight(ClientLevel level, float partialTick) {
		return Mth.cos(level.getSunAngle(partialTick));
	}

	/** Whether the sun is low enough for a rainbow to show above the horizon, and up. */
	private static boolean sunRight(ClientLevel level) {
		float y = sunHeight(level, 1);
		return y > 0.035f && y < 0.62f;
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if(level == null || mc.player == null || mc.isPaused())
			return;
		float rain = level.getRainLevel(1);
		long now = level.getGameTime();
		if(rain > 0.2f)
			rainedTicks++;
		else if(rain < 0.02f && rainedTicks > 0) {
			// The rain has cleared: a rainbow, maybe, if it was rain (not snow) and the sun is low behind you.
			BlockPos pos = mc.player.blockPosition();
			if(rainedTicks >= RAINED && AwmClientConfig.RAINBOWS.get() && sunRight(level)
					&& level.getBiome(pos).value().getPrecipitationAt(pos) == Biome.Precipitation.RAIN
					&& Whirls.hash(now / 1200, level.dimension().location().hashCode(), 11) < CHANCE)
				until = now + LASTS;
			rainedTicks = 0;
		}
		boolean show = now < until && AwmClientConfig.RAINBOWS.get() && sunRight(level) && rain < 0.05f;
		alpha = show ? Math.min(1, alpha + 0.005f) : Math.max(0, alpha - 0.005f);
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		rainedTicks = 0;
		until = Long.MIN_VALUE;
		alpha = 0;
	}

	@SubscribeEvent
	public static void onRenderStage(RenderLevelStageEvent event) {
		if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || alpha <= 0.01f)
			return;
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if(level == null)
			return;
		float angle = level.getSunAngle(event.getPartialTick().getGameTimeDeltaPartialTick(false));
		// Inside the far plane even at the shortest render distances, or it would be clipped away.
		float distance = Math.min(DISTANCE, mc.gameRenderer.getDepthFar() * 0.8f);
		float[] quads = RainbowShape.quads(angle, distance, alpha);

		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		for(int i = 0; i < quads.length; i += 7)
			buffer.addVertex(quads[i], quads[i + 1], quads[i + 2]).setColor(quads[i + 3], quads[i + 4], quads[i + 5], quads[i + 6]);
		MeshData mesh = buffer.build();
		if(mesh != null)
			BufferUploader.drawWithShader(mesh);
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
	}
}
