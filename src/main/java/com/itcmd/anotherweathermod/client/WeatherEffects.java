package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.api.Hazard;
import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.network.ClientWeatherState;
import com.itcmd.anotherweathermod.network.LocalWeatherPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * What the client half adds on top of vanilla rain: hail, and thick fog in sandstorms and blizzards. Everything eases
 * in and out, and fades indoors.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID, value = Dist.CLIENT)
public final class WeatherEffects {

	/** Fog distance, blocks, at a full sandstorm and a full blizzard. */
	static final float SANDSTORM_FOG = 18;
	static final float BLIZZARD_FOG = 26;
	static final float EASE = 0.02f;
	static final int HAIL_RADIUS = 10;

	private static float dust;
	private static float blizzard;
	private static float hail;

	private WeatherEffects() {
	}

	@SubscribeEvent
	public static void onTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if(level == null || mc.isPaused())
			return;
		LocalWeatherPayload weather = ClientWeatherState.get();
		Camera camera = mc.gameRenderer.getMainCamera();
		float outdoors = level.canSeeSky(BlockPos.containing(camera.getPosition())) ? 1 : 0.25f;

		float dustTarget = weather.has(Hazard.SANDSTORM) ? 1 : weather.precipitation() == Precipitation.DUST ? 0.35f : 0;
		float blizzardTarget = weather.has(Hazard.BLIZZARD) ? 1 : 0;
		float hailTarget = weather.has(Hazard.HAIL) ? Math.min(1, weather.rain()) : 0;
		dust = approach(dust, dustTarget * outdoors);
		blizzard = approach(blizzard, blizzardTarget * outdoors);
		hail = approach(hail, hailTarget);

		if(hail > 0.01f && mc.options.particles().get() != net.minecraft.client.ParticleStatus.MINIMAL)
			spawnHail(mc, level, camera.getPosition(), weather);
	}

	private static void spawnHail(Minecraft mc, ClientLevel level, Vec3 camera, LocalWeatherPayload weather) {
		int count = Math.round(hail * (mc.options.particles().get() == net.minecraft.client.ParticleStatus.ALL ? 14 : 6));
		double driftX = weather.windX() / 20 * 0.25;
		double driftZ = weather.windZ() / 20 * 0.25;
		for(int i = 0; i < count; i++) {
			double x = camera.x + (level.random.nextDouble() * 2 - 1) * HAIL_RADIUS;
			double z = camera.z + (level.random.nextDouble() * 2 - 1) * HAIL_RADIUS;
			double y = camera.y + 6 + level.random.nextDouble() * 6;
			if(level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) > y)
				continue; // Under a roof.
			mc.particleEngine.add(new HailParticle(level, x, y, z, driftX, driftZ));
		}
	}

	@SubscribeEvent
	public static void onRenderFog(ViewportEvent.RenderFog event) {
		if(event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || event.getType() != FogType.NONE)
			return;
		if(dust < 0.01f && blizzard < 0.01f)
			return;
		float far = event.getFarPlaneDistance();
		if(dust > 0.01f)
			far = Mth.lerp(dust, far, Math.min(far, SANDSTORM_FOG));
		if(blizzard > 0.01f)
			far = Mth.lerp(blizzard, far, Math.min(far, BLIZZARD_FOG));
		event.setFarPlaneDistance(far);
		event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.1f));
		event.setCanceled(true); // NeoForge applies the new distances only when the event is cancelled.
	}

	@SubscribeEvent
	public static void onFogColor(ViewportEvent.ComputeFogColor event) {
		if(dust > 0.01f)
			tint(event, dust * 0.85f, 0.80f, 0.66f, 0.45f);
		if(blizzard > 0.01f)
			tint(event, blizzard * 0.8f, 0.86f, 0.89f, 0.93f);
	}

	private static void tint(ViewportEvent.ComputeFogColor event, float amount, float r, float g, float b) {
		event.setRed(Mth.lerp(amount, event.getRed(), r));
		event.setGreen(Mth.lerp(amount, event.getGreen(), g));
		event.setBlue(Mth.lerp(amount, event.getBlue(), b));
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		ClientWeatherState.reset();
		dust = blizzard = hail = 0;
	}

	private static float approach(float from, float to) {
		if(Math.abs(to - from) <= EASE)
			return to;
		return from < to ? from + EASE : from - EASE;
	}
}
