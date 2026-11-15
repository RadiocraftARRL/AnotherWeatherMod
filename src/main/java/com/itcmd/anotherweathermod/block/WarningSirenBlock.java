package com.itcmd.anotherweathermod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An electronic outdoor warning siren (moved from RadioCraft on 2026-11-13; models from {@code tools/siren_models.py}):
 * the head is two blocks tall, an omnidirectional stack of cells or a horn array. {@link #segment()} 0 is the foot,
 * where it is placed, what drops and where its {@link WarningSirenBlockEntity} is; the rest is placed and broken with
 * it.
 * <p>
 * Two ways: the head alone ({@link Head}, two blocks: it stands on a fence post, a log or anything, and sounds while
 * either half has redstone power), or on a pole ({@link Pole}, twelve blocks: a control cabinet at the foot, nine
 * blocks of pole, the head on top; the cabinet sounds it from redstone or its key switch, a right click). A
 * {@link SirenControllerBlock} on its data wire sounds it too. It is on while {@link #POWERED} or {@link #SWITCHED} is
 * set on its foot (see {@link #isOn}). RadioCraft will add playing a radio through it.
 * <p>
 * Its sound (2026-11-13): a sneaking right click on any part steps its {@link SirenMode} (air raid, tornado,
 * klaxon). The strength of its signal (the strongest of the redstone at it, its controllers, and 15 for a pole's
 * key switch) sets how fast its rotor winds up to ({@link SirenRotor}); clients play it with
 * {@code WarningSirenSound}.
 */
public abstract class WarningSirenBlock extends HorizontalDirectionalBlock implements EntityBlock {

	/** Redstone on it, or a controller sounding it. */
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	/** The pole siren's key switch: on until switched off. */
	public static final BooleanProperty SWITCHED = BooleanProperty.create("switched");

	/** Which head (the shapes; the models are the blockstates'). */
	public enum Type { OMNI, HORN }

	private static final VoxelShape OMNI_LOWER = Shapes.or(box(5, 0, 5, 11, 2.5, 11), box(2.5, 2.5, 2.5, 13.5, 16, 13.5));
	private static final VoxelShape OMNI_UPPER = box(2.5, 0, 2.5, 13.5, 15.5, 13.5);
	private static final VoxelShape HORN_LOWER = Shapes.or(box(4.5, 0, 4.5, 11.5, 3, 11.5), box(0, 3, 0, 16, 16, 16));
	private static final VoxelShape HORN_UPPER = box(0, 0, 0, 16, 15.75, 16);
	private static final VoxelShape CABINET = Shapes.or(box(1, 0, 2, 15, 15, 14), box(5, 15, 5, 11, 16, 11));
	private static final VoxelShape POLE = box(6, 0, 6, 10, 16, 10);

	private final Type type;
	private final MapCodec<WarningSirenBlock> codec;

	@SuppressWarnings("this-escape")
	protected WarningSirenBlock(Properties properties, Type type) {
		super(properties);
		this.type = type;
		this.codec = simpleCodec(p -> make(p, type));
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(SWITCHED, false).setValue(segment(), 0));
	}

	/** The same kind of siren with {@code properties}, for the codec. */
	protected abstract WarningSirenBlock make(Properties properties, Type type);

	/** The segment property: 0 at the foot. */
	public abstract IntegerProperty segment();

	/** Blocks tall. */
	public abstract int height();

	public Type type() {
		return type;
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return codec;
	}

	/**
	 * True while the siren whose foot is {@code foot} should sound: powered (redstone or a controller), or switched on.
	 * The sound (to come) belongs at the head, {@code height() - 1} blocks up.
	 */
	public static boolean isOn(BlockState foot) {
		return foot.getBlock() instanceof WarningSirenBlock && (foot.getValue(POWERED) || foot.getValue(SWITCHED));
	}

	/** The foot of the siren that {@code state} at {@code pos} is part of. */
	public BlockPos foot(BlockPos pos, BlockState state) {
		return pos.below(state.getValue(segment()));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED, SWITCHED);
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		Level level = context.getLevel();
		if(pos.getY() + height() > level.getMaxBuildHeight())
			return null;
		for(int i = 1; i < height(); i++)
			if(!level.getBlockState(pos.above(i)).canBeReplaced(context))
				return null;
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(POWERED, redstone(level, pos));
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		for(int i = 1; i < height(); i++)
			level.setBlock(pos.above(i), state.setValue(POWERED, false).setValue(segment(), i), Block.UPDATE_ALL);
	}

	/** A segment goes when the one under it or over it does, so the whole siren comes down together. */
	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		int segment = state.getValue(segment());
		if(direction == Direction.UP && segment < height() - 1 && !(neighbour.is(this) && neighbour.getValue(segment()) == segment + 1))
			return Blocks.AIR.defaultBlockState();
		if(direction == Direction.DOWN && segment > 0 && !(neighbour.is(this) && neighbour.getValue(segment()) == segment - 1))
			return Blocks.AIR.defaultBlockState();
		return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		int segment = state.getValue(segment());
		if(segment == 0)
			return true;
		BlockState below = level.getBlockState(pos.below());
		return below.is(this) && below.getValue(segment()) == segment - 1;
	}

	/** In creative, breaking it higher up must not let the foot drop the siren as the rest comes down. */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		int segment = state.getValue(segment());
		if(!level.isClientSide && player.isCreative() && segment > 0) {
			BlockPos foot = pos.below(segment);
			BlockState footState = level.getBlockState(foot);
			if(footState.is(this) && footState.getValue(segment()) == 0) {
				level.setBlock(foot, footState.getFluidState().is(Fluids.WATER) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 35);
				level.levelEvent(player, 2001, foot, Block.getId(footState));
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/** Redstone at the head's foot or its top half (the head alone), or at the cabinet (on a pole). */
	private boolean redstone(Level level, BlockPos foot) {
		return level.hasNeighborSignal(foot) || height() == 2 && level.hasNeighborSignal(foot.above());
	}

	/** Sets {@link #POWERED} on the foot at {@code foot} from its redstone and the controllers sounding it. */
	public void refresh(Level level, BlockPos foot) {
		BlockState footState = level.getBlockState(foot);
		if(!footState.is(this) || footState.getValue(segment()) != 0)
			return;
		boolean powered = redstone(level, foot) || level.getBlockEntity(foot) instanceof WarningSirenBlockEntity siren && siren.drive() > 0;
		if(powered != footState.getValue(POWERED))
			level.setBlock(foot, footState.setValue(POWERED, powered), Block.UPDATE_ALL);
		if(level.getBlockEntity(foot) instanceof WarningSirenBlockEntity siren) {
			int signal = Math.max(level.getBestNeighborSignal(foot), siren.drive());
			if(height() == 2)
				signal = Math.max(signal, level.getBestNeighborSignal(foot.above()));
			if(footState.getValue(SWITCHED))
				signal = 15;
			siren.setSignal(signal);
		}
	}

	/** A sneaking right click on any part steps the siren to its next {@link SirenMode}. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if(!player.isShiftKeyDown())
			return InteractionResult.PASS;
		if(!level.isClientSide && level.getBlockEntity(foot(pos, state)) instanceof WarningSirenBlockEntity siren) {
			SirenMode mode = siren.cycleMode();
			level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4F, 0.9F);
			player.displayClientMessage(Component.translatable("message.anotherweathermod.siren.mode",
					Component.translatable("message.anotherweathermod.siren.mode." + mode.key())), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moved) {
		if(!level.isClientSide)
			refresh(level, foot(pos, state));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		int segment = state.getValue(segment());
		int head = height() - 2;
		if(segment == head)
			return type == Type.OMNI ? OMNI_LOWER : HORN_LOWER;
		if(segment == head + 1)
			return type == Type.OMNI ? OMNI_UPPER : HORN_UPPER;
		return segment == 0 ? CABINET : POLE;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable(height() == 2 ? "tooltip.anotherweathermod.siren" : "tooltip.anotherweathermod.siren_pole")
				.withStyle(net.minecraft.ChatFormatting.GRAY));
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return state.getValue(segment()) == 0 ? new WarningSirenBlockEntity(pos, state) : null;
	}

	@Nullable
	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(type != AwmBlocks.WARNING_SIREN_ENTITY.get())
			return null;
		return level.isClientSide ? (BlockEntityTicker<T>)(BlockEntityTicker<WarningSirenBlockEntity>)WarningSirenBlockEntity::clientTick
				: (BlockEntityTicker<T>)(BlockEntityTicker<WarningSirenBlockEntity>)WarningSirenBlockEntity::serverTick;
	}

	/** The siren head alone: two blocks. */
	public static class Head extends WarningSirenBlock {
		public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, 1);

		public Head(Properties properties, Type type) {
			super(properties, type);
		}

		@Override
		protected WarningSirenBlock make(Properties properties, Type type) {
			return new Head(properties, type);
		}

		@Override
		public IntegerProperty segment() {
			return SEGMENT;
		}

		@Override
		public int height() {
			return 2;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			super.createBlockStateDefinition(builder);
			builder.add(SEGMENT);
		}
	}

	/** On a pole with a control cabinet at its foot: twelve blocks. A right click on the cabinet turns its key switch. */
	public static class Pole extends WarningSirenBlock {
		public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, 11);

		public Pole(Properties properties, Type type) {
			super(properties, type);
		}

		@Override
		protected WarningSirenBlock make(Properties properties, Type type) {
			return new Pole(properties, type);
		}

		@Override
		public IntegerProperty segment() {
			return SEGMENT;
		}

		@Override
		public int height() {
			return 12;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			super.createBlockStateDefinition(builder);
			builder.add(SEGMENT);
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if(player.isShiftKeyDown())
				return super.useWithoutItem(state, level, pos, player, hit); // Its mode.
			if(state.getValue(SEGMENT) != 0)
				return InteractionResult.PASS;
			if(!level.isClientSide) {
				boolean on = !state.getValue(SWITCHED);
				level.setBlock(pos, state.setValue(SWITCHED, on), Block.UPDATE_ALL);
				refresh(level, pos); // The key switch sounds it at full strength.
				level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4F, on ? 0.7F : 0.6F);
				player.displayClientMessage(Component.translatable(on ? "message.anotherweathermod.siren.on" : "message.anotherweathermod.siren.off"), true);
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
	}
}
