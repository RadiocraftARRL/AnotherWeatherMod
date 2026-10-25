package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.network.ClientWeatherState;
import com.itcmd.anotherweathermod.network.NearbyStormsPayload;
import com.itcmd.anotherweathermod.sim.StormCell;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where each tornado in sight is, smoothed. The server sends tornado positions every two seconds and the client
 * carries them on by their storm's motion, so left alone a funnel would jump each time a new position comes in (a
 * tornado wanders off its storm's straight line) and its foot would bob with every tree and dip under it. Here the
 * position glides toward the latest estimate, the foot follows the ground averaged over the funnel's width (leaves
 * left out), and a funnel that lifts thins away instead of vanishing.
 * <p>
 * It also tracks how far down each funnel reaches: a new tornado lowers out of the cloud as a thin rope and widens as
 * it comes down, touching the ground once the server's tornado is strong enough to do damage
 * ({@link StormCell#TOUCHED_DOWN}); a dying one draws back up into the cloud.
 */
public final class TornadoTracker {

	/**
	 * A tornado as drawn, at a moment between ticks. {@code reach} is how far down from the cloud it reaches, 0 to 1
	 * (on the ground).
	 */
	public record Funnel(long id, double x, double z, double ground, double radius, float strength, float reach) {

		/** The radius of its core, thin while it lowers and full once down. */
		public double coreRadius() {
			return radius * (0.3 + 0.7 * reach);
		}
	}

	/** Values per tornado, now and then again for last tick: x, z, ground, radius, strength, reach. */
	private static final int N = 6;
	/** Most a funnel lowers or rises in a tick, as a share of the way down: about 12 seconds from cloud to ground. */
	static final double REACH_RATE = 0.004;

	private static final Map<Long, double[]> tracks = new HashMap<>();

	private TornadoTracker() {
	}

	/** Moves every tornado on a tick. Call once a client tick. */
	static void tick(ClientLevel level) {
		NearbyStormsPayload payload = ClientWeatherState.storms();
		double age = level.getGameTime() - payload.gameTime();
		Set<Long> seen = new HashSet<>();
		for(NearbyStormsPayload.Storm storm : payload.storms()) {
			if(storm.tornado() <= 0)
				continue;
			seen.add(storm.id());
			double tx = storm.tornadoX() + storm.velocityX() * age;
			double tz = storm.tornadoZ() + storm.velocityZ() * age;
			double ground = ground(level, tx, tz, storm.tornadoRadius());
			double reach = Math.min(1, storm.tornado() / StormCell.TOUCHED_DOWN);
			double[] t = tracks.get(storm.id());
			if(t == null) {
				tracks.put(storm.id(), new double[] { tx, tz, ground, storm.tornadoRadius(), 0, 0, tx, tz, ground,
						storm.tornadoRadius(), 0, 0 });
				continue;
			}
			System.arraycopy(t, 0, t, N, N);
			t[0] += (tx - t[0]) * 0.06;
			t[1] += (tz - t[1]) * 0.06;
			t[2] += (ground - t[2]) * 0.05;
			t[3] += (storm.tornadoRadius() - t[3]) * 0.05;
			t[4] += (storm.tornado() - t[4]) * 0.04;
			t[5] += Mth.clamp(reach - t[5], -REACH_RATE, REACH_RATE);
		}
		// Gone from the payload (lifted, or out of range): draw back up and thin away, then forget.
		for(Iterator<Map.Entry<Long, double[]>> it = tracks.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, double[]> e = it.next();
			if(seen.contains(e.getKey()))
				continue;
			double[] t = e.getValue();
			System.arraycopy(t, 0, t, N, N);
			t[4] -= 0.01;
			t[5] = Math.max(0, t[5] - REACH_RATE * 2);
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
			funnels.add(new Funnel(e.getKey(), Mth.lerp(partialTick, t[N], t[0]), Mth.lerp(partialTick, t[N + 1], t[1]),
					Mth.lerp(partialTick, t[N + 2], t[2]), Mth.lerp(partialTick, t[N + 3], t[3]),
					(float)Math.max(0, Mth.lerp(partialTick, t[N + 4], t[4])),
					(float)Mth.lerp(partialTick, t[N + 5], t[5])));
		}
		return funnels;
	}

	/** The tornado {@code id} now, or null once it is gone. */
	public static Funnel funnel(long id) {
		double[] t = tracks.get(id);
		return t == null ? null : new Funnel(id, t[0], t[1], t[2], t[3], (float)Math.max(0, t[4]), (float)t[5]);
	}

	static void reset() {
		tracks.clear();
	}
}
