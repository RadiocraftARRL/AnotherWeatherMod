package com.itcmd.anotherweathermod.api.event;

import com.itcmd.anotherweathermod.api.Hazard;
import com.itcmd.anotherweathermod.api.WeatherSample;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** A player walked (or was caught) into, or out of, dangerous weather. Posted on the server thread. */
public abstract class HazardEvent extends Event {

	private final ServerPlayer player;
	private final Hazard hazard;
	private final WeatherSample sample;

	protected HazardEvent(ServerPlayer player, Hazard hazard, WeatherSample sample) {
		this.player = player;
		this.hazard = hazard;
		this.sample = sample;
	}

	public ServerPlayer getPlayer() {
		return player;
	}

	public Hazard getHazard() {
		return hazard;
	}

	/** The weather at the player when it changed. */
	public WeatherSample getSample() {
		return sample;
	}

	public static class Entered extends HazardEvent {
		public Entered(ServerPlayer player, Hazard hazard, WeatherSample sample) {
			super(player, hazard, sample);
		}
	}

	public static class Left extends HazardEvent {
		public Left(ServerPlayer player, Hazard hazard, WeatherSample sample) {
			super(player, hazard, sample);
		}
	}
}
