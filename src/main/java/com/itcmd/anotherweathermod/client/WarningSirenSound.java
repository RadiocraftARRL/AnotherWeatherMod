package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.block.AwmBlocks;
import com.itcmd.anotherweathermod.block.SirenMode;
import com.itcmd.anotherweathermod.block.SirenRotor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * A warning siren's sound (2026-11-13), from the maintainer's recording: a few looping layers per siren, each
 * following its {@link SirenVoices.Voice} every tick ({@link SirenRotor}). It plays from the voice the server keeps
 * up to date, not from the block (2026-11-14), so it fades out with distance and never stops when the chunk unloads.
 * <ul>
 * <li>{@link Layer#MAIN}: the recording at the rotor's pitch, louder as it speeds up, wavering a little (a real rotor
 * is never quite steady, 2026-11-14).</li>
 * <li>{@link Layer#LOW}: the same an octave down, for pitches under Minecraft's lowest (half): the two crossfade, so
 * a siren winding up from a crawl starts very low.</li>
 * <li>{@link Layer#ECHO_NEAR}, {@link Layer#ECHO_FAR}, {@link Layer#ECHO_DISTANT}: what the siren did a moment, a
 * second and two seconds ago, quieter, from either side of it, thrown back by the country round; there close by and
 * strong far away ({@link SirenRotor#echo}).</li>
 * </ul>
 * The horn's recording is clipped harder than the omni's (more distortion). It carries {@link SirenRotor#HEARD}
 * blocks: there is no attenuation of the sound engine's, the falloff is set here. The klaxon plays its own pulsed
 * recording, at once and at full pitch, with the same echoes.
 */
public final class WarningSirenSound extends AbstractTickableSoundInstance {

	enum Layer { MAIN, LOW, ECHO_NEAR, ECHO_FAR, ECHO_DISTANT }

	static final int ECHO_NEAR_TICKS = 9, ECHO_FAR_TICKS = 21, ECHO_DISTANT_TICKS = 38;
	private static final float ECHO_NEAR_GAIN = 0.45F, ECHO_FAR_GAIN = 0.3F, ECHO_DISTANT_GAIN = 0.18F;
	/** How far the wail's pitch wavers, either way, as a share of it. */
	private static final float WAVER = 0.012F;

	private final SirenVoices.Voice voice;
	private final Layer layer;
	private final SirenMode mode;
	private final boolean horn;
	private final double headX, headY, headZ;
	private final double phase;

	private WarningSirenSound(SirenVoices.Voice voice, Layer layer, SoundEvent event) {
		super(event, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
		this.voice = voice;
		this.layer = layer;
		this.mode = voice.mode();
		this.horn = voice.horn();
		this.headX = voice.pos().getX() + 0.5D;
		this.headY = voice.pos().getY() + voice.height() - 0.5D;
		this.headZ = voice.pos().getZ() + 0.5D;
		this.phase = (voice.pos().hashCode() & 0xFFFF) / 65536.0D * Math.PI * 2.0D;
		looping = true;
		delay = 0;
		attenuation = Attenuation.NONE;
		volume = 0.0F;
		pitch = 1.0F;
		x = headX;
		y = headY;
		z = headZ;
	}

	/** Starts the voice's layers unless they are playing (again after its mode or head changes). */
	public static void keepPlaying(SirenVoices.Voice voice) {
		SirenMode mode = voice.mode();
		if(!voice.sounds.isEmpty() && voice.sounds.stream().allMatch(s -> !s.isStopped() && s.mode == mode && s.horn == voice.horn()))
			return;
		for(WarningSirenSound s : voice.sounds)
			s.stopNow();
		List<WarningSirenSound> layers = new ArrayList<>();
		for(Layer layer : Layer.values()) {
			if(layer == Layer.LOW && !mode.winds)
				continue;
			layers.add(new WarningSirenSound(voice, layer, event(mode, voice.horn(), layer == Layer.LOW)));
		}
		voice.sounds = layers;
		for(WarningSirenSound sound : layers)
			Minecraft.getInstance().getSoundManager().play(sound);
	}

	private static SoundEvent event(SirenMode mode, boolean horn, boolean low) {
		if(!mode.winds)
			return AwmBlocks.KLAXON_SOUND.get();
		if(horn)
			return (low ? AwmBlocks.WARNING_SIREN_HORN_LOW_SOUND : AwmBlocks.WARNING_SIREN_HORN_SOUND).get();
		return (low ? AwmBlocks.WARNING_SIREN_LOW_SOUND : AwmBlocks.WARNING_SIREN_SOUND).get();
	}

	void stopNow() {
		stop();
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		if(mc.level == null || SirenVoices.at(voice.pos()) != voice || voice.mode() != mode) {
			stop();
			return;
		}
		int ago = switch(layer) {
			case ECHO_NEAR -> ECHO_NEAR_TICKS;
			case ECHO_FAR -> ECHO_FAR_TICKS;
			case ECHO_DISTANT -> ECHO_DISTANT_TICKS;
			default -> 0;
		};
		float speed = ago == 0 ? voice.speed() : voice.speedAgo(ago);
		Player player = mc.player;
		if(player == null) {
			volume = 0.0F;
			return;
		}
		double dx = player.getX() - headX, dy = player.getEyeY() - headY, dz = player.getZ() - headZ;
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double falloff = SirenRotor.falloff(distance);
		float loud;
		if(mode.winds) {
			// A real rotor never turns quite steadily: two slow wavers, different for every siren.
			double t = (mc.level.getGameTime() - ago) / 20.0D;
			float waver = 1.0F + WAVER * (float)(0.6D * Math.sin(t * 1.7D + phase) + 0.4D * Math.sin(t * 4.3D + phase * 2.0D));
			float wanted = SirenRotor.pitch(mode, speed) * waver;
			float low = SirenRotor.lowShare(wanted);
			loud = SirenRotor.loudness(speed);
			if(layer == Layer.LOW) {
				pitch = Math.max(SirenRotor.ENGINE_LOWEST, Math.min(2.0F, wanted * 2.0F));
				loud *= low;
			}
			else {
				pitch = Math.max(SirenRotor.ENGINE_LOWEST, Math.min(2.0F, wanted));
				if(layer == Layer.MAIN)
					loud *= 1.0F - low;
			}
		}
		else {
			pitch = 1.0F;
			loud = speed > 0.5F ? SirenRotor.klaxonLoudness(Math.max(1, voice.signal())) : 0.0F;
		}
		if(ago > 0) {
			// Thrown back from off to one side of the siren as heard from here, alternating sides, further off the further
			// away the listener is.
			double flat = Math.max(1.0D, Math.sqrt(dx * dx + dz * dz));
			double side = Math.max(30.0D, Math.min(160.0D, distance * 0.5D)) * (layer == Layer.ECHO_FAR ? -1.0D : 1.0D) * (layer == Layer.ECHO_DISTANT ? 1.4D : 1.0D);
			x = headX + -dz / flat * side;
			z = headZ + dx / flat * side;
			float gain = layer == Layer.ECHO_NEAR ? ECHO_NEAR_GAIN : layer == Layer.ECHO_FAR ? ECHO_FAR_GAIN : ECHO_DISTANT_GAIN;
			loud *= (float)SirenRotor.echo(distance) * gain;
		}
		if(horn && layer == Layer.MAIN)
			loud *= 1.05F; // The horn throws it a little harder.
		volume = (float)Math.min(1.0D, loud * falloff);
		if(layer == Layer.MAIN)
			subtitle(mc, voice.speed() > 0.001F || voice.signal() > 0 ? Math.max(volume, mode.winds ? (float)(SirenRotor.loudness(voice.speed()) * falloff) : volume) : 0.0F);
	}

	private final SirenSubtitles.Clock subtitles = new SirenSubtitles.Clock();

	/** The subtitle while the siren can be heard ({@link SirenSubtitles}): which siren, and what its rotor is doing. */
	private void subtitle(Minecraft mc, float heard) {
		String key = "subtitles.anotherweathermod.siren." + mode.key();
		if(mode.winds)
			key = SirenSubtitles.winding(key, voice.speed(), voice.speedAgo(10));
		SirenSubtitles.tick(subtitles, this, key, heard);
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}
}
