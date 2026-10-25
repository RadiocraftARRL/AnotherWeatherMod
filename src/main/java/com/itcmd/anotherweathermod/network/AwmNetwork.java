package com.itcmd.anotherweathermod.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The mod's payloads. All optional, so vanilla clients and clients without the mod still join; the server only sends
 * them to connections that have the channel.
 */
public final class AwmNetwork {

	public static final String VERSION = "5";

	private AwmNetwork() {
	}

	/** Listens on the mod bus (see the mod constructor). */
	public static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(VERSION).optional()
				.playToClient(LocalWeatherPayload.TYPE, LocalWeatherPayload.STREAM_CODEC,
						(payload, context) -> ClientWeatherState.set(payload))
				.playToClient(NearbyStormsPayload.TYPE, NearbyStormsPayload.STREAM_CODEC,
						(payload, context) -> ClientWeatherState.setStorms(payload))
				.playToClient(ServerSettingsPayload.TYPE, ServerSettingsPayload.STREAM_CODEC,
						(payload, context) -> ClientWeatherState.setSettings(payload))
				.playBidirectional(ProtectorPayload.TYPE, ProtectorPayload.STREAM_CODEC, (payload, context) -> {
					if(context.flow().isClientbound())
						com.itcmd.anotherweathermod.client.StormProtectorScreen.open(payload);
					else
						ProtectorPayload.handleOnServer(payload, context);
				});
	}
}
