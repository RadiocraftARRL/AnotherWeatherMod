package com.itcmd.anotherweathermod.mixin.client;

import com.itcmd.anotherweathermod.client.StormHorizon;
import com.itcmd.anotherweathermod.client.WeatherEffects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The "storm darkness" shader compatibility setting: under a severe storm or hurricane, the sky, the clouds and the
 * world's light dim toward night. And looking toward a storm on its way, the sky and clouds dim (see
 * {@link StormHorizon}).
 */
@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {

	@Inject(method = "getSkyDarken", at = @At("RETURN"), cancellable = true)
	private void awm$stormSkyDarken(float partialTick, CallbackInfoReturnable<Float> cir) {
		float dark = WeatherEffects.stormDarkness();
		if(dark > 0)
			cir.setReturnValue(Mth.lerp(dark, cir.getReturnValueF(), 0.2f));
	}

	@Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
	private void awm$stormSkyColor(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
		float dark = WeatherEffects.stormDarkness();
		if(dark > 0)
			cir.setReturnValue(cir.getReturnValue().scale(1 - 0.85 * dark));
		// Looking toward a storm on its way, the sky turns slate.
		float toward = StormHorizon.lookingToward() * 0.6f;
		if(toward > 0) {
			Vec3 sky = cir.getReturnValue();
			double bright = Math.max(sky.x, Math.max(sky.y, sky.z));
			cir.setReturnValue(sky.lerp(StormHorizon.slate(Math.min(1, bright * 1.2)), toward));
		}
	}

	@Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
	private void awm$stormCloudColor(float partialTick, CallbackInfoReturnable<Vec3> cir) {
		float dark = WeatherEffects.stormDarkness();
		if(dark > 0)
			cir.setReturnValue(cir.getReturnValue().scale(1 - 0.7 * dark));
		float toward = StormHorizon.lookingToward();
		if(toward > 0)
			cir.setReturnValue(cir.getReturnValue().scale(1 - 0.35 * toward));
	}
}
