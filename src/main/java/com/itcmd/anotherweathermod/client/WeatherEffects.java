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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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
	/** A tornadic storm hides less, so its tornado can be seen. */
	static final float TORNADO_MURK = 160;
	static final float NIGHT_MURK = 0.55f;
	/** A hurricane's fog distance by category, 0 to 5. */
	static final float[] HURRICANE_MURK = { 85, 75, 62, 44, 33, 25 }; // 2026-11-13: 1 and 2 steady rain, not a whiteout.
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
	/** Each tornado's roar, by tornado. */
	private static final Map<Long, TornadoSound> roars = new HashMap<>();
	/** How far under a severe storm's dark sky the player is, 0..1, eased. */
	private static float stormSky;
	/** Whether the storm overhead has closed the sky ({@link #darkensSky(StormKind, float, boolean)}). */
	private static boolean skyClosed;
	/** The wind the server sent, eased so its gusts (sent four times a second) swell rather than jump. */
	private static float windX;
	private static float windZ;
	/** A tornado's pull beyond its wind, eased the same way. */
	private static float pullX;
	private static float pullZ;
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
		pullX += (weather.pullX() - pullX) * 0.25f;
		pullZ += (weather.pullZ() - pullZ) * 0.25f;
		// Rain in a severe storm cuts the view, a hurricane's most, and night more still.
		StormKind kind = weather.stormKind();
		boolean hurricane = kind == StormKind.HURRICANE;
		boolean severe = hurricane || kind != null && kind.scale() >= 3;
		boolean hailing = weather.precipitation() == Precipitation.HAIL;
		float murkTarget = fog && severe && (hailing || weather.precipitation() == Precipitation.RAIN)
				? Math.min(1, weather.rain() * 1.2f) * (0.5f + 0.5f * outdoors) * (kind == StormKind.TORNADO ? 0.5f : 1) : 0;
		// In a hurricane's eye the rain and wind let up, but the sky stays overcast and the air murky (2026-11-18).
		boolean eye = weather.has(Hazard.HURRICANE_EYE);
		if(eye && fog)
			murkTarget = Math.max(murkTarget, EYE_MURK * (0.5f + 0.5f * outdoors));
		murk = approach(murk, murkTarget);
		skyClosed = eye || darkensSky(kind, weather.rain(), skyClosed);
		stormSky = approach(stormSky, skyClosed ? 1 : 0);
		float night = Mth.clamp((1 - level.getSkyDarken(1)) / 0.8f, 0, 1);
		float distanceTarget = (hurricane ? HURRICANE_MURK[Mth.clamp(weather.category(), 0, 5)]
				: kind == StormKind.TORNADO ? TORNADO_MURK : hailing ? HAIL_FOG : SEVERE_FOG) * Mth.lerp(night, 1, NIGHT_MURK);
		murkDistance += (distanceTarget - murkDistance) * 0.02f;
		fogBank = approach(fogBank, fog ? weather.fog() : 0);
		heat = approach(heat, fog && weather.has(Hazard.HEAT_WAVE) ? outdoors : 0);
		cold = approach(cold, fog && weather.has(Hazard.COLD_SNAP) ? outdoors : 0);
		// Mist at dawn on rainy days (strongest at sunrise, gone by mid-morning) and while wet ground dries after rain.
		float dawn = Dawn.mist(level.getDayTime(), weather.dayType());
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
				|| player.isPassenger() || player.isInWater() || weather.sheltered())
			return;
		double speed = Math.hypot(windX, windZ);
		if(weather.has(Hazard.HIGH_WIND) && calmSpeed > 1 && !WindPush.sheltered(player.level(), player, windX, windZ)) {
			// High wind: each gust shoves you about half a block, then lets you go.
			double gusting = Mth.clamp((speed / calmSpeed - 1) / 0.5, 0, 1);
			WindPush.push(player, windX, windZ, WindPush.GUST_SHOVE * gusting);
		}
		// A tornado draws you in from further out than its wind alone.
		double pushX = windX + pullX, pushZ = windZ + pullZ;
		double pushSpeed = Math.hypot(pushX, pushZ);
		if(pushSpeed < WindPush.FROM || WindPush.sheltered(player.level(), player, pushX, pushZ))
			return;
		double force = WindPush.force(pushSpeed);
		if(player.isFallFlying())
			force *= 0.6 / 1.4; // An elytra slices through it better than a body caught in the air.
		WindPush.push(player, pushX, pushZ, force);
		// Near a tornado you are lifted off your feet (a full set of gold keeps you down).
		if(weather.lift() > 0.05f) {
			double lift = com.itcmd.anotherweathermod.world.Tornadoes.LIFT * weather.lift()
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
		tornadoRoar(mc, camera);
		if(!AwmClientConfig.TORNADO_FUNNELS.get() || mc.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL)
			return;
		float cloudY = level.effects().getCloudHeight();
		for(TornadoTracker.Funnel funnel : TornadoTracker.funnels(1)) {
			if(funnel.strength() <= 0.02f || Math.hypot(funnel.x() - camera.x, funnel.z() - camera.z) > 220)
				continue;
			double top = Math.max(20, (Float.isNaN(cloudY) ? 192 : cloudY) - funnel.ground());
			// Many small puffs, in layers round the funnel, up the part of it that has come down.
			for(int i = scaled(mc, 32 * funnel.strength() * funnel.reach()); i > 0; i--) {
				double h = Math.pow(level.random.nextDouble(), 1.6) * 0.85;
				mc.particleEngine.add(new TornadoCloudParticle(level, funnel, top,
						StormRenderer.funnelHeight(h, top, funnel.reach())));
			}
			if(StormRenderer.touchdown(funnel) <= 0)
				continue;
			for(int i = scaled(mc, 10 * funnel.strength() * StormRenderer.touchdown(funnel)); i > 0; i--) {
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

	/** The roar of each tornado in sight, heard from far off, loudest close by, from where the funnel is. */
	private static void tornadoRoar(Minecraft mc, Vec3 camera) {
		Set<Long> heard = new HashSet<>();
		if(AwmClientConfig.WIND_SOUND.get())
			for(TornadoTracker.Funnel funnel : TornadoTracker.funnels(1)) {
				double d = Math.hypot(funnel.x() - camera.x, funnel.z() - camera.z);
				float loudness = (float)(funnel.strength() * Math.min(1, funnel.reach() * 1.5)
						* Math.pow(Mth.clamp(1.15 - d / TornadoSound.HEARD, 0, 1), 1.2));
				if(loudness <= 0.01f)
					continue;
				heard.add(funnel.id());
				TornadoSound sound = roars.get(funnel.id());
				if(sound == null || sound.isStopped()) {
					sound = new TornadoSound(funnel.x(), funnel.ground() + 12, funnel.z());
					roars.put(funnel.id(), sound);
					mc.getSoundManager().play(sound);
				}
				sound.follow(funnel.x(), funnel.ground() + 12, funnel.z(), Math.min(1, loudness * 1.3f));
			}
		for(Iterator<Map.Entry<Long, TornadoSound>> it = roars.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, TornadoSound> e = it.next();
			if(!heard.contains(e.getKey())) {
				e.getValue().fadeOut();
				it.remove();
			}
		}
	}

	/**
	 * Whether a storm of {@code kind} raining {@code rain} closes the sky over the player: any hurricane, and severe
	 * thunderstorms, hailstorms and tornadic storms. Under it the sky, the fog and the sunset glow stay storm grey at
	 * dawn and dusk too (2026-11-13).
	 */
	public static boolean darkensSky(StormKind kind, float rain) {
		return kind != null && (kind == StormKind.HURRICANE || kind.scale() >= 3) && rain > 0.3f;
	}

	/** Rain under which a closed sky opens again: lower than it takes to close it. A hurricane's stays closed while it rains at all. */
	static final float OPEN_SEVERE = 0.15f, OPEN_HURRICANE = 0.001f;
	/** How murky a hurricane's eye is, 0 to 1: less than its rain, but still a storm's air. */
	static final float EYE_MURK = 0.45f;

	/**
	 * As {@link #darkensSky(StormKind, float)}, but once closed ({@code closed}) the sky stays closed until the rain
	 * falls well below that (2026-11-18): a tropical storm's outer bands swing its rain either side of 0.3, and the sky
	 * flipped from storm grey to clear and back as they passed. A hurricane keeps it closed all through its bands.
	 */
	public static boolean darkensSky(StormKind kind, float rain, boolean closed) {
		if(!closed)
			return darkensSky(kind, rain);
		if(kind == null || kind != StormKind.HURRICANE && kind.scale() < 3)
			return false;
		return rain > (kind == StormKind.HURRICANE ? OPEN_HURRICANE : OPEN_SEVERE);
	}

	/** How closed in the sky is by the storm overhead, 0 to 1 (eased): {@link #darkensSky}. */
	public static float stormSky() {
		return stormSky;
	}

	/** How much of vanilla's clouds to show: under a severe storm's or hurricane's deck, they would show white. */
	public static float vanillaCloudAlpha() {
		return AwmClientConfig.STORM_CLOUDS.get() ? 1 - stormSky : 1;
	}

	/** How far off the mod's clouds fade out, drawn in to the fog in thick fog and storm murk. */
	public static float cloudFadeEnd(float range) {
		float thick = Math.max(fogBank, murk);
		return Mth.lerp(thick, range, Math.max(48, Math.min(range, murkDistance * 1.3f)));
	}

	/** How far toward night the sky is dimmed under a storm: the storm darkness setting, while under one. */
	public static float stormDarkness() {
		return stormSky * AwmClientConfig.STORM_DARKNESS.get().floatValue();
	}

	/** Thunder for the game to read under a storm (the storms-as-thunder setting), or 0. */
	public static float thunderBoost() {
		return AwmClientConfig.STORMS_AS_THUNDER.get() ? stormSky : 0;
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
			// Under a hurricane or severe storm all of it, so dawn and dusk do not show past the storm (2026-11-13).
			float hide = Math.max(Math.max(Math.max(fogBank, CloudRenderer.inside()), Math.max(dust, blizzard)), Math.max(murk * 0.8f, stormSky));
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
				&& cold < 0.01f && CloudRenderer.inside() < 0.01f)
			return;
		float far = fogDistance(event.getFarPlaneDistance());
		event.setFarPlaneDistance(far);
		event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.1f));
		event.setCanceled(true); // NeoForge applies the new distances only when the event is cancelled.
	}

	/** How far you see inside a cloud, and inside a storm's, blocks (2026-11-18). */
	static final float IN_CLOUD = 14, IN_STORM_CLOUD = 8;

	/** How thick the weather's fog is now (fog banks, storm murk, sandstorms, blizzards), 0 to 1. */
	public static float fogThickness() {
		return Math.max(Math.max(fogBank, murk), Math.max(dust, blizzard));
	}

	/** How far off the weather's fog closes in to, from {@code far} without it. */
	public static float fogDistance(float far) {
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
		float cloud = CloudRenderer.inside();
		if(cloud > 0.01f) // Inside a cloud (2026-11-18): a few blocks, less in a storm's.
			far = Mth.lerp(cloud, far, Math.min(far, CloudRenderer.insideStorm() ? IN_STORM_CLOUD : IN_CLOUD));
		return far;
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
		if(murk > 0.01f || stormSky > 0.01f) {
			// Toward a dark rain grey, scaled by the sky's own brightness so the night stays dark; under the storm's deck
			// nearly all the way, so a sunset's orange does not tint the fog (2026-11-13).
			float grey = 0.42f * Mth.clamp(Minecraft.getInstance().level == null ? 1
					: Minecraft.getInstance().level.getSkyDarken(1), 0.2f, 1);
			tint(event, Math.max(murk * 0.6f, stormSky * 0.85f), grey, grey * 1.02f, grey * 1.08f);
		}
		if(dust > 0.01f)
			tint(event, dust * 0.85f, 0.80f, 0.66f, 0.45f);
		if(blizzard > 0.01f)
			tint(event, blizzard * 0.8f, 0.86f, 0.89f, 0.93f);
		float cloud = CloudRenderer.inside();
		if(cloud > 0.01f) { // Inside a cloud: its own white, or a storm's dark grey, as lit as the sky.
			float shade = (CloudRenderer.insideStorm() ? 0.36f : 0.9f) * sky;
			tint(event, cloud * 0.9f, shade, shade * 1.01f, shade * 1.04f);
		}
	}

	private static void tint(ViewportEvent.ComputeFogColor event, float amount, float r, float g, float b) {
		event.setRed(Mth.lerp(amount, event.getRed(), r));
		event.setGreen(Mth.lerp(amount, event.getGreen(), g));
		event.setBlue(Mth.lerp(amount, event.getBlue(), b));
	}

	/** A new dimension (or a respawn): the old one's weather goes; a managed dimension sends its own within a tick or two. */
	@SubscribeEvent
	public static void onClone(ClientPlayerNetworkEvent.Clone event) {
		if(event.getOldPlayer().level() != event.getNewPlayer().level() || event.getOldPlayer().level().dimension()
				!= event.getNewPlayer().level().dimension()) {
			ClientWeatherState.forgetWeather();
			TornadoTracker.reset();
		}
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		ClientWeatherState.reset();
		TornadoTracker.reset();
		roars.values().forEach(TornadoSound::fadeOut);
		CloudRenderer.clear();
		roars.clear();
		dust = blizzard = hail = murk = stormSky = fogBank = mist = heat = cold = windX = windZ = pullX = pullZ = calmSpeed = 0;
		if(windSound != null)
			windSound.setStrength(0);
	}

	private static float approach(float from, float to) {
		if(Math.abs(to - from) <= EASE)
			return to;
		return from < to ? from + EASE : from - EASE;
	}
}
