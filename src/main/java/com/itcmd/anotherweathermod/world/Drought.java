package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmConfig;
import com.itcmd.anotherweathermod.api.Hazard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

/**
 * Crops wilt in a heat wave: on dry farmland they stop growing until it passes, and even watered ones grow at half
 * speed. Crops under a roof are out of its sun and grow as usual.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID)
public final class Drought {

	private Drought() {
	}

	@SubscribeEvent
	public static void onCropGrow(CropGrowEvent.Pre event) {
		if(!(event.getLevel() instanceof ServerLevel level) || !AwmConfig.EXTREME_TEMPERATURE_EFFECTS.get())
			return;
		LevelWeather weather = WeatherManager.get(level);
		BlockPos pos = event.getPos();
		if(weather == null || !level.canSeeSky(pos) || !weather.sample(pos, level.getGameTime()).has(Hazard.HEAT_WAVE))
			return;
		if(!watered(level.getBlockState(pos.below())) || level.random.nextBoolean())
			event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
	}

	/** Whether {@code soil} is wet farmland (or something else that is not farmland, which keeps its own rules). */
	static boolean watered(BlockState soil) {
		return !soil.hasProperty(FarmBlock.MOISTURE) || soil.getValue(FarmBlock.MOISTURE) > 0;
	}
}
