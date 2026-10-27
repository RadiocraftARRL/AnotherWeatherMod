package com.itcmd.anotherweathermod.gametest;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.api.AnotherWeatherApi;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.api.WeatherSample;
import com.itcmd.anotherweathermod.block.AwmBlocks;
import com.itcmd.anotherweathermod.block.BarometerBlock;
import com.itcmd.anotherweathermod.block.InstrumentBlock;
import com.itcmd.anotherweathermod.block.TornadoSirenBlock;
import com.itcmd.anotherweathermod.block.WallInstrumentBlock;
import com.itcmd.anotherweathermod.block.WindVaneBlock;
import com.itcmd.anotherweathermod.sim.StormCell;
import com.itcmd.anotherweathermod.world.LevelWeather;
import com.itcmd.anotherweathermod.world.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The weather instruments read the weather, show it and give a redstone signal. */
@GameTestHolder(AnotherWeatherMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class InstrumentGameTests {

	private static LevelWeather weather(GameTestHelper helper) {
		LevelWeather weather = WeatherManager.get(helper.getLevel());
		if(weather == null)
			throw new AssertionError("The GameTest level should have localized weather");
		return weather;
	}

	private static StormCell storm(GameTestHelper helper, StormKind kind, BlockPos absolute) {
		LevelWeather weather = weather(helper);
		long now = helper.getLevel().getGameTime();
		return weather.field().add(StormCell.matureAt(kind, absolute.getX() + 0.5, absolute.getZ() + 0.5, now,
				20 * 60 * 20, 96, 1f, 3, weather.field().wind()), now);
	}

	/** A barometer on a wall reads fair in clear weather and falls under a storm; it drops off without its wall. */
	@GameTest(template = WeatherGameTests.EMPTY, batch = "awm_instruments_barometer", timeoutTicks = 100)
	public static void barometerFallsUnderAStorm(GameTestHelper helper) {
		weather(helper).field().clear();
		BlockPos wall = new BlockPos(4, 2, 4);
		BlockPos at = wall.south();
		helper.setBlock(wall, Blocks.STONE);
		helper.setBlock(at, AwmBlocks.BAROMETER.get().defaultBlockState().setValue(WallInstrumentBlock.FACING,
				Direction.SOUTH));
		helper.runAfterDelay(5, () -> {
			BlockState clear = helper.getBlockState(at);
			helper.assertTrue(clear.getValue(BarometerBlock.NEEDLE) >= 3, "fair, got " + clear.getValue(BarometerBlock.NEEDLE));
			helper.assertTrue(clear.getValue(InstrumentBlock.POWER) == 13, "13 at 1013 hPa, got " + clear.getValue(InstrumentBlock.POWER));
			storm(helper, StormKind.SUPERCELL, helper.absolutePos(at));
		});
		helper.runAfterDelay(30, () -> {
			BlockState stormy = helper.getBlockState(at);
			helper.assertTrue(stormy.getValue(BarometerBlock.NEEDLE) <= 2, "falls under a supercell, got "
					+ stormy.getValue(BarometerBlock.NEEDLE));
			helper.assertTrue(stormy.getValue(InstrumentBlock.POWER) < 13, "and its signal with it");
			weather(helper).field().clear();
			helper.setBlock(wall, Blocks.AIR);
			helper.assertBlockNotPresent(AwmBlocks.BAROMETER.get(), at);
			helper.succeed();
		});
	}

	/** Under a storm, the wind vane points the way the wind comes from and signals its force. */
	@GameTest(template = WeatherGameTests.EMPTY, batch = "awm_instruments_vane", timeoutTicks = 100)
	public static void windVanePointsIntoTheWind(GameTestHelper helper) {
		weather(helper).field().clear();
		BlockPos at = new BlockPos(8, 2, 8);
		helper.setBlock(at, AwmBlocks.WIND_VANE.get());
		// To one side of a severe storm, where its outflow blows hard.
		storm(helper, StormKind.SEVERE_THUNDERSTORM, helper.absolutePos(at).west(50));
		helper.runAfterDelay(30, () -> {
			ServerLevel level = helper.getLevel();
			WeatherSample sample = AnotherWeatherApi.get().sample(level, helper.absolutePos(at));
			BlockState vane = helper.getBlockState(at);
			int expected = (int)Math.round(sample.windFromDegrees() / 22.5) % 16;
			helper.assertTrue(sample.windSpeed() > 2, "the storm blows here, got " + sample.windSpeed());
			// The gusts move a little between the reading and now.
			int off = Math.abs(vane.getValue(WindVaneBlock.DIRECTION) - expected);
			helper.assertTrue(Math.min(off, 16 - off) <= 1, "points " + vane.getValue(WindVaneBlock.DIRECTION)
					+ ", wind from " + expected);
			helper.assertTrue(vane.getValue(InstrumentBlock.POWER) > 0, "signals the force");
			weather(helper).field().clear();
			helper.succeed();
		});
	}

	/** The rain gauge out under a storm signals the rain falling on it; a roof keeps it dry. */
	@GameTest(template = WeatherGameTests.EMPTY, batch = "awm_instruments_gauge", timeoutTicks = 100)
	public static void rainGaugeFeelsTheRain(GameTestHelper helper) {
		weather(helper).field().clear();
		ServerLevel level = helper.getLevel();
		BlockPos open = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, helper.absolutePos(new BlockPos(8, 2, 8)));
		BlockPos covered = helper.absolutePos(new BlockPos(3, 2, 3));
		level.setBlock(open, AwmBlocks.RAIN_GAUGE.get().defaultBlockState(), Block.UPDATE_ALL);
		helper.setBlock(new BlockPos(3, 2, 3), AwmBlocks.RAIN_GAUGE.get());
		helper.setBlock(new BlockPos(3, 3, 3), Blocks.STONE);
		storm(helper, StormKind.THUNDERSTORM, open);
		helper.runAfterDelay(40, () -> {
			int outside = level.getBlockState(open).getValue(InstrumentBlock.POWER);
			int inside = level.getBlockState(covered).getValue(InstrumentBlock.POWER);
			level.setBlock(open, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			weather(helper).field().clear();
			helper.assertTrue(outside > 5, "rain falls in the open, got " + outside);
			helper.assertTrue(inside == 0, "but not under a roof, got " + inside);
			helper.succeed();
		});
	}

	/**
	 * The siren winds up on a redstone signal, the horn turning, to the speed the signal asks; it coasts down without
	 * one; it breaks as one block.
	 */
	@GameTest(template = WeatherGameTests.EMPTY, batch = "awm_instruments_siren", timeoutTicks = 400)
	public static void sirenWindsUpOnRedstone(GameTestHelper helper) {
		BlockPos lower = new BlockPos(8, 2, 8);
		BlockPos upper = lower.above();
		BlockState state = AwmBlocks.TORNADO_SIREN.get().defaultBlockState();
		helper.setBlock(lower, state);
		helper.setBlock(upper, state.setValue(TornadoSirenBlock.HALF, DoubleBlockHalf.UPPER));
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(speed(helper, lower) == 0, "still with no signal");
			helper.setBlock(lower.east(), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(40, () -> {
			int speed = speed(helper, lower);
			helper.assertTrue(speed > 0 && speed < 15, "winding up, not there at once: " + speed);
		});
		helper.runAfterDelay(150, () -> {
			helper.assertTrue(speed(helper, lower) == 15, "full speed on a full signal, got " + speed(helper, lower));
			helper.assertTrue(helper.getBlockState(upper).getValue(TornadoSirenBlock.DIRECTION) > 0, "the horn turns");
			helper.setBlock(lower.east(), Blocks.AIR);
		});
		helper.runAfterDelay(200, () -> {
			int speed = speed(helper, lower);
			helper.assertTrue(speed > 0 && speed < 15, "coasting down, not stopped at once: " + speed);
		});
		helper.runAfterDelay(400 - 10, () -> {
			helper.assertTrue(speed(helper, lower) == 0, "stopped in the end, got " + speed(helper, lower));
			helper.setBlock(lower, Blocks.AIR);
			helper.assertBlockNotPresent(AwmBlocks.TORNADO_SIREN.get(), upper);
			helper.succeed();
		});
	}

	private static int speed(GameTestHelper helper, BlockPos lower) {
		return helper.getBlockState(lower).getValue(TornadoSirenBlock.SPEED);
	}

	/** The acoustic sensor hears a tornado down nearby, louder the closer it is, and nothing once it is gone. */
	@GameTest(template = WeatherGameTests.EMPTY, batch = "awm_instruments_sensor", timeoutTicks = 100)
	public static void sensorHearsATornado(GameTestHelper helper) {
		weather(helper).field().clear();
		BlockPos at = new BlockPos(8, 2, 8);
		helper.setBlock(at, AwmBlocks.TORNADO_SENSOR.get());
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(helper.getBlockState(at).getValue(InstrumentBlock.POWER) == 0, "quiet with no tornado");
			LevelWeather weather = weather(helper);
			long now = helper.getLevel().getGameTime();
			BlockPos abs = helper.absolutePos(at);
			StormCell cell = StormCell.atLife(StormKind.TORNADO, abs.getX(), abs.getZ(), now, 0.4, 36000, 240, 1f, 5,
					weather.field().wind());
			// Its funnel 80 blocks east of the sensor.
			weather.field().add(cell.shifted(abs.getX() + 80 - cell.tornadoX(now), abs.getZ() - cell.tornadoZ(now)), now);
		});
		helper.runAfterDelay(50, () -> {
			int power = helper.getBlockState(at).getValue(InstrumentBlock.POWER);
			helper.assertTrue(power >= 8 && power <= 13, "a tornado about 80 blocks off gives about 11, got " + power);
			weather(helper).field().clear();
		});
		helper.runAfterDelay(75, () -> {
			helper.assertTrue(helper.getBlockState(at).getValue(InstrumentBlock.POWER) == 0, "quiet once it is gone");
			helper.succeed();
		});
	}
}
