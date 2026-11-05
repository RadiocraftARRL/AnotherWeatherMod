package com.itcmd.anotherweathermod.block;

import net.minecraft.core.BlockPos;

/**
 * A siren's block entity that a {@link SirenControllerBlockEntity} can sound over data wire. A siren runs at the
 * strongest of its own redstone signal and what its controllers ask for.
 */
public interface WiredSiren {

	/**
	 * The controller at {@code controller} asks for {@code signal}, 0 to 15. 0 lets go: the siren forgets that
	 * controller.
	 */
	void drive(BlockPos controller, int signal);
}
