package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmClientConfig;
import com.itcmd.anotherweathermod.network.ClientWeatherState;
import com.itcmd.anotherweathermod.network.LocalWeatherPayload;
import com.itcmd.anotherweathermod.network.NearbyStormsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.Locale;

/**
 * {@code /awmclient debug}: what this client knows of the weather and what its clouds make of it, for working out why
 * the sky looks the way it does. Runs on the client alone, so it works on any server.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID, value = Dist.CLIENT)
public final class ClientDebugCommand {

	private ClientDebugCommand() {
	}

	@SubscribeEvent
	public static void onRegister(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("awmclient")
				.then(Commands.literal("debug").executes(ctx -> debug(ctx.getSource()))));
	}

	private static int debug(CommandSourceStack source) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if(level == null || mc.player == null)
			return 0;
		double px = mc.player.getX(), pz = mc.player.getZ();
		long now = level.getGameTime();
		NearbyStormsPayload payload = ClientWeatherState.storms();
		double age = now - payload.gameTime();
		LocalWeatherPayload local = ClientWeatherState.get();
		say(source, String.format(Locale.ROOT, "Here: %s rain %.2f, storm %s%s; storms payload %d ticks old, %d storm(s)",
				local.precipitation(), local.rain(), local.stormKind(), CloudCover.overcast(local) ? " (rain with no storm: vanilla's /weather override, overcast deck drawn)" : "",
				(long)age, payload.storms().size()));
		for(NearbyStormsPayload.Storm s : payload.storms()) {
			double sx = s.x() + s.velocityX() * age, sz = s.z() + s.velocityZ() * age;
			double d = Math.hypot(sx - px, sz - pz);
			say(source, String.format(Locale.ROOT, " #%d %s %s: %.0f blocks off (%s), radius %.0f, rain edge %.0f, intensity %.2f",
					s.id(), s.kind(), s.precipitation(), d, d < s.rainEdge() ? "in its rain" : d < s.radius() ? "inside" : "outside",
					s.radius(), s.rainEdge(), s.intensity()));
		}
		double driftX = now * CloudRenderer.DRIFT;
		CloudCover cover = new CloudCover(payload, now, driftX, CloudRenderer.ground(level), CloudCover.overcast(local));
		int key = cover.cell(Mth.floor((px - driftX) / CloudCover.CELL), Mth.floor(pz / CloudCover.CELL));
		String cell = key == CloudCover.NONE ? "clear" : !CloudCover.isStorm(key) ? "fair-weather cumulus, size " + (key - CloudCover.FAIR)
				: String.format(Locale.ROOT, "storm deck, depth %d/%d, kind %d, precipitation %d", CloudCover.stormDepth(key),
						CloudCover.STORM_DEPTHS, CloudCover.stormKind(key), CloudCover.stormPrecipitation(key));
		say(source, "Cloud overhead: " + cell);
		float cloudY = level.effects().getCloudHeight();
		double range = AwmClientConfig.CLOUD_DETAIL.get().range();
		float weatherFade = WeatherEffects.cloudFadeEnd((float)range);
		say(source, String.format(Locale.ROOT, "Clouds: mod's %s, height %.0f (%.0f above you), range %.0f, fade end %.0f (weather %.0f)",
				CloudRenderer.active() ? "on" : "OFF (vanilla)", cloudY, cloudY - mc.player.getEyeY(), range,
				CloudCover.fadeEnd(weatherFade, cloudY - mc.player.getEyeY(), range), weatherFade));
		return 1;
	}

	private static void say(CommandSourceStack source, String text) {
		source.sendSystemMessage(Component.literal(text));
	}
}
