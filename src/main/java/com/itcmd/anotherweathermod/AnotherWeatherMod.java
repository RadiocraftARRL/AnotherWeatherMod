package com.itcmd.anotherweathermod;

import com.itcmd.anotherweathermod.api.ApiHolder;
import com.itcmd.anotherweathermod.network.AwmNetwork;
import com.itcmd.anotherweathermod.world.ApiImpl;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(AnotherWeatherMod.MOD_ID)
public class AnotherWeatherMod {

	public static final String MOD_ID = "anotherweathermod";
	public static final Logger LOGGER = LogUtils.getLogger();

	public AnotherWeatherMod(IEventBus modEventBus, ModContainer container) {
		container.registerConfig(ModConfig.Type.SERVER, AwmConfig.SPEC);
		container.registerConfig(ModConfig.Type.CLIENT, AwmClientConfig.SPEC);
		AwmGameRules.init();
		ApiHolder.set(new ApiImpl());
		modEventBus.addListener(AwmNetwork::register);
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
