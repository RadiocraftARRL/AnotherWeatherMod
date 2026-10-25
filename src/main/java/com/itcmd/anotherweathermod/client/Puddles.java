package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
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
 * Puddles: in the rain, still water collects in patches on open ground round the player, and dries up a few minutes
 * after the rain stops. Purely a picture, drawn on top of the blocks; nothing in the world changes, so they work with
 * any server.
 *
 * <p>Where puddles form is fixed noise over the ground; how wet it is decides how much of it fills. Each corner of a
 * block's top gets its own depth, so a puddle's edge fades across the block instead of stopping square.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID, value = Dist.CLIENT)
public final class Puddles {

	private static final ResourceLocation WATER = ResourceLocation.withDefaultNamespace("block/water_still");
	static final int RADIUS = 20;
	/** Ticks of rain to soak the ground, and ticks of dry weather to dry it. */
	static final float WETTING = 20 * 60;
	static final float DRYING = 20 * 180;
	/** Most of the ground a puddle covers when it is soaked. */
	static final double MAX_COVER = 0.28;
	/** Puddle noise scale, blocks. */
	static final double SCALE = 4.5;

	/** How wet the ground round the player is, 0..1. */
	private static float wetness;
	private static final List<BlockPos> spots = new ArrayList<>();
	private static BlockPos builtAt;
	private static float builtWetness = -1;

	private Puddles() {
	}

	/** How wet the ground round the player is, 0..1. */
	public static float wetness() {
		return wetness;
	}

