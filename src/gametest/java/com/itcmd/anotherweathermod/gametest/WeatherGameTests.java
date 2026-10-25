package com.itcmd.anotherweathermod.gametest;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.api.AnotherWeatherApi;
import com.itcmd.anotherweathermod.api.Hazard;
import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.api.WeatherSample;
import com.itcmd.anotherweathermod.api.event.WindDamageEvent;
import com.itcmd.anotherweathermod.sim.StormCell;
import com.itcmd.anotherweathermod.world.Hail;
import com.itcmd.anotherweathermod.world.LevelWeather;
import com.itcmd.anotherweathermod.world.WeatherManager;
import com.itcmd.anotherweathermod.world.WindDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Localized weather in a real level. Storms are level-wide state and reach across plots, so every test that adds one
 * has a batch of its own and clears the storms before and after.
 */
@GameTestHolder(AnotherWeatherMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID)
public class WeatherGameTests {

	static final String EMPTY = "empty16";
	private static final BlockPos SPOT = new BlockPos(8, 2, 8);

	/** A block that {@link #onWindDamage} protects, for the cancel test. */
	@Nullable private static BlockPos protectedPos;

	@SubscribeEvent
	public static void onWindDamage(WindDamageEvent event) {
		if(event.getPos().equals(protectedPos))
			event.setCanceled(true);
	}

