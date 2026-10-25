package com.itcmd.anotherweathermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Something the wind carries: a faint streak of air, a grain of sand, or a scrap of leaf. Added straight to the
 * particle engine, like {@link HailParticle}, so nothing needs registering.
 */
public final class WindParticle extends TextureSheetParticle {

	private static final ResourceLocation PARTICLE_ATLAS = ResourceLocation.withDefaultNamespace("textures/atlas/particles.png");
	private static final ResourceLocation STREAK = ResourceLocation.withDefaultNamespace("generic_0");

	private final ParticleRenderType renderType;

	private WindParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
			ParticleRenderType renderType) {
		super(level, x, y, z, xd, yd, zd);
		this.renderType = renderType;
		this.xd = xd;
		this.yd = yd;
		this.zd = zd;
		gravity = 0;
		friction = 0.99f;
		hasPhysics = false;
		lifetime = 25 + random.nextInt(25);
	}

	/** A faint pale streak of moving air (or, tinted, sand). */
	public static WindParticle streak(ClientLevel level, double x, double y, double z, double xd, double zd, float r,
			float g, float b, float alpha) {
		WindParticle p = new WindParticle(level, x, y, z, xd, (level.random.nextDouble() - 0.5) * 0.02, zd,
				ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT);
		// The particle atlas belongs to the particle engine, not the model manager: ask the texture manager for it.
		p.setSprite(((TextureAtlas)Minecraft.getInstance().getTextureManager().getTexture(PARTICLE_ATLAS)).getSprite(STREAK));
		p.setColor(r, g, b);
		p.setAlpha(alpha);
		p.quadSize = 0.05f + p.random.nextFloat() * 0.05f;
		return p;
	}

	/** A white wisp of a gust: bigger, brighter and quicker than a streak, gone in a second or so. */
	public static WindParticle wisp(ClientLevel level, double x, double y, double z, double xd, double zd) {
		WindParticle p = streak(level, x, y, z, xd * 1.3, zd * 1.3, 1, 1, 1, 0.3f + level.random.nextFloat() * 0.2f);
		p.quadSize = 0.1f + p.random.nextFloat() * 0.08f;
		p.lifetime = 15 + p.random.nextInt(15);
		return p;
	}

	/** A bit of {@code state} (leaves, sand) tumbling along. */
	public static WindParticle debris(ClientLevel level, double x, double y, double z, double xd, double zd,
			BlockState state) {
		WindParticle p = new WindParticle(level, x, y, z, xd, (level.random.nextDouble() - 0.3) * 0.08, zd,
				ParticleRenderType.TERRAIN_SHEET);
		p.setSprite(Minecraft.getInstance().getBlockRenderer().getBlockModelShaper()
				.getTexture(state, level, BlockPos.containing(x, y, z)));
		int color = Minecraft.getInstance().getBlockColors().getColor(state, level, BlockPos.containing(x, y, z), 0);
		if(color != -1)
			p.setColor((color >> 16 & 0xFF) / 255f, (color >> 8 & 0xFF) / 255f, (color & 0xFF) / 255f);
		p.quadSize = 0.06f + p.random.nextFloat() * 0.06f;
		p.roll = p.random.nextFloat() * 6.28f;
		return p;
	}

	@Override
	public void tick() {
		oRoll = roll;
		if(renderType == ParticleRenderType.TERRAIN_SHEET)
			roll += 0.3f; // Debris tumbles.
		super.tick();
		if(age > lifetime - 10)
			alpha *= 0.85f;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return renderType;
	}
}
