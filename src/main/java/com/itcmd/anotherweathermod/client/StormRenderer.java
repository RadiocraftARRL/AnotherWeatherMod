package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmClientConfig;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.network.ClientWeatherState;
import com.itcmd.anotherweathermod.network.NearbyStormsPayload;
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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * Storms seen from outside: a dark cloud deck under the vanilla clouds over each storm, and a curtain of rain hanging
 * from its edge, drawn on the side facing the camera. Inside a storm the vanilla rain (driven by the rain level the
 * server sends) does the rest.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID, value = Dist.CLIENT)
public final class StormRenderer {

	private static final ResourceLocation CLOUDS = ResourceLocation.withDefaultNamespace("textures/environment/clouds.png");
	private static final ResourceLocation RAIN = ResourceLocation.withDefaultNamespace("textures/environment/rain.png");
	private static final ResourceLocation SNOW = ResourceLocation.withDefaultNamespace("textures/environment/snow.png");

	/** Segments around a cloud deck. */
	static final int DECK_SEGMENTS = 40;
	/** Segments along a rain wall. */
	static final int WALL_SEGMENTS = RainWall.SEGMENTS;
	/** Longest stretch of rain wall drawn, blocks along the edge. */
	static final double WALL_LENGTH = 700;
	/** Rain walls show to this far outside the edge (and fade away as the camera walks in, see {@link RainWall}). */
	static final double WALL_OUTSIDE = 900;

	/** How much the rain walls show, 0 indoors to 1 outdoors, eased (see {@link RainWall#outdoors}). */
	private static float wallsOutdoors = 1;
	private static long lastFrame;

	private StormRenderer() {
	}

	/** Steps {@link #wallsOutdoors} for this frame, from where the camera is. */
	private static float wallsOutdoors(ClientLevel level, Vec3 camera) {
		int open = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				Mth.floor(camera.x), Mth.floor(camera.z));
		boolean outdoors = RainWall.outdoors(camera.y, open);
		long now = System.nanoTime();
		float seconds = lastFrame == 0 ? 1e3f : Math.min(0.25f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		wallsOutdoors = RainWall.stepOutdoors(wallsOutdoors, outdoors, seconds);
		return wallsOutdoors;
	}

	@SubscribeEvent
	public static void onRenderStage(RenderLevelStageEvent event) {
		if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER)
			return;
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		NearbyStormsPayload payload = ClientWeatherState.storms();
		boolean clouds = AwmClientConfig.STORM_CLOUDS.get();
		boolean walls = AwmClientConfig.RAIN_WALLS.get();
		if(level == null || payload.storms().isEmpty() || !clouds && !walls && !AwmClientConfig.TORNADO_FUNNELS.get())
			return;
		float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
		double age = level.getGameTime() - payload.gameTime() + partial;
		Vec3 camera = event.getCamera().getPosition();
		float cloudY = level.effects().getCloudHeight();
		double viewDistance = mc.options.getEffectiveRenderDistance() * 16.0;

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

