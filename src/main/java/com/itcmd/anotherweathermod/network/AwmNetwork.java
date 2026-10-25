package com.itcmd.anotherweathermod.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The mod's payloads. All optional, so vanilla clients and clients without the mod still join; the server only sends
 * them to connections that have the channel.
 */
public final class AwmNetwork {

	public static final String VERSION = "2";

	private AwmNetwork() {
	}

	/** Listens on the mod bus (see the mod constructor). */
	public static void register(RegisterPayloadHandlersEvent event) {
		event.registrar(VERSION).optional()
				.playToClient(LocalWeatherPayload.TYPE, LocalWeatherPayload.STREAM_CODEC,
						(payload, context) -> ClientWeatherState.set(payload))
				.playToClient(NearbyStormsPayload.TYPE, NearbyStormsPayload.STREAM_CODEC,
						(payload, context) -> ClientWeatherState.setStorms(payload));
	}
}
