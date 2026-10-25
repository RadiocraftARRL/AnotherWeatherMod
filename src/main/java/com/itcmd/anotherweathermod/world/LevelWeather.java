package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.AwmConfig;
import com.itcmd.anotherweathermod.api.Hazard;
import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.WeatherSample;
import com.itcmd.anotherweathermod.api.event.HazardEvent;
import com.itcmd.anotherweathermod.api.event.StormEvent;
import com.itcmd.anotherweathermod.network.LocalWeatherPayload;
import com.itcmd.anotherweathermod.sim.PointWeather;
import com.itcmd.anotherweathermod.sim.SteeringWind;
import com.itcmd.anotherweathermod.sim.StormCell;
import com.itcmd.anotherweathermod.sim.StormField;
import com.itcmd.anotherweathermod.sim.StormGenerator;
import com.itcmd.anotherweathermod.sim.WeatherModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Localized weather for one level: its storms, the forecast, and what each player in it is sent. Vanilla's global
 * weather stays clear in a managed level (see {@code MixinServerLevel}); {@code /weather} still works, as a world-wide
 * override for its duration.
 */
public final class LevelWeather {

	private final ServerLevel level;
	private final StormField field;
	private final StormGenerator generator;
	private final LevelClimate climate;
	private final WeatherSavedData data;
	private final Regrowth regrowth = new Regrowth();
	private final Map<UUID, PlayerTrack> players = new HashMap<>();

	private volatile boolean overrideRain;
	private volatile boolean overrideThunder;

	LevelWeather(ServerLevel level) {
		this.level = level;
		long seed = level.getSeed() ^ level.dimension().location().hashCode() * 0x9e3779b97f4a7c15L;
		this.field = new StormField(new SteeringWind(seed));
		this.generator = new StormGenerator(seed);
		this.climate = new LevelClimate(level);
		this.data = WeatherSavedData.get(level);
		if(data.loaded != null)
			field.load(data.loaded, level.getGameTime());
		data.field = field;
		if(data.loadedRegrowth != null)
			regrowth.load(data.loadedRegrowth, level.holderLookup(Registries.BLOCK));
		data.regrowth = regrowth;
		if(!data.initialized) {
			// Whatever vanilla weather was going on stops: from now on storms bring the rain.
			if(level.dimension() == Level.OVERWORLD)
				level.setWeatherParameters(0, 0, false, false);
			data.initialized = true;
			data.setDirty();
		}
	}

	public ServerLevel level() {
		return level;
	}

	public StormField field() {
		return field;
	}

	public Regrowth regrowth() {
		return regrowth;
	}

	public LevelClimate climate() {
		return climate;
	}

	public boolean overrideRain() {
		return overrideRain;
	}

	void tick() {
		long now = level.getGameTime();
		// The global weather stays clear; storms bring the rain.
		level.setRainLevel(0);
		level.setThunderLevel(0);
		tickOverride();

		if(now % 20 == 0) {
			long before = field.nextSlotTick();
			List<StormGenerator.Target> targets = new ArrayList<>();
			for(ServerPlayer player : level.players())
				if(!player.isSpectator())
					targets.add(new StormGenerator.Target(player.getX(), player.getZ()));
			for(StormCell cell : generator.topUp(field, now, targets, climate, AwmConfig.generatorSettings()))
				NeoForge.EVENT_BUS.post(new StormEvent.Forecast(level, cell));
			if(field.nextSlotTick() != before)
				data.setDirty();
			int scars = regrowth.size();
			regrowth.tick(this);
			if(regrowth.size() != scars)
				data.setDirty();
		}

		StormField.Changes changes = field.advance(now);
		for(StormCell cell : changes.formed())
			NeoForge.EVENT_BUS.post(new StormEvent.Formed(level, cell));
		for(StormField.StageChange change : changes.stages())
			NeoForge.EVENT_BUS.post(new StormEvent.StageChanged(level, change.cell(), change.from(), change.to()));
		for(StormCell cell : changes.dissipated())
			NeoForge.EVENT_BUS.post(new StormEvent.Dissipated(level, cell));
		if(changes.any())
			data.setDirty();

		tickPlayers(now);
	}

	/**
	 * {@code /weather rain} and {@code /weather thunder} set the overworld's weather flags with a duration. Vanilla's
	 * own countdown is off in managed levels, so it runs here.
	 */
	private void tickOverride() {
		ServerLevelData overworld = level.getServer().getWorldData().overworldData();
		if(level.dimension() == Level.OVERWORLD) {
			if(overworld.isRaining() && countDown(overworld.getRainTime(), overworld::setRainTime))
				overworld.setRaining(false);
			if(overworld.isThundering() && countDown(overworld.getThunderTime(), overworld::setThunderTime))
				overworld.setThundering(false);
		}
		overrideRain = overworld.isRaining();
		overrideThunder = overrideRain && overworld.isThundering();
	}

	/** Counts {@code time} down by one tick; true when it has run out. */
	private static boolean countDown(int time, java.util.function.IntConsumer set) {
		if(time <= 1) {
			set.accept(0);
			return true;
		}
		set.accept(time - 1);
		return false;
	}

