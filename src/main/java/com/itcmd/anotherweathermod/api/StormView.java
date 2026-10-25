package com.itcmd.anotherweathermod.api;

import net.minecraft.world.phys.Vec3;

/**
 * A read-only view of a storm cell, active or still pending in the forecast. Everything about a storm is a function of
 * game time, so any of these can be asked for a future tick to forecast it.
 */
public interface StormView {

	/** Unique within its level. */
	long id();

	StormKind kind();

	/** The game tick it forms (or formed). */
	long birthTick();

	/** How long it lives, in ticks. */
	int lifetimeTicks();

	/** The game tick it is gone. */
	default long deathTick() {
		return birthTick() + lifetimeTicks();
	}

	StormStage stageAt(long gameTime);

	/** Its centre at {@code gameTime} ({@code y} is always 0). */
	Vec3 centerAt(long gameTime);

	/** Its velocity at {@code gameTime}, in blocks per tick ({@code y} is always 0). */
	Vec3 velocityAt(long gameTime);

	/** Its radius at {@code gameTime}, in blocks. 0 before it forms and after it is gone. */
	double radiusAt(long gameTime);

	/** Its overall strength at {@code gameTime}, 0..1. */
	float intensityAt(long gameTime);
}
