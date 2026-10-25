package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.network.ClientWeatherState;
import com.itcmd.anotherweathermod.network.NearbyStormsPayload;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Where each tornado in sight is, smoothed. The server sends tornado positions every two seconds and the client
 * carries them on by their storm's motion, so left alone a funnel would jump each time a new position comes in (a
 * tornado wanders off its storm's straight line) and its foot would bob with every tree and dip under it. Here the
 * position glides toward the latest estimate, the foot follows the ground averaged over the funnel's width (leaves
 * left out), and a funnel that lifts thins away instead of vanishing.
 */
public final class TornadoTracker {

	/** A tornado as drawn, at a moment between ticks. */
	public record Funnel(long id, double x, double z, double ground, double radius, float strength) {
	}

	/** Per tornado: now and last tick, x, z, ground, radius, strength. */
	private static final Map<Long, double[]> tracks = new HashMap<>();

	private TornadoTracker() {
	}

	/** Moves every tornado on a tick. Call once a client tick. */
	static void tick(ClientLevel level) {
		NearbyStormsPayload payload = ClientWeatherState.storms();
		double age = level.getGameTime() - payload.gameTime();
		java.util.Set<Long> seen = new java.util.HashSet<>();
		for(NearbyStormsPayload.Storm storm : payload.storms()) {
			if(storm.tornado() <= 0)
				continue;
			seen.add(storm.id());
			double tx = storm.tornadoX() + storm.velocityX() * age;
			double tz = storm.tornadoZ() + storm.velocityZ() * age;
			double ground = ground(level, tx, tz, storm.tornadoRadius());
			double[] t = tracks.get(storm.id());
			if(t == null) {
				tracks.put(storm.id(), new double[] { tx, tz, ground, storm.tornadoRadius(), 0, tx, tz, ground,
						storm.tornadoRadius(), 0 });
				continue;
			}
			System.arraycopy(t, 0, t, 5, 5);
			t[0] += (tx - t[0]) * 0.06;
			t[1] += (tz - t[1]) * 0.06;
			t[2] += (ground - t[2]) * 0.05;
			t[3] += (storm.tornadoRadius() - t[3]) * 0.05;
			t[4] += (storm.tornado() - t[4]) * 0.04;
		}
		// Gone from the payload (lifted, or out of range): thin away, then forget.
		for(Iterator<Map.Entry<Long, double[]>> it = tracks.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, double[]> e = it.next();
			if(seen.contains(e.getKey()))
				continue;
			double[] t = e.getValue();
			System.arraycopy(t, 0, t, 5, 5);
			t[4] -= 0.02;
			if(t[4] <= 0)
				it.remove();
		}
	}

	/** The ground under a funnel: the mean height over its foot, trees left out. */
	private static double ground(ClientLevel level, double x, double z, double radius) {
		double sum = 0;
		int count = 0;
		for(int i = -1; i <= 1; i++)
			for(int j = -1; j <= 1; j++) {
				BlockPos at = BlockPos.containing(x + i * radius * 0.5, 0, z + j * radius * 0.5);
				if(!level.isLoaded(at))
					continue;
				sum += level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
				count++;
			}
		return count == 0 ? level.getSeaLevel() : sum / count;
	}

	/** Every tornado in sight, between the last tick and this one. */
	public static List<Funnel> funnels(float partialTick) {
		List<Funnel> funnels = new ArrayList<>(tracks.size());
		for(Map.Entry<Long, double[]> e : tracks.entrySet()) {
			double[] t = e.getValue();
			funnels.add(new Funnel(e.getKey(), Mth.lerp(partialTick, t[5], t[0]), Mth.lerp(partialTick, t[6], t[1]),
					Mth.lerp(partialTick, t[7], t[2]), Mth.lerp(partialTick, t[8], t[3]),
					(float)Math.max(0, Mth.lerp(partialTick, t[9], t[4]))));
		}
		return funnels;
	}

	/** The tornado {@code id} now, or null once it is gone. */
	public static Funnel funnel(long id) {
		double[] t = tracks.get(id);
		return t == null ? null : new Funnel(id, t[0], t[1], t[2], t[3], (float)Math.max(0, t[4]));
	}

	static void reset() {
		tracks.clear();
	}
}
