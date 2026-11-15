package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmSounds;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.network.ClientWeatherState;
import com.itcmd.anotherweathermod.network.LocalWeatherPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Sounds of a hard blow (2026-11-13, the maintainer in a category 2: "the rain should be much louder. Some creaking wood
 * sounds on planks/wood would be good too"). In a hurricane, a roar of rain on top of vanilla's patter, louder with the
 * category and the wind; under a roof it drums on it instead. In any gale, the wooden blocks round the player creak now
 * and then, more often the harder it blows.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID, value = Dist.CLIENT)
public final class StormSounds {

	/** Hurricane rain at its quietest (category 1, light wind) and loudest; vanilla's own rain is about 0.2. */
	static final float RAIN_QUIET = 0.32f, RAIN_LOUD = 0.85f; // Toned down 2026-11-13 (was 0.45 to 1.0).
	/** Rain sounds a tick in a hurricane. */
	static final int RAIN_SOUNDS = 2;
	/**
	 * Category 4 and 5 stack more rain on top (2026-11-13, the maintainer: "stacked rain sound to get across more rain"):
	 * this many extra layers a tick for each category above 3, deeper in pitch, so it is a wall of water, not just louder.
	 */
	static final int STACKED_PER_CATEGORY = 1; // Was 2: a 4 adds one deeper layer, a 5 two.

	/** Rain sounds a tick for a hurricane of {@code category}. */
	static int rainSounds(int category) {
		return RAIN_SOUNDS + Math.max(0, category - 3) * STACKED_PER_CATEGORY;
	}
	/** Wood starts to creak at this wind, metres per second (a strong gale), and creaks most often at {@link #CREAK_FULL}. */
	static final double CREAK_FROM = 17, CREAK_FULL = 45;
	/** The most creaks a second, in the fiercest wind. */
	static final double CREAKS_PER_SECOND = 1.5;
	/** Creaks come from wooden blocks this close, and a few spots are tried for one each time. */
	static final int CREAK_REACH = 10, CREAK_TRIES = 8;

	private StormSounds() {
	}

	/** How loud a hurricane's rain is, 0 to 1, by category (1 to 5) and wind (metres per second). */
	static float rainVolume(int category, double wind) {
		float byCategory = Mth.clamp((category - 1) / 4f, 0, 1);
		float byWind = (float)Mth.clamp((wind - 20) / 40, 0, 1);
		return Mth.lerp(Math.max(byCategory, byWind) * 0.7f + Math.min(byCategory, byWind) * 0.3f, RAIN_QUIET, RAIN_LOUD);
	}

	/** Chance a tick that some wood nearby creaks, in wind of {@code wind} metres per second. */
	static double creakChance(double wind) {
		if(wind < CREAK_FROM)
			return 0;
		double t = Mth.clamp((wind - CREAK_FROM) / (CREAK_FULL - CREAK_FROM), 0, 1);
		return CREAKS_PER_SECOND / 20 * (0.1 + 0.9 * t * t);
	}

	@SubscribeEvent
	public static void onTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if(level == null || mc.player == null || mc.isPaused())
			return;
		LocalWeatherPayload weather = ClientWeatherState.get();
		double wind = Math.hypot(WeatherEffects.windX(), WeatherEffects.windZ()) * WeatherEffects.gust(level.getGameTime());
		BlockPos player = mc.player.blockPosition();
		boolean covered = level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getX(), player.getZ()) > mc.player.getEyeY();

		// The hurricane's rain: a roar, or a drumming on the roof.
		float rain = level.getRainLevel(1);
		if(weather.stormKind() == StormKind.HURRICANE && rain > 0.2f
				&& level.getBiome(player).value().getPrecipitationAt(player) == Biome.Precipitation.RAIN) {
			int category = Mth.clamp(weather.category(), 1, 5);
			float volume = rainVolume(category, wind) * rain;
			int layers = rainSounds(category);
			for(int i = 0; i < layers; i++) {
				boolean stacked = i >= RAIN_SOUNDS; // The extra layers of a category 4 or 5: deeper, a roar.
				if(level.random.nextInt(3) == 0)
					continue;
				int x = player.getX() + level.random.nextInt(13) - 6, z = player.getZ() + level.random.nextInt(13) - 6;
				int y = covered ? Mth.floor(mc.player.getEyeY()) + 2 : level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				level.playLocalSound(x + 0.5, y, z + 0.5, covered ? SoundEvents.WEATHER_RAIN_ABOVE : SoundEvents.WEATHER_RAIN,
						SoundSource.WEATHER, volume * (covered ? 0.8f : 1) * (stacked ? 0.7f : 1),
						stacked ? 0.5f + level.random.nextFloat() * 0.15f : 0.7f + level.random.nextFloat() * 0.25f, false);
			}
		}

		// Wood creaking in a gale.
		if(level.random.nextDouble() >= creakChance(wind))
			return;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for(int i = 0; i < CREAK_TRIES; i++) {
			pos.set(player.getX() + level.random.nextInt(CREAK_REACH * 2 + 1) - CREAK_REACH,
					player.getY() + level.random.nextInt(9) - 3,
					player.getZ() + level.random.nextInt(CREAK_REACH * 2 + 1) - CREAK_REACH);
			if(level.getBlockState(pos).getSoundType(level, pos, mc.player) != SoundType.WOOD)
				continue;
			float strength = (float)Mth.clamp((wind - CREAK_FROM) / (CREAK_FULL - CREAK_FROM), 0, 1);
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, AwmSounds.WOOD_CREAK.get(), SoundSource.BLOCKS,
					0.35f + 0.45f * strength, 0.8f + level.random.nextFloat() * 0.3f, false);
			return;
		}
	}
}
