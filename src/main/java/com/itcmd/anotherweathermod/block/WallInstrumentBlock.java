package com.itcmd.anotherweathermod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * An instrument hung on a wall, like a ladder: {@code facing} points away from the wall. Its model is drawn facing
 * south, with its back on the north face.
 */
public abstract class WallInstrumentBlock extends InstrumentBlock {

	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

	/** The bounds of the model facing south, in pixels. */
	@SuppressWarnings("this-escape")
	protected WallInstrumentBlock(Properties properties, double x0, double y0, double z0, double x1, double y1,
			double z1) {
		super(properties);
		shapes.put(Direction.SOUTH, Block.box(x0, y0, z0, x1, y1, z1));
		shapes.put(Direction.NORTH, Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0));
		shapes.put(Direction.WEST, Block.box(16 - z1, y0, x0, 16 - z0, y1, x1));
		shapes.put(Direction.EAST, Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0));
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(POWER, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FACING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.get(state.getValue(FACING));
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		BlockPos wall = pos.relative(facing.getOpposite());
		return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState();
		for(Direction direction : context.getNearestLookingDirections()) {
			if(direction.getAxis().isHorizontal()) {
				state = state.setValue(FACING, direction.getOpposite());
				if(state.canSurvive(context.getLevel(), context.getClickedPos()))
					return state;
			}
		}
		return null;
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
			BlockPos pos, BlockPos neighborPos) {
		return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
				? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
	}
}
