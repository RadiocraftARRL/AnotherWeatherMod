package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AwmClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;

/**
 * A hailstone: a small chip of the ice texture that falls fast, bounces, lies on the ground a while and melts away.
 * Added straight to the particle engine, so it needs no registered particle type (which would stop vanilla clients
 * joining).
 */
public final class HailParticle extends TextureSheetParticle {

	/** Most landing sounds a tick, so a hailstorm patters without using up the sound channels. */
	static final int MAX_SOUNDS_PER_TICK = 5;
	private static long soundTick;
	private static int soundsThisTick;

	private final float size;
	private final boolean far;
	private int landings;
	/** The age it came to rest at, or -1 while it is still moving. */
	private int restedAt = -1;

	/**
	 * @param far a small, distant stone: it does not linger as long and is quieter when it lands
	 */
	public HailParticle(ClientLevel level, double x, double y, double z, double xSpeed, double zSpeed, boolean far) {
		super(level, x, y, z, xSpeed, -1.2, zSpeed);
		setSprite(Minecraft.getInstance().getBlockRenderer().getBlockModelShaper()
				.getTexture(Blocks.PACKED_ICE.defaultBlockState(), level, BlockPos.containing(x, y, z)));
		this.far = far;
		xd = xSpeed;
		yd = -1.2;
		zd = zSpeed;
		gravity = 2.5f;
		friction = 0.98f;
		size = far ? 0.035f + random.nextFloat() * 0.03f : 0.06f + random.nextFloat() * 0.05f;
		quadSize = size;
		lifetime = 400; // Set properly once it lands.
		rCol = gCol = bCol = 0.92f + random.nextFloat() * 0.06f;
	}

	/** How long a stone lies on the ground, ticks: longer the higher the quality. */
	private int restTicks() {
		int base = switch(AwmClientConfig.QUALITY.get()) {
			case LOW -> 20;
			case MEDIUM -> 60;
			case HIGH -> 140;
			case ULTRA -> 240;
		};
		if(far)
			base /= 2;
		return base + random.nextInt(base / 2 + 1);
	}

	@Override
	public void tick() {
		super.tick();
		if(restedAt < 0 && onGround) {
			if(landings++ == 0) {
				// The first landing: a bounce and a tick on the ground.
				yd = 0.12 + random.nextDouble() * 0.15;
				xd = (random.nextDouble() - 0.5) * 0.2;
				zd = (random.nextDouble() - 0.5) * 0.2;
				patter();
			}
			else {
				restedAt = age;
				xd = zd = 0;
				lifetime = age + restTicks();
			}
		}
		if(restedAt >= 0) {
			// Melts: shrinks over its last 30 ticks.
			int left = lifetime - age;
			quadSize = size * Math.min(1, left / 30f);
		}
	}

	/** Pit, pat: a little click as it hits the ground. */
	private void patter() {
		if(soundTick != level.getGameTime()) {
			soundTick = level.getGameTime();
			soundsThisTick = 0;
		}
		if(soundsThisTick >= MAX_SOUNDS_PER_TICK || random.nextInt(far ? 4 : 2) != 0)
			return;
		soundsThisTick++;
		float volume = (far ? 0.08f : 0.18f) + random.nextFloat() * 0.08f;
		level.playLocalSound(x, y, z, random.nextBoolean() ? SoundEvents.STONE_HIT : SoundEvents.GLASS_HIT,
				SoundSource.WEATHER, volume, 1.7f + random.nextFloat() * 0.3f, false);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.TERRAIN_SHEET;
	}
}
