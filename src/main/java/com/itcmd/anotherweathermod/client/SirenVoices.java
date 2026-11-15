package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.block.SirenMode;
import com.itcmd.anotherweathermod.block.SirenRotor;
import com.itcmd.anotherweathermod.network.SirenPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The warning sirens this client hears (2026-11-14), each a {@link Voice} kept from what the server sends
 * ({@link SirenPayload}, within earshot, loaded chunk or not) and from the siren's block entity while it is loaded. The
 * rotor turns here each tick as on the server ({@link SirenRotor#step}) and is put back on the server's speed with
 * every update, so a siren heard again after walking off comes back where it really is. Its {@link WarningSirenSound}
 * layers play from the voice, never from the block, so they fade out with distance rather than stop when the chunk
 * unloads. A voice the server has not mentioned for {@link #FORGET_TICKS} (out of earshot, or the server stopped) is
 * let wind down and dropped.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID, value = Dist.CLIENT)
public final class SirenVoices {

	/** Ticks of rotor speed a voice remembers, for the echoes. */
	public static final int HISTORY = 48;
	/** Ticks without word from the server before a voice is let go. */
	static final int FORGET_TICKS = 60;

	/** One siren as this client hears it. */
	public static final class Voice {
		final BlockPos pos;
		SirenMode mode = SirenMode.AIR_RAID;
		boolean horn;
		int height = 2;
		int signal;
		float speed;
		private final float[] past = new float[HISTORY];
		private int pastAt;
		int quietFor;
		java.util.List<WarningSirenSound> sounds = java.util.List.of();

		Voice(BlockPos pos) {
			this.pos = pos;
		}

		public BlockPos pos() {
			return pos;
		}

		public SirenMode mode() {
			return mode;
		}

		public boolean horn() {
			return horn;
		}

		public int height() {
			return height;
		}

		public int signal() {
			return signal;
		}

		public float speed() {
			return speed;
		}

		/** The rotor's speed {@code ticks} ago, up to {@link #HISTORY} - 1. */
		public float speedAgo(int ticks) {
			int t = Math.max(0, Math.min(HISTORY - 1, ticks));
			return past[Math.floorMod(pastAt - 1 - t, HISTORY)];
		}

		boolean silent() {
			if(signal > 0 || speed > 0.001F)
				return false;
			for(float s : past)
				if(s > 0.001F)
					return false;
			return true;
		}
	}

	private static final Map<BlockPos, Voice> VOICES = new HashMap<>();

	private SirenVoices() {}

	/** The voice of the siren at {@code pos}, or null. */
	public static Voice at(BlockPos pos) {
		return VOICES.get(pos);
	}

	/** The server's word on a siren: its state, and where its rotor is. */
	public static void fromServer(SirenPayload payload) {
		Voice voice = VOICES.computeIfAbsent(payload.pos().immutable(), Voice::new);
		SirenMode[] modes = SirenMode.values();
		voice.mode = modes[Math.max(0, Math.min(modes.length - 1, payload.mode()))];
		voice.horn = payload.horn();
		voice.height = payload.height();
		voice.signal = payload.signal();
		voice.speed = payload.speed(); // The server's rotor: where it really is.
		voice.quietFor = 0;
	}

	/** The siren's loaded block entity's state (its rotor is left to the server's word). */
	public static void fromBlock(BlockPos pos, SirenMode mode, boolean horn, int height, int signal) {
		Voice voice = VOICES.get(pos);
		if(voice == null) {
			if(signal <= 0)
				return;
			voice = new Voice(pos.immutable());
			VOICES.put(voice.pos, voice);
		}
		voice.mode = mode;
		voice.horn = horn;
		voice.height = height;
		voice.signal = signal;
		voice.quietFor = 0;
	}

	@SubscribeEvent
	public static void onTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if(mc.level == null) {
			stopAll();
			return;
		}
		if(mc.isPaused())
			return;
		for(Iterator<Voice> it = VOICES.values().iterator(); it.hasNext(); ) {
			Voice voice = it.next();
			if(++voice.quietFor > FORGET_TICKS)
				voice.signal = 0; // No word: out of earshot, or gone. Let it wind down.
			voice.speed = voice.mode.winds ? SirenRotor.step(voice.speed, voice.signal) : voice.signal > 0 ? 1.0F : 0.0F;
			voice.past[voice.pastAt] = voice.speed;
			voice.pastAt = (voice.pastAt + 1) % HISTORY;
			if(voice.silent()) {
				for(WarningSirenSound sound : voice.sounds)
					sound.stopNow();
				it.remove();
				continue;
			}
			WarningSirenSound.keepPlaying(voice);
		}
	}

	private static void stopAll() {
		for(Voice voice : VOICES.values())
			for(WarningSirenSound sound : voice.sounds)
				sound.stopNow();
		VOICES.clear();
	}
}
