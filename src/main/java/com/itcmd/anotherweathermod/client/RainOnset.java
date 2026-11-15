package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmClientConfig;
import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.network.ClientWeatherState;
import com.itcmd.anotherweathermod.network.NearbyStormsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The first drops of a storm on its way (2026-11-13, the maintainer: "a few drops of rain here and there, then
 * occasional ones, then more frequent, then it's raining"). Out to {@link #REACH} blocks outside a storm's rain the odd
 * drop falls round the player and splashes, more of them the nearer the rain, until at the rain's edge the storm's own
 * rain takes over. Particles only, a handful a tick at most.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID, value = Dist.CLIENT)
public final class RainOnset {

	/** The first drops come this far outside a storm's rain, blocks. */
	static final double REACH = 140;
	/** Drops keep coming this far inside the rain's edge, while the rain there is still light. */
	static final double INSIDE = 24;
	/** Drops a tick at the rain's edge from a storm at full strength. */
	static final double MOST = 7;
	/** Drops fall from this high over the player, and land within {@link #SPREAD} blocks of them. */
	static final int HEIGHT = 14;
	static final int SPREAD = 12;

	private static double owed;
	/** Sounds at most this many a tick, however many drops land (2026-11-13: the first drops were silent). */
	static final int MOST_SOUNDS = 2;
	/** Below this many drops a tick each is heard alone, a single drip; above, as a soft patter of rain. */
	static final double PATTER_FROM = 1.5;
	/** How loud a lone drop is, and the patter at its loudest (vanilla rain is about 0.2 overhead). */
	static final float DRIP_VOLUME = 0.12f, PATTER_VOLUME = 0.1f;
	/** Under a roof the drops outside are heard this much quieter. */
	static final float UNDER_COVER = 0.35f;

	/** How loud the patter is with {@code dropsPerTick} drops landing: rising to {@link #PATTER_VOLUME} at {@link #MOST}. */
	static float patterVolume(double dropsPerTick) {
		if(dropsPerTick < PATTER_FROM)
			return 0;
		return (float)(PATTER_VOLUME * Mth.clamp((dropsPerTick - PATTER_FROM) / (MOST - PATTER_FROM) * 0.7 + 0.3, 0, 1));
	}

	private RainOnset() {
	}

	/**
	 * Drops a tick {@code fromEdge} blocks outside a storm's rain (negative inside) for a storm of {@code strength}
	 * (0..1): none beyond {@link #REACH}, then rising as the cube of how near, so far out a drop now and then (a few a
	 * minute at a hundred blocks), closer in an occasional one, then frequent, then {@link #MOST} at the edge.
	 */
	static double dropsPerTick(double fromEdge, double strength) {
		if(fromEdge >= REACH || fromEdge < -INSIDE || strength <= 0)
			return 0;
		double near = 1 - Mth.clamp(fromEdge / REACH, 0, 1);
		return MOST * Math.min(1, strength) * near * near * near;
	}

	/** The most drops a tick any storm in {@code payload} brings to ({@code x}, {@code z}) at {@code age} ticks on. */
	static double dropsAt(NearbyStormsPayload payload, double x, double z, double age) {
		double most = 0;
		for(NearbyStormsPayload.Storm s : payload.storms()) {
			double edge = s.rainEdge();
			if(edge <= 0 || s.precipitation() == Precipitation.DUST || s.precipitation() == Precipitation.SNOW)
				continue;
			double sx = s.x() + s.velocityX() * age, sz = s.z() + s.velocityZ() * age;
			most = Math.max(most, dropsPerTick(Math.hypot(x - sx, z - sz) - edge, s.intensity()));
		}
		return most;
	}

	@SubscribeEvent
	public static void onTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if(level == null || mc.player == null || mc.isPaused() || !AwmClientConfig.RAIN_ONSET.get()
				|| mc.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL) {
			owed = 0;
			return;
		}
		NearbyStormsPayload payload = ClientWeatherState.storms();
		if(payload.storms().isEmpty()) {
			owed = 0;
			return;
		}
		double px = mc.player.getX(), pz = mc.player.getZ();
		double drops = dropsAt(payload, px, pz, level.getGameTime() - payload.gameTime());
		// Once the storm's own rain is falling hard the drops are lost in it.
		drops *= 1 - Mth.clamp(level.getRainLevel(1) * 2, 0, 1);
		owed = Math.min(owed + drops, MOST * 2);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int sounds = 0;
		// Indoors (something overhead) the drops outside are heard muffled, as vanilla's rain is.
		boolean covered = level.getHeight(Heightmap.Types.MOTION_BLOCKING, mc.player.getBlockX(), mc.player.getBlockZ()) > mc.player.getEyeY();
		float patter = patterVolume(drops);
		while(owed >= 1) {
			owed--;
			int x = Mth.floor(px) + level.random.nextInt(SPREAD * 2 + 1) - SPREAD;
			int z = Mth.floor(pz) + level.random.nextInt(SPREAD * 2 + 1) - SPREAD;
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			pos.set(x, ground, z);
			if(level.getBiome(pos).value().getPrecipitationAt(pos) != Biome.Precipitation.RAIN)
				continue;
			double dx = x + level.random.nextDouble(), dz = z + level.random.nextDouble();
			if(level.random.nextInt(3) == 0) // Some land in sight at once: a splash on the ground.
				level.addParticle(ParticleTypes.RAIN, dx, ground + 0.05, dz, 0, 0, 0);
			else // The rest are seen falling (and splash where they land).
				level.addParticle(ParticleTypes.FALLING_WATER, dx, Math.max(ground + 2, mc.player.getY() + HEIGHT - level.random.nextInt(6)),
						dz, 0, 0, 0);
			if(sounds < MOST_SOUNDS && (patter > 0 ? level.random.nextInt(2) == 0 : true)) {
				sounds++;
				float cover = covered ? UNDER_COVER : 1;
				if(patter > 0) // Drops close together: a soft patter, rain's own sound, quiet and a little high.
					level.playLocalSound(dx, ground + 0.1, dz, net.minecraft.sounds.SoundEvents.WEATHER_RAIN,
							net.minecraft.sounds.SoundSource.WEATHER, patter * cover, 1.1f + level.random.nextFloat() * 0.3f, false);
				else // A lone drop: one drip where it lands.
					level.playLocalSound(dx, ground + 0.1, dz, net.minecraft.sounds.SoundEvents.POINTED_DRIPSTONE_DRIP_WATER,
							net.minecraft.sounds.SoundSource.WEATHER, DRIP_VOLUME * cover * (0.6f + level.random.nextFloat() * 0.6f),
							0.8f + level.random.nextFloat() * 0.5f, false);
			}
		}
	}
}
