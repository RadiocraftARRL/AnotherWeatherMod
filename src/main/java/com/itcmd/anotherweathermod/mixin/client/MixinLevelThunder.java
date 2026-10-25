package com.itcmd.anotherweathermod.mixin.client;

import com.itcmd.anotherweathermod.client.WeatherEffects;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The "storms as thunder" shader compatibility setting: on the client, under a severe storm or hurricane, the game
 * (and so a shader pack) reads full thunder. Only client levels: the server's thunder is untouched.
 */
@Mixin(Level.class)
public abstract class MixinLevelThunder {

	@Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
	private void awm$stormThunder(float partialTick, CallbackInfoReturnable<Float> cir) {
		if(!((Level)(Object)this).isClientSide)
			return;
		float thunder = WeatherEffects.thunderBoost();
		if(thunder > cir.getReturnValueF())
			cir.setReturnValue(thunder);
	}
}
