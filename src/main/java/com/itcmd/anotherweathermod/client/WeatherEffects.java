package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmClientConfig;
import com.itcmd.anotherweathermod.api.Hazard;
import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.network.ClientWeatherState;
import com.itcmd.anotherweathermod.network.LocalWeatherPayload;
import com.itcmd.anotherweathermod.world.WindPush;
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
	/**
	 * Fog distance, blocks, in the heart of a severe thunderstorm (or supercell) and of a hurricane, by day; at night
	 * it closes in to {@link #NIGHT_MURK} of that.
	 */
	static final float SEVERE_FOG = 90;
	static final float HURRICANE_FOG = 40;
	/** Hail closes the view in more than rain does. */
	static final float HAIL_FOG = 65;
	static final float NIGHT_MURK = 0.55f;
	/** A hurricane's fog distance by category, 0 to 5. */
	static final float[] HURRICANE_MURK = { 80, 60, 50, 40, 32, 24 };
	/** Fog distance in a dense fog bank, by day. */
	static final float FOG_BANK = 14;
	/** Fog distance in the morning mist and after rain. */
	static final float MIST = 70;
	static final float EASE = 0.02f;
	static final int HAIL_RADIUS = 10;
	static final int HAIL_FAR = 28;

	/** Wind speed, metres per second, from which the wind is heard and seen. */
	static final float WIND_FELT = 8;
	/** Wind speed for the full roar. */
	static final float WIND_GALE = 35;
	/** Wind speed from which leaves blow about. */
	static final float WIND_DEBRIS = 22;

	private static float dust;
	private static float blizzard;
	private static float hail;
	/** How much storm murk there is, 0..1, and the fog distance it closes in to. */
	private static float murk;
	private static float murkDistance = SEVERE_FOG;
	/** Dense fog, mist, heat haze and cold, each 0..1, eased. */
	private static float fogBank;
	private static float mist;
	private static float heat;
	private static float cold;
	private static WindSound windSound;
	/** The wind the server sent, eased so its gusts (sent four times a second) swell rather than jump. */
	private static float windX;
	private static float windZ;
	/** The wind's slow average, metres per second: a gust is the wind rising above it. */
	private static float calmSpeed;

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
		windX += (weather.windX() - windX) * 0.25f;
		windZ += (weather.windZ() - windZ) * 0.25f;
		// Rain in a severe storm cuts the view, a hurricane's most, and night more still.
		StormKind kind = weather.stormKind();
		boolean hurricane = kind == StormKind.HURRICANE;
		boolean severe = hurricane || kind != null && kind.scale() >= 3;
		boolean hailing = weather.precipitation() == Precipitation.HAIL;
		float murkTarget = fog && severe && (hailing || weather.precipitation() == Precipitation.RAIN)
				? Math.min(1, weather.rain() * 1.2f) * (0.5f + 0.5f * outdoors) : 0;
		murk = approach(murk, murkTarget);
		float night = Mth.clamp((1 - level.getSkyDarken(1)) / 0.8f, 0, 1);
		float distanceTarget = (hurricane ? HURRICANE_MURK[Mth.clamp(weather.category(), 0, 5)]
				: hailing ? HAIL_FOG : SEVERE_FOG) * Mth.lerp(night, 1, NIGHT_MURK);
		murkDistance += (distanceTarget - murkDistance) * 0.02f;
		fogBank = approach(fogBank, fog ? weather.fog() : 0);
		heat = approach(heat, fog && weather.has(Hazard.HEAT_WAVE) ? outdoors : 0);
		cold = approach(cold, fog && weather.has(Hazard.COLD_SNAP) ? outdoors : 0);
		// Mist at dawn (strongest at sunrise, gone by mid-morning) and while wet ground dries after rain.
		long day = level.getDayTime() % 24000;
		float dawn = day >= 22000 ? (day - 22000) / 2000f : day < 3000 ? 1 - day / 3000f : 0;
		float afterRain = weather.rain() < 0.2f ? Puddles.wetness() : 0;
		mist = approach(mist, AwmClientConfig.MIST.get() ? Math.max(dawn * 0.8f, afterRain) * 0.5f * outdoors : 0);
		dust = approach(dust, dustTarget * outdoors);
		blizzard = approach(blizzard, blizzardTarget * outdoors);
		hail = approach(hail, hailTarget);

		if(hail > 0.01f && AwmClientConfig.HAIL.get() && mc.options.particles().get() != net.minecraft.client.ParticleStatus.MINIMAL)
			spawnHail(mc, level, camera.getPosition(), weather);
		wind(mc, level, camera.getPosition(), weather, outdoors);
		push(mc, weather);
		tornadoDebris(mc, level, camera.getPosition());
	}

	/**
	 * Strong wind shoves the player downwind: a nudge in a severe thunderstorm's gusts, a struggle to walk into a
	 * hurricane's eyewall, and a long ride if you jump or glide in it. Out of the wind (under a roof or with a wall
	 * upwind) it does not reach you; sneaking halves it.
	 */
	private static void push(Minecraft mc, LocalWeatherPayload weather) {
		var player = mc.player;
		if(player == null || !AwmClientConfig.WIND_PUSH.get() || player.isSpectator() || player.getAbilities().flying
				|| player.isPassenger() || player.isInWater())
			return;
		double speed = Math.hypot(windX, windZ);
		if(speed < WindPush.FROM || WindPush.sheltered(player.level(), player, windX, windZ))
			return;
		double force = WindPush.force(speed);
		if(player.isFallFlying())
			force *= 0.6 / 1.4; // An elytra slices through it better than a body caught in the air.
		WindPush.push(player, windX, windZ, force);
		// In a tornado's core you are lifted off your feet (a full set of gold keeps you down).
		if(weather.tornado() > 0.05f) {
			double lift = com.itcmd.anotherweathermod.world.Tornadoes.LIFT * weather.tornado()
					* (1 - com.itcmd.anotherweathermod.world.WindPush.ballast(player) / 4.0);
			var v = player.getDeltaMovement();
			player.setDeltaMovement(v.x, Math.min(com.itcmd.anotherweathermod.world.Tornadoes.MAX_RISE, v.y + lift), v.z);
		}
	}

	/**
	 * Round each tornado in sight: dark puffs of cloud whirling up the funnel, and dirt and leaves flung round and up
	 * from its foot, which rides the ground.
	 */
	private static void tornadoDebris(Minecraft mc, ClientLevel level, Vec3 camera) {
		TornadoTracker.tick(level);
		if(!AwmClientConfig.TORNADO_FUNNELS.get() || mc.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL)
			return;
		float cloudY = level.effects().getCloudHeight();
		for(TornadoTracker.Funnel funnel : TornadoTracker.funnels(1)) {
			if(funnel.strength() <= 0.02f || Math.hypot(funnel.x() - camera.x, funnel.z() - camera.z) > 220)
				continue;
			double top = Math.max(20, (Float.isNaN(cloudY) ? 192 : cloudY) - funnel.ground());
			for(int i = scaled(mc, 6 * funnel.strength()); i > 0; i--)
				mc.particleEngine.add(new TornadoCloudParticle(level, funnel, top,
						Math.pow(level.random.nextDouble(), 1.8) * top * 0.7));
			for(int i = scaled(mc, 10 * funnel.strength()); i > 0; i--) {
				double a = level.random.nextDouble() * Math.PI * 2;
				double r = funnel.radius() * (0.4 + level.random.nextDouble());
				double x = funnel.x() + Math.cos(a) * r, z = funnel.z() + Math.sin(a) * r;
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
				// Round counter-clockwise and up.
				double spin = 0.9 + level.random.nextDouble() * 0.6;
				var state = level.random.nextInt(3) == 0 ? net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState()
						: net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
				WindParticle bit = WindParticle.whirl(level, x, ground + level.random.nextDouble() * 3, z,
						Math.sin(a) * spin, 0.15 + level.random.nextDouble() * 0.35, -Math.cos(a) * spin, state);
				bit.darken(0.5f);
				mc.particleEngine.add(bit);
			}
		}
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

	/** The wind here now, metres per second, eased. */
	static float windX() {
		return windX;
	}

	static float windZ() {
		return windZ;
	}

	/** A little flutter on top of the wind the server sends (which has the storms' real gusts in it): 0.8 to 1.2. */
	static float gust(long time) {
		return (float)(1 + 0.12 * Math.sin(time * 0.05) + 0.08 * Math.sin(time * 0.17 + 1.3));
	}

	private static void wind(Minecraft mc, ClientLevel level, Vec3 camera, LocalWeatherPayload weather, float outdoors) {
		double speed = Math.hypot(windX, windZ) * gust(level.getGameTime());
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
		double vx = windX / 20 * 0.8 * gust(level.getGameTime());
		double vz = windZ / 20 * 0.8 * gust(level.getGameTime());
		boolean sand = weather.precipitation() == Precipitation.DUST;
		// How much the wind is gusting now: 0 at its usual strength, 1 at half again as strong.
		float now = (float)Math.hypot(windX, windZ);
		calmSpeed += (now - calmSpeed) * 0.01f;
		float gusting = calmSpeed < 1 ? 0 : Mth.clamp((now / calmSpeed - 1) / 0.5f, 0, 1);
		if(AwmClientConfig.WIND_STREAKS.get() && gusting > 0.05f && !sand)
			gustWisps(mc, level, camera, vx * (1 + gusting * 0.5), vz * (1 + gusting * 0.5),
					scaled(mc, gusting * strength * 42));
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

	/** White wisps that blow past in a gust, brighter and faster than the usual streaks, low over the ground. */
	private static void gustWisps(Minecraft mc, ClientLevel level, Vec3 camera, double vx, double vz, int count) {
		for(int i = 0; i < count; i++) {
			double x = camera.x + (level.random.nextDouble() * 2 - 1) * 16 - vx * 18;
			double z = camera.z + (level.random.nextDouble() * 2 - 1) * 16 - vz * 18;
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
			double y = Math.max(ground, camera.y - 3) + level.random.nextDouble() * 4;
			mc.particleEngine.add(WindParticle.wisp(level, x, y, z, vx, vz));
		}
	}

	/** Hail near the camera, and more, smaller stones further out (to {@link #HAIL_FAR}) so it fills the view. */
	private static void spawnHail(Minecraft mc, ClientLevel level, Vec3 camera, LocalWeatherPayload weather) {
		boolean all = mc.options.particles().get() == net.minecraft.client.ParticleStatus.ALL;
		int near = scaled(mc, hail * (all ? 14 : 6));
		int far = scaled(mc, hail * (all ? 30 : 10));
		double driftX = windX / 20 * 0.25;
		double driftZ = windZ / 20 * 0.25;
		for(int i = 0; i < near + far; i++) {
			boolean distant = i >= near;
			double angle = level.random.nextDouble() * Math.PI * 2;
			double dist = distant ? HAIL_RADIUS + level.random.nextDouble() * (HAIL_FAR - HAIL_RADIUS)
					: Math.sqrt(level.random.nextDouble()) * HAIL_RADIUS;
			double x = camera.x + Math.cos(angle) * dist;
			double z = camera.z + Math.sin(angle) * dist;
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
			if(ground > camera.y + 4)
				continue; // Under a roof (or behind a hill).
			double y = Math.max(ground, camera.y) + 6 + level.random.nextDouble() * 6;
			mc.particleEngine.add(new HailParticle(level, x, y, z, driftX, driftZ, distant));
		}
	}

	@SubscribeEvent
	public static void onRenderFog(ViewportEvent.RenderFog event) {
		if(event.getType() != FogType.NONE)
			return;
		if(event.getMode() == FogRenderer.FogMode.FOG_SKY) {
			// Thick fog and storms hide the sky too: pull the sky's fog in so it turns the fog colour.
			float hide = Math.max(Math.max(fogBank, Math.max(dust, blizzard)), murk * 0.8f);
			if(hide < 0.01f)
				return;
			event.setFarPlaneDistance(Mth.lerp(hide, event.getFarPlaneDistance(), 0));
			event.setNearPlaneDistance(0);
			event.setCanceled(true);
			return;
		}
		if(event.getMode() != FogRenderer.FogMode.FOG_TERRAIN)
			return;
		if(dust < 0.01f && blizzard < 0.01f && murk < 0.01f && fogBank < 0.01f && mist < 0.01f && heat < 0.01f
				&& cold < 0.01f)
			return;
		float far = event.getFarPlaneDistance();
		float night = Minecraft.getInstance().level == null ? 0
				: Mth.clamp((1 - Minecraft.getInstance().level.getSkyDarken(1)) / 0.8f, 0, 1);
		if(mist > 0.01f)
			far = Mth.lerp(mist, far, Math.min(far, MIST));
		if(heat > 0.01f) // Heat haze.
			far = Mth.lerp(heat * 0.4f, far, Math.min(far, 96));
		if(cold > 0.01f)
			far = Mth.lerp(cold * 0.3f, far, Math.min(far, 80));
		if(fogBank > 0.01f)
			far = Mth.lerp(fogBank, far, Math.min(far, FOG_BANK * Mth.lerp(night, 1, 0.75f)));
		if(murk > 0.01f)
			far = Mth.lerp(murk, far, Math.min(far, murkDistance));
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
		float sky = Minecraft.getInstance().level == null ? 1 : Mth.clamp(Minecraft.getInstance().level.getSkyDarken(1), 0.2f, 1);
		if(mist > 0.01f)
			tint(event, mist * 0.5f, 0.82f * sky, 0.84f * sky, 0.86f * sky);
		if(fogBank > 0.01f)
			tint(event, fogBank * 0.85f, 0.74f * sky, 0.76f * sky, 0.78f * sky);
		if(heat > 0.01f)
			tint(event, heat * 0.3f, 1.0f, 0.84f, 0.62f);
		if(cold > 0.01f)
			tint(event, cold * 0.35f, 0.70f * sky, 0.80f * sky, 0.98f * sky);
		if(murk > 0.01f) {
			// Toward a dark rain grey, scaled by the sky's own brightness so the night stays dark.
			float grey = 0.42f * Mth.clamp(Minecraft.getInstance().level == null ? 1
					: Minecraft.getInstance().level.getSkyDarken(1), 0.2f, 1);
			tint(event, murk * 0.6f, grey, grey * 1.02f, grey * 1.08f);
		}
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
		TornadoTracker.reset();
		dust = blizzard = hail = murk = fogBank = mist = heat = cold = windX = windZ = calmSpeed = 0;
		if(windSound != null)
			windSound.setStrength(0);
	}

	private static float approach(float from, float to) {
		if(Math.abs(to - from) <= EASE)
			return to;
		return from < to ? from + EASE : from - EASE;
	}
}
