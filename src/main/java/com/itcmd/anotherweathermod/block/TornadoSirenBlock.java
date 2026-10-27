package com.itcmd.anotherweathermod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A tornado siren, two blocks tall: a control cabinet on a pole below, the round horn above. A redstone signal on
 * either half drives its rotor, the signal's strength setting the speed: 15 runs it full, and anything that varies
 * the signal (a tornado acoustic sensor, a timer) makes it rise and fall. The rotor takes a few seconds to wind up and
 * longer to coast down, and the horn turns as it sounds. The lower half keeps the rotor
 * ({@link TornadoSirenBlockEntity}).
 */
public class TornadoSirenBlock extends BaseEntityBlock {

	public static final MapCodec<TornadoSirenBlock> CODEC = simpleCodec(TornadoSirenBlock::new);
	public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
	/** The rotor's speed, 0 still to 15 full: what the sound follows. Only the lower half has it. */
	public static final IntegerProperty SPEED = IntegerProperty.create("speed", 0, 15);
	/** Where the horn points, 16 points clockwise from north. Only the upper half turns. */
	public static final IntegerProperty DIRECTION = IntegerProperty.create("direction", 0, 15);
	private static final VoxelShape LOWER = Block.box(2.5, 0, 2.5, 13.5, 16, 13.5);
	private static final VoxelShape UPPER = Block.box(2, 0, 2, 14, 14, 14);

	@SuppressWarnings("this-escape")
	public TornadoSirenBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER).setValue(SPEED, 0)
				.setValue(DIRECTION, 0));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HALF, SPEED, DIRECTION);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new TornadoSirenBlockEntity(pos, state) : null;
	}

	@Override
	public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if(state.getValue(HALF) != DoubleBlockHalf.LOWER)
			return null;
		return createTickerHelper(type, AwmBlocks.TORNADO_SIREN_ENTITY.get(),
				level.isClientSide ? TornadoSirenBlockEntity::clientTick : TornadoSirenBlockEntity::serverTick);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		Level level = context.getLevel();
		return pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)
				? defaultBlockState() : null;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		if(state.getValue(HALF) == DoubleBlockHalf.UPPER) {
			BlockState below = level.getBlockState(pos.below());
			return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
		}
		return Block.canSupportCenter(level, pos.below(), Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
			BlockPos pos, BlockPos neighborPos) {
		DoubleBlockHalf half = state.getValue(HALF);
		if(direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP))
			return neighbor.is(this) && neighbor.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
		if(half == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canSurvive(level, pos))
			return Blocks.AIR.defaultBlockState();
		return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		// Broken from the top in creative, the bottom would otherwise drop the siren.
		if(!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
			BlockPos below = pos.below();
			if(level.getBlockState(below).is(this))
				level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if(!level.isClientSide) {
			BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
			int speed = level.getBlockState(lower).is(this) ? level.getBlockState(lower).getValue(SPEED) : 0;
			player.displayClientMessage(Component.translatable(speed > 0
					? "block.anotherweathermod.tornado_siren.sounding" : "block.anotherweathermod.tornado_siren.idle",
					Math.round(speed * 100 / 15.0)), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}
}
