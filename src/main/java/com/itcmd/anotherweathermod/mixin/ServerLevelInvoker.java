package com.itcmd.anotherweathermod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lightning from storms goes through vanilla's target search, so rods (and other mods' hooks on it) still work. */
@Mixin(ServerLevel.class)
public interface ServerLevelInvoker {

	@Invoker("findLightningTargetAround")
	BlockPos awm$findLightningTargetAround(BlockPos pos);
}
