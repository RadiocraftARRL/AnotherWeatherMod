package com.itcmd.anotherweathermod.mixin.client;

import com.itcmd.anotherweathermod.client.WeatherEffects;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No sunset or sunrise glow under a hurricane or severe storm (2026-11-13, the maintainer: at dusk "the sunset/sky is
 * clearly visible past the storm"). Vanilla draws the glow straight onto the sky, past any fog, so it showed through the
 * storm's deck; it fades out as the storm's sky closes in.
 */
@Mixin(DimensionSpecialEffects.class)
public abstract class MixinDimensionSpecialEffects {

	@Inject(method = "getSunriseColor", at = @At("RETURN"), cancellable = true)
	private void awm$noSunsetInStorms(float timeOfDay, float partialTicks, CallbackInfoReturnable<float[]> cir) {
		float sky = WeatherEffects.stormSky();
		float[] colour = cir.getReturnValue();
		if(colour == null || sky <= 0.01f)
			return;
		if(sky >= 0.99f) {
			cir.setReturnValue(null);
			return;
		}
		cir.setReturnValue(new float[] { colour[0], colour[1], colour[2], colour[3] * (1 - sky) });
	}
}
