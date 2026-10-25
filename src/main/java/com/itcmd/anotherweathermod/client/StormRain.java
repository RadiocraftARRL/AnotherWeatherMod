package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AwmClientConfig;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.network.ClientWeatherState;
import com.itcmd.anotherweathermod.network.LocalWeatherPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Rain in severe storms (severe thunderstorms, supercells, hurricanes): darker than vanilla's and driven sideways by
 * the wind. It replaces vanilla's rain while you are in one (see {@code MixinLevelRenderer}) and is drawn the same
 * way, a ring of textured columns round the camera, each slanted downwind.
 */
public final class StormRain {

	private static final ResourceLocation RAIN = ResourceLocation.withDefaultNamespace("textures/environment/rain.png");
	/** Rain falls about this fast, metres per second: the slant is the wind over this. */
	static final double FALL_SPEED = 14;
	/** The most slant, blocks across per block down. */
	static final double MAX_SLANT = 1.3;
	/** Colour of the rain, darker and greyer than vanilla's white. */
	static final float SHADE = 0.5f;

	private StormRain() {
	}

	/** Whether the camera is in a storm whose rain this draws. */
	static boolean active(ClientLevel level, double camX, double camY, double camZ) {
		if(!AwmClientConfig.STORM_RAIN.get())
			return false;
		StormKind kind = ClientWeatherState.get().stormKind();
		if(kind != StormKind.SEVERE_THUNDERSTORM && kind != StormKind.SUPERCELL && kind != StormKind.HURRICANE)
			return false;
		// Snow and dry biomes keep vanilla's (or no) precipitation.
		BlockPos pos = BlockPos.containing(camX, camY, camZ);
		return level.getBiome(pos).value().getPrecipitationAt(pos) == Biome.Precipitation.RAIN;
	}

	/**
	 * Draws the rain if the camera is in a severe storm.
	 *
	 * @return whether it did (and vanilla's rain should not be drawn)
	 */
	public static boolean render(LightTexture lightTexture, float partialTick, double camX, double camY, double camZ) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if(level == null || !active(level, camX, camY, camZ))
			return false;
		float rain = level.getRainLevel(partialTick);
		if(rain <= 0)
			return true;

		LocalWeatherPayload weather = ClientWeatherState.get();
		double windX = WeatherEffects.windX();
		double windZ = WeatherEffects.windZ();
		double speed = Math.hypot(windX, windZ);
		double slant = Math.min(MAX_SLANT, speed / FALL_SPEED);
		double dirX = speed > 1e-6 ? windX / speed : 0;
		double dirZ = speed > 1e-6 ? windZ / speed : 0;
		int radius = Minecraft.useFancyGraphics() ? 10 : 5;
		int cx = Mth.floor(camX), cy = Mth.floor(camY), cz = Mth.floor(camZ);
		double time = level.getGameTime() + partialTick;

		lightTexture.turnOnLightLayer();
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(Minecraft.useShaderTransparency());
		RenderSystem.setShader(GameRenderer::getParticleShader);
		RenderSystem.setShaderTexture(0, RAIN);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for(int z = cz - radius; z <= cz + radius; z++)
			for(int x = cx - radius; x <= cx + radius; x++) {
				double dx = x + 0.5 - camX;
				double dz = z + 0.5 - camZ;
				double distSqr = dx * dx + dz * dz;
				if(distSqr > radius * radius)
					continue;
				pos.set(x, cy, z);
				if(level.getBiome(pos).value().getPrecipitationAt(pos) != Biome.Precipitation.RAIN)
					continue;
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				int bottom = Math.max(cy - radius, ground);
				int top = Math.max(cy + radius, ground);
				if(bottom >= top)
					continue;
				double height = top - bottom;
				// Slanted downwind: the top sits upwind of the bottom, centred on the column.
				double shiftX = dirX * slant * height / 2;
				double shiftZ = dirZ * slant * height / 2;
				// Faces the camera.
				double dist = Math.max(0.01, Math.sqrt(distSqr));
				float sideX = (float)(-dz / dist * 0.5);
				float sideZ = (float)(dx / dist * 0.5);
				float bx = (float)(dx + shiftX), bz = (float)(dz + shiftZ);
				float tx = (float)(dx - shiftX), tz = (float)(dz - shiftZ);
				float y0 = (float)(bottom - camY), y1 = (float)(top - camY);

				long hash = x * 3129871L ^ z * 116129781L;
				float offset = (hash >>> 8 & 31) / 32f;
				// Faster down the streak in a slant, and scrolling a little faster than vanilla's in a storm.
				float scroll = (float)(-(time * (0.12 + 0.02 * (hash >>> 16 & 3)) % 32) - offset);
				float v0 = (float)(bottom * 0.25 / Math.sqrt(1 + slant * slant)) + scroll;
				float v1 = (float)(top * 0.25 / Math.sqrt(1 + slant * slant)) + scroll;
				float alpha = (float)((1 - distSqr / (radius * radius)) * 0.5 + 0.5) * rain;
				pos.set(x, Math.max(cy, ground), z);
				int light = LevelRenderer.getLightColor(level, pos);
				float g = SHADE * (weather.stormKind() == StormKind.HURRICANE ? 0.9f : 1);

				buffer.addVertex(bx - sideX, y0, bz - sideZ).setUv(0, v0).setColor(g, g, g * 1.06f, alpha).setLight(light);
				buffer.addVertex(bx + sideX, y0, bz + sideZ).setUv(1, v0).setColor(g, g, g * 1.06f, alpha).setLight(light);
				buffer.addVertex(tx + sideX, y1, tz + sideZ).setUv(1, v1).setColor(g, g, g * 1.06f, alpha).setLight(light);
				buffer.addVertex(tx - sideX, y1, tz - sideZ).setUv(0, v1).setColor(g, g, g * 1.06f, alpha).setLight(light);
			}

		MeshData mesh = buffer.build();
		if(mesh != null)
			BufferUploader.drawWithShader(mesh);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
		RenderSystem.depthMask(true);
		lightTexture.turnOffLightLayer();
		return true;
	}
}