	/** Open sky over the plot (the plot itself has a roof). */
	private static BlockPos sky(GameTestHelper helper) {
		return helper.getLevel().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, helper.absolutePos(SPOT));
	}

	private static LevelWeather weather(GameTestHelper helper) {
		LevelWeather weather = WeatherManager.get(helper.getLevel());
		if(weather == null)
			throw new AssertionError("The GameTest level should have localized weather");
		return weather;
	}

	private static StormCell spawn(GameTestHelper helper, StormKind kind, BlockPos relative, double radius, float intensity) {
		LevelWeather weather = weather(helper);
		BlockPos pos = helper.absolutePos(relative);
		ServerLevel level = helper.getLevel();
		long now = level.getGameTime();
		StormCell cell = StormCell.matureAt(kind, pos.getX() + 0.5, pos.getZ() + 0.5, now, 20 * 60 * 20, radius,
				intensity, 1, weather.field().wind());
		return weather.field().add(cell, now);
	}

	/** It rains under a storm and nowhere else, and {@code isRainingAt} (crops, fire, mobs) agrees. */
	@GameTest(template = EMPTY, batch = "awm_rain")
	public static void rainFollowsTheStorm(GameTestHelper helper) {
		LevelWeather weather = weather(helper);
		weather.field().clear();
		ServerLevel level = helper.getLevel();
		BlockPos pos = sky(helper);
		helper.assertFalse(level.isRainingAt(pos), "no storm, no rain");

		StormCell storm = spawn(helper, StormKind.THUNDERSTORM, SPOT, 64, 0.9f);
		helper.assertTrue(level.isRainingAt(pos), "raining under the storm");
		helper.assertFalse(level.isRainingAt(pos.east(200)), "dry 200 blocks away");

		WeatherSample sample = AnotherWeatherApi.get().sample(level, pos);
		helper.assertTrue(sample.precipitation() == Precipitation.RAIN, "rain falls, got " + sample.precipitation());
		helper.assertTrue(sample.has(Hazard.LIGHTNING), "a thunderstorm has lightning");
		helper.assertTrue(sample.stormId() == storm.id(), "the sample names the storm");
		helper.assertTrue(sample.pressure() < 1013, "pressure falls under a storm");

		weather.field().remove(storm.id());
		helper.assertFalse(level.isRainingAt(pos), "storm gone, rain gone");
		helper.succeed();
	}

	/** {@code /weather rain} still works, as a world-wide override for its duration. */
	@GameTest(template = EMPTY, batch = "awm_override", timeoutTicks = 100)
	public static void weatherCommandOverrides(GameTestHelper helper) {
		weather(helper).field().clear();
		ServerLevel level = helper.getLevel();
		BlockPos pos = sky(helper);
		level.setWeatherParameters(0, 40, true, false);
		helper.runAfterDelay(2, () -> helper.assertTrue(level.isRainingAt(pos), "the override rains everywhere"));
		helper.runAfterDelay(50, () -> {
			helper.assertFalse(level.isRainingAt(pos), "the override runs out");
			helper.succeed();
		});
	}

	/** Strong wind breaks fragile blocks and strips foliage, each from its own wind speed; stone stays. */
	@GameTest(template = EMPTY)
	public static void windBreaksWhatItShould(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pane = new BlockPos(3, 2, 3);
		BlockPos leaves = new BlockPos(6, 2, 3);
		BlockPos stone = new BlockPos(9, 2, 3);
		helper.setBlock(pane, Blocks.GLASS_PANE);
		helper.setBlock(leaves, Blocks.OAK_LEAVES);
		helper.setBlock(stone, Blocks.STONE);

		// Blowing south (+z), so upwind is north: open air in the plot.
		WindDamage.apply(level, helper.absolutePos(pane), 20, 0, 20);
		WindDamage.apply(level, helper.absolutePos(leaves), 20, 0, 20);
		helper.assertBlockPresent(Blocks.GLASS_PANE, pane);
		helper.assertBlockPresent(Blocks.OAK_LEAVES, leaves);

		WindDamage.apply(level, helper.absolutePos(leaves), 30, 0, 30);
		WindDamage.apply(level, helper.absolutePos(pane), 30, 0, 30);
		helper.assertBlockNotPresent(Blocks.OAK_LEAVES, leaves);
		helper.assertBlockPresent(Blocks.GLASS_PANE, pane);

		WindDamage.apply(level, helper.absolutePos(pane), 40, 0, 40);
		WindDamage.apply(level, helper.absolutePos(stone), 70, 0, 70);
		helper.assertBlockNotPresent(Blocks.GLASS_PANE, pane);
		helper.assertBlockPresent(Blocks.STONE, stone);
		helper.succeed();
	}

	/**
	 * Severe thunderstorm gusts (26 to 30 m/s) blow an outside wooden door open but leave an iron door, and below
	 * {@code Gusts.DOOR_WIND} even the wooden one stays shut.
	 */
	@GameTest(template = EMPTY)
	public static void gustsBlowDoorsOpen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos wooden = new BlockPos(3, 2, 12);
		BlockPos iron = new BlockPos(8, 2, 12);
		placeDoor(helper, wooden, Blocks.OAK_DOOR);
		placeDoor(helper, iron, Blocks.IRON_DOOR);

		WindDamage.apply(level, helper.absolutePos(wooden), 15, 0, 15);
		helper.assertBlockProperty(wooden, DoorBlock.OPEN, false);
		WindDamage.apply(level, helper.absolutePos(wooden), 28, 0, 28);
		WindDamage.apply(level, helper.absolutePos(iron), 28, 0, 28);
		helper.assertBlockProperty(wooden, DoorBlock.OPEN, true);
		helper.assertBlockProperty(wooden.above(), DoorBlock.OPEN, true);
		helper.assertBlockProperty(iron, DoorBlock.OPEN, false);
		helper.succeed();
	}

	private static void placeDoor(GameTestHelper helper, BlockPos pos, net.minecraft.world.level.block.Block door) {
		helper.setBlock(pos, door.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(pos.above(), door.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	/** A wall upwind shelters a block, and a listener can cancel the damage. */
	@GameTest(template = EMPTY)
	public static void shelterAndCancel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos sheltered = new BlockPos(3, 2, 8);
		BlockPos guarded = new BlockPos(10, 2, 8);
		helper.setBlock(sheltered, Blocks.GLASS_PANE);
		helper.setBlock(sheltered.north(), Blocks.STONE); // Wind blows south, so north is upwind.
		helper.setBlock(guarded, Blocks.GLASS_PANE);

		WindDamage.apply(level, helper.absolutePos(sheltered), 60, 0, 60);
		helper.assertBlockPresent(Blocks.GLASS_PANE, sheltered);

		protectedPos = helper.absolutePos(guarded);
		try {
			WindDamage.apply(level, protectedPos, 60, 0, 60);
		}
		finally {
			protectedPos = null;
		}
		helper.assertBlockPresent(Blocks.GLASS_PANE, guarded);
		helper.succeed();
	}

	/** Leaves and scoured grass the wind took grow back; a torch it knocked off does not. */
	@GameTest(template = EMPTY, batch = "awm_regrowth", timeoutTicks = 200)
	public static void natureGrowsBack(GameTestHelper helper) {
		LevelWeather weather = weather(helper);
		weather.field().clear();
		ServerLevel level = helper.getLevel();
		BlockPos log = new BlockPos(4, 2, 4);
		BlockPos leaves = log.east();
		BlockPos grass = new BlockPos(10, 1, 10);
		BlockPos torch = new BlockPos(12, 2, 4);
		helper.setBlock(log, Blocks.OAK_LOG);
		helper.setBlock(leaves, Blocks.OAK_LEAVES);
		helper.setBlock(grass, Blocks.GRASS_BLOCK);
		helper.setBlock(torch.below(), Blocks.STONE);
		helper.setBlock(torch, Blocks.TORCH);
		int before = weather.regrowth().size();
		WindDamage.apply(level, helper.absolutePos(leaves), 30, 0, 30);
		WindDamage.apply(level, helper.absolutePos(grass), 55, 0, 55);
		WindDamage.apply(level, helper.absolutePos(torch), 45, 0, 45);
		helper.assertBlockNotPresent(Blocks.OAK_LEAVES, leaves);
		helper.assertBlockPresent(Blocks.COARSE_DIRT, grass);
		helper.assertBlockNotPresent(Blocks.TORCH, torch);
		helper.assertTrue(weather.regrowth().size() == before + 2, "leaves and grass wait to grow back, the torch not");

		weather.regrowth().hurry(level.getGameTime());
		helper.runAfterDelay(45, () -> {
			helper.assertBlockPresent(Blocks.OAK_LEAVES, leaves);
			helper.assertBlockPresent(Blocks.GRASS_BLOCK, grass);
			helper.assertBlockNotPresent(Blocks.TORCH, torch);
			helper.succeed();
		});
	}

	/** Hail beats a grown crop back a stage or two. */
	@GameTest(template = EMPTY)
	public static void hailBeatsCropsBack(GameTestHelper helper) {
		BlockPos wheat = new BlockPos(5, 2, 5);
		CropBlock crop = (CropBlock)Blocks.WHEAT;
		helper.setBlock(wheat.below(), Blocks.FARMLAND);
		helper.setBlock(wheat, crop.getStateForAge(7));
		Hail.crop(helper.getLevel(), helper.absolutePos(wheat));
		BlockState state = helper.getBlockState(wheat);
		helper.assertTrue(state.is(Blocks.WHEAT), "the wheat is still there");
		int age = crop.getAge(state);
		helper.assertTrue(age == 5 || age == 6, "set back to 5 or 6, got " + age);
		helper.succeed();
	}

	/** A strong supercell over a rain biome hails; the forecast sees a storm that is still pending. */
	/** An EF3 tornado sitting on the plot tears the leaves off and lifts a loose item up. */
	@GameTest(template = EMPTY, batch = "awm_tornado", timeoutTicks = 200)
	public static void tornadoTearsAndLifts(GameTestHelper helper) {
		LevelWeather weather = weather(helper);
		weather.field().clear();
		ServerLevel level = helper.getLevel();
		BlockPos pos = sky(helper);
		BlockPos leaves = new BlockPos(SPOT.getX(), pos.getY() - helper.absolutePos(BlockPos.ZERO).getY(), SPOT.getZ());
		for(int dx = -2; dx <= 2; dx++)
			for(int dz = -2; dz <= 2; dz++)
				helper.setBlock(leaves.offset(dx, 0, dz), Blocks.OAK_LEAVES.defaultBlockState()
						.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
		long now = level.getGameTime();
		StormCell cell = StormCell.atLife(StormKind.TORNADO, pos.getX(), pos.getZ(), now, 0.5, 72000, 240, 1f, 4,
				weather.field().wind()).withCategory(3);
		cell = cell.shifted(pos.getX() + 0.5 - cell.tornadoX(now), pos.getZ() + 0.5 - cell.tornadoZ(now));
		weather.field().add(cell, now);
		var item = new net.minecraft.world.entity.item.ItemEntity(level, pos.getX() + 3.5, pos.getY() + 1.5, pos.getZ() + 0.5,
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
		level.addFreshEntity(item);
		double startY = item.getY();
		helper.runAfterDelay(100, () -> {
			int left = 0;
			for(int dx = -2; dx <= 2; dx++)
				for(int dz = -2; dz <= 2; dz++)
					if(helper.getBlockState(leaves.offset(dx, 0, dz)).is(Blocks.OAK_LEAVES))
						left++;
			weather.field().clear();
			helper.assertTrue(left < 20, "the tornado tore most of the 25 leaves off, " + left + " left");
			helper.assertTrue(!item.isAlive() || item.getY() > startY + 2 || item.distanceToSqr(pos.getX(), item.getY(), pos.getZ()) > 64,
					"the stick was carried off");
			helper.succeed();
		});
	}

	/**
	 * In an EF4 or EF5's core everything but stone, earth and water goes, sheltered or not; a weaker wind of the
	 * same speed only takes what its tags say. (A live EF5 reaches well past a test plot, so this tests the rule.)
	 */
	@GameTest(template = EMPTY)
	public static void violentTornadoLevelsBuildings(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos planks = new BlockPos(3, 2, 3), wall = new BlockPos(3, 2, 2), stone = new BlockPos(6, 2, 3),
				dirt = new BlockPos(9, 2, 3);
		helper.setBlock(planks, Blocks.OAK_PLANKS);
		helper.setBlock(wall, Blocks.STONE); // Upwind shelter, which a violent tornado ignores.
		helper.setBlock(stone, Blocks.STONE);
		helper.setBlock(dirt, Blocks.DIRT);
		WindDamage.apply(level, helper.absolutePos(planks), 95, 0, 95);
		helper.assertBlockPresent(Blocks.OAK_PLANKS, planks);
		WindDamage.apply(level, helper.absolutePos(planks), 95, 0, 95, true);
		WindDamage.apply(level, helper.absolutePos(stone), 95, 0, 95, true);
		WindDamage.apply(level, helper.absolutePos(dirt), 95, 0, 95, true);
		helper.assertBlockNotPresent(Blocks.OAK_PLANKS, planks);
		helper.assertBlockPresent(Blocks.STONE, stone);
		helper.assertBlockPresent(Blocks.DIRT, dirt);
		helper.succeed();
	}

	@GameTest(template = EMPTY, batch = "awm_hail")
	public static void supercellHailsAndForecastSeesPending(GameTestHelper helper) {
		LevelWeather weather = weather(helper);
		weather.field().clear();
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(SPOT);
		StormCell supercell = spawn(helper, StormKind.SUPERCELL, SPOT, 96, 1f);
		WeatherSample sample = AnotherWeatherApi.get().sample(level, pos);
		helper.assertTrue(sample.has(Hazard.HAIL), "hail under a full supercell, got " + sample.hazards());
		weather.field().remove(supercell.id());

		long now = level.getGameTime();
		StormCell later = new StormCell(0, StormKind.SHOWER, now + 600, 12000, pos.getX(), pos.getZ(), 0, 0, 150, 0.7f,
				2, weather.field().wind());
		StormCell placed = weather.field().add(later, now);
		var arrival = AnotherWeatherApi.get().nextArrival(level, pos, 24000);
		helper.assertTrue(arrival.isPresent() && arrival.get().storm().id() == placed.id(), "the pending shower is forecast");
		helper.assertTrue(arrival.get().arrivalTick() > now, "it has not arrived yet");
		helper.assertTrue(AnotherWeatherApi.get().pendingStorms(level).size() == 1, "it is pending");
		weather.field().clear();
		helper.succeed();
	}
}
