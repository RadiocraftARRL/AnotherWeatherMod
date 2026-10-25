package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmClientConfig;
import com.itcmd.anotherweathermod.api.Hazard;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.network.ClientWeatherState;
import com.itcmd.anotherweathermod.network.NearbyStormsPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Small, harmless whirls, drawn by each client: waterspouts, thin pale funnels that hang now and then from a
 * thunderstorm over open water, and dust devils, thin spinning columns of sand or dust that wander over hot, dry ground
 * on clear days (more of them in a heat wave). Where and when each one is comes from its storm or patch of ground and
 * the time, so players standing together see the same ones.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID, value = Dist.CLIENT)
public final class Whirls {

	/** One whirl, as drawn now: its funnel, how tall it is, and the ground it kicks up (dust devils only). */
	record Whirl(TornadoTracker.Funnel funnel, double height, StormRenderer.Look look, boolean water,
			BlockState ground) {
	}

	/** A storm's chances at a waterspout come in slots of this many ticks. */
	static final long SPOUT_SLOT = 3600;
	/** Dust devils: patches of ground this many blocks a side, each with a chance at one per slot. */
	static final int DEVIL_CELL = 80;
	static final long DEVIL_SLOT = 1200;
	/** Patches looked at each way from the camera. */
	static final int DEVIL_RANGE = 2;
	/** Most blocks away a whirl is drawn or makes particles. */
	static final double RANGE = 400;

	static final StormRenderer.Look SPOUT = new StormRenderer.Look(0.55f, 0.92f, 0.97f, 1.05f, 0.55f);

	private Whirls() {
	}

	/** Every whirl in range now. */
	static List<Whirl> whirls(ClientLevel level, double time, Vec3 camera) {
		List<Whirl> whirls = new ArrayList<>();
		if(!AwmClientConfig.WHIRLS.get())
			return whirls;
		float cloudY = level.effects().getCloudHeight();
		double top = Float.isNaN(cloudY) ? 192 : cloudY;
		NearbyStormsPayload payload = ClientWeatherState.storms();
		double age = time - payload.gameTime();
		for(NearbyStormsPayload.Storm storm : payload.storms()) {
			Whirl spout = waterspout(level, storm, age, time, top, camera);
			if(spout != null)
				whirls.add(spout);
		}
		if(level.isDay() && level.getRainLevel(1) < 0.1f) {
			int cx = Mth.floor(camera.x / DEVIL_CELL), cz = Mth.floor(camera.z / DEVIL_CELL);
			boolean heatWave = ClientWeatherState.get().has(Hazard.HEAT_WAVE);
			for(int i = cx - DEVIL_RANGE; i <= cx + DEVIL_RANGE; i++)
				for(int j = cz - DEVIL_RANGE; j <= cz + DEVIL_RANGE; j++) {
					Whirl devil = dustDevil(level, i, j, time, heatWave, camera);
					if(devil != null)
						whirls.add(devil);
				}
		}
		return whirls;
	}

	/** A thunderstorm's waterspout now, if it has one over open water. */
	private static Whirl waterspout(ClientLevel level, NearbyStormsPayload.Storm storm, double age, double time,
			double top, Vec3 camera) {
		StormKind kind = storm.kind();
		if(!kind.bringsRain() || kind.scale() < 1 && kind != StormKind.HURRICANE)
			return null;
		long slot = (long)Math.floor(time / SPOUT_SLOT);
		double chance = kind == StormKind.HURRICANE ? 0.5 : 0.2 + 0.08 * kind.scale();
		if(hash(storm.id(), slot, 1) >= chance)
			return null;
		double life = (time - slot * SPOUT_SLOT) / SPOUT_SLOT;
		double start = hash(storm.id(), slot, 2) * 0.3, length = 0.4 + hash(storm.id(), slot, 3) * 0.3;
		double p = (life - start) / length;
		if(p <= 0 || p >= 1)
			return null;
		double angle = hash(storm.id(), slot, 4) * Math.PI * 2;
		double out = storm.radius() * (0.15 + 0.45 * hash(storm.id(), slot, 5));
		double x = storm.x() + storm.velocityX() * age + Math.cos(angle) * out;
		double z = storm.z() + storm.velocityZ() * age + Math.sin(angle) * out;
		if(Math.hypot(x - camera.x, z - camera.z) > RANGE)
			return null;
		double radius = 2.5 + hash(storm.id(), slot, 6) * 2.5;
		// Only over open water: here and all round.
		for(int k = 0; k < 5; k++) {
			double ox = k == 0 ? 0 : Math.cos(k * Math.PI / 2) * radius * 3;
			double oz = k == 0 ? 0 : Math.sin(k * Math.PI / 2) * radius * 3;
			if(!water(level, x + ox, z + oz))
				return null;
		}
		double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
		float ramp = (float)Math.min(1, Math.min(p, 1 - p) / 0.15);
		var funnel = new TornadoTracker.Funnel(storm.id() * 31 + slot, x, z, ground, radius, ramp, ramp);
		return new Whirl(funnel, Math.max(20, top - ground), SPOUT, true, null);
	}

