package com.itcmd.anotherweathermod.mixin;

import com.itcmd.anotherweathermod.world.LevelWeather;
import com.itcmd.anotherweathermod.world.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class MixinLevel {

	/**
	 * In a managed level it rains where a storm is: crops, fire, mobs, tridents and anything else that asks
	 * {@code isRainingAt} agree with what players see. Client levels keep vanilla's check, which already follows the
	 * rain level each player is sent.
	 */
	@Inject(method = "isRainingAt", at = @At("HEAD"), cancellable = true)
	private void awm$localRain(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		LevelWeather weather = WeatherManager.get((Level)(Object)this);
		if(weather != null)
			cir.setReturnValue(weather.isRainingAt(pos));
	}
}
