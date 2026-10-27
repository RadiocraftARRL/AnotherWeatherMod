package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmClientConfig;
import com.itcmd.anotherweathermod.AwmSounds;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.network.ClientWeatherState;
import com.itcmd.anotherweathermod.network.NearbyStormsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Lightning inside the clouds, and its thunder. Every thundering storm near the player flashes now and then
 * somewhere under its deck, more often the stronger it is; the mod's clouds light up round each flash (see
 * {@link CloudRenderer}), flickering through a few strokes. Some flashes are heard: a few of the far ones, most of the
 * near ones. Their thunder comes from the flash's direction once the sound has had time to get here, at 343 blocks a
 * second: a low roll that swells in and grumbles away, quieter and deeper the further off it is, and only a strike
 * within {@link #NEAR} blocks cracks first. A few rolls at most at once, so a storm rumbles rather than bangs. Real
 * bolts flash the clouds too, but their thunder is the game's own.
 *
 * <p>The flicker and the idea of thunder from a storm's bearing come from Localized Weather (MIT, see THIRD_PARTY.md).
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID, value = Dist.CLIENT)
public final class StormLightning {

	/** Flashes the cloud shader lights at once: the brightest, nearest ones. */
	public static final int SLOTS = 4;
	/** Ticks a flash lasts. */
	static final int LIFE = 18;
	/** How far round a flash the cloud lights up, blocks. */
	static final float GLOW = 96;
	/** Speed of sound, blocks a tick. */
	static final double SOUND_SPEED = 343 / 20.0;
	/** Thunder dies away this far off, blocks. */
	static final double HEARD = 1400;
	/** Strikes nearer than this, blocks, crack before they roll. */
	static final double NEAR = 100;
	/** Most rolls of thunder heard at once (a near strike is always heard). */
	static final int MAX_ROLLING = 3;
	/** Ticks a roll of thunder lasts, about. */
	static final int ROLL_TICKS = 150;
	/** Under this many blocks the sky lights up with a flash too. */
	static final double SKY_FLASH = 220;

	/** A flash: where, when, how strong, and a seed for its flicker. */
	record Flash(double x, double z, long born, float strength, float seed) {
	}

	/** Thunder on its way: where it came from, when it gets here, how loud it was there. */
	private record Rumble(double x, double z, long due, float strength) {
	}

	private static final List<Flash> flashes = new ArrayList<>();
	private static final List<Rumble> rumbles = new ArrayList<>();
	/** When each roll of thunder now sounding ends. */
	private static final List<Long> rolling = new ArrayList<>();
	private static final RandomSource random = RandomSource.create();

	private StormLightning() {
	}

