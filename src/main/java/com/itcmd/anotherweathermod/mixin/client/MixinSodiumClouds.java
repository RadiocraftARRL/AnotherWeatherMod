package com.itcmd.anotherweathermod.mixin.client;

import com.itcmd.anotherweathermod.client.WeatherEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Sodium draws vanilla's clouds itself, setting their colour as it does, so vanilla's fade under a severe storm (see
 * {@link MixinLevelRenderer}) is applied to its alpha there. Nothing happens without Sodium, or if its renderer changes.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.immediate.CloudRenderer", remap = false)
public abstract class MixinSodiumClouds {

	@ModifyArg(method = "render", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V", ordinal = 0), index = 3, require = 0)
	private float awm$fade(float alpha) {
		return alpha * WeatherEffects.vanillaCloudAlpha();
	}
}