		// Rain walls are a view of the sky outside: indoors (under a roof or in a cave) they are hidden.
		float outdoors = walls ? wallsOutdoors(level, camera) : 0;
		// Far storms fade into merged masses (StormLod): each drawn in full only as much as its detail.
		StormLod.View lod = DistantStorms.view(payload, level.getGameTime() + partial, camera.x, camera.z);
		boolean decks = clouds && !Float.isNaN(cloudY) && !CloudRenderer.active(); // The mod's clouds draw storms themselves.
		float top = Float.isNaN(cloudY) ? 192 : cloudY;
		List<NearbyStormsPayload.Storm> storms = payload.storms();
		// Where each storm's rain is now (centre x, z, rain edge): a wall hides where it runs inside another's rain.
		double[][] rain = new double[storms.size()][];
		for(int i = 0; i < storms.size(); i++) {
			NearbyStormsPayload.Storm s = storms.get(i);
			rain[i] = new double[] { s.x() + s.velocityX() * age, s.z() + s.velocityZ() * age, s.rainEdge() };
		}
		for(int i = 0; i < storms.size(); i++) {
			NearbyStormsPayload.Storm storm = storms.get(i);
			float detail = (float)lod.detail(i);
			if(detail < 0.01f)
				continue;
			double sx = storm.x() + storm.velocityX() * age;
			double sz = storm.z() + storm.velocityZ() * age;
			if(decks)
				deck(storm, sx, sz, cloudY, camera, viewDistance, level.getGameTime() + partial, detail);
			if(walls && outdoors > 0.01f)
				wall(level, storm, sx, sz, level.getSeaLevel() - 8, top, camera, viewDistance,
					level.getGameTime() + partial, outdoors * detail, rain, i);
		}
		for(StormLod.Mass mass : lod.masses()) {
			if(mass.alpha() < 0.01f)
				continue;
			if(decks)
				massDeck(mass, cloudY, camera, viewDistance, level.getGameTime() + partial);
			if(walls && outdoors > 0.01f)
				massWall(level, mass, level.getSeaLevel() - 8, top, camera, viewDistance, level.getGameTime() + partial,
						outdoors);
		}
		if(AwmClientConfig.TORNADO_FUNNELS.get())
			for(TornadoTracker.Funnel funnel : TornadoTracker.funnels(partial))
				funnel(level, funnel, Math.max(20, (Float.isNaN(cloudY) ? 192 : cloudY) - funnel.ground()), Look.TORNADO,
						camera, level.getGameTime() + partial);

		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
	}

	/** Darkness of the deck: thunderstorms and worse are much darker than showers. */
	private static float deckAlpha(NearbyStormsPayload.Storm storm) {
		return storm.intensity() * (storm.kind() == StormKind.SHOWER ? 0.55f : 0.85f);
	}

	/** A dark disc of cloud texture at cloud height, solid in the middle and fading to nothing at the edge. */
	private static void deck(NearbyStormsPayload.Storm storm, double sx, double sz, float cloudY, Vec3 camera,
			double viewDistance, double time, float detail) {
		double radius = storm.radius();
		double dist = Math.hypot(sx - camera.x, sz - camera.z) - radius;
		float fade = (float)Mth.clamp(1 - (dist - viewDistance) / viewDistance, 0, 1);
		float alpha = deckAlpha(storm) * fade * detail;
		if(alpha < 0.02f)
			return;
		float shade = AwmClientConfig.bright(storm.kind() == StormKind.SHOWER ? 0.45f : 0.28f, AwmClientConfig.CLOUD_BRIGHTNESS);
		RenderSystem.setShaderTexture(0, CLOUDS);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		double[] rings = { 0, radius * 0.55, radius * 0.85, radius };
		float[] ringAlpha = { alpha, alpha, alpha * 0.6f, 0 };
		double scroll = time * 0.03 / 3072;
		for(int layer = 0; layer < 2; layer++) {
			float y = (float)(cloudY - 3 - layer * 6 - camera.y);
			for(int ring = 0; ring < rings.length - 1; ring++)
				for(int i = 0; i < DECK_SEGMENTS; i++) {
					double a0 = Math.PI * 2 * i / DECK_SEGMENTS;
					double a1 = Math.PI * 2 * (i + 1) / DECK_SEGMENTS;
					deckVertex(buffer, sx, sz, rings[ring], a0, y, camera, scroll, shade, ringAlpha[ring], layer);
					deckVertex(buffer, sx, sz, rings[ring + 1], a0, y, camera, scroll, shade, ringAlpha[ring + 1], layer);
					deckVertex(buffer, sx, sz, rings[ring + 1], a1, y, camera, scroll, shade, ringAlpha[ring + 1], layer);
					deckVertex(buffer, sx, sz, rings[ring], a1, y, camera, scroll, shade, ringAlpha[ring], layer);
				}
		}
		draw(buffer);
	}

	private static void deckVertex(BufferBuilder buffer, double sx, double sz, double r, double angle, float y,
			Vec3 camera, double scroll, float shade, float alpha, int layer) {
		double x = sx + Math.sin(angle) * r;
		double z = sz - Math.cos(angle) * r;
		// The cloud texture at vanilla's scale (12 blocks a pixel, 256 pixels), offset per layer.
		float u = (float)(x / 3072 + scroll + layer * 0.37);
		float v = (float)(z / 3072 + layer * 0.21);
		buffer.addVertex((float)(x - camera.x), y, (float)(z - camera.z)).setUv(u, v).setColor(shade, shade, shade * 1.08f,
				alpha);
	}

	/**
	 * A shaft of falling rain from the cloud base to the ground along the edge of the storm's rain (not its radius:
	 * the rain thins out to nothing well before that, see {@link NearbyStormsPayload.Storm#rainEdge}), on the side
	 * facing the camera, so you meet the wall when the rain starts. It is {@link RainWall#LAYERS} sheets of streaks set
	 * one behind another into the rain, each faint, bowed and leaning its own way ({@link RainWall#wobble}) and falling at
	 * its own speed, thick under the cloud and thinning toward the ground, tinted by the storm's darkness (white for snow,
	 * which falls slowly and drifts). No haze sheet (2026-11-13). It fades away as the camera walks into the rain.
	 */
	private static void wall(ClientLevel level, NearbyStormsPayload.Storm storm, double sx, double sz, int bottom, float top,
			Vec3 camera, double viewDistance, double time, float outdoors, double[][] rain, int self) {
		double edge = storm.rainEdge();
		if(edge <= 0)
			return;
		double dx = camera.x - sx;
		double dz = camera.z - sz;
		double fromEdge = Math.hypot(dx, dz) - edge;
		double farthest = Math.min(WALL_OUTSIDE, viewDistance * 2);
		if(fromEdge < -RainWall.ENTER_FADE || fromEdge > farthest)
			return;
		float strength = storm.intensity() * (storm.kind() == StormKind.SHOWER ? 0.7f : 1f)
				* (storm.kind() == StormKind.HURRICANE ? 0.8f : 1f) * outdoors;
		float shaft = strength * RainWall.distanceFade(fromEdge, farthest);
		if(shaft < 0.02f)
			return;

		double facing = Math.atan2(dx, -dz); // Bearing from the centre to the camera.
		double span = Math.min(Math.PI, WALL_LENGTH / 2 / edge);
		// Snow if the storm brings snow, or the ground at the wall would turn its rain to snow.
		int ground = CloudRenderer.ground(level).precipitation(sx + Math.sin(facing) * edge, sz - Math.cos(facing) * edge);
		boolean snow = CloudCover.localPrecipitation(CloudCover.precipitationIndex(storm.precipitation()), ground) == 1;
		float darkness = CloudCover.DARKNESS[CloudCover.kindIndex(storm.kind())];
		float bright = AwmClientConfig.bright(1f, AwmClientConfig.RAIN_BRIGHTNESS);

		RenderSystem.setShaderTexture(0, snow ? SNOW : RAIN);
		BufferBuilder streaks = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		for(int layer = RainWall.LAYERS - 1; layer >= 0; layer--) { // Back to front.
			double r = edge - RainWall.DEPTH[layer];
			if(r < 8)
				continue;
			float[] tint = RainWall.tint(darkness, snow, layer);
			float alpha = shaft * RainWall.LAYER_ALPHA[layer];
			double v = RainWall.scroll(time, snow, storm.id(), layer);
			double u = RainWall.offset(storm.id(), layer) + RainWall.drift(time, snow, layer);
			sheet(streaks, sx, sz, r, facing, span, bottom, top, camera, tint, bright, alpha, u, v, storm.id(), layer, rain, self);
		}
		draw(streaks);
	}

	/**
	 * One sheet of a rain wall at {@code r} from the storm's centre, {@code span} radians either side of
	 * {@code facing}, in {@link RainWall#ROWS} rows from {@code bottom} to {@code top}, fading toward both ends: streaks
	 * scrolled by {@code u} and {@code v}, every corner pushed in or out by the sheet's {@link RainWall#wobble}.
	 */
	private static void sheet(BufferBuilder buffer, double sx, double sz, double r, double facing, double span, int bottom,
			float top, Vec3 camera, float[] tint, float bright, float alpha, double u, double v, long storm, int layer,
			double[][] rain, int self) {
		float red = Math.min(1, tint[0] * bright), green = Math.min(1, tint[1] * bright), blue = Math.min(1, tint[2] * bright);
		for(int i = 0; i < WALL_SEGMENTS; i++) {
			double a0 = facing - span + 2 * span * i / WALL_SEGMENTS;
			double a1 = facing - span + 2 * span * (i + 1) / WALL_SEGMENTS;
			float e0 = (float)Math.min(1, (1 - Math.abs(2.0 * i / WALL_SEGMENTS - 1)) * 3)
					* RainWall.outsideOthers(sx + Math.sin(a0) * r, sz - Math.cos(a0) * r, rain, self);
			float e1 = (float)Math.min(1, (1 - Math.abs(2.0 * (i + 1) / WALL_SEGMENTS - 1)) * 3)
					* RainWall.outsideOthers(sx + Math.sin(a1) * r, sz - Math.cos(a1) * r, rain, self);
			if(e0 <= 0 && e1 <= 0)
				continue; // Inside another storm's rain: storms that touch show one curtain round them all.
			double s0 = Math.sin(a0), c0 = Math.cos(a0), s1 = Math.sin(a1), c1 = Math.cos(a1);
			float u0 = (float)(a0 * r / RainWall.U_BLOCKS + u), u1 = (float)(a1 * r / RainWall.U_BLOCKS + u);
			for(int k = 0; k < RainWall.ROWS; k++) {
				double f0 = k / (double)RainWall.ROWS, f1 = (k + 1) / (double)RainWall.ROWS;
				float y0 = (float)(bottom + (top - 4 - bottom) * f0 - camera.y);
				float y1 = (float)(bottom + (top - 4 - bottom) * f1 - camera.y);
				float g0 = RainWall.vertical(f0), g1 = RainWall.vertical(f1);
				// The sheet's corners, each bowed in or out along the wall and with height.
				double r00 = r + RainWall.wobble(storm, layer, a0 * r, f0), r10 = r + RainWall.wobble(storm, layer, a1 * r, f0);
				double r11 = r + RainWall.wobble(storm, layer, a1 * r, f1), r01 = r + RainWall.wobble(storm, layer, a0 * r, f1);
				float v0 = (float)(y0 / RainWall.V_BLOCKS + v), v1 = (float)(y1 / RainWall.V_BLOCKS + v);
				buffer.addVertex((float)(sx + s0 * r00 - camera.x), y0, (float)(sz - c0 * r00 - camera.z)).setUv(u0, v0)
						.setColor(red, green, blue, alpha * e0 * g0);
				buffer.addVertex((float)(sx + s1 * r10 - camera.x), y0, (float)(sz - c1 * r10 - camera.z)).setUv(u1, v0)
						.setColor(red, green, blue, alpha * e1 * g0);
				buffer.addVertex((float)(sx + s1 * r11 - camera.x), y1, (float)(sz - c1 * r11 - camera.z)).setUv(u1, v1)
						.setColor(red, green, blue, alpha * e1 * g1);
				buffer.addVertex((float)(sx + s0 * r01 - camera.x), y1, (float)(sz - c0 * r01 - camera.z)).setUv(u0, v1)
						.setColor(red, green, blue, alpha * e0 * g1);
			}
		}
	}

	/**
	 * How a funnel is drawn. A tornado's is near black; a waterspout's and a dust devil's are paler ({@code lighten} of
	 * the way to white) and see-through ({@code alpha}), tinted by {@code r}, {@code g} and {@code b}.
	 */
	public record Look(float lighten, float r, float g, float b, float alpha) {

		public static final Look TORNADO = new Look(0, 1, 1.02f, 1.08f, 1);

		float shade(float base) {
			return base + (1 - base) * lighten;
		}
	}

	/** Rings up a tornado funnel, and segments round it. */
	static final int FUNNEL_RINGS = 22;
	static final int FUNNEL_SEGMENTS = 24;
	/** The funnel's cloud shells round its solid core, inside out: radius, spin speed and opacity. */
	static final double[] SHELL_RADIUS = { 1.0, 1.25 };
	static final double[] SHELL_SPIN = { 1.0, 0.55 };
	static final float[] SHELL_ALPHA = { 0.8f, 0.5f };
	/** The solid core's radius, as a share of the funnel's. */
	static final double CORE = 0.8;

	/** A funnel's radius at height {@code h} (0 at its foot, 1 at the cloud) for a core radius {@code rt}. */
	public static double funnelRadius(double rt, double h) {
		return rt * (0.4 + 2.6 * Math.pow(h, 2.2));
	}

	/** How far a funnel's column has snaked off its foot at height {@code h}, x and z. */
	public static double[] funnelSway(double rt, double h, double time) {
		return new double[] { rt * 0.6 * Math.sin(time * 0.013 + h * 3.1) * h, rt * 0.6 * Math.cos(time * 0.011 + h * 2.7) * h };
	}

	/**
	 * Height above the ground of the point {@code h} up a funnel's shape (0 its tip, 1 the cloud), for a funnel
	 * {@code height} tall from the ground that reaches {@code reach} of the way down.
	 */
	public static double funnelHeight(double h, double height, float reach) {
		return height * (1 - reach * (1 - h));
	}

	/** How much of a funnel's foot is on the ground: its debris cloud shows only as it touches down. */
	static float touchdown(TornadoTracker.Funnel funnel) {
		return Mth.clamp((funnel.reach() - 0.85f) / 0.15f, 0, 1);
	}

	/**
	 * A tornado: a solid, dark core wrapped in grey shells of cloud, lumpy and spinning at different speeds, from the
	 * storm's base down toward the ground, thin at the foot and flaring at the top, the column snaking. A new one
	 * lowers out of the cloud as a thin rope and widens as it comes down. Once down, its foot rides the ground and a
	 * squat cloud of dust and debris churns round it. {@link TornadoCloudParticle}s whirl round it on top of this.
	 */
	static void funnel(ClientLevel level, TornadoTracker.Funnel funnel, double height, Look look, Vec3 camera,
			double time) {
		if(funnel.strength() <= 0.01f || funnel.reach() <= 0.01f)
			return;
		// The core: solid, so nothing shows through, but mottled so it reads as cloud.
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder core = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		for(int ring = 0; ring < FUNNEL_RINGS; ring++) {
			double h0 = ring / (double)FUNNEL_RINGS, h1 = (ring + 1) / (double)FUNNEL_RINGS;
			for(int i = 0; i < FUNNEL_SEGMENTS; i++) {
				double a0 = Math.PI * 2 * i / FUNNEL_SEGMENTS, a1 = Math.PI * 2 * (i + 1) / FUNNEL_SEGMENTS;
				coreVertex(core, level, funnel, height, look, h0, a0, time, camera);
				coreVertex(core, level, funnel, height, look, h1, a0, time, camera);
				coreVertex(core, level, funnel, height, look, h1, a1, time, camera);
				coreVertex(core, level, funnel, height, look, h0, a1, time, camera);
			}
		}
		draw(core);
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, CLOUDS);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		for(int shell = SHELL_RADIUS.length - 1; shell >= 0; shell--)
			for(int ring = 0; ring < FUNNEL_RINGS; ring++) {
				double h0 = ring / (double)FUNNEL_RINGS, h1 = (ring + 1) / (double)FUNNEL_RINGS;
				for(int i = 0; i < FUNNEL_SEGMENTS; i++) {
					double a0 = Math.PI * 2 * i / FUNNEL_SEGMENTS, a1 = Math.PI * 2 * (i + 1) / FUNNEL_SEGMENTS;
					funnelVertex(buffer, level, funnel, height, look, shell, h0, a0, time, camera);
					funnelVertex(buffer, level, funnel, height, look, shell, h1, a0, time, camera);
					funnelVertex(buffer, level, funnel, height, look, shell, h1, a1, time, camera);
					funnelVertex(buffer, level, funnel, height, look, shell, h0, a1, time, camera);
				}
			}
		// The debris cloud: a squat, churning, dark brown-grey ring round the foot, once it is down.
		if(touchdown(funnel) > 0)
			for(int layer = 0; layer < 2; layer++)
				for(int i = 0; i < FUNNEL_SEGMENTS; i++) {
					double a0 = Math.PI * 2 * i / FUNNEL_SEGMENTS, a1 = Math.PI * 2 * (i + 1) / FUNNEL_SEGMENTS;
					skirtVertex(buffer, level, funnel, look, layer, 0, a0, time, camera);
					skirtVertex(buffer, level, funnel, look, layer, 1, a0, time, camera);
					skirtVertex(buffer, level, funnel, look, layer, 1, a1, time, camera);
					skirtVertex(buffer, level, funnel, look, layer, 0, a1, time, camera);
				}
		draw(buffer);
	}

	/** Where on a funnel the point {@code h} up and {@code angle} round, {@code scale} times its radius out, is. */
	private static Vec3 funnelPoint(ClientLevel level, TornadoTracker.Funnel funnel, double height, double scale,
			double h, double angle, double time) {
		double rt = funnel.coreRadius();
		double radius = funnelRadius(rt, h) * scale;
		double[] sway = funnelSway(rt, h, time);
		double x = funnel.x() + sway[0] + Math.sin(angle) * radius;
		double z = funnel.z() + sway[1] - Math.cos(angle) * radius;
		// Once down, the foot follows the ground under it; above, the funnel rises from the smoothed ground.
		double y = h == 0 && funnel.reach() >= 0.99f ? footY(level, x, z, funnel.ground()) - 1
				: funnel.ground() + funnelHeight(h, height, funnel.reach());
		return new Vec3(x, y, z);
	}

	private static void coreVertex(BufferBuilder buffer, ClientLevel level, TornadoTracker.Funnel funnel,
			double height, Look look, double h, double angle, double time, Vec3 camera) {
		double spin = time * 0.05 * 1.7;
		double lump = 1 + 0.15 * Math.sin(angle * 3 + h * 9 - spin * 2) + 0.1 * Math.sin(angle * 5 - h * 14 + spin * 1.3);
		Vec3 at = funnelPoint(level, funnel, height, CORE * lump, h, angle, time);
		// Mottled near-black to dark grey, banded as it turns.
		float mottle = (float)(0.5 + 0.25 * Math.sin(angle * 4 + h * 20 - spin * 2.4) + 0.25 * Math.sin(angle * 7 - h * 11 + spin));
		float shade = AwmClientConfig.bright(look.shade(0.05f + 0.1f * mottle + 0.05f * (float)h),
				AwmClientConfig.TORNADO_BRIGHTNESS);
		float alpha = Math.min(1, funnel.strength() * 2) * (float)(h < 0.9 ? 1 : (1 - h) / 0.1) * look.alpha();
		buffer.addVertex((float)(at.x - camera.x), (float)(at.y - camera.y), (float)(at.z - camera.z))
				.setColor(Math.min(1, shade * look.r()), Math.min(1, shade * look.g()), Math.min(1, shade * look.b()), alpha);
	}

	private static void funnelVertex(BufferBuilder buffer, ClientLevel level, TornadoTracker.Funnel funnel,
			double height, Look look, int shell, double h, double angle, double time, Vec3 camera) {
		double spin = time * 0.05 * SHELL_SPIN[shell];
		// Lumpy: bulges that wind round and up the funnel as it turns, different on each shell.
		double lump = 1 + 0.2 * Math.sin(angle * 3 + h * 9 - spin * 2 + shell)
				+ 0.12 * Math.sin(angle * 5 - h * 14 + spin * 1.3 + shell * 2.1);
		Vec3 at = funnelPoint(level, funnel, height, SHELL_RADIUS[shell] * lump, h, angle, time);
		float u = (float)(angle / (Math.PI * 2) * 2 + spin * (1.6 - h));
		float v = (float)(h * 2.2 + shell * 0.31 - time * 0.004 * SHELL_SPIN[shell]);
		float shade = AwmClientConfig.bright(look.shade((0.2f + 0.12f * (float)h) * (1 + 0.25f * shell)),
				AwmClientConfig.TORNADO_BRIGHTNESS);
		float alpha = funnel.strength() * SHELL_ALPHA[shell] * (float)(h < 0.88 ? 1 : (1 - h) / 0.12) * look.alpha();
		buffer.addVertex((float)(at.x - camera.x), (float)(at.y - camera.y), (float)(at.z - camera.z)).setUv(u, v)
				.setColor(Math.min(1, shade * look.r()), Math.min(1, shade * look.g()), Math.min(1, shade * look.b()), alpha);
	}

	/** The ground under ({@code x}, {@code z}), leaves left out, but never far from the smoothed foot. */
	private static double footY(ClientLevel level, double x, double z, double ground) {
		BlockPos at = BlockPos.containing(x, 0, z);
		if(!level.isLoaded(at))
			return ground;
		double here = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				at.getX(), at.getZ());
		return Mth.clamp(here, ground - 4, ground + 4);
	}

	private static void skirtVertex(BufferBuilder buffer, ClientLevel level, TornadoTracker.Funnel funnel, Look look,
			int layer, int top, double angle, double time, Vec3 camera) {
		double rt = funnel.coreRadius();
		double spin = time * 0.06 * (layer == 0 ? 1.4 : 0.9);
		double lump = 1 + 0.25 * Math.sin(angle * 4 + spin * 2 + layer);
		double radius = rt * (top == 0 ? 1.1 + 0.3 * layer : 1.7 + 0.5 * layer) * lump;
		double x = funnel.x() + Math.sin(angle) * radius;
		double z = funnel.z() - Math.cos(angle) * radius;
		double y = footY(level, x, z, funnel.ground()) + (top == 0 ? -1 : (6 + 5 * layer) * (look == Look.TORNADO ? 1 : Math.min(1, rt / 8)));
		float u = (float)(angle / (Math.PI * 2) * 3 + spin * 1.6 + layer * 0.5);
		float shade = AwmClientConfig.bright(look.shade(0.2f + 0.04f * layer), AwmClientConfig.TORNADO_BRIGHTNESS);
		float alpha = funnel.strength() * touchdown(funnel) * (top == 0 ? 0.7f : 0) * (layer == 0 ? 1 : 0.6f) * look.alpha();
		// A tornado's debris is brown-grey; a waterspout's spray and a dust devil's dust take the funnel's tint.
		float r = look == Look.TORNADO ? 1.1f : look.r(), g = look == Look.TORNADO ? 1 : look.g();
		float b = look == Look.TORNADO ? 0.92f : look.b();
		buffer.addVertex((float)(x - camera.x), (float)(y - camera.y), (float)(z - camera.z))
				.setUv(u, (float)(top * 0.4 + layer * 0.2))
				.setColor(Math.min(1, shade * r), Math.min(1, shade * g), Math.min(1, shade * b), alpha);
	}

	/**
	 * A merged mass's dark deck (vanilla clouds only): an ellipse round its line, solid in the middle and fading to its
	 * edge.
	 */
	private static void massDeck(StormLod.Mass m, float cloudY, Vec3 camera, double viewDistance, double time) {
		double dist = Math.max(0, m.distance(camera.x, camera.z) - m.width());
		float fade = (float)Mth.clamp(1 - (dist - viewDistance) / viewDistance, 0, 1);
		float alpha = m.intensity() * 0.8f * fade * m.alpha();
		if(alpha < 0.02f)
			return;
		float shade = AwmClientConfig.bright(0.3f, AwmClientConfig.CLOUD_BRIGHTNESS);
		RenderSystem.setShaderTexture(0, CLOUDS);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		double a = m.halfLength() + m.width(), b = m.width();
		double px = -m.uz(), pz = m.ux();
		float y = (float)(cloudY - 4 - camera.y);
		double scroll = time * 0.03 / 3072;
		double[] rings = { 0, 0.7, 1 };
		float[] ringAlpha = { alpha, alpha * 0.8f, 0 };
		int n = RainWall.MASS_SEGMENTS * 2;
		for(int ring = 0; ring < rings.length - 1; ring++)
			for(int i = 0; i < n; i++) {
				double t0 = Math.PI * 2 * i / n, t1 = Math.PI * 2 * (i + 1) / n;
				massDeckVertex(buffer, m, a * rings[ring], b * rings[ring], t0, px, pz, y, camera, scroll, shade, ringAlpha[ring]);
				massDeckVertex(buffer, m, a * rings[ring + 1], b * rings[ring + 1], t0, px, pz, y, camera, scroll, shade, ringAlpha[ring + 1]);
				massDeckVertex(buffer, m, a * rings[ring + 1], b * rings[ring + 1], t1, px, pz, y, camera, scroll, shade, ringAlpha[ring + 1]);
				massDeckVertex(buffer, m, a * rings[ring], b * rings[ring], t1, px, pz, y, camera, scroll, shade, ringAlpha[ring]);
			}
		draw(buffer);
	}

	private static void massDeckVertex(BufferBuilder buffer, StormLod.Mass m, double a, double b, double t, double px,
			double pz, float y, Vec3 camera, double scroll, float shade, float alpha) {
		double x = m.x() + m.ux() * a * Math.cos(t) + px * b * Math.sin(t);
		double z = m.z() + m.uz() * a * Math.cos(t) + pz * b * Math.sin(t);
		buffer.addVertex((float)(x - camera.x), y, (float)(z - camera.z)).setUv((float)(x / 3072 + scroll), (float)(z / 3072))
				.setColor(shade, shade, shade * 1.08f, alpha);
	}

	/**
	 * A merged mass's rain: {@link RainWall#MASS_LAYERS} faint sheets of streaks, one behind the other, across the side of the mass facing the camera,
	 * {@link RainWall#MASS_SEGMENTS} by {@link RainWall#MASS_ROWS}, in place of a full wall for each of its storms.
	 */
	private static void massWall(ClientLevel level, StormLod.Mass m, int bottom, float top, Vec3 camera,
			double viewDistance, double time, float outdoors) {
		double dx = camera.x - m.x(), dz = camera.z - m.z();
		double dist = Math.hypot(dx, dz);
		if(dist < 1)
			return;
		double nx = dx / dist, nz = dz / dist; // Toward the camera.
		double edge = m.support(nx, nz) * 0.85; // Near where the rain stops.
		double fromEdge = dist - edge;
		double farthest = Math.min(WALL_OUTSIDE, viewDistance * 2);
		if(fromEdge < 0 || fromEdge > farthest)
			return;
		float strength = m.intensity() * m.alpha() * outdoors;
		float shaft = strength * RainWall.distanceFade(fromEdge, farthest);
		if(shaft < 0.02f)
			return;
		double px = -nz, pz = nx; // Across the line of sight.
		double half = Math.min(WALL_LENGTH, m.support(px, pz) * 0.9);
		double cx = m.x() + nx * edge, cz = m.z() + nz * edge;
		int ground = CloudRenderer.ground(level).precipitation(cx, cz);
		boolean snow = CloudCover.localPrecipitation(m.precipitation(), ground) == 1;
		float darkness = CloudCover.DARKNESS[Mth.clamp(m.kind(), 0, CloudCover.DARKNESS.length - 1)];
		float bright = AwmClientConfig.bright(1f, AwmClientConfig.RAIN_BRIGHTNESS);
		float[] tint = RainWall.tint(darkness, snow, 0);

		RenderSystem.setShaderTexture(0, snow ? SNOW : RAIN);
		BufferBuilder streaks = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		for(int layer = RainWall.MASS_LAYERS - 1; layer >= 0; layer--) { // Back to front.
			double back = RainWall.DEPTH[layer * 2];
			flatSheet(streaks, cx - nx * back, cz - nz * back, px, pz, half, bottom, top, camera, tint, bright,
					shaft * RainWall.LAYER_ALPHA[layer], RainWall.offset(m.key(), layer), RainWall.scroll(time, snow, m.key(), layer));
		}
		draw(streaks);
	}

	/** A straight sheet {@code half} either side of ({@code cx}, {@code cz}) along ({@code px}, {@code pz}), fading at its ends. */
	private static void flatSheet(BufferBuilder buffer, double cx, double cz, double px, double pz, double half, int bottom,
			float top, Vec3 camera, float[] tint, float bright, float alpha, double u, double v) {
		float red = Math.min(1, tint[0] * bright), green = Math.min(1, tint[1] * bright), blue = Math.min(1, tint[2] * bright);
		int segments = RainWall.MASS_SEGMENTS, rows = RainWall.MASS_ROWS;
		for(int i = 0; i < segments; i++) {
			double s0 = -half + 2 * half * i / segments, s1 = -half + 2 * half * (i + 1) / segments;
			float e0 = (float)Math.min(1, (1 - Math.abs(2.0 * i / segments - 1)) * 2.5);
			float e1 = (float)Math.min(1, (1 - Math.abs(2.0 * (i + 1) / segments - 1)) * 2.5);
			float x0 = (float)(cx + px * s0 - camera.x), z0 = (float)(cz + pz * s0 - camera.z);
			float x1 = (float)(cx + px * s1 - camera.x), z1 = (float)(cz + pz * s1 - camera.z);
			float u0 = (float)(s0 / RainWall.U_BLOCKS + u), u1 = (float)(s1 / RainWall.U_BLOCKS + u);
			for(int k = 0; k < rows; k++) {
				double f0 = k / (double)rows, f1 = (k + 1) / (double)rows;
				float y0 = (float)(bottom + (top - 4 - bottom) * f0 - camera.y);
				float y1 = (float)(bottom + (top - 4 - bottom) * f1 - camera.y);
				float g0 = RainWall.vertical(f0), g1 = RainWall.vertical(f1);
				float v0 = (float)(y0 / RainWall.V_BLOCKS + v), v1 = (float)(y1 / RainWall.V_BLOCKS + v);
				buffer.addVertex(x0, y0, z0).setUv(u0, v0).setColor(red, green, blue, alpha * e0 * g0);
				buffer.addVertex(x1, y0, z1).setUv(u1, v0).setColor(red, green, blue, alpha * e1 * g0);
				buffer.addVertex(x1, y1, z1).setUv(u1, v1).setColor(red, green, blue, alpha * e1 * g1);
				buffer.addVertex(x0, y1, z0).setUv(u0, v1).setColor(red, green, blue, alpha * e0 * g1);
			}
		}
	}

	private static void draw(BufferBuilder buffer) {
		MeshData mesh = buffer.build();
		if(mesh != null)
			BufferUploader.drawWithShader(mesh);
	}
}
