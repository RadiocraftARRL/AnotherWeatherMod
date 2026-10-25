package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmConfig;
import com.itcmd.anotherweathermod.network.ServerSettingsPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps a {@link LevelWeather} for each managed level and ticks it. */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID)
public final class WeatherManager {

	private static final Map<ResourceKey<Level>, LevelWeather> levels = new ConcurrentHashMap<>();

	private WeatherManager() {
	}

	/** The localized weather of {@code level}, or null if it is not managed (or has not ticked yet). */
	@Nullable
	public static LevelWeather get(Level level) {
		if(!(level instanceof ServerLevel))
			return null;
		LevelWeather weather = levels.get(level.dimension());
		return weather != null && weather.level() == level ? weather : null;
	}

	/** Whether the config wants localized weather in {@code level}. Only levels with a sky qualify. */
	public static boolean wanted(ServerLevel level) {
		if(!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling())
			return false;
		return AwmConfig.DIMENSIONS.get().contains(level.dimension().location().toString());
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		if(!(event.getLevel() instanceof ServerLevel level))
			return;
		LevelWeather weather = levels.get(level.dimension());
		if(weather == null || weather.level() != level) {
			if(!wanted(level))
				return;
			weather = new LevelWeather(level);
			levels.put(level.dimension(), weather);
		}
		weather.tick();
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if(event.getLevel() instanceof ServerLevel level)
			levels.remove(level.dimension());
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		levels.clear();
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		resync(event.getEntity());
		if(event.getEntity() instanceof ServerPlayer player && LevelWeather.connected(player)
				&& player.connection.hasChannel(ServerSettingsPayload.TYPE))
			PacketDistributor.sendToPlayer(player, ServerSettingsPayload.current());
	}

	/** The server config changed: tell everyone what they may now draw. Listens on the mod bus. */
	public static void onConfigReload(ModConfigEvent.Reloading event) {
		var server = ServerLifecycleHooks.getCurrentServer();
		if(event.getConfig().getSpec() != AwmConfig.SPEC || server == null)
			return;
		server.execute(() -> {
			for(ServerPlayer player : server.getPlayerList().getPlayers())
				if(LevelWeather.connected(player) && player.connection.hasChannel(ServerSettingsPayload.TYPE))
					PacketDistributor.sendToPlayer(player, ServerSettingsPayload.current());
		});
	}

	@SubscribeEvent
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		resync(event.getEntity());
	}

	@SubscribeEvent
	public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		resync(event.getEntity());
	}

	private static void resync(net.minecraft.world.entity.player.Player player) {
		if(player instanceof ServerPlayer serverPlayer) {
			LevelWeather weather = get(serverPlayer.serverLevel());
			if(weather != null)
				weather.resync(serverPlayer);
		}
	}
}
