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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The sky darkening toward a storm on its way. Each storm near the player but not over them weighs on the sky in its
 * direction: more the stronger and nearer it is, and more if it is heading this way. Looking toward it, the fog, the
 * sky and the clouds dim and turn slate grey (see {@link #toward}); looking away the sky stays clear. A dark band low
 * on the horizon on the storm's side shows it whichever way you look (not with a shader pack, which draws its own
 * sky). Under the storm itself the storm darkness and murk in {@link WeatherEffects} take over.
 *
 * <p>The idea comes from Localized Weather's storm proximity darkening (MIT, see THIRD_PARTY.md).
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID, value = Dist.CLIENT)
public final class StormHorizon {

	/** A storm's edge this many blocks off no longer darkens the sky. */
	static final double REACH = 1400;
	/** Segments round the horizon band. */
	static final int SEGMENTS = 48;
	/** The band reaches from just under the horizon to this high, degrees. */
	static final float BAND_TOP = 26;
	/** Storm slate: the colour the sky and fog turn toward. */
	static final float[] SLATE = { 0.27f, 0.29f, 0.34f };

	/** A storm's pull on the sky: the way to it (unit vector) and how much, 0 to 1. */
	private record Pull(double x, double z, float weight) {
	}

	private static List<Pull> pulls = List.of();

	private StormHorizon() {
	}

	@SubscribeEvent
	public static void onTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		Player player = mc.player;
		if(level == null || player == null || !AwmClientConfig.STORM_HORIZON.get()) {
			pulls = List.of();
			return;
		}
		NearbyStormsPayload payload = ClientWeatherState.storms();
		double age = level.getGameTime() - payload.gameTime();
		List<Pull> next = new ArrayList<>();
		for(NearbyStormsPayload.Storm storm : payload.storms()) {
			if(!storm.kind().bringsRain())
				continue;
			double dx = storm.x() + storm.velocityX() * age - player.getX();
			double dz = storm.z() + storm.velocityZ() * age - player.getZ();
			double distance = Math.hypot(dx, dz);
			double edge = distance - storm.radius();
			if(edge <= 0 || edge >= REACH)
				continue;
			double ux = dx / distance, uz = dz / distance;
			// Heading this way: its velocity points back along the way to it.
			double speed = Math.hypot(storm.velocityX(), storm.velocityZ());
			double coming = speed < 1e-6 ? 0 : Mth.clamp(-(storm.velocityX() * ux + storm.velocityZ() * uz) / speed, 0, 1);
			double near = 1 - smoothstep(edge / REACH);
			float weight = (float)(storm.intensity() * darkness(storm.kind()) * near * (0.65 + 0.35 * coming));
			if(weight > 0.02f)
				next.add(new Pull(ux, uz, weight));
		}
		pulls = next;
	}

	/** How dark a storm of {@code kind} looks from afar, 0 to 1. */
	private static float darkness(StormKind kind) {
		if(kind == StormKind.HURRICANE)
			return 1;
		return switch(kind.scale()) {
			case 0 -> 0.35f;
			case 1 -> 0.6f;
			case 2 -> 0.72f;
			default -> 0.9f;
		};
	}

	/** Storm slate at {@code brightness} (1 for full daylight). */
	public static Vec3 slate(double brightness) {
		return new Vec3(SLATE[0] * brightness, SLATE[1] * brightness, SLATE[2] * brightness);
	}

	/** How darkened the sky is looking along ({@code x}, {@code z}), 0 to 1 (need not be a unit vector). */
	public static float toward(double x, double z) {
		double length = Math.hypot(x, z);
		float dark = 0;
		for(Pull pull : pulls) {
			// A broad lobe toward the storm, and a touch all round once it is close and big.
			double dot = length < 1e-6 ? 0 : Math.max(0, (pull.x() * x + pull.z() * z) / length);
			dark = Math.max(dark, pull.weight() * (float)(0.15 + 0.85 * dot * dot));
		}
		return Math.min(1, dark);
	}

	/** {@link #toward} the way the camera looks now. */
	public static float lookingToward() {
		if(pulls.isEmpty())
			return 0;
		Vector3f look = Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
		return toward(look.x(), look.z());
	}

	@SubscribeEvent
	public static void onFogColor(ViewportEvent.ComputeFogColor event) {
		if(pulls.isEmpty())
			return;
		Vector3f look = event.getCamera().getLookVector();
		float amount = toward(look.x(), look.z()) * 0.55f;
		ClientLevel level = Minecraft.getInstance().level;
		float sky = level == null ? 1 : Mth.clamp(level.getSkyDarken((float)event.getPartialTick()), 0.2f, 1);
		event.setRed(Mth.lerp(amount, event.getRed(), SLATE[0] * sky));
		event.setGreen(Mth.lerp(amount, event.getGreen(), SLATE[1] * sky));
		event.setBlue(Mth.lerp(amount, event.getBlue(), SLATE[2] * sky));
	}

	/** The dark band low on the horizon toward each storm. */
	@SubscribeEvent
	public static void onRenderStage(RenderLevelStageEvent event) {
		if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || pulls.isEmpty() || ShaderPacks.inUse())
			return;
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if(level == null)
			return;
		float sky = Mth.clamp(level.getSkyDarken(event.getPartialTick().getGameTimeDeltaPartialTick(false)), 0.2f, 1);
		// Behind every block in view, inside the far plane.
		float distance = mc.options.getEffectiveRenderDistance() * 16 * 2.5f;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		float bottom = -3 * Mth.DEG_TO_RAD, top = BAND_TOP * Mth.DEG_TO_RAD;
		float r = SLATE[0] * sky, g = SLATE[1] * sky, b = SLATE[2] * sky;
		for(int s = 0; s < SEGMENTS; s++) {
			float a0 = Mth.TWO_PI * s / SEGMENTS, a1 = Mth.TWO_PI * (s + 1) / SEGMENTS;
			float d0 = band(a0), d1 = band(a1);
			if(d0 < 0.01f && d1 < 0.01f)
				continue;
			vertex(buffer, a0, bottom, distance, r, g, b, d0);
			vertex(buffer, a1, bottom, distance, r, g, b, d1);
			vertex(buffer, a1, top, distance, r, g, b, 0);
			vertex(buffer, a0, top, distance, r, g, b, 0);
		}
		MeshData mesh = buffer.build();
		if(mesh != null)
			BufferUploader.drawWithShader(mesh);
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
	}

	/** The band's darkness at compass angle {@code angle} (radians, 0 along +x), without the all-round part. */
	private static float band(float angle) {
		float dark = 0;
		double x = Mth.cos(angle), z = Mth.sin(angle);
		for(Pull pull : pulls) {
			double dot = Math.max(0, pull.x() * x + pull.z() * z);
			dark = Math.max(dark, pull.weight() * (float)(dot * dot * dot));
		}
		return Math.min(0.8f, dark * 0.75f);
	}

	private static void vertex(BufferBuilder buffer, float angle, float elevation, float distance, float r, float g,
			float b, float alpha) {
		float flat = Mth.cos(elevation) * distance;
		buffer.addVertex(Mth.cos(angle) * flat, Mth.sin(elevation) * distance, Mth.sin(angle) * flat).setColor(r, g, b, alpha);
	}

	private static double smoothstep(double t) {
		t = Mth.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		pulls = List.of();
	}
}
