package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmClientConfig;
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
 * What the client half adds on top of vanilla rain: hail, thick fog in sandstorms and blizzards, and gusty wind you
 * hear and see (streaks of air, blown sand, leaves torn off in a gale). Everything eases in and out, and fades indoors.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID, value = Dist.CLIENT)
public final class WeatherEffects {

	/** Fog distance, blocks, at a full sandstorm and a full blizzard. */
	static final float SANDSTORM_FOG = 18;
	static final float BLIZZARD_FOG = 26;
	static final float EASE = 0.02f;
	static final int HAIL_RADIUS = 10;

	/** Wind speed, metres per second, from which the wind is heard and seen. */
	static final float WIND_FELT = 8;
	/** Wind speed for the full roar. */
	static final float WIND_GALE = 35;
	/** Wind speed from which leaves blow about. */
	static final float WIND_DEBRIS = 22;

	private static float dust;
	private static float blizzard;
	private static float hail;
	/** A sudden gust on top of the slow ones: jumps up, dies away. */
	private static float burst;
	private static WindSound windSound;

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

		boolean fog = AwmClientConfig.STORM_FOG.get();
		float dustTarget = !fog ? 0 : weather.has(Hazard.SANDSTORM) ? 1 : weather.precipitation() == Precipitation.DUST ? 0.35f : 0;
		float blizzardTarget = fog && weather.has(Hazard.BLIZZARD) ? 1 : 0;
		float hailTarget = weather.has(Hazard.HAIL) ? Math.min(1, weather.rain()) : 0;
		dust = approach(dust, dustTarget * outdoors);
		blizzard = approach(blizzard, blizzardTarget * outdoors);
		hail = approach(hail, hailTarget);

		if(hail > 0.01f && AwmClientConfig.HAIL.get() && mc.options.particles().get() != net.minecraft.client.ParticleStatus.MINIMAL)
			spawnHail(mc, level, camera.getPosition(), weather);
		wind(mc, level, camera.getPosition(), weather, outdoors);
	}

	/**
	 * {@code count} particles scaled by the quality setting, rounded at random so small counts still come out right
	 * on average.
	 */
	static int scaled(Minecraft mc, float count) {
		float n = count * AwmClientConfig.QUALITY.get().particles();
		int whole = (int)n;
		return whole + (mc.level != null && mc.level.random.nextFloat() < n - whole ? 1 : 0);
	}

	/** How gusty it is now: about 1, rising to 1.6 in a gust. */
	static float gust(long time) {
		float slow = (float)(0.18 * Math.sin(time * 0.05) + 0.12 * Math.sin(time * 0.17 + 1.3));
		return Math.max(0.5f, 1 + slow + burst * 0.5f);
	}

	private static void wind(Minecraft mc, ClientLevel level, Vec3 camera, LocalWeatherPayload weather, float outdoors) {
		if(level.random.nextInt(200) == 0)
			burst = 1;
		burst *= 0.95f;
		double speed = Math.hypot(weather.windX(), weather.windZ()) * gust(level.getGameTime());
		float strength = (float)Mth.clamp((speed - WIND_FELT) / (WIND_GALE - WIND_FELT), 0, 1) * (0.4f + 0.6f * outdoors);

		float sound = AwmClientConfig.WIND_SOUND.get() ? strength : 0; // Off: a playing sound fades out.
		if(sound > 0 && (windSound == null || windSound.isStopped())) {
			windSound = new WindSound();
			mc.getSoundManager().play(windSound);
		}
		if(windSound != null)
			windSound.setStrength(sound);

		if(strength <= 0 || outdoors < 1 || mc.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL)
			return;
		double vx = weather.windX() / 20 * 0.8 * gust(level.getGameTime());
		double vz = weather.windZ() / 20 * 0.8 * gust(level.getGameTime());
		boolean sand = weather.precipitation() == Precipitation.DUST;
		int count = AwmClientConfig.WIND_STREAKS.get() ? scaled(mc, strength * (sand ? 10 : 4)) : 0;
		for(int i = 0; i < count; i++) {
			// Upwind of the camera, so they blow past it.
			double x = camera.x + (level.random.nextDouble() * 2 - 1) * 14 - vx * 15;
			double z = camera.z + (level.random.nextDouble() * 2 - 1) * 14 - vz * 15;
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
			double y = Math.max(ground, camera.y - 3) + level.random.nextDouble() * 5;
			if(sand)
				mc.particleEngine.add(WindParticle.streak(level, x, y, z, vx, vz, 0.86f, 0.74f, 0.52f, 0.5f));
			else
				mc.particleEngine.add(WindParticle.streak(level, x, y, z, vx, vz, 1, 1, 1, 0.18f));
		}
		for(int i = AwmClientConfig.WIND_DEBRIS.get() && speed > WIND_DEBRIS ? scaled(mc, strength) : 0; i > 0; i--) {
			double x = camera.x + (level.random.nextDouble() * 2 - 1) * 12 - vx * 12;
			double z = camera.z + (level.random.nextDouble() * 2 - 1) * 12 - vz * 12;
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, 0, z));
			net.minecraft.world.level.block.state.BlockState leaves = net.minecraft.world.level.block.Blocks.OAK_LEAVES
					.defaultBlockState();
			mc.particleEngine.add(WindParticle.debris(level, x, top.getY() + 1 + level.random.nextDouble() * 4, z, vx,
					vz, leaves));
		}
	}

	private static void spawnHail(Minecraft mc, ClientLevel level, Vec3 camera, LocalWeatherPayload weather) {
		int count = scaled(mc, hail * (mc.options.particles().get() == net.minecraft.client.ParticleStatus.ALL ? 14 : 6));
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
		dust = blizzard = hail = burst = 0;
		if(windSound != null)
			windSound.setStrength(0);
	}

	private static float approach(float from, float to) {
		if(Math.abs(to - from) <= EASE)
			return to;
		return from < to ? from + EASE : from - EASE;
	}
}
