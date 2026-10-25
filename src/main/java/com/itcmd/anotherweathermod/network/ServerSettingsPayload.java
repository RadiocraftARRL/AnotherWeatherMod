package com.itcmd.anotherweathermod.network;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The server's settings that change what clients draw, sent when a player joins and when the config is reloaded. */
public record ServerSettingsPayload(boolean customClouds) implements CustomPacketPayload {

	public static final Type<ServerSettingsPayload> TYPE = new Type<>(AnotherWeatherMod.id("server_settings"));
	public static final StreamCodec<ByteBuf, ServerSettingsPayload> STREAM_CODEC = ByteBufCodecs.BOOL
			.map(ServerSettingsPayload::new, ServerSettingsPayload::customClouds);

	/** What a client assumes until told otherwise, and on a server without the mod. */
	public static final ServerSettingsPayload DEFAULT = new ServerSettingsPayload(true);

	/** The settings as the server's config has them now. */
	public static ServerSettingsPayload current() {
		return new ServerSettingsPayload(AwmConfig.CUSTOM_CLOUDS.get());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
