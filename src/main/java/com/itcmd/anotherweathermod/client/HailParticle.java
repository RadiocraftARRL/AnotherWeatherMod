package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AwmClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;

/**
 * A hailstone: a small chip of the ice texture that falls fast, bounces, lies on the ground a while and melts away.
 * Added straight to the particle engine, so it needs no registered particle type (which would stop vanilla clients
 * joining).
 */
public final class HailParticle extends TextureSheetParticle {

	/** Most landing sounds a tick, so a hailstorm patters without using up the sound channels. */
	static final int MAX_SOUNDS_PER_TICK = 8;
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
		BlockPos below = BlockPos.containing(x, y - 0.2, z);
		BlockState state = level.getBlockState(below);
		if(state.isAir())
			state = level.getBlockState(below.below());
		Hit hit = hit(state.getSoundType(level, below, null), state);
		// Louder than before, and uneven: most stones tick, some crack down hard.
		float volume = hit.volume * 1.6f * (far ? 0.5f : 1) * (0.45f + random.nextFloat() * 0.6f
				+ (random.nextInt(6) == 0 ? 0.6f : 0));
		level.playLocalSound(x, y, z, hit.sound, SoundSource.WEATHER, volume,
				hit.pitch + random.nextFloat() * 0.25f, false);
	}

	/** What a stone sounds like landing on something. */
	private record Hit(SoundEvent sound, float volume, float pitch) {
	}

	/** A sharp clang on metal and glass, a thud on wood, a click on stone, a patter on soft ground, a plip on water. */
	private static Hit hit(SoundType type, BlockState state) {
		if(!state.getFluidState().isEmpty())
			return new Hit(SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, 0.5f, 1.2f);

		if(type == SoundType.METAL || type == SoundType.COPPER || type == SoundType.NETHERITE_BLOCK
				|| type == SoundType.ANVIL || type == SoundType.CHAIN || type == SoundType.LANTERN
				|| type == SoundType.COPPER_BULB || type == SoundType.COPPER_GRATE || type == SoundType.HEAVY_CORE)
			return new Hit(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.45f, 1.75f);
		if(type == SoundType.GLASS || type == SoundType.AMETHYST || type == SoundType.AMETHYST_CLUSTER)
			return new Hit(SoundEvents.GLASS_HIT, 0.75f, 1.55f);
		if(type == SoundType.WOOD || type == SoundType.NETHER_WOOD || type == SoundType.CHERRY_WOOD
				|| type == SoundType.BAMBOO_WOOD || type == SoundType.BAMBOO || type == SoundType.LADDER
				|| type == SoundType.SCAFFOLDING)
			return new Hit(SoundEvents.WOOD_HIT, 0.75f, 0.95f);
		if(type == SoundType.STONE || type == SoundType.DEEPSLATE || type == SoundType.DEEPSLATE_BRICKS
				|| type == SoundType.DEEPSLATE_TILES || type == SoundType.POLISHED_DEEPSLATE || type == SoundType.TUFF
				|| type == SoundType.NETHER_BRICKS || type == SoundType.CALCITE || type == SoundType.BASALT
				|| type == SoundType.MUD_BRICKS)
			return new Hit(SoundEvents.STONE_HIT, 0.55f, 1.65f);
		// Grass, dirt, sand, leaves, snow, wool: its own soft hit, high.
		return new Hit(type.getHitSound(), 0.4f, 1.5f);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.TERRAIN_SHEET;
	}
}
