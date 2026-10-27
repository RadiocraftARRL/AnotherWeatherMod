package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.block.AwmBlocks;
import com.itcmd.anotherweathermod.block.TornadoSirenBlock;
import com.itcmd.anotherweathermod.block.TornadoSirenBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A tornado siren's wail, one loop for as long as its rotor turns. It follows the rotor: the pitch climbs from half to
 * full as it winds up and sags as it coasts down, and it grows louder with speed. It swells as the turning horn faces
 * you and fades as it turns away, and carries {@link #HEARD} blocks (no attenuation of its own: it is set here).
 */
public final class SirenSound extends AbstractTickableSoundInstance {

	/** How far off, in blocks, the siren has died away. */
	static final double HEARD = 240;

	private final TornadoSirenBlockEntity siren;
	private final BlockPos pos;
	private float speed;
	/** Where the horn points, degrees, eased between the blockstate's 22.5 degree steps. */
	private float heading = Float.NaN;

	private SirenSound(TornadoSirenBlockEntity siren) {
		super(AwmBlocks.TORNADO_SIREN_SOUND.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
		this.siren = siren;
		this.pos = siren.getBlockPos();
		looping = true;
		delay = 0;
		attenuation = Attenuation.NONE;
		volume = 0;
		pitch = 0.5f;
		x = pos.getX() + 0.5;
		y = pos.getY() + 1.5;
		z = pos.getZ() + 0.5;
	}

	/** Starts the siren's sound unless it is already playing. */
	public static void keepPlaying(TornadoSirenBlockEntity siren) {
		if(siren.sound instanceof SirenSound sound && !sound.isStopped())
			return;
		SirenSound sound = new SirenSound(siren);
		siren.sound = sound;
		Minecraft.getInstance().getSoundManager().play(sound);
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		BlockState lower = mc.level == null ? null : mc.level.getBlockState(pos);
		BlockState upper = mc.level == null ? null : mc.level.getBlockState(pos.above());
		if(siren.isRemoved() || lower == null || !lower.is(AwmBlocks.TORNADO_SIREN.get())
				|| !upper.is(AwmBlocks.TORNADO_SIREN.get())) {
			stop();
			return;
		}
		// The server sets the speed in steps a few ticks apart; glide between them.
		speed += (lower.getValue(TornadoSirenBlock.SPEED) / 15f - speed) * 0.08f;
		float target = upper.getValue(TornadoSirenBlock.DIRECTION) * 22.5f;
		heading = Float.isNaN(heading) ? target : heading + wrap(target - heading) * 0.15f;
		pitch = 0.5f + 0.5f * speed;
		Player player = mc.player;
		if(player == null) {
			volume = 0;
			return;
		}
		double dx = player.getX() - x, dz = player.getZ() - z;
		double distance = Math.sqrt(dx * dx + dz * dz + (player.getEyeY() - y) * (player.getEyeY() - y));
		double facing = 1;
		if(distance > 1) {
			// The horn's mouth points along the heading: +x is east, -z is north.
			double hx = Math.sin(Math.toRadians(heading)), hz = -Math.cos(Math.toRadians(heading));
			double dot = (hx * dx + hz * dz) / Math.max(1e-6, Math.sqrt(dx * dx + dz * dz));
			facing = 0.3 + 0.7 * Math.pow(0.5 + 0.5 * dot, 1.5);
		}
		double falloff = Math.max(0, 1 - distance / HEARD);
		volume = (float)(Math.pow(speed, 1.5) * facing * falloff * falloff);
		if(speed < 0.01f && lower.getValue(TornadoSirenBlock.SPEED) == 0)
			stop();
	}

	private static float wrap(float degrees) {
		degrees %= 360;
		return degrees > 180 ? degrees - 360 : degrees < -180 ? degrees + 360 : degrees;
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}
}
