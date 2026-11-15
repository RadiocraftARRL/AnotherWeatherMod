package com.itcmd.anotherweathermod.gametest;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.block.AwmBlocks;
import com.itcmd.anotherweathermod.block.WarningSirenBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The electronic warning sirens (moved from RadioCraft, 2026-11-13): the head alone and the pole siren stand and come
 * down whole, sound on redstone or the pole's key switch, and a siren controller sounds a pole siren over data wire
 * touching its pole.
 */
@GameTestHolder(AnotherWeatherMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class WarningSirenGameTests {

	private static void place(GameTestHelper helper, WarningSirenBlock siren, BlockPos footAt) {
		BlockState foot = siren.defaultBlockState();
		helper.setBlock(footAt, foot);
		siren.setPlacedBy(helper.getLevel(), helper.absolutePos(footAt), foot, null, ItemStack.EMPTY);
	}

	/**
	 * The pole siren: placed, it stands twelve blocks with the head on top; the cabinet's key switch and redstone at the
	 * cabinet turn it on; breaking the pole takes the whole thing down.
	 */
	@GameTest(template = WeatherGameTests.EMPTY, timeoutTicks = 60)
	public static void poleSirenStandsAndSwitches(GameTestHelper helper) {
		WarningSirenBlock pole = AwmBlocks.OMNI_SIREN_POLE.get();
		BlockPos footAt = new BlockPos(4, 1, 4);
		place(helper, pole, footAt);
		for(int i = 0; i < 12; i++) {
			BlockState at = helper.getBlockState(footAt.above(i));
			helper.assertTrue(at.is(pole) && at.getValue(pole.segment()) == i, "Segment " + i + " is " + at);
		}
		helper.assertTrue(!WarningSirenBlock.isOn(helper.getBlockState(footAt)), "On by itself");
		helper.useBlock(footAt);
		helper.assertTrue(WarningSirenBlock.isOn(helper.getBlockState(footAt)), "The key switch did not turn it on");
		helper.useBlock(footAt);
		helper.assertTrue(!WarningSirenBlock.isOn(helper.getBlockState(footAt)), "The key switch did not turn it off");
		helper.setBlock(footAt.east(), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(WarningSirenBlock.isOn(helper.getBlockState(footAt)), "Redstone at the cabinet did not turn it on");
			helper.setBlock(footAt.east(), Blocks.AIR);
			helper.destroyBlock(footAt.above(5));
		});
		helper.runAfterDelay(5, () -> helper.succeedIf(() -> {
			helper.assertTrue(!WarningSirenBlock.isOn(helper.getBlockState(footAt)) || !helper.getBlockState(footAt).is(pole), "Still on with the power gone");
			for(int i = 0; i < 12; i++)
				helper.assertTrue(!helper.getBlockState(footAt.above(i)).is(pole), "Segment " + i + " stayed up with the pole broken");
		}));
	}

	/** The siren head alone: two blocks, on while either half has redstone power. */
	@GameTest(template = WeatherGameTests.EMPTY, timeoutTicks = 40)
	public static void sirenHeadSoundsOnRedstone(GameTestHelper helper) {
		WarningSirenBlock head = AwmBlocks.HORN_SIREN.get();
		BlockPos footAt = new BlockPos(4, 2, 4);
		helper.setBlock(footAt.below(), Blocks.OAK_FENCE);
		place(helper, head, footAt);
		helper.assertTrue(helper.getBlockState(footAt.above()).is(head), "No top half");
		helper.setBlock(footAt.above().east(), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(2, () -> helper.succeedIf(() -> helper.assertTrue(
				WarningSirenBlock.isOn(helper.getBlockState(footAt)), "Redstone at the top half did not turn it on")));
	}

	/**
	 * A siren controller sounds a pole siren whose data wire touches the pole halfway up (it finds the foot), and lets
	 * go when its signal goes.
	 */
	@GameTest(template = WeatherGameTests.EMPTY, batch = "awm_warning_siren_controller", timeoutTicks = 200)
	public static void controllerSoundsAPoleSiren(GameTestHelper helper) {
		WarningSirenBlock pole = AwmBlocks.HORN_SIREN_POLE.get();
		BlockPos footAt = new BlockPos(8, 1, 4);
		place(helper, pole, footAt);
		BlockPos control = new BlockPos(3, 3, 4);
		helper.setBlock(control, AwmBlocks.SIREN_CONTROLLER.get());
		for(int x = 4; x < 8; x++)
			helper.setBlock(new BlockPos(x, 3, 4), AwmBlocks.DATA_WIRE.get()); // Touches the pole two blocks up.
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(!WarningSirenBlock.isOn(helper.getBlockState(footAt)), "on with the controller idle");
			helper.setBlock(control.west(), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(WarningSirenBlock.isOn(helper.getBlockState(footAt)), "the controller did not sound the pole siren");
			helper.setBlock(control.west(), Blocks.AIR);
		});
		helper.runAfterDelay(140, () -> helper.succeedIf(() -> helper.assertTrue(!WarningSirenBlock.isOn(helper.getBlockState(footAt)),
				"still sounding with the controller's signal gone")));
	}
}
