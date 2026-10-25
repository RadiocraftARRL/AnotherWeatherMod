package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.sim.Climate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

/**
 * The ground of a server level. Biomes come from loaded chunks where there are some, else from the biome source,
 * so nothing here loads or generates chunks.
 */
public final class LevelClimate implements Climate {

	private final ServerLevel level;

	public LevelClimate(ServerLevel level) {
		this.level = level;
	}

	private Holder<Biome> biome(int x, int y, int z) {
		return level.getBiome(new BlockPos(x, y, z));
	}

	@Override
	public Precipitation basePrecipitation(int x, int y, int z) {
		BlockPos pos = new BlockPos(x, y, z);
		return switch(biome(x, y, z).value().getPrecipitationAt(pos)) {
			case RAIN -> Precipitation.RAIN;
			case SNOW -> Precipitation.SNOW;
			case NONE -> Precipitation.NONE;
		};
	}

	@Override
	public float biomeTemperature(int x, int y, int z) {
		return biome(x, y, z).value().getBaseTemperature();
	}

	@Override
	public boolean isOcean(int x, int z) {
		// Straight from the biome source: this may be thousands of blocks from anything loaded.
		Holder<Biome> biome = level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
				QuartPos.fromBlock(x), QuartPos.fromBlock(level.getSeaLevel()), QuartPos.fromBlock(z),
				level.getChunkSource().randomState().sampler());
		return biome.is(BiomeTags.IS_OCEAN);
	}

	@Override
	public int seaLevel() {
		return level.getSeaLevel();
	}
}
