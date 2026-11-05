package com.itcmd.anotherweathermod.gametest;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.block.AwmBlocks;
import com.itcmd.anotherweathermod.block.SirenControllerBlock;
import com.itcmd.anotherweathermod.block.SirenControllerBlockEntity;
import com.itcmd.anotherweathermod.block.TornadoSirenBlock;
import com.itcmd.anotherweathermod.block.TornadoSirenBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The siren controller sounds the sirens its data wire reaches, and lets go when the wire or its signal goes. */
@GameTestHolder(AnotherWeatherMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class SirenControllerGameTests {

	private static void siren(GameTestHelper helper, BlockPos lower) {
		BlockState state = AwmBlocks.TORNADO_SIREN.get().defaultBlockState();
		helper.setBlock(lower, state);
		helper.setBlock(lower.above(), state.setValue(TornadoSirenBlock.HALF, DoubleBlockHalf.UPPER));
	}

	private static void wire(GameTestHelper helper, BlockPos from, int length) {
		for(int i = 0; i < length; i++)
			helper.setBlock(from.east(i), AwmBlocks.DATA_WIRE.get());
	}

	private static int drive(GameTestHelper helper, BlockPos lower) {
		if(!(helper.getBlockEntity(lower) instanceof TornadoSirenBlockEntity siren))
			throw new AssertionError("no siren rotor at " + lower);
		return siren.drive();
	}

	private static SirenControllerBlockEntity controller(GameTestHelper helper, BlockPos pos) {
		if(!(helper.getBlockEntity(pos) instanceof SirenControllerBlockEntity controller))
			throw new AssertionError("no controller at " + pos);
		return controller;
	}

	/**
	 * A powered controller sounds a siren six blocks of wire away, at its signal's strength, and the siren winds up;
	 * lifting a piece of the wire lets it go. (Plain redstone on the siren: {@code sirenWindsUpOnRedstone}.)
	 */
	@GameTest(template = WeatherGameTests.EMPTY, batch = "awm_siren_controller_wire", timeoutTicks = 200)
	public static void controllerSoundsWiredSiren(GameTestHelper helper) {
		BlockPos control = new BlockPos(2, 2, 8);
		BlockPos lower = new BlockPos(9, 2, 8);
		helper.setBlock(control, AwmBlocks.SIREN_CONTROLLER.get());
		wire(helper, control.east(), 6);
		siren(helper, lower);
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(drive(helper, lower) == 0, "nothing asked with no signal");
			helper.setBlock(control.west(), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(controller(helper, control).sirens().equals(java.util.List.of(helper.absolutePos(lower))),
					"the controller reaches the one siren, got " + controller(helper, control).sirens());
			helper.assertTrue(drive(helper, lower) == 15, "a redstone block asks full speed, got " + drive(helper, lower));
			helper.assertTrue(helper.getBlockState(control).getValue(SirenControllerBlock.POWERED), "the lamp lights");
		});
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(helper.getBlockState(lower).getValue(TornadoSirenBlock.SPEED) > 0, "the siren winds up");
			helper.setBlock(control.east(3), Blocks.AIR);
		});
		helper.runAfterDelay(85, () -> {
			helper.assertTrue(drive(helper, lower) == 0, "a cut wire lets it go, got " + drive(helper, lower));
			helper.assertTrue(controller(helper, control).sirens().isEmpty(), "and the controller reaches nothing");
			helper.setBlock(control.west(), Blocks.AIR);
			helper.succeed();
		});
	}

	/** A siren placed on the wire of a powered controller sounds at once; one the signal drops lets go. */
	@GameTest(template = WeatherGameTests.EMPTY, batch = "awm_siren_controller_later", timeoutTicks = 100)
	public static void sirenPlacedLaterIsFound(GameTestHelper helper) {
		BlockPos control = new BlockPos(2, 2, 8);
		BlockPos lower = new BlockPos(6, 2, 8);
		helper.setBlock(control, AwmBlocks.SIREN_CONTROLLER.get());
		helper.setBlock(control.west(), Blocks.REDSTONE_BLOCK);
		wire(helper, control.east(), 3);
		helper.runAfterDelay(5, () -> siren(helper, lower));
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(drive(helper, lower) == 15, "the new siren is found, got " + drive(helper, lower));
			helper.setBlock(control.west(), Blocks.AIR);
		});
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(drive(helper, lower) == 0, "no signal, no drive, got " + drive(helper, lower));
			helper.assertTrue(!helper.getBlockState(control).getValue(SirenControllerBlock.POWERED), "the lamp goes out");
			helper.succeed();
		});
	}

	/**
	 * The wire climbs a wall to wire on top of it, reaching a siren up there; a weaker signal asks a slower speed;
	 * breaking the controller lets the siren go.
	 */
	@GameTest(template = WeatherGameTests.EMPTY, batch = "awm_siren_controller_climb", timeoutTicks = 100)
	public static void wireClimbsAWall(GameTestHelper helper) {
		BlockPos control = new BlockPos(2, 2, 8);
		BlockPos lower = new BlockPos(6, 3, 8);
		helper.setBlock(control, AwmBlocks.SIREN_CONTROLLER.get());
		helper.setBlock(new BlockPos(3, 2, 8), AwmBlocks.DATA_WIRE.get());
		helper.setBlock(new BlockPos(4, 2, 8), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 3, 8), AwmBlocks.DATA_WIRE.get());
		helper.setBlock(new BlockPos(5, 3, 8), AwmBlocks.DATA_WIRE.get());
		helper.setBlock(new BlockPos(6, 2, 8), Blocks.STONE);
		siren(helper, lower);
		// Dust next to a redstone block gives the controller 15; one more dust between them gives 14.
		helper.setBlock(new BlockPos(0, 1, 8), Blocks.STONE);
		helper.setBlock(new BlockPos(1, 1, 8), Blocks.STONE);
		helper.setBlock(new BlockPos(0, 2, 8), Blocks.REDSTONE_BLOCK);
		helper.setBlock(new BlockPos(1, 2, 8), Blocks.REDSTONE_WIRE);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(drive(helper, lower) == 15, "over the wall at the dust's 15, got " + drive(helper, lower));
			helper.setBlock(new BlockPos(0, 2, 8), Blocks.AIR);
			helper.setBlock(new BlockPos(0, 2, 7), Blocks.REDSTONE_BLOCK);
			helper.setBlock(new BlockPos(0, 2, 8), Blocks.REDSTONE_WIRE);
		});
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(drive(helper, lower) == 14, "a signal of 14 asks 14, got " + drive(helper, lower));
			helper.setBlock(control, Blocks.AIR);
		});
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(drive(helper, lower) == 0, "a broken controller lets go, got " + drive(helper, lower));
			helper.succeed();
		});
	}
}
