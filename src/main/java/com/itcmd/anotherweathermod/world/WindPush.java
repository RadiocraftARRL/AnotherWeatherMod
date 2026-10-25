package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmConfig;
import com.itcmd.anotherweathermod.api.WeatherSample;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;

/**
 * Strong wind shoves things downwind: mobs, animals, items and the like (here, on the server) and the player (by the
 * client, which moves its own player; see {@code WeatherEffects}). Really big things (iron golems, ghasts, the
 * dragon) stand firm, heavy ones are pushed less, and gold armor (the {@link #BALLAST} tag) weighs you down: each piece
 * takes a quarter off, a full set holds you still. Out of the wind (under a roof, or a wall upwind) nothing moves.
 */
public final class WindPush {

	/** Wind speed, metres per second, from which the wind pushes, and at which it pushes hardest. */
	public static final double FROM = 22;
	public static final double FULL = 65;
	/** The push at {@link #FULL}, blocks per tick added each tick: walking against it barely gets anywhere. */
	public static final double MAX = 0.05;
	/** Bounding box volume, cubic blocks, from which an entity is too big to push. */
	static final double TOO_BIG = 4;
	/** Armor that weighs its wearer down against the wind. */
	public static final TagKey<Item> BALLAST = TagKey.create(Registries.ITEM, AnotherWeatherMod.id("wind_ballast"));
	static final int RANGE = 48;

	private WindPush() {
	}

	/** The push, blocks per tick per tick, of wind at {@code speed} on something of ordinary size. */
	public static double force(double speed) {
		if(speed <= FROM)
			return 0;
		return Math.pow(Math.min(1, (speed - FROM) / (FULL - FROM)), 1.5) * MAX;
	}

	/** Whether {@code entity} is out of the wind blowing toward ({@code windX}, {@code windZ}). */
	public static boolean sheltered(Level level, Entity entity, double windX, double windZ) {
		BlockPos head = BlockPos.containing(entity.getEyePosition());
		if(!level.canSeeSky(head))
			return true;
		double speed = Math.hypot(windX, windZ);
		if(speed < 1e-6)
			return true;
		// A solid block upwind, at the feet or head, breaks the wind.
		for(int step = 1; step <= 2; step++)
			for(BlockPos at : new BlockPos[] { head, entity.blockPosition() }) {
				BlockPos upwind = BlockPos.containing(at.getX() + 0.5 - windX / speed * step, at.getY() + 0.5,
						at.getZ() + 0.5 - windZ / speed * step);
				if(level.isLoaded(upwind) && level.getBlockState(upwind).isSolidRender(level, upwind))
					return true;
			}
		return false;
	}

	/**
	 * Pushes {@code entity} by {@code force} downwind, less for its size and its ballast. Caught in the air it goes
	 * further; sneaking or swimming it holds on better.
	 */
	public static void push(Entity entity, double windX, double windZ, double force) {
		double speed = Math.hypot(windX, windZ);
		if(speed < 1e-6 || force <= 0)
			return;
		double volume = entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight();
		if(volume >= TOO_BIG)
			return;
		force /= 1 + Math.max(0, volume - 0.9); // A cow or a horse leans into it better than a zombie.
		if(entity instanceof LivingEntity living)
			force *= 1 - ballast(living) / 4.0;
		if(entity.isShiftKeyDown())
			force *= 0.5;
		if(entity.isInWater())
			force *= 0.3;
		else if(!entity.onGround())
			force *= 1.4;
		if(force > 0)
			entity.setDeltaMovement(entity.getDeltaMovement().add(windX / speed * force, 0, windZ / speed * force));
	}

	/** How many pieces of {@link #BALLAST} armor {@code living} wears, 0 to 4. */
	public static int ballast(LivingEntity living) {
		int pieces = 0;
		for(EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
				EquipmentSlot.FEET })
			if(living.getItemBySlot(slot).is(BALLAST))
				pieces++;
		return pieces;
	}

	/** Pushes everything but players near each player, every other tick (twice as hard). */
	static void tick(ServerLevel level, LevelWeather weather, long now) {
		if(now % 2 != 0 || !AwmConfig.WIND_PUSH.get())
			return;
		Set<Entity> done = new HashSet<>();
		for(ServerPlayer player : level.players()) {
			if(player.isSpectator())
				continue;
			// Cheap first look: no storm near enough to blow here.
			if(!weather.field().anyReaches(player.getX(), player.getZ(), now, RANGE))
				continue;
			AABB box = player.getBoundingBox().inflate(RANGE, 24, RANGE);
			for(Entity entity : level.getEntities((Entity)null, box, e -> !(e instanceof Player) && !e.isSpectator()
					&& !e.noPhysics && !e.isPassenger() && !e.isNoGravity())) {
				if(!done.add(entity) || StormProtection.protects(level, entity.getX(), entity.getZ()))
					continue;
				WeatherSample sample = weather.sample(entity.blockPosition(), now);
				double force = force(sample.windSpeed());
				if(force > 0 && !sheltered(level, entity, sample.wind().x, sample.wind().z)) {
					push(entity, sample.wind().x, sample.wind().z, force * 2);
					entity.hurtMarked = true; // Send the new motion to clients.
				}
			}
		}
	}
}
