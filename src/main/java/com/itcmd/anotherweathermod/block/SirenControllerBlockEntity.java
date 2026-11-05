package com.itcmd.anotherweathermod.block;

import com.itcmd.anotherweathermod.world.SirenWires;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The siren controller's memory: the redstone signal it last passed on, and the sirens its wire reached when it last
 * walked it. It does not tick. It walks the wire ({@link SirenWires#flood}) only when it is powered and its answer is
 * stale: just loaded, or wire or a siren changed somewhere in the level ({@link SirenWires} notices that). Then, and
 * whenever its input changes, it tells each siren the signal ({@link WiredSiren#drive}); 0 lets them go. All of this
 * runs at the end of the level's tick ({@link #update}), so several redstone changes in one tick cost one update.
 */
public class SirenControllerBlockEntity extends BlockEntity {

	/** Ticks between walks a chunk loading can cause, for a controller whose last walk ran into unloaded blocks. */
	public static final int RETRY_TICKS = 20;

	/** The signal it passes on, 0 to 15. */
	private int input;
	/** The rotors of the sirens it reached, nearest first. */
	private List<BlockPos> sirens = List.of();
	/** True when the wire may have changed since it walked it. */
	private boolean stale = true;
	/** True when its last walk stopped short at unloaded blocks or too much wire. */
	private boolean incomplete;
	/** True when chunks loaded since, which might let an incomplete walk go further. */
	private boolean retry;
	private long lastFlood = Long.MIN_VALUE / 2;

	public SirenControllerBlockEntity(BlockPos pos, BlockState state) {
		super(AwmBlocks.SIREN_CONTROLLER_ENTITY.get(), pos, state);
	}

	/** The signal it passes on, 0 to 15. */
	public int input() {
		return input;
	}

	/** The sirens it reached when it last walked its wire. */
	public List<BlockPos> sirens() {
		return sirens;
	}

	/** True if it is sounding the siren whose rotor is at {@code siren}. */
	public boolean drives(BlockPos siren) {
		return input > 0 && sirens.contains(siren);
	}

	/** Wire or a siren changed in the level: walk again before the next time it sounds anything. */
	public void markStale() {
		stale = true;
	}

	/** A chunk loaded: if its last walk stopped short, it may now go further. */
	public boolean wantsRetry() {
		if(incomplete && input > 0)
			retry = true;
		return retry;
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if(level != null && !level.isClientSide) {
			stale = true;
			SirenWires.loaded(this);
		}
	}

	@Override
	public void setRemoved() {
		if(level != null && !level.isClientSide)
			SirenWires.unloaded(this);
		super.setRemoved();
	}

	/**
	 * Reads its input and passes it on, walking the wire first if it is powered and its answer is stale. Returns
	 * false to be asked again next tick (a retry too soon after the last walk).
	 */
	public boolean update() {
		Level level = this.level;
		if(level == null || level.isClientSide || isRemoved())
			return true;
		long now = level.getGameTime();
		int signal = level.getBestNeighborSignal(worldPosition);
		List<BlockPos> before = sirens;
		if(signal > 0 && !stale && retry && now - lastFlood < RETRY_TICKS)
			return false;
		if(signal > 0 && (stale || retry)) {
			SirenWiring.Result result = SirenWires.flood(level, worldPosition);
			sirens = result.sirens();
			incomplete = result.incomplete();
			stale = false;
			retry = false;
			lastFlood = now;
		}
		for(BlockPos siren : before)
			if(!sirens.contains(siren))
				drive(level, siren, 0);
		boolean changed = signal != input || before != sirens;
		input = signal;
		if(changed)
			for(BlockPos siren : sirens)
				drive(level, siren, signal);
		BlockState state = getBlockState();
		if(state.hasProperty(SirenControllerBlock.POWERED) && state.getValue(SirenControllerBlock.POWERED) != signal > 0)
			level.setBlock(worldPosition, state.setValue(SirenControllerBlock.POWERED, signal > 0), Block.UPDATE_CLIENTS);
		if(changed)
			setChanged();
		return true;
	}

	/** The controller is being broken: every siren it sounds lets go. */
	public void release() {
		if(level == null)
			return;
		for(BlockPos siren : sirens)
			drive(level, siren, 0);
		input = 0;
	}

	private void drive(Level level, BlockPos siren, int signal) {
		if(level.isLoaded(siren) && level.getBlockEntity(siren) instanceof WiredSiren wired)
			wired.drive(worldPosition, signal);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("input", input);
		long[] positions = new long[sirens.size()];
		for(int i = 0; i < positions.length; i++)
			positions[i] = sirens.get(i).asLong();
		tag.putLongArray("sirens", positions);
		tag.putBoolean("incomplete", incomplete);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		input = tag.getInt("input");
		List<BlockPos> positions = new ArrayList<>();
		for(long position : tag.getLongArray("sirens"))
			positions.add(BlockPos.of(position));
		sirens = List.copyOf(positions);
		incomplete = tag.getBoolean("incomplete");
		stale = true;
	}
}
