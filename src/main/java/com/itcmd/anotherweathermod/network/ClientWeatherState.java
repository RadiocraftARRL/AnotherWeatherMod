package com.itcmd.anotherweathermod.network;

/**
 * The last {@link LocalWeatherPayload} the server sent this client. Plain data, no client classes, so the payload
 * handler is safe to name on a dedicated server.
 */
public final class ClientWeatherState {

	private static volatile LocalWeatherPayload current = LocalWeatherPayload.CLEAR;
	private static volatile NearbyStormsPayload storms = new NearbyStormsPayload(0, java.util.List.of());

	private ClientWeatherState() {
	}

	public static LocalWeatherPayload get() {
		return current;
	}

	static void set(LocalWeatherPayload payload) {
		current = payload;
	}

	/** The storms near this player, as of {@link NearbyStormsPayload#gameTime()}. */
	public static NearbyStormsPayload storms() {
		return storms;
	}

	static void setStorms(NearbyStormsPayload payload) {
		storms = payload;
	}

	public static void reset() {
		current = LocalWeatherPayload.CLEAR;
		storms = new NearbyStormsPayload(0, java.util.List.of());
	}
}
