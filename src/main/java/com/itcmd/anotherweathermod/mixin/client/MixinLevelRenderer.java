package com.itcmd.anotherweathermod.mixin.client;

import com.itcmd.anotherweathermod.client.CloudRenderer;
import com.itcmd.anotherweathermod.client.StormRain;
import com.itcmd.anotherweathermod.client.WeatherEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In a severe storm, {@link StormRain} draws the rain instead of vanilla; {@link CloudRenderer} draws the clouds, or
 * vanilla's fade out under a severe storm, where they would show white against its dark sky. Applied ahead of other
 * mods' mixins here, so the mod's clouds win over Sodium's, which would otherwise cancel {@code renderClouds} first.
 */
@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class MixinLevelRenderer {

	@Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
	private void awm$stormRain(LightTexture lightTexture, float partialTick, double camX, double camY, double camZ,
			CallbackInfo ci) {
		if(StormRain.render(lightTexture, partialTick, camX, camY, camZ))
			ci.cancel();
	}

	@Unique
	private boolean awm$cloudsFaded;

	/** The mod's own clouds if they are on; otherwise vanilla's, faded out under a severe storm's dark sky. */
	@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
	private void awm$clouds(PoseStack poseStack, Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick,
			double camX, double camY, double camZ, CallbackInfo ci) {
		if(CloudRenderer.render(frustumMatrix, projectionMatrix, partialTick, camX, camY, camZ)) {
			ci.cancel();
			return;
		}
		float alpha = WeatherEffects.vanillaCloudAlpha();
		if(alpha <= 0.01f) {
			ci.cancel();
		} else if(alpha < 0.99f) {
			RenderSystem.setShaderColor(1, 1, 1, alpha);
			awm$cloudsFaded = true;
		}
	}

	@Inject(method = "renderClouds", at = @At("RETURN"))
	private void awm$cloudsDone(PoseStack poseStack, Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick,
			double camX, double camY, double camZ, CallbackInfo ci) {
		if(awm$cloudsFaded) {
			RenderSystem.setShaderColor(1, 1, 1, 1);
			awm$cloudsFaded = false;
		}
	}
}
