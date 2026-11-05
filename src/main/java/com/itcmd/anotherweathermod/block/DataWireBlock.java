package com.itcmd.anotherweathermod.block;

import com.itcmd.anotherweathermod.world.AwmTags;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * AWM's data wire, for linking siren controllers to sirens where RadioCraft (whose data wire does the same and more)
 * is not installed. It is always registered, so a world keeps its wire when RadioCraft is added or removed; with
 * RadioCraft loaded it leaves the creative tab and its recipe is off, but laid wire keeps working and joins
 * RadioCraft's.
 * <p>
 * It is drawn like RadioCraft's: a thin cable along the floor through the middle of the block, reaching the wire
 * and devices round it (anything in {@code anotherweathermod:siren_wire}, the sirens, the controllers), straight up
 * and down through the middle, and like redstone dust it climbs a wall to wire on top of it ({@link Side#UP}) and
 * steps down a ledge to wire below. Which sirens it links is decided by {@link SirenWiring}, from the blocks, not
 * from this state. No collision, breaks at once.
 */
public class DataWireBlock extends Block {

	public static final MapCodec<DataWireBlock> CODEC = simpleCodec(DataWireBlock::new);

	/** How a side of the wire links: not, along the floor, or along the floor and up the wall there. */
	public enum Side implements StringRepresentable {
		NONE, SIDE, UP;

		@Override
		public @NotNull String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}

		boolean linked() {
			return this != NONE;
		}
	}

	public static final EnumProperty<Side> NORTH = EnumProperty.create("north", Side.class);
	public static final EnumProperty<Side> EAST = EnumProperty.create("east", Side.class);
	public static final EnumProperty<Side> SOUTH = EnumProperty.create("south", Side.class);
	public static final EnumProperty<Side> WEST = EnumProperty.create("west", Side.class);
	public static final BooleanProperty UP = BlockStateProperties.UP;
	public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
	public static final Map<Direction, EnumProperty<Side>> SIDES = new EnumMap<>(Map.of(Direction.NORTH, NORTH,
			Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST));

	/** The outline: the 1 px cable with a pixel round it, easy enough to aim at. */
	private static final VoxelShape MIDDLE = Block.box(6.5, 0, 6.5, 9.5, 2, 9.5);
	private static final VoxelShape RISER = Block.box(6.5, 0, 6.5, 9.5, 16, 9.5);
	private static final Map<Direction, VoxelShape> FLOOR = new EnumMap<>(Direction.class);
	private static final Map<Direction, VoxelShape> CLIMB = new EnumMap<>(Direction.class);

	static {
		FLOOR.put(Direction.NORTH, Block.box(6.5, 0, 0, 9.5, 2, 9.5));
		FLOOR.put(Direction.SOUTH, Block.box(6.5, 0, 6.5, 9.5, 2, 16));
		FLOOR.put(Direction.WEST, Block.box(0, 0, 6.5, 9.5, 2, 9.5));
		FLOOR.put(Direction.EAST, Block.box(6.5, 0, 6.5, 16, 2, 9.5));
		CLIMB.put(Direction.NORTH, Block.box(6.5, 0, 0, 9.5, 16, 2));
		CLIMB.put(Direction.SOUTH, Block.box(6.5, 0, 14, 9.5, 16, 16));
		CLIMB.put(Direction.WEST, Block.box(0, 0, 6.5, 2, 16, 9.5));
		CLIMB.put(Direction.EAST, Block.box(14, 0, 6.5, 16, 16, 9.5));
	}

	/** Each state's outline, worked out once. */
	private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

	@SuppressWarnings("this-escape")
	public DataWireBlock(Properties properties) {
		super(properties);
		BlockState state = stateDefinition.any().setValue(UP, false).setValue(DOWN, false);
		for(EnumProperty<Side> side : SIDES.values())
			state = state.setValue(side, Side.NONE);
		registerDefaultState(state);
		for(BlockState possible : stateDefinition.getPossibleStates())
			shapes.put(possible, shapeOf(possible));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.getOrDefault(state, MIDDLE);
	}

	private static VoxelShape shapeOf(BlockState state) {
		VoxelShape shape = state.getValue(UP) ? RISER : MIDDLE;
		for(Map.Entry<Direction, EnumProperty<Side>> side : SIDES.entrySet()) {
			Side value = state.getValue(side.getValue());
			if(value.linked())
				shape = Shapes.or(shape, FLOOR.get(side.getKey()));
			if(value == Side.UP)
				shape = Shapes.or(shape, CLIMB.get(side.getKey()));
		}
		return shape;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return connect(defaultBlockState(), context.getLevel(), context.getClickedPos());
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
			BlockPos pos, BlockPos neighborPos) {
		return connect(state, level, pos);
	}

	/** Tells the wire diagonally above and below (which climbs to or down to this one) to look again. */
	@Override
	protected void updateIndirectNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos, int flags,
			int recursionLeft) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for(Direction direction : Direction.Plane.HORIZONTAL)
			for(int dy = -1; dy <= 1; dy += 2) {
				at.setWithOffset(pos, direction).move(0, dy, 0);
				if(level.getBlockState(at).getBlock() instanceof DataWireBlock) {
					BlockPos beside = at.relative(direction.getOpposite());
					level.neighborShapeChanged(direction.getOpposite(), level.getBlockState(beside), at.immutable(),
							beside, flags, recursionLeft);
				}
			}
	}

	/** {@code state} with its links worked out from the blocks round {@code pos}. */
	BlockState connect(BlockState state, BlockGetter level, BlockPos pos) {
		BlockState above = level.getBlockState(pos.above());
		boolean coveredAbove = joins(above) || above.isRedstoneConductor(level, pos.above());
		for(Map.Entry<Direction, EnumProperty<Side>> entry : SIDES.entrySet()) {
			Direction direction = entry.getKey();
			BlockPos next = pos.relative(direction);
			BlockState neighbor = level.getBlockState(next);
			Side side = Side.NONE;
			if(joins(neighbor))
				side = Side.SIDE;
			else if(neighbor.isFaceSturdy(level, next, direction.getOpposite())) {
				if(!coveredAbove && isWire(level.getBlockState(next.above())))
					side = Side.UP;
			}
			else if(isWire(level.getBlockState(next.below())))
				side = Side.SIDE;
			state = state.setValue(entry.getValue(), side);
		}
		return state.setValue(UP, joins(above)).setValue(DOWN, joins(level.getBlockState(pos.below())));
	}

	/** True for wire this wire joins: anything in {@code anotherweathermod:siren_wire}. */
	static boolean isWire(BlockState state) {
		return state.is(AwmTags.SIREN_WIRE);
	}

	/** True for what the wire runs into: wire, sirens, controllers. */
	static boolean joins(BlockState state) {
		return isWire(state) || state.is(AwmTags.SIRENS) || state.getBlock() instanceof SirenControllerBlock;
	}
}
