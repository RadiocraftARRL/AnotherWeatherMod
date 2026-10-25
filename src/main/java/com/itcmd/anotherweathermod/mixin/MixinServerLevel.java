package com.itcmd.anotherweathermod.mixin;

import com.itcmd.anotherweathermod.world.ChunkWeather;
import com.itcmd.anotherweathermod.world.WeatherManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel {

	/**
	 * Vanilla's world-wide weather cycle is off in managed levels: it would send every player the same rain. The
	 * {@code /weather} command still sets the weather flags, which {@code LevelWeather} reads as an override.
	 */
	@Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
	private void awm$noGlobalWeather(CallbackInfo ci) {
		if(WeatherManager.get((ServerLevel)(Object)this) != null)
			ci.cancel();
	}

	/** Lightning, snow, cauldrons, hail and wind under local storms. */
	@Inject(method = "tickChunk", at = @At("TAIL"))
	private void awm$localWeather(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
		ChunkWeather.tick((ServerLevel)(Object)this, chunk, randomTickSpeed);
	}
}
