package com.itcmd.anotherweathermod.network;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A warning siren as the server has it (2026-11-14): sent to every player within earshot
 * ({@link com.itcmd.anotherweathermod.block.SirenRotor#HEARD}) about twice a second while it sounds, loaded chunk or
 * not, so the sound fades out with distance instead of stopping when the siren's chunk unloads, and comes back where
 * the rotor really is. {@code mode} the {@link com.itcmd.anotherweathermod.block.SirenMode} ordinal, {@code height}
 * blocks from its foot to its head, {@code speed} the rotor 0 to 1.
 */
public record SirenPayload(BlockPos pos, int mode, boolean horn, int height, int signal, float speed) implements CustomPacketPayload {

	public static final Type<SirenPayload> TYPE = new Type<>(AnotherWeatherMod.id("siren"));
	public static final StreamCodec<ByteBuf, SirenPayload> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, SirenPayload::pos, ByteBufCodecs.VAR_INT, SirenPayload::mode, ByteBufCodecs.BOOL, SirenPayload::horn,
			ByteBufCodecs.VAR_INT, SirenPayload::height, ByteBufCodecs.VAR_INT, SirenPayload::signal, ByteBufCodecs.FLOAT, SirenPayload::speed,
			SirenPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
