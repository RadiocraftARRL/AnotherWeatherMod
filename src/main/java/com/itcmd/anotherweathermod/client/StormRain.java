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
 * Rain in thunderstorms and worse: greyer and darker than vanilla's the stronger the storm, with thick bands of rain
 * mixed in and more of it further off, and driven sideways by the wind. It replaces vanilla's rain while you are in one (see {@code MixinLevelRenderer}) and is drawn the same
 * way, a ring of textured columns round the camera, each slanted downwind.
 */
public final class StormRain {

	private static final ResourceLocation RAIN = ResourceLocation.withDefaultNamespace("textures/environment/rain.png");
	/** Rain falls about this fast, metres per second: the slant is the wind over this. */
	static final double FALL_SPEED = 14;
	/** The most slant, blocks across per block down: about 10 degrees. */
	static final double MAX_SLANT = 0.18;
	/** A hurricane drives its rain much flatter, up to about 50 degrees. */
	static final double HURRICANE_SLANT = 1.2;
	/** Colour of the rain, greyer and darker than vanilla's white; each sheet varies a little round it. */
	static final float SHADE = 0.62f;
	/** Rain colour by severity, 1 (a thunderstorm) to 5: greyer and darker the worse it is. */
	static final float[] SHADE_BY_SEVERITY = { 0.8f, 0.76f, 0.7f, 0.62f, 0.58f, 0.54f };
	/** Sheets of rain per near column, each a little offset with its own speed and shade, for depth. */
	static final int SHEETS = 2;
	/** Beyond the near columns, fainter rain out to this far gives the storm depth (fancy graphics). */
	static final int FAR = 18;

	private StormRain() {
	}

	/** Whether the camera is in a storm whose rain this draws. */
	static boolean active(ClientLevel level, double camX, double camY, double camZ) {
		if(!AwmClientConfig.STORM_RAIN.get() || !AwmClientConfig.STORM_RAIN_WITH_SHADER_PACKS.get() && ShaderPacks.inUse())
			return false;
		StormKind kind = ClientWeatherState.get().stormKind();
		if(kind == null || kind != StormKind.HURRICANE && kind.scale() < 1)
			return false; // Showers keep vanilla's rain.
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
		boolean hurricaneRain = weather.stormKind() == StormKind.HURRICANE;
		int category = Mth.clamp(weather.category(), 0, 5);
		// How severe it is, 1 to 5: a hurricane counts as one category up.
		int severity = hurricaneRain ? Math.min(5, category + 1) : Math.max(1, category);
		double slant = Math.min(hurricaneRain ? HURRICANE_SLANT * (0.5 + 0.1 * category) : MAX_SLANT,
				speed / FALL_SPEED);
		double dirX = speed > 1e-6 ? windX / speed : 0;
		double dirZ = speed > 1e-6 ? windZ / speed : 0;
		int near = Minecraft.useFancyGraphics() ? 10 : 5;
		int radius = Minecraft.useFancyGraphics() ? FAR + 2 * severity : near;
		float baseShade = SHADE_BY_SEVERITY[severity] * AwmClientConfig.RAIN_BRIGHTNESS.get().floatValue();
		float thickChance = 0.04f + 0.05f * severity;
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
				boolean distant = distSqr > near * near;
				// Far columns only every other block (every one in the worst storms): fainter rain behind the near curtain.
				if(distant && severity < 4 && ((x + z) & 1) != 0)
					continue;
				pos.set(x, cy, z);
				if(level.getBiome(pos).value().getPrecipitationAt(pos) != Biome.Precipitation.RAIN)
					continue;
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				int reach = distant ? radius : near;
				int bottom = Math.max(cy - reach, ground);
				int top = Math.max(cy + reach, ground);
				if(bottom >= top)
					continue;
				double height = top - bottom;
				// Slanted downwind: the top sits upwind of the bottom, centred on the column.
				double shiftX = dirX * slant * height / 2;
				double shiftZ = dirZ * slant * height / 2;
				double dist = Math.max(0.01, Math.sqrt(distSqr));
				float fade = distant ? (float)(0.55 * (1 - (dist - near) / (radius - near)))
						: (float)((1 - distSqr / (near * near)) * 0.5 + 0.5);
				float alpha = fade * rain;
				if(alpha < 0.02f)
					continue;
				pos.set(x, Math.max(cy, ground), z);
				int light = LevelRenderer.getLightColor(level, pos);
				long hash = x * 3129871L ^ z * 116129781L;
				for(int sheet = 0; sheet < (distant ? 1 : SHEETS); sheet++) {
					long h = hash * 31 + sheet * 0x9E3779B9L;
					// Now and then a thick, dark sheet: heavier bands of rain, more of them the worse the storm.
					boolean thick = ((h >>> 40 & 1023) / 1023f) < thickChance;
					// Each sheet sits a little off the column's centre, so the rain is not one flat wall.
					double px = dx + ((h >>> 20 & 15) / 15.0 - 0.5) * 0.6;
					double pz = dz + ((h >>> 24 & 15) / 15.0 - 0.5) * 0.6;
					double pd = Math.max(0.01, Math.hypot(px, pz));
					// Faces the camera.
					float half = thick ? 0.9f : 0.5f;
					float sideX = (float)(-pz / pd * half);
					float sideZ = (float)(px / pd * half);
					float bx = (float)(px + shiftX), bz = (float)(pz + shiftZ);
					float tx = (float)(px - shiftX), tz = (float)(pz - shiftZ);
					float y0 = (float)(bottom - camY), y1 = (float)(top - camY);
					float offset = (h >>> 8 & 31) / 32f;
					float fall = (float)(0.11 + 0.025 * (h >>> 16 & 3) + 0.03 * sheet);
					// v runs up the sheet, so it grows with time for the streaks to fall.
					float scroll = (float)(time * fall % 32 - offset);
					float stretch = (float)(0.25 / Math.sqrt(1 + slant * slant)) * (sheet == 0 ? 1 : 0.7f);
					float v0 = bottom * stretch + scroll;
					float v1 = top * stretch + scroll;
					// Grey with a little variation: some sheets bluer, some browner, some lighter.
					float shade = baseShade * (thick ? 0.8f : 1) * (0.88f + (h >>> 28 & 7) / 7f * 0.24f);
					float r = Math.min(1, shade * (1 + ((h >>> 32 & 7) - 3.5f) * 0.012f));
					float b = Math.min(1, shade * (1.04f + ((h >>> 36 & 7) - 3.5f) * 0.015f));
					float a2 = Math.min(1, alpha * (sheet == 0 ? 1 : 0.65f) * (thick ? 1.25f : 1));
					buffer.addVertex(bx - sideX, y0, bz - sideZ).setUv(0, v0).setColor(r, shade, b, a2).setLight(light);
					buffer.addVertex(bx + sideX, y0, bz + sideZ).setUv(1, v0).setColor(r, shade, b, a2).setLight(light);
					buffer.addVertex(tx + sideX, y1, tz + sideZ).setUv(1, v1).setColor(r, shade, b, a2).setLight(light);
					buffer.addVertex(tx - sideX, y1, tz - sideZ).setUv(0, v1).setColor(r, shade, b, a2).setLight(light);
				}
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
