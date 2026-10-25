package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.AwmConfig;
import com.itcmd.anotherweathermod.AwmGameRules;
import com.itcmd.anotherweathermod.api.Hazard;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.api.WeatherSample;
import com.itcmd.anotherweathermod.mixin.ServerLevelInvoker;
import com.itcmd.anotherweathermod.sim.PointWeather;
import com.itcmd.anotherweathermod.sim.WeatherModel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What a storm does to the chunks under it, run at the end of each chunk tick ({@code ServerLevel.tickChunk}). This
 * repeats vanilla's thunder and precipitation rolls, which do nothing in a managed level because its global weather
 * stays clear, with the local storm instead. Chunks with no storm over them cost one cheap check.
 */
public final class ChunkWeather {

	/** Vanilla: one lightning chance in this many chunk ticks while thundering. */
	static final int VANILLA_LIGHTNING = 100000;
	/** Vanilla: one precipitation tick per this many random ticks. */
	static final int VANILLA_PRECIPITATION = 48;

	private ChunkWeather() {
	}

	public static void tick(ServerLevel level, LevelChunk chunk, int randomTickSpeed) {
		LevelWeather weather = WeatherManager.get(level);
		if(weather == null)
			return;
		ChunkPos chunkPos = chunk.getPos();
		long now = level.getGameTime();
		double cx = chunkPos.getMiddleBlockX();
		double cz = chunkPos.getMiddleBlockZ();
		boolean override = weather.overrideRain();
		if(!override && !weather.field().anyCovers(cx, cz, now))
			return;
		PointWeather point = weather.field().sample(cx, cz, now);
		double rain = override ? 1 : point.rain();
		lightning(level, weather, chunkPos, point.thunder());

		if(rain > WeatherModel.RAINING) {
			int rolls = randomTickSpeed;
			if(point.windSpeed() > WeatherModel.BLIZZARD_WIND && rain > 0.5)
				rolls *= 2; // Blizzards pile snow up faster.
			for(int i = 0; i < rolls; i++)
				if(level.random.nextInt(VANILLA_PRECIPITATION) == 0)
					precipitation(level, level.getBlockRandomPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ(), 15));
		}

		if(point.stormKind() == StormKind.SUPERCELL && AwmConfig.HAIL_DAMAGE.get())
			hail(level, weather, chunkPos, now);

		if(windDamageOn(level, point))
			wind(level, chunkPos, point);
	}

	private static void lightning(ServerLevel level, LevelWeather weather, ChunkPos chunkPos, double thunder) {
		if(thunder <= WeatherModel.LIGHTNING_THUNDER)
			return;
		double chance = thunder * AwmConfig.LIGHTNING_MULTIPLIER.get() / VANILLA_LIGHTNING;
		if(level.random.nextDouble() >= chance)
			return;
		BlockPos target = ((ServerLevelInvoker)level).awm$findLightningTargetAround(
				level.getBlockRandomPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ(), 15));
		if(!weather.isRainingAt(target))
			return;
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if(bolt != null) {
			bolt.moveTo(Vec3.atBottomCenterOf(target));
			level.addFreshEntity(bolt);
		}
	}

	/** Vanilla's {@code ServerLevel.tickPrecipitation}, the part that needs rain: snow layers and cauldrons. */
	private static void precipitation(ServerLevel level, BlockPos column) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
		BlockPos below = top.below();
		if(!level.isAreaLoaded(below, 1))
			return;
		Biome biome = level.getBiome(top).value();
		int maxLayers = level.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT);
		if(maxLayers > 0 && biome.shouldSnow(level, top)) {
			BlockState state = level.getBlockState(top);
			if(state.is(Blocks.SNOW)) {
				int layers = state.getValue(SnowLayerBlock.LAYERS);
				if(layers < Math.min(maxLayers, 8)) {
					BlockState more = state.setValue(SnowLayerBlock.LAYERS, layers + 1);
					Block.pushEntitiesUp(state, more, level, top);
					level.setBlockAndUpdate(top, more);
				}
			}
			else
				level.setBlockAndUpdate(top, Blocks.SNOW.defaultBlockState());
		}
		Biome.Precipitation type = biome.getPrecipitationAt(below);
		if(type != Biome.Precipitation.NONE) {
			BlockState state = level.getBlockState(below);
			state.getBlock().handlePrecipitation(state, level, below, type);
		}
	}

	/** Hail stings mobs and breaks crops and panes. Players are handled with the rest of their weather. */
	private static void hail(ServerLevel level, LevelWeather weather, ChunkPos chunkPos, long now) {
		if(level.random.nextInt(20) != 0)
			return;
		BlockPos column = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
				level.getBlockRandomPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ(), 15));
		WeatherSample sample = weather.sample(column, now);
		if(!sample.has(Hazard.HAIL))
			return;
		Hail.strike(level, column);
		for(int i = 0; i < 2; i++) // Crops are the hail's main victims: a few more columns.
			Hail.strike(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
					level.getBlockRandomPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ(), 15)));
		AABB box = new AABB(chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ(),
				chunkPos.getMaxBlockX() + 1, level.getMaxBuildHeight(), chunkPos.getMaxBlockZ() + 1);
		for(LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> !(e instanceof net.minecraft.world.entity.player.Player)))
			for(int i = 0; i < 20; i++) // Make up for running one tick in twenty.
				Hail.pelt(level, entity);
	}

	private static boolean windDamageOn(ServerLevel level, PointWeather point) {
		if(point.windSpeed() < WeatherModel.DAMAGING_WIND || !level.getGameRules().getBoolean(AwmGameRules.DO_WIND_DAMAGE))
			return false;
		if(point.stormKind() == StormKind.HURRICANE)
			return AwmConfig.HURRICANE_WIND_DAMAGE.get();
		return AwmConfig.SEVERE_WIND_DAMAGE.get();
	}

	/**
	 * Thunderstorm gusts pick this share of the blocks a hurricane's wind of the same speed would. Only the gusts blow
	 * hard enough to do damage (a lull is {@code StormCell.LULL} of a gust), so leaves go in the gusts and not between.
	 */
	static final double SEVERE_DAMAGE_RATE = 0.25;

	/** A few surface blocks per chunk tick, more the harder it blows. */
	private static void wind(ServerLevel level, ChunkPos chunkPos, PointWeather point) {
		double speed = point.windSpeed();
		double chance = Math.min(1, (speed - WeatherModel.DAMAGING_WIND) / 40);
		if(point.stormKind() != StormKind.HURRICANE)
			chance *= SEVERE_DAMAGE_RATE;
		if(level.random.nextDouble() >= chance)
			return;
		BlockPos column = level.getBlockRandomPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ(), 15);
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
		// The heightmap stops at the first block that blocks motion, so a torch or flower sits at top, leaves and
		// grass under it.
		if(!WindDamage.apply(level, top, speed, point.windX(), point.windZ()))
			WindDamage.apply(level, top.below(), speed, point.windX(), point.windZ());
	}
}