	private static boolean water(ClientLevel level, double x, double z) {
		BlockPos column = BlockPos.containing(x, 0, z);
		if(!level.isLoaded(column))
			return false;
		BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below();
		return level.getFluidState(surface).is(FluidTags.WATER);
	}

	/** Patch ({@code i}, {@code j})'s dust devil now, if it has one. */
	private static Whirl dustDevil(ClientLevel level, int i, int j, double time, boolean heatWave, Vec3 camera) {
		long patch = (long)i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL;
		// Each patch's slots are staggered, so they do not all change at once.
		double shifted = time + hash(patch, 0, 7) * DEVIL_SLOT;
		long slot = (long)Math.floor(shifted / DEVIL_SLOT);
		if(hash(patch, slot, 1) >= (heatWave ? 0.3 : 0.1))
			return null;
		double life = (shifted - slot * DEVIL_SLOT) / DEVIL_SLOT;
		double start = hash(patch, slot, 2) * 0.4, length = 0.35 + hash(patch, slot, 3) * 0.25;
		double p = (life - start) / length;
		if(p <= 0 || p >= 1)
			return null;
		// It wanders across the patch at a walking pace.
		double heading = hash(patch, slot, 4) * Math.PI * 2;
		double travelled = p * length * DEVIL_SLOT * 0.06;
		double x = (i + 0.2 + 0.6 * hash(patch, slot, 5)) * DEVIL_CELL + Math.cos(heading) * travelled
				+ Math.sin(time * 0.05 + patch) * 1.5;
		double z = (j + 0.2 + 0.6 * hash(patch, slot, 6)) * DEVIL_CELL + Math.sin(heading) * travelled
				+ Math.cos(time * 0.043 + patch) * 1.5;
		if(Math.hypot(x - camera.x, z - camera.z) > RANGE)
			return null;
		BlockPos column = BlockPos.containing(x, 0, z);
		if(!level.isLoaded(column))
			return null;
		BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		Biome biome = level.getBiome(surface).value();
		if(biome.hasPrecipitation() || biome.getBaseTemperature() < 1 || !level.canSeeSky(surface))
			return null;
		BlockState ground = level.getBlockState(surface.below());
		if(!ground.getFluidState().isEmpty())
			return null;
		int colour = ground.getMapColor(level, surface.below()).col;
		float r = (colour >> 16 & 255) / 255f, g = (colour >> 8 & 255) / 255f, b = (colour & 255) / 255f;
		float max = Math.max(0.01f, Math.max(r, Math.max(g, b)));
		var look = new StormRenderer.Look(0.45f, r / max, g / max, b / max, 0.3f);
		float ramp = (float)Math.min(1, Math.min(p, 1 - p) / 0.2);
		double radius = 1.2 + hash(patch, slot, 8) * 1.3;
		var funnel = new TornadoTracker.Funnel(patch * 31 + slot, x, z, surface.getY(), radius, ramp, 1);
		return new Whirl(funnel, 14 + hash(patch, slot, 9) * 16, look, false, ground);
	}

	/** A number in [0, 1), the same for the same inputs. */
	static double hash(long a, long b, long salt) {
		long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 11) * 0x1.0p-53;
	}

	@SubscribeEvent
	public static void onRenderStage(RenderLevelStageEvent event) {
		if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER)
			return;
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if(level == null)
			return;
		double time = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
		Vec3 camera = event.getCamera().getPosition();
		List<Whirl> whirls = whirls(level, time, camera);
		if(whirls.isEmpty())
			return;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		for(Whirl whirl : whirls)
			StormRenderer.funnel(level, whirl.funnel(), whirl.height(), whirl.look(), camera, time);
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
	}

	/** Spray thrown up round a waterspout's foot, sand and dust whirled up a dust devil. */
	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if(level == null || mc.isPaused() || mc.options.particles().get() == ParticleStatus.MINIMAL)
			return;
		Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
		for(Whirl whirl : whirls(level, level.getGameTime(), camera)) {
			var f = whirl.funnel();
			if(Math.hypot(f.x() - camera.x, f.z() - camera.z) > 160)
				continue;
			for(int n = Math.round((whirl.water() ? 6 : 4) * f.strength()); n > 0; n--) {
				double a = level.random.nextDouble() * Math.PI * 2;
				double r = f.radius() * (0.5 + level.random.nextDouble() * (whirl.water() ? 1.5 : 0.8));
				double x = f.x() + Math.cos(a) * r, z = f.z() + Math.sin(a) * r;
				double spin = whirl.water() ? 0.35 : 0.5;
				if(whirl.water()) {
					level.addParticle(level.random.nextInt(3) == 0 ? ParticleTypes.SPLASH : ParticleTypes.CLOUD, x,
							f.ground() + level.random.nextDouble(), z, Math.sin(a) * spin,
							0.05 + level.random.nextDouble() * 0.15, -Math.cos(a) * spin);
				}
				else {
					mc.particleEngine.add(WindParticle.whirl(level, x, f.ground() + level.random.nextDouble() * whirl.height() * 0.6,
							z, Math.sin(a) * spin, 0.1 + level.random.nextDouble() * 0.25, -Math.cos(a) * spin, whirl.ground()));
				}
			}
		}
	}
}
