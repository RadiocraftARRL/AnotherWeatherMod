package com.itcmd.anotherweathermod.api.event;

import com.itcmd.anotherweathermod.api.StormStage;
import com.itcmd.anotherweathermod.api.StormView;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

/** A storm's life. Posted on {@code NeoForge.EVENT_BUS} on the server thread. */
public abstract class StormEvent extends Event {

	private final ServerLevel level;
	private final StormView storm;

	protected StormEvent(ServerLevel level, StormView storm) {
		this.level = level;
		this.storm = storm;
	}

	public ServerLevel getLevel() {
		return level;
	}

	public StormView getStorm() {
		return storm;
	}

	/** The forecast decided on a new storm. It forms at {@link StormView#birthTick()}. */
	public static class Forecast extends StormEvent {
		public Forecast(ServerLevel level, StormView storm) {
			super(level, storm);
		}
	}

	/** A storm formed. */
	public static class Formed extends StormEvent {
		public Formed(ServerLevel level, StormView storm) {
			super(level, storm);
		}
	}

	/** A storm moved to a new stage of its life. */
	public static class StageChanged extends StormEvent {
		private final StormStage from;
		private final StormStage to;

		public StageChanged(ServerLevel level, StormView storm, StormStage from, StormStage to) {
			super(level, storm);
			this.from = from;
			this.to = to;
		}

		public StormStage getFrom() {
			return from;
		}

		public StormStage getTo() {
			return to;
		}
	}

	/** A storm is gone (it died, or a command removed it). */
	public static class Dissipated extends StormEvent {
		public Dissipated(ServerLevel level, StormView storm) {
			super(level, storm);
		}
	}
}