	@SubscribeEvent
	public static void onTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if(level == null || mc.player == null) {
			flashes.clear();
			rumbles.clear();
			rolling.clear();
			return;
		}
		if(mc.isPaused())
			return;
		long now = level.getGameTime();
		flashes.removeIf(flash -> now - flash.born() > LIFE || now < flash.born());
		rolling.removeIf(end -> end <= now || end - now > ROLL_TICKS);
		boolean clouds = AwmClientConfig.LIGHTNING_IN_CLOUDS.get();
		boolean thunder = AwmClientConfig.DISTANT_THUNDER.get();
		if(clouds || thunder) {
			NearbyStormsPayload payload = ClientWeatherState.storms();
			double age = now - payload.gameTime();
			for(NearbyStormsPayload.Storm storm : payload.storms()) {
				if(!storm.kind().hasThunder() || storm.intensity() < 0.25f || random.nextFloat() >= rate(storm))
					continue;
				// Somewhere under the deck, more often toward the middle.
				double angle = random.nextDouble() * Math.PI * 2, out = storm.radius() * 0.7 * Math.sqrt(random.nextDouble());
				double x = storm.x() + storm.velocityX() * age + Math.cos(angle) * out;
				double z = storm.z() + storm.velocityZ() * age + Math.sin(angle) * out;
				float strength = storm.intensity() * (0.6f + 0.4f * random.nextFloat());
				if(clouds)
					flash(level, x, z, strength, now);
				if(thunder)
					rumble(mc.player, x, z, strength, now);
			}
		}
		for(Iterator<Rumble> it = rumbles.iterator(); it.hasNext(); ) {
			Rumble rumble = it.next();
			if(rumble.due() > now)
				continue;
			it.remove();
			if(thunder && now - rumble.due() < 40)
				play(level, mc.player, rumble, now);
		}
	}

	/** Chance a tick that {@code storm} flashes: more the stronger it is, every few seconds in a supercell. */
	private static float rate(NearbyStormsPayload.Storm storm) {
		int scale = storm.kind() == StormKind.HURRICANE ? 3 : Math.max(1, storm.kind().scale());
		return storm.intensity() * (0.003f + 0.0035f * scale);
	}

	/** A bolt came down: the cloud above it lights up. Its thunder is the game's. */
	@SubscribeEvent
	public static void onEntityJoin(EntityJoinLevelEvent event) {
		if(event.getLevel().isClientSide() && event.getEntity() instanceof LightningBolt bolt
				&& AwmClientConfig.LIGHTNING_IN_CLOUDS.get())
			flashes.add(new Flash(bolt.getX(), bolt.getZ(), event.getLevel().getGameTime(), 1, random.nextFloat() * 10));
	}

	private static void flash(ClientLevel level, double x, double z, float strength, long now) {
		flashes.add(new Flash(x, z, now, strength, random.nextFloat() * 10));
		Player player = Minecraft.getInstance().player;
		if(player != null && strength > 0.5f && Math.hypot(x - player.getX(), z - player.getZ()) < SKY_FLASH
				&& level.getSkyFlashTime() == 0)
			level.setSkyFlashTime(2);
	}

	/** A flash's thunder sets out, if it is one that is heard: few far off, most close by. */
	private static void rumble(Player player, double x, double z, float strength, long now) {
		double distance = Math.hypot(x - player.getX(), z - player.getZ());
		double near = 1 - distance / HEARD;
		if(near > 0 && (distance < NEAR || random.nextDouble() < 0.1 + 0.8 * near * near))
			rumbles.add(new Rumble(x, z, now + (long)(distance / SOUND_SPEED), strength));
	}

	/**
	 * Thunder from {@code rumble}'s direction: placed a few blocks from the player that way, so it pans, at a loudness
	 * for how far off it really is. Far thunder only rolls; a near strike cracks first.
	 */
	private static void play(ClientLevel level, Player player, Rumble rumble, long now) {
		double dx = rumble.x() - player.getX(), dz = rumble.z() - player.getZ();
		double distance = Math.hypot(dx, dz);
		boolean close = distance < NEAR;
		if(!close && rolling.size() >= MAX_ROLLING)
			return;
		float near = (float)Mth.clamp(1 - distance / HEARD, 0, 1);
		float volume = close ? 0.55f + 0.35f * rumble.strength()
				: (float)Math.pow(near, 1.6) * (0.25f + 0.4f * rumble.strength());
		if(volume < 0.03f)
			return;
		double ux = distance < 1 ? 0 : dx / distance, uz = distance < 1 ? 0 : dz / distance;
		// Deeper the further off: the high end is lost on the way.
		float pitch = close ? 0.9f + random.nextFloat() * 0.1f : 0.78f + 0.2f * near + random.nextFloat() * 0.06f;
		level.playLocalSound(player.getX() + ux * 6, player.getEyeY() + 4, player.getZ() + uz * 6,
				(close ? AwmSounds.NEAR_THUNDER : AwmSounds.DISTANT_THUNDER).get(), SoundSource.WEATHER, volume, pitch,
				false);
		rolling.add(now + ROLL_TICKS);
	}

	/** How bright {@code flash} is at {@code time} (ticks, with the partial tick), 0 to 1: a few flickering strokes. */
	static float brightness(Flash flash, double time) {
		double age = time - flash.born();
		if(age < 0 || age > LIFE)
			return 0;
		double phase = age * 1.9 + flash.seed();
		double flicker = Math.abs(Math.sin(phase) * Math.sin(phase * 1.31) * Math.sin(phase * 0.77));
		return (float)(flash.strength() * Math.exp(-age / 6) * (0.3 + 0.7 * Math.min(1, flicker * 2.2)));
	}

	/**
	 * The {@link #SLOTS} flashes the clouds should show at {@code time}, as x and z (relative to the camera), glow
	 * radius and brightness; unused slots are all zero.
	 */
	static float[][] slots(double cameraX, double cameraZ, double time) {
		float[][] out = new float[SLOTS][4];
		List<Flash> lit = new ArrayList<>(flashes);
		lit.sort(Comparator.comparingDouble(flash -> -brightness(flash, time)
				/ (1 + Math.hypot(flash.x() - cameraX, flash.z() - cameraZ) / 400)));
		for(int i = 0; i < SLOTS && i < lit.size(); i++) {
			Flash flash = lit.get(i);
			out[i] = new float[] { (float)(flash.x() - cameraX), (float)(flash.z() - cameraZ), GLOW,
					brightness(flash, time) };
		}
		return out;
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		flashes.clear();
		rumbles.clear();
		rolling.clear();
	}
}