	@SubscribeEvent
	public static void onTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if(level == null || mc.isPaused() || mc.player == null)
			return;
		BlockPos at = mc.player.blockPosition();
		boolean rainHere = level.getBiome(at).value().getPrecipitationAt(at) == Biome.Precipitation.RAIN;
		float rain = rainHere ? level.getRainLevel(1) : 0;
		if(rain > 0.2f)
			wetness = Math.min(1, wetness + rain / WETTING);
		else
			wetness = Math.max(0, wetness - 1 / DRYING);
		drip(mc, level, at, rain);
		// Find the puddle spots again when the player has moved or the ground got wetter or drier.
		if(builtAt == null || builtAt.distManhattan(at) > 3 || Math.abs(builtWetness - wetness) > 0.03f
				|| level.getGameTime() % 40 == 0)
			rebuild(level, at);
	}

	/** After rain, and with the dew at dawn, water drips from the leaves round the player. */
	private static void drip(Minecraft mc, ClientLevel level, BlockPos at, float rain) {
		if(!AwmClientConfig.MIST.get() || mc.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL)
			return;
		long day = level.getDayTime() % 24000;
		float dew = day >= 22500 || day < 2000 ? 0.4f : 0;
		float amount = rain > 0.2f ? 0 : Math.max(wetness, dew); // While it rains vanilla's leaves drip already.
		int tries = WeatherEffects.scaled(mc, amount * 24);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for(int i = 0; i < tries; i++) {
			pos.set(at.getX() + level.random.nextInt(21) - 10, at.getY() + level.random.nextInt(12) - 2,
					at.getZ() + level.random.nextInt(21) - 10);
			if(!level.getBlockState(pos).is(BlockTags.LEAVES) || !level.getBlockState(pos.below()).isAir())
				continue;
			level.addParticle(net.minecraft.core.particles.ParticleTypes.DRIPPING_WATER,
					pos.getX() + level.random.nextDouble(), pos.getY() - 0.05, pos.getZ() + level.random.nextDouble(),
					0, 0, 0);
		}
	}

	/** How deep the noise says the water is at ({@code x}, {@code z}), below 0 for dry. */
	static double depth(double x, double z, float wetness) {
		double n = valueNoise(x / SCALE, z / SCALE) * 0.65 + valueNoise(x / (SCALE * 0.4) + 31.7, z / (SCALE * 0.4) - 12.3) * 0.35;
		return n - (1 - MAX_COVER * wetness);
	}

	private static void rebuild(ClientLevel level, BlockPos at) {
		builtAt = at;
		builtWetness = wetness;
		spots.clear();
		if(wetness < 0.02f || !AwmClientConfig.PUDDLES.get())
			return;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for(int z = at.getZ() - RADIUS; z <= at.getZ() + RADIUS; z++)
			for(int x = at.getX() - RADIUS; x <= at.getX() + RADIUS; x++) {
				if(maxCornerDepth(x, z) <= 0)
					continue;
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				pos.set(x, top, z);
				if(Math.abs(top - at.getY()) > 12 || !holdsWater(level, pos))
					continue;
				spots.add(pos.immutable());
			}
	}

	private static double maxCornerDepth(int x, int z) {
		double d = Double.NEGATIVE_INFINITY;
		for(int c = 0; c < 4; c++)
			d = Math.max(d, depth(x + (c & 1), z + (c >> 1), wetness));
		return d;
	}

	/** Open ground with a flat solid top, where it rains. */
	private static boolean holdsWater(ClientLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if(state.isAir() || !state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES) || state.is(BlockTags.ICE)
				|| state.is(BlockTags.SNOW) || !state.isFaceSturdy(level, pos, Direction.UP))
			return false;
		BlockPos above = pos.above();
		return level.getBlockState(above).isAir()
				&& level.getBiome(above).value().getPrecipitationAt(above) == Biome.Precipitation.RAIN;
	}

	@SubscribeEvent
	public static void onRenderStage(RenderLevelStageEvent event) {
		if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || spots.isEmpty()
				|| !AwmClientConfig.PUDDLES.get())
			return;
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if(level == null)
			return;
		Vec3 camera = event.getCamera().getPosition();
		TextureAtlasSprite water = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(WATER);

		mc.gameRenderer.lightTexture().turnOnLightLayer();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.enablePolygonOffset();
		RenderSystem.polygonOffset(-1, -10);
		RenderSystem.setShader(GameRenderer::getParticleShader);
		RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
		// RenderSystem's modelview already holds the camera's rotation here; vertices are camera-relative.
		for(BlockPos pos : spots) {
			BlockPos above = pos.above();
			int light = LevelRenderer.getLightColor(level, above);
			int tint = BiomeColors.getAverageWaterColor(level, above);
			// Water in a puddle shows mostly the grey sky: the biome's water colour, washed out.
			float r = Mth.lerp(0.55f, (tint >> 16 & 0xFF) / 255f, 0.62f);
			float g = Mth.lerp(0.55f, (tint >> 8 & 0xFF) / 255f, 0.66f);
			float b = Mth.lerp(0.55f, (tint & 0xFF) / 255f, 0.72f);
			float y = (float)(pos.getY() + 1.002 - camera.y);
			for(int c : new int[] { 0, 2, 3, 1 }) { // Corners anticlockwise.
				int ox = c & 1, oz = c >> 1;
				float alpha = (float)Mth.clamp(depth(pos.getX() + ox, pos.getZ() + oz, wetness) * 9, 0, 0.62);
				float x = (float)(pos.getX() + ox - camera.x);
				float z = (float)(pos.getZ() + oz - camera.z);
				buffer.addVertex(x, y, z).setUv(Mth.lerp(ox, water.getU0(), water.getU1()),
						Mth.lerp(oz, water.getV0(), water.getV1())).setColor(r, g, b, alpha).setLight(light);
			}
		}
		MeshData mesh = buffer.build();
		if(mesh != null)
			BufferUploader.drawWithShader(mesh);
		RenderSystem.polygonOffset(0, 0);
		RenderSystem.disablePolygonOffset();
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
		mc.gameRenderer.lightTexture().turnOffLightLayer();
	}

	/** Smooth value noise in [0, 1]. */
	static double valueNoise(double x, double z) {
		int ix = Mth.floor(x), iz = Mth.floor(z);
		double fx = x - ix, fz = z - iz;
		double sx = fx * fx * (3 - 2 * fx), sz = fz * fz * (3 - 2 * fz);
		double a = Mth.lerp(sx, hash(ix, iz), hash(ix + 1, iz));
		double c = Mth.lerp(sx, hash(ix, iz + 1), hash(ix + 1, iz + 1));
		return Mth.lerp(sz, a, c);
	}

	private static double hash(int x, int z) {
		long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
		h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 11) * 0x1.0p-53;
	}
}
