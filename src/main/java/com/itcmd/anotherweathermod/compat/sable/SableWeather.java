package com.itcmd.anotherweathermod.compat.sable;

import com.itcmd.anotherweathermod.AwmConfig;
import com.itcmd.anotherweathermod.sim.PointWeather;
import com.itcmd.anotherweathermod.sim.StormCell;
import com.itcmd.anotherweathermod.world.LevelWeather;
import com.itcmd.anotherweathermod.world.StormProtection;
import com.itcmd.anotherweathermod.world.Tornadoes;
import com.itcmd.anotherweathermod.world.WeatherManager;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * The half of {@link SableCompat} that touches Sable's classes: only loaded when Sable is installed.
 */
final class SableWeather {

	/** Height above the ground a tornado still lifts, blocks, as for mobs. */
	private static final int LIFT_HEIGHT = 60;

	private SableWeather() {
	}

	static void register() {
		// Sable asks for the air's velocity (metres per second) where sails and propellers need it; the point may be on
		// a sub-level, so take it out to where it really is first. Server levels only: the server runs the physics.
		SubLevelHelper.registerWindProvider((pos, level) -> wind(level, pos));
	}

	private static Vector3dc wind(Level level, Vector3dc pos) {
		if(!(level instanceof ServerLevel server))
			return null;
		LevelWeather weather = WeatherManager.get(server);
		if(weather == null)
			return null;
		Vec3 at = SableCompanion.INSTANCE.projectOutOfSubLevel(server, (net.minecraft.core.Position)new Vec3(pos.x(), pos.y(), pos.z()));
		PointWeather point = weather.field().sample(at.x, at.z, server.getGameTime());
		return new Vector3d(point.windX(), 0, point.windZ());
	}

	static void tick(ServerLevel level, LevelWeather weather, long now) {
		double share = AwmConfig.SABLE_SHIP_PUSH.get();
		SubLevelContainer container = SubLevelContainer.getContainer(level);
		if(share <= 0 || container == null)
			return;
		double seconds = SableCompat.EVERY / 20.0;
		for(SubLevel subLevel : container.getAllSubLevels()) {
			if(!(subLevel instanceof ServerSubLevel ship) || ship.isRemoved())
				continue;
			double mass = ship.getMassTracker().getMass();
			Vector3dc center = ship.logicalPose().position();
			if(mass <= 0 || StormProtection.protects(level, center.x(), center.z()))
				continue;
			Vector3d push = new Vector3d();
			// Downwind, past damaging wind speeds (cheap first look: no storm near).
			if(weather.field().anyReaches(center.x(), center.z(), now, 0)) {
				PointWeather point = weather.field().sample(center.x(), center.z(), now);
				double speed = point.windSpeed();
				double accel = SableCompat.shipAcceleration(speed, share);
				if(accel > 0)
					push.add(point.windX() / speed * accel, 0, point.windZ() / speed * accel);
			}
			// Up, in a tornado's core, near the ground.
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int)Math.floor(center.x()), (int)Math.floor(center.z()));
			if(center.y() < ground + LIFT_HEIGHT)
				for(StormCell cell : weather.field().active()) {
					double strength = cell.tornadoStrength(now);
					if(strength < StormCell.TOUCHED_DOWN)
						continue;
					double radius = cell.tornadoRadius() * (0.6 + 0.4 * strength);
					double closeness = Tornadoes.closeness(new Vec3(center.x(), center.y(), center.z()), cell.tornadoX(now), cell.tornadoZ(now), radius) * strength;
					push.y += SableCompat.tornadoLift(closeness, share);
				}
			if(push.lengthSquared() < 1e-9)
				continue;
			RigidBodyHandle handle = RigidBodyHandle.of(ship);
			if(handle == null || !handle.isValid())
				continue;
			// An impulse is mass times the change in velocity; Sable takes it in the ship's own frame.
			push.mul(mass * seconds);
			handle.applyLinearImpulse(ship.logicalPose().transformNormalInverse(push, new Vector3d()));
		}
	}
}
