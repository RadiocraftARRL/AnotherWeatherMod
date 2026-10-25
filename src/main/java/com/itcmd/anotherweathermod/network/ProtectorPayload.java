package com.itcmd.anotherweathermod.network;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.block.AwmBlocks;
import com.itcmd.anotherweathermod.world.StormProtection;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A storm protector's radius: to the client, to open its screen; back to the server, to set it. The server only takes
 * it from an operator in creative standing near the protector.
 */
public record ProtectorPayload(BlockPos pos, int radius) implements CustomPacketPayload {

	public static final Type<ProtectorPayload> TYPE = new Type<>(AnotherWeatherMod.id("protector"));
	public static final StreamCodec<ByteBuf, ProtectorPayload> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, ProtectorPayload::pos, ByteBufCodecs.VAR_INT, ProtectorPayload::radius,
			ProtectorPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** On the server: a player set the radius. */
	static void handleOnServer(ProtectorPayload payload, IPayloadContext context) {
		if(!(context.player() instanceof ServerPlayer player) || !player.canUseGameMasterBlocks()
				|| player.distanceToSqr(payload.pos().getCenter()) > 64 * 64)
			return;
		if(!player.serverLevel().getBlockState(payload.pos()).is(AwmBlocks.STORM_PROTECTOR.get()))
			return;
		StormProtection.get(player.serverLevel()).set(payload.pos(), payload.radius());
	}
}
