package com.itcmd.anotherweathermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;

/**
 * A hailstone: a small chip of the ice texture that falls fast, bounces once and melts. Added straight to the particle
 * engine, so it needs no registered particle type (which would stop vanilla clients joining).
 */
public final class HailParticle extends TextureSheetParticle {

	private boolean bounced;

	public HailParticle(ClientLevel level, double x, double y, double z, double xSpeed, double zSpeed) {
		super(level, x, y, z, xSpeed, -1.2, zSpeed);
		setSprite(Minecraft.getInstance().getBlockRenderer().getBlockModelShaper()
				.getTexture(Blocks.PACKED_ICE.defaultBlockState(), level, BlockPos.containing(x, y, z)));
		xd = xSpeed;
		yd = -1.2;
		zd = zSpeed;
		gravity = 2.5f;
		friction = 0.98f;
		quadSize = 0.06f + random.nextFloat() * 0.05f;
		lifetime = 40 + random.nextInt(20);
		rCol = gCol = bCol = 0.95f;
	}

	@Override
	public void tick() {
		super.tick();
		if(onGround && !bounced) {
			bounced = true;
			yd = 0.15 + random.nextDouble() * 0.15;
			xd = (random.nextDouble() - 0.5) * 0.2;
			zd = (random.nextDouble() - 0.5) * 0.2;
			lifetime = Math.min(lifetime, age + 12);
			if(random.nextInt(6) == 0)
				level.playLocalSound(x, y, z, SoundEvents.STONE_HIT, SoundSource.WEATHER, 0.12f,
						1.6f + random.nextFloat() * 0.4f, false);
		}
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.TERRAIN_SHEET;
	}
}
