package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** The client half's entry point: the config screen in the mod list, and the clouds' shader. */
@Mod(value = AnotherWeatherMod.MOD_ID, dist = Dist.CLIENT)
public final class AnotherWeatherModClient {

	public AnotherWeatherModClient(ModContainer container, IEventBus modBus) {
		container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
		modBus.addListener(CloudRenderer::onRegisterShaders);
	}
}