	private void tickPlayers(long now) {
		float ease = AwmConfig.RAIN_EASE_PER_TICK.get().floatValue();
		Set<UUID> seen = new java.util.HashSet<>();
		for(ServerPlayer player : level.players()) {
			seen.add(player.getUUID());
			PlayerTrack track = players.computeIfAbsent(player.getUUID(), k -> new PlayerTrack());
			WeatherSample sample = sample(player.blockPosition(), now);
			float rain = overrideRain ? 1 : sample.rainLevel();
			float thunder = overrideThunder ? 1 : sample.thunderLevel();
			track.update(player, rain, thunder, ease);
			track.updateHazards(player, sample);
			if(sample.has(Hazard.HAIL) && AwmConfig.HAIL_DAMAGE.get())
				Hail.pelt(level, player);
			if(now % 10 == 0 && player.connection.hasChannel(LocalWeatherPayload.TYPE)) {
				StormCell storm = sample.inStorm() ? field.find(sample.stormId()) : null;
				PacketDistributor.sendToPlayer(player, LocalWeatherPayload.of(sample, storm == null ? null : storm.kind()));
			}
		}
		players.keySet().retainAll(seen);
	}

	/** Makes the next tick send {@code player} their weather in full (they joined, respawned or changed dimension). */
	void resync(ServerPlayer player) {
		PlayerTrack track = players.get(player.getUUID());
		if(track != null)
			track.fresh = true;
	}

	/** The weather at {@code pos} at {@code gameTime}. */
	public WeatherSample sample(BlockPos pos, long gameTime) {
		PointWeather point = field.sample(pos.getX() + 0.5, pos.getZ() + 0.5, gameTime);
		Precipitation base = climate.basePrecipitation(pos.getX(), pos.getY(), pos.getZ());
		float temperature = climate.biomeTemperature(pos.getX(), pos.getY(), pos.getZ());
		long dayTime = level.getDayTime() + (gameTime - level.getGameTime());
		return WeatherModel.sample(point, base, temperature, pos.getY() - climate.seaLevel(),
				WeatherModel.night(dayTime));
	}

	/** Local rain level at ({@code x}, {@code z}) now, 0..1, counting the {@code /weather} override. */
	public double rainLevel(double x, double z) {
		if(overrideRain)
			return 1;
		long now = level.getGameTime();
		if(!field.anyCovers(x, z, now))
			return 0;
		return field.sample(x, z, now).rain();
	}

	/** {@code Level.isRainingAt} for a managed level: vanilla's sky and biome checks, with the local rain. */
	public boolean isRainingAt(BlockPos pos) {
		if(rainLevel(pos.getX() + 0.5, pos.getZ() + 0.5) <= WeatherModel.RAINING)
			return false;
		if(!level.canSeeSky(pos))
			return false;
		if(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() > pos.getY())
			return false;
		return level.getBiome(pos).value().getPrecipitationAt(pos) == net.minecraft.world.level.biome.Biome.Precipitation.RAIN;
	}

	public void markDirty() {
		data.setDirty();
	}

	/** What one player has been sent. */
	private static final class PlayerTrack {
		float rain;
		float thunder;
		boolean raining;
		boolean fresh = true;
		Set<Hazard> hazards = EnumSet.noneOf(Hazard.class);

		void update(ServerPlayer player, float targetRain, float targetThunder, float ease) {
			float newRain = approach(rain, targetRain, ease);
			float newThunder = approach(thunder, targetThunder, ease);
			var connection = player.connection;
			if(fresh) {
				raining = newRain > 0;
				connection.send(new ClientboundGameEventPacket(raining ? ClientboundGameEventPacket.START_RAINING
						: ClientboundGameEventPacket.STOP_RAINING, 0));
				connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, newRain));
				connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, newThunder));
				fresh = false;
			}
			else {
				if(newRain > 0 && !raining) {
					connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0));
					raining = true;
				}
				else if(newRain == 0 && raining) {
					// STOP_RAINING sets the client's rain to 1, so the level follows it.
					connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0));
					raining = false;
					rain = -1;
				}
				if(newRain != rain)
					connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, newRain));
				if(newThunder != thunder)
					connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, newThunder));
			}
			rain = newRain;
			thunder = newThunder;
		}

		void updateHazards(ServerPlayer player, WeatherSample sample) {
			if(hazards.equals(sample.hazards()))
				return;
			for(Hazard hazard : hazards)
				if(!sample.has(hazard))
					NeoForge.EVENT_BUS.post(new HazardEvent.Left(player, hazard, sample));
			for(Hazard hazard : sample.hazards())
				if(!hazards.contains(hazard))
					NeoForge.EVENT_BUS.post(new HazardEvent.Entered(player, hazard, sample));
			hazards = sample.hazards().isEmpty() ? EnumSet.noneOf(Hazard.class) : EnumSet.copyOf(sample.hazards());
		}

		private static float approach(float from, float to, float step) {
			if(from < 0)
				from = 0;
			if(Math.abs(to - from) <= step)
				return to;
			return from < to ? from + step : from - step;
		}
	}
}
