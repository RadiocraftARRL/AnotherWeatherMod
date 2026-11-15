package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.block.SirenMode;
import com.itcmd.anotherweathermod.block.SirenRotor;
import com.itcmd.anotherweathermod.network.SirenPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The sounding warning sirens of each level, kept by the server (2026-11-14, the maintainer: the siren cut out once
 * far enough away, and came back with the rotor where the client guessed). A siren carries
 * {@link SirenRotor#HEARD} blocks, much further than chunks stay loaded round a player, so its block entity cannot be
 * what plays it: the server turns every sounding siren's rotor here each tick (loaded or not: its signal cannot change
 * while it is unloaded) and sends its state ({@link SirenPayload}) to every player within earshot every
 * {@link #SEND_TICKS}, and once more when it has stopped. The client plays each siren from that
 * ({@code SirenVoices}), so it fades out with distance and comes back where the rotor is.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID)
public final class SirenBroadcast {

	/** Ticks between the updates sent to the players in earshot. */
	public static final int SEND_TICKS = 10;

	/** One sounding siren as the server has it. */
	static final class Siren {
		SirenMode mode;
		boolean horn;
		int height;
		int signal;
		float speed;
		boolean sentStopped;
	}

	private static final Map<ResourceKey<Level>, Map<BlockPos, Siren>> SIRENS = new HashMap<>();

	private SirenBroadcast() {}

	/** A siren's state now (from its block entity, while loaded): kept and sent on. Its rotor turns here. */
	public static void update(ServerLevel level, BlockPos pos, SirenMode mode, boolean horn, int height, int signal) {
		Map<BlockPos, Siren> sirens = SIRENS.computeIfAbsent(level.dimension(), k -> new HashMap<>());
		Siren siren = sirens.get(pos);
		if(siren == null) {
			if(signal <= 0)
				return; // Silent and not sounding: nothing to keep.
			siren = new Siren();
			sirens.put(pos.immutable(), siren);
		}
		boolean changed = siren.mode != mode || siren.signal != signal;
		siren.mode = mode;
		siren.horn = horn;
		siren.height = height;
		siren.signal = signal;
		if(changed) {
			siren.sentStopped = false;
			send(level, pos, siren);
		}
	}

	/** The siren at {@code pos} is gone (broken): it stops at once for everyone. */
	public static void removed(ServerLevel level, BlockPos pos) {
		Map<BlockPos, Siren> sirens = SIRENS.get(level.dimension());
		Siren siren = sirens == null ? null : sirens.remove(pos);
		if(siren != null) {
			siren.signal = 0;
			siren.speed = 0.0F;
			send(level, pos, siren);
		}
	}

	/** The rotor speed the server has for the siren at {@code pos}, 0 if it is not sounding. */
	public static float speed(Level level, BlockPos pos) {
		Map<BlockPos, Siren> sirens = SIRENS.get(level.dimension());
		Siren siren = sirens == null ? null : sirens.get(pos);
		return siren == null ? 0.0F : siren.speed;
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		if(!(event.getLevel() instanceof ServerLevel level))
			return;
		Map<BlockPos, Siren> sirens = SIRENS.get(level.dimension());
		if(sirens == null || sirens.isEmpty())
			return;
		boolean send = level.getGameTime() % SEND_TICKS == 0;
		for(Iterator<Map.Entry<BlockPos, Siren>> it = sirens.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<BlockPos, Siren> entry = it.next();
			Siren siren = entry.getValue();
			siren.speed = siren.mode.winds ? SirenRotor.step(siren.speed, siren.signal) : siren.signal > 0 ? 1.0F : 0.0F;
			boolean stopped = siren.signal <= 0 && siren.speed <= 0.001F;
			if(send && !(stopped && siren.sentStopped)) {
				send(level, entry.getKey(), siren);
				siren.sentStopped = stopped;
			}
			if(stopped && siren.sentStopped)
				it.remove();
		}
	}

	private static void send(ServerLevel level, BlockPos pos, Siren siren) {
		SirenPayload payload = new SirenPayload(pos, siren.mode.ordinal(), siren.horn, siren.height, siren.signal, siren.speed);
		double reach = SirenRotor.HEARD + 32.0D;
		for(ServerPlayer player : level.players())
			if(player.distanceToSqr(pos.getCenter()) < reach * reach && player.connection.hasChannel(SirenPayload.TYPE))
				PacketDistributor.sendToPlayer(player, payload);
	}

	/** Forgets every level's sirens when the server stops (a single player world reopened starts afresh). */
	@SubscribeEvent
	public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
		SIRENS.clear();
	}
}
