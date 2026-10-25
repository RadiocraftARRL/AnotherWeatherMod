package com.itcmd.anotherweathermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

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
		this.out = 0.7 + random.nextDouble() * 0.6;
		var atlas = (TextureAtlas)Minecraft.getInstance().getTextureManager().getTexture(PARTICLE_ATLAS);
		setSprite(atlas.getSprite(ResourceLocation.withDefaultNamespace("big_smoke_" + random.nextInt(12))));
		float shade = 0.13f + random.nextFloat() * 0.1f;
		setColor(shade, shade * 1.02f, shade * 1.07f);
		fullAlpha = 0.55f + random.nextFloat() * 0.3f;
		alpha = 0;
		quadSize = (float)(1.5 + random.nextDouble() * 2.5 + height / top * 4);
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
		double h = height / top;
		double radius = StormRenderer.funnelRadius(funnel.radius(), h);
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

	private void place(TornadoTracker.Funnel funnel) {
		double h = height / top;
		double radius = StormRenderer.funnelRadius(funnel.radius(), h) * out;
		double[] sway = StormRenderer.funnelSway(funnel.radius(), h, level.getGameTime());
		setPos(funnel.x() + sway[0] + Math.sin(angle) * radius, funnel.ground() + height,
				funnel.z() + sway[1] - Math.cos(angle) * radius);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}
}
