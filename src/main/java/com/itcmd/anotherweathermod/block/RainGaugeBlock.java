package com.itcmd.anotherweathermod.block;

import com.itcmd.anotherweathermod.api.WeatherSample;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A rain gauge: out under the open sky it collects what falls, up to 90 mm, until emptied (use it while sneaking).
 * Snow and hail count as the water they melt to. A comparator reads how full it is (a step for each 6 mm); the signal
 * is how hard it is raining on it now.
 */
public class RainGaugeBlock extends InstrumentBlock {

	/** How full it is, in steps of {@link #MM_PER_STEP}. */
	public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 15);
	public static final double MM_PER_STEP = 6;
	/** Millimetres a second in the heaviest rain (6 mm a minute: a cloudburst, but a storm only lasts minutes). */
	static final double HEAVIEST_MM_PER_SECOND = 0.1;
	private static final VoxelShape SHAPE = Block.box(4.5, 0, 4.5, 11.5, 13.75, 11.5);

	@SuppressWarnings("this-escape")
	public RainGaugeBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FILL, 0).setValue(POWER, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FILL);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Millimetres falling into the gauge each reading. */
	private static double rate(ServerLevel level, BlockPos pos, WeatherSample sample) {
		if(!level.canSeeSky(pos.above()))
			return 0;
		double melt = switch(sample.precipitation()) {
			case RAIN -> 1;
			case HAIL -> 0.8;
			case SNOW -> 0.1;
			default -> 0;
		};
		return sample.rainLevel() * melt * HEAVIEST_MM_PER_SECOND * INTERVAL / 20.0;
	}

	@Override
	protected BlockState read(BlockState state, ServerLevel level, BlockPos pos, WeatherSample sample) {
		double rate = rate(level, pos, sample);
		int fill = state.getValue(FILL);
		// Whole steps only: add one with the chance of the share of a step that fell this time.
		if(fill < 15 && level.random.nextDouble() < rate / MM_PER_STEP)
			fill++;
		return state.setValue(FILL, fill).setValue(POWER, clamp15(rate / HEAVIEST_MM_PER_SECOND * 15));
	}

	@Override
	protected Component reading(BlockState state, ServerLevel level, BlockPos pos, WeatherSample sample) {
		int mm = (int)(state.getValue(FILL) * MM_PER_STEP);
		return state.getValue(FILL) >= 15
				? Component.translatable("block.anotherweathermod.rain_gauge.full", mm)
				: Component.translatable("block.anotherweathermod.rain_gauge.reading", mm);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if(!player.isShiftKeyDown())
			return super.useWithoutItem(state, level, pos, player, hit);
		if(level instanceof ServerLevel && state.getValue(FILL) > 0) {
			level.setBlock(pos, state.setValue(FILL, 0), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6f, 1.4f);
			player.displayClientMessage(Component.translatable("block.anotherweathermod.rain_gauge.emptied",
					(int)(state.getValue(FILL) * MM_PER_STEP)), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return state.getValue(FILL);
	}
}
