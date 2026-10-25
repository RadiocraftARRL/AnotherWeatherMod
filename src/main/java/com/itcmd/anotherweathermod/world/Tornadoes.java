package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.AwmConfig;
import com.itcmd.anotherweathermod.AwmGameRules;
import com.itcmd.anotherweathermod.sim.PointWeather;
import com.itcmd.anotherweathermod.sim.StormCell;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What a tornado does on the ground, where it is loaded: tears at the blocks along its track (its core and a ring
 * round it, through tree crowns) and lifts what it catches. The wind field round it pushes things too
 * ({@link WindPush}); this adds the lift, which the wind alone does not.
 */
public final class Tornadoes {

	/** Columns a full-strength EF0 tornado works on each tick; each rating up adds {@link #COLUMNS_PER_EF}. */
	static final int COLUMNS = 6;
	static final int COLUMNS_PER_EF = 4;
	/** Blocks down from the surface it reaches: through a tree's crown into its trunk. */
	static final int DEPTH = 4;
	/** An EF4 or EF5 tears down further each pass, to the ground: whole trees, whole houses. */
	static final int DEMOLISH_DEPTH = 8;
	/** Most blocks an EF4 or EF5 tears away in a tick, to keep the server's load bearable. */
	static final int DEMOLISH_BUDGET = 96;
	/** Upward push in the core, blocks per tick per tick, and the fastest it lifts anything. */
	public static final double LIFT = 0.14;
	public static final double MAX_RISE = 0.9;

	private Tornadoes() {
	}

	static void tick(ServerLevel level, LevelWeather weather, long now) {
		boolean damage = AwmConfig.TORNADO_DAMAGE.get() && level.getGameRules().getBoolean(AwmGameRules.DO_WIND_DAMAGE);
		for(StormCell cell : weather.field().active()) {
			double strength = cell.tornadoStrength(now);
			if(strength < StormCell.TOUCHED_DOWN)
				continue;
			double tx = cell.tornadoX(now), tz = cell.tornadoZ(now);
			BlockPos base = BlockPos.containing(tx, level.getSeaLevel(), tz);
			if(!level.isLoaded(base))
				continue;
			double radius = cell.tornadoRadius() * (0.6 + 0.4 * strength);
			if(damage)
				tear(level, weather, now, cell, tx, tz, radius, strength);
			lift(level, now, tx, tz, radius, strength);
		}
	}

	private static void tear(ServerLevel level, LevelWeather weather, long now, StormCell cell, double tx, double tz,
			double radius, double strength) {
		// Wider tornadoes cover more ground: more columns, by area, past a 16 block core.
		double area = Math.max(1, radius * radius / 256);
		int columns = Math.max(1, (int)Math.round((COLUMNS + COLUMNS_PER_EF * cell.tornadoRating()) * area * strength));
		double top = cell.tornadoWind() * strength;
		boolean violent = cell.tornadoRating() >= 4;
		int budget = DEMOLISH_BUDGET;
		for(int i = 0; i < columns; i++) {
			// Most of the damage in the core, some in the ring round it.
			double d = radius * 1.6 * Math.pow(level.random.nextDouble(), 0.7);
			double angle = level.random.nextDouble() * Math.PI * 2;
			double x = tx + Math.cos(angle) * d, z = tz + Math.sin(angle) * d;
			BlockPos column = BlockPos.containing(x, 0, z);
			if(!level.isLoaded(column))
				continue;
			PointWeather point = weather.field().sample(x, z, now);
			// The wind model's core is calm at the very centre, but a real tornado's core is the worst of it (suction
			// vortices, debris): full strength across the core, falling off beyond.
			double speed = Math.max(point.windSpeed(), d < radius ? top : top * Math.pow(radius / d, 1.2));
			BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
			// A violent tornado's core levels the column down to stone, earth or water.
			boolean demolish = violent && d < radius * 1.2;
			for(int down = 0; down < (demolish ? DEMOLISH_DEPTH : DEPTH); down++) {
				BlockPos at = surface.below(down);
				if(demolish && (budget <= 0 || level.getBlockState(at).is(AwmTags.TORNADO_PROOF)
						|| !level.getBlockState(at).getFluidState().isEmpty()))
					break;
				if(WindDamage.apply(level, at, speed, point.windX(), point.windZ(), demolish) && demolish)
					budget--;
			}
		}
	}

	/** Lifts and spins what is in the core. Players lift themselves on their own client, the same way. */
	private static void lift(ServerLevel level, long now, double tx, double tz, double radius, double strength) {
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int)Math.floor(tx), (int)Math.floor(tz));
		AABB box = new AABB(tx - radius * 2, ground - 4, tz - radius * 2, tx + radius * 2, ground + 60, tz + radius * 2);
		for(Entity entity : level.getEntities((Entity)null, box, e -> !(e instanceof Player) && !e.isSpectator()
				&& !e.noPhysics && !e.isPassenger())) {
			if(entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight() >= WindPush.TOO_BIG
					|| StormProtection.protects(level, entity.getX(), entity.getZ()))
				continue;
			double closeness = closeness(entity.position(), tx, tz, radius) * strength;
			if(closeness <= 0)
				continue;
			Vec3 v = entity.getDeltaMovement();
			entity.setDeltaMovement(v.x, Math.min(MAX_RISE, v.y + LIFT * closeness), v.z);
			entity.fallDistance = 0; // Only for the ride up; what comes down still falls.
			entity.hurtMarked = true;
		}
	}

	/** 1 at a tornado's centre, 0 from twice its radius out. */
	public static double closeness(Vec3 pos, double tx, double tz, double radius) {
		double d = Math.hypot(pos.x - tx, pos.z - tz);
		return Math.max(0, 1 - d / (2 * radius));
	}
}
