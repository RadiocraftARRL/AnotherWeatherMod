package com.itcmd.anotherweathermod.mixin.client;

import com.itcmd.anotherweathermod.client.StormRain;
import com.itcmd.anotherweathermod.client.WeatherEffects;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In a severe storm, {@link StormRain} draws the rain instead of vanilla, and the vanilla clouds, which would show white
 * against the storm's dark sky, are left out.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {

	@Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
	private void awm$stormRain(LightTexture lightTexture, float partialTick, double camX, double camY, double camZ,
			CallbackInfo ci) {
		if(StormRain.render(lightTexture, partialTick, camX, camY, camZ))
			ci.cancel();
	}

	@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
	private void awm$hideClouds(CallbackInfo ci) {
		if(WeatherEffects.hideVanillaClouds())
			ci.cancel();
	}
}
