package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AwmClientConfig;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.network.NearbyStormsPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * This frame's {@link StormLod} view of the storms the server sent, worked out once a frame for the clouds and the
 * storm renderer alike. Its details line up with {@link NearbyStormsPayload#storms()}; hurricanes and storms that bring
 * no rain are never merged.
 */
final class DistantStorms {

	private static final StormLod.Tracker tracker = new StormLod.Tracker();
	private static NearbyStormsPayload source;
	private static double lastTime = Double.NaN;
	private static StormLod.View view = StormLod.View.NONE;
	private static long lastNanos;

	private DistantStorms() {
	}

	/** Whether far storms are merged (the client setting). */
	static boolean enabled() {
		return AwmClientConfig.DISTANT_STORM_MERGING.get();
	}

	/**
	 * The view of {@code payload}'s storms at {@code time} (game time and partial tick) from ({@code camX},
	 * {@code camZ}); the same object for the rest of the frame.
	 */
	static StormLod.View view(NearbyStormsPayload payload, double time, double camX, double camZ) {
		if(!enabled()) {
			if(view != StormLod.View.NONE)
				clear();
			return StormLod.View.NONE;
		}
		if(payload == source && time == lastTime)
			return view;
		long nanos = System.nanoTime();
		float seconds = lastNanos == 0 ? 0 : Math.min(0.25f, (nanos - lastNanos) / 1.0e9f);
		lastNanos = nanos;
		source = payload;
		lastTime = time;
		view = compute(payload, time, camX, camZ, AwmClientConfig.DISTANT_STORM_DISTANCE.get(),
				AwmClientConfig.DISTANT_STORM_BLEND.get(), tracker, seconds);
		return view;
	}

	/** The view of {@code payload} at {@code time}, its masses faded by {@code tracker} over {@code seconds}. */
	static StormLod.View compute(NearbyStormsPayload payload, double time, double camX, double camZ, double near,
			double blend, StormLod.Tracker tracker, float seconds) {
		List<NearbyStormsPayload.Storm> storms = payload.storms();
		double age = time - payload.gameTime();
		List<StormLod.Body> bodies = new ArrayList<>();
		int[] index = new int[storms.size()];
		for(int i = 0; i < storms.size(); i++) {
			NearbyStormsPayload.Storm s = storms.get(i);
			if(!s.kind().bringsRain() || s.kind() == StormKind.HURRICANE) {
				index[i] = -1;
				continue;
			}
			index[i] = bodies.size();
			bodies.add(new StormLod.Body(s.id(), s.x() + s.velocityX() * age, s.z() + s.velocityZ() * age, s.radius(),
					s.intensity(), CloudCover.kindIndex(s.kind()), CloudCover.precipitationIndex(s.precipitation()),
					s.velocityX(), s.velocityZ()));
		}
		StormLod.View raw = StormLod.view(bodies, camX, camZ, near, blend);
		double[] detail = new double[storms.size()];
		for(int i = 0; i < storms.size(); i++)
			detail[i] = index[i] < 0 ? 1 : raw.detail(index[i]);
		return new StormLod.View(detail, tracker.update(raw.masses(), seconds));
	}

	static void clear() {
		tracker.clear();
		source = null;
		lastTime = Double.NaN;
		view = StormLod.View.NONE;
		lastNanos = 0;
	}
}
