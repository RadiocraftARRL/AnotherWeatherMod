package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AwmClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A ragged puff of dark cloud caught in a tornado: it whirls counter-clockwise round the funnel and climbs, faster
 * near the ground, so the funnel reads as a churning mass and not a smooth cone. It follows its tornado as the
 * tornado moves ({@link TornadoTracker}), and thins out when the tornado lifts. Added straight to the particle engine.
 */
public final class TornadoCloudParticle extends TextureSheetParticle {

	private static final ResourceLocation PARTICLE_ATLAS = ResourceLocation.withDefaultNamespace("textures/atlas/particles.png");

	private final long tornado;
	private double angle;
	/** Distance from the funnel's axis, as a share of the funnel's radius at its height. */
	private final double out;
	/** Height above the funnel's foot, blocks, and the funnel's full height. */
	private double height;
	private final double top;
	private final float fullAlpha;

	public TornadoCloudParticle(ClientLevel level, TornadoTracker.Funnel funnel, double top, double height) {
		super(level, funnel.x(), funnel.ground() + height, funnel.z());
		this.tornado = funnel.id();
		this.top = top;
		this.height = height;
		this.angle = random.nextDouble() * Math.PI * 2;
		// Layers round the funnel, from hugging the core to well out.
		this.out = 0.85 + random.nextDouble() * 0.8;
		var atlas = (TextureAtlas)Minecraft.getInstance().getTextureManager().getTexture(PARTICLE_ATLAS);
		setSprite(atlas.getSprite(ResourceLocation.withDefaultNamespace("big_smoke_" + random.nextInt(12))));
		// Near-black and mid grey, mixed.
		float shade = AwmClientConfig.TORNADO_BRIGHTNESS.get().floatValue() * (random.nextInt(5) < 2 ? 0.03f + random.nextFloat() * 0.06f : 0.18f + random.nextFloat() * 0.2f);
		setColor(shade, shade * 1.02f, shade * 1.07f);
		fullAlpha = 0.7f + random.nextFloat() * 0.25f;
		alpha = 0;
		quadSize = (float)(0.7 + random.nextDouble() * 1.4 + height / top * 2);
		lifetime = 50 + random.nextInt(50);
		hasPhysics = false;
		gravity = 0;
		roll = random.nextFloat() * 6.28f;
		place(funnel);
		xo = x;
		yo = y;
		zo = z;
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		oRoll = roll;
		TornadoTracker.Funnel funnel = TornadoTracker.funnel(tornado);
		if(funnel == null || age++ >= lifetime) {
			remove();
			return;
		}
		double h = shape(funnel);
		double radius = StormRenderer.funnelRadius(funnel.coreRadius(), h);
		// Faster round and up near the ground, where the wind is worst.
		angle += (0.35 - 0.2 * h) / Math.max(1, radius * 0.25);
		height += 0.25 + 0.35 * (1 - h);
		roll += 0.05f;
		place(funnel);
		float fade = Math.min(1, Math.min(age / 10f, (lifetime - age) / 15f));
		alpha = fullAlpha * fade * Math.min(1, funnel.strength() * 1.5f) * (h > 0.92 ? (float)((1 - h) / 0.08) : 1);
		if(h >= 1)
			remove();
	}

	/** How far up the funnel's shape it is, 0 at the tip to 1 at the cloud, as far down as the funnel reaches. */
	private double shape(TornadoTracker.Funnel funnel) {
		return Mth.clamp(1 - (1 - height / top) / Math.max(0.05, funnel.reach()), 0, 1);
	}

	private void place(TornadoTracker.Funnel funnel) {
		double h = shape(funnel);
		double radius = StormRenderer.funnelRadius(funnel.coreRadius(), h) * out;
		double[] sway = StormRenderer.funnelSway(funnel.coreRadius(), h, level.getGameTime());
		setPos(funnel.x() + sway[0] + Math.sin(angle) * radius, funnel.ground() + height,
				funnel.z() + sway[1] - Math.cos(angle) * radius);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}
}
