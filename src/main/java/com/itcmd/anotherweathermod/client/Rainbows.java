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
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Now and then, when rain clears with the sun low behind you, a rainbow: an arc 42 degrees round the point opposite
 * the sun, red outside and violet in, fading in as the rain stops and out a couple of minutes later or as the sun
 * climbs or sets. Hills in front of it hide it.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID, value = Dist.CLIENT)
public final class Rainbows {

	/** Chance that rain clearing brings a rainbow, when the sun is right. */
	static final double CHANCE = 0.5;
	/** How long it rained, ticks, for its clearing to count. */
	static final int RAINED = 400;
	/** How long a rainbow lasts, ticks. */
	static final int LASTS = 2400;
	/** How far off it is drawn, blocks: hills nearer than this hide it. */
	static final float DISTANCE = 160;
	/** The arc's angle from the point opposite the sun, degrees, outer (red) edge to inner (violet). */
	static final float OUTER = 42.5f, INNER = 39.5f;
	static final int SEGMENTS = 96;
	/** Bands, outer to inner. */
	private static final float[][] COLOURS = { { 1, 0.1f, 0.1f }, { 1, 0.5f, 0.05f }, { 1, 0.95f, 0.15f },
			{ 0.2f, 0.9f, 0.25f }, { 0.15f, 0.45f, 1 }, { 0.35f, 0.2f, 0.85f }, { 0.55f, 0.2f, 0.75f } };

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
		ClientLevel level = Minecraft.getInstance().level;
		if(level == null)
			return;
		float angle = level.getSunAngle(event.getPartialTick().getGameTimeDeltaPartialTick(false));
		// Opposite the sun, which runs east to west over the x axis.
		Vec3 anti = new Vec3(Mth.sin(angle), -Mth.cos(angle), 0);
		Vec3 u = new Vec3(0, 0, 1);
		Vec3 v = anti.cross(u).normalize();
		float strength = alpha * 0.35f;

		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		int bands = COLOURS.length;
		for(int band = 0; band < bands; band++) {
			float outer = Mth.lerp(band / (float)bands, OUTER, INNER), inner = Mth.lerp((band + 1) / (float)bands, OUTER, INNER);
			// Soft at its edges.
			float a = strength * (band == 0 || band == bands - 1 ? 0.55f : 1);
			float[] c = COLOURS[band];
			for(int s = 0; s < SEGMENTS; s++) {
				double p0 = Math.PI * 2 * s / SEGMENTS, p1 = Math.PI * 2 * (s + 1) / SEGMENTS;
				Vec3 o0 = point(anti, u, v, outer, p0), o1 = point(anti, u, v, outer, p1);
				Vec3 i0 = point(anti, u, v, inner, p0), i1 = point(anti, u, v, inner, p1);
				if(o0.y < 0 && o1.y < 0)
					continue; // Below the horizon.
				vertex(buffer, o0, c, a);
				vertex(buffer, o1, c, a);
				vertex(buffer, i1, c, a);
				vertex(buffer, i0, c, a);
			}
		}
		MeshData mesh = buffer.build();
		if(mesh != null)
			BufferUploader.drawWithShader(mesh);
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
	}

	/** The direction {@code degrees} from {@code anti}, {@code phase} round it, out at the rainbow's distance. */
	private static Vec3 point(Vec3 anti, Vec3 u, Vec3 v, float degrees, double phase) {
		double t = Math.toRadians(degrees);
		return anti.scale(Math.cos(t)).add(u.scale(Math.cos(phase) * Math.sin(t))).add(v.scale(Math.sin(phase) * Math.sin(t)))
				.scale(DISTANCE);
	}

	private static void vertex(BufferBuilder buffer, Vec3 at, float[] c, float a) {
		buffer.addVertex((float)at.x, (float)at.y, (float)at.z).setColor(c[0], c[1], c[2], a);
	}
}
