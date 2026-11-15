package com.itcmd.anotherweathermod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * The foot of a {@link WarningSirenBlock} (2026-11-13): what the {@link SirenControllerBlock}s wired to it ask for, as
 * the tornado siren keeps it ({@link TornadoSirenBlockEntity}). Any controller asking for more than 0 sounds it. What
 * they ask is saved, and every {@link #CHECK_TICKS} it lets go of loaded controllers that no longer drive it.
 * <p>
 * Also kept here (2026-11-13): its {@link SirenMode} (a sneaking right click steps it) and the signal it sounds on,
 * 0 to 15, both saved and sent to the clients. Each client turns the rotor itself from that signal
 * ({@link SirenRotor}), one step a tick, and remembers the last {@link #HISTORY} ticks of it for the echoes.
 */
public class WarningSirenBlockEntity extends BlockEntity implements WiredSiren {

	/** Ticks between checks that the controllers driving it still do. */
	static final int CHECK_TICKS = 100;

	private final Map<BlockPos, Integer> drives = new HashMap<>();
	private SirenMode mode = SirenMode.AIR_RAID;
	/** The signal it sounds on, 0 to 15 (the server sets it; the clients hear it). */
	private int signal;

	/** Unloaded with its chunk rather than broken: the server keeps it sounding (SirenBroadcast). */
	private boolean unloading;

	public WarningSirenBlockEntity(BlockPos pos, BlockState state) {
		super(AwmBlocks.WARNING_SIREN_ENTITY.get(), pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, WarningSirenBlockEntity siren) {
		if(!siren.drives.isEmpty() && (level.getGameTime() + pos.hashCode()) % CHECK_TICKS == 0)
			siren.checkDrives(level, pos);
		if(level instanceof net.minecraft.server.level.ServerLevel server && state.getBlock() instanceof WarningSirenBlock block)
			com.itcmd.anotherweathermod.world.SirenBroadcast.update(server, pos, siren.mode, block.type() == WarningSirenBlock.Type.HORN, block.height(), siren.signal);
	}

	@Override
	public void onChunkUnloaded() {
		super.onChunkUnloaded();
		unloading = true;
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if(!unloading && level instanceof net.minecraft.server.level.ServerLevel server)
			com.itcmd.anotherweathermod.world.SirenBroadcast.removed(server, worldPosition); // Broken: it stops for everyone.
	}

	public SirenMode mode() {
		return mode;
	}

	/** Steps to the next {@link SirenMode}; returns it. Server. */
	public SirenMode cycleMode() {
		mode = mode.next();
		setChanged();
		sync();
		return mode;
	}

	/** The signal it sounds on, 0 to 15. */
	public int signal() {
		return signal;
	}

	/** Sets the signal it sounds on (server), telling the clients when it changes. */
	public void setSignal(int signal) {
		signal = Math.max(0, Math.min(15, signal));
		if(signal != this.signal) {
			this.signal = signal;
			setChanged();
			sync();
		}
	}

	private void sync() {
		if(level != null && !level.isClientSide)
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
	}

	/** The rotor's speed now, 0 to 1, as the server turns it (SirenBroadcast). */
	public float speed() {
		return level == null ? 0.0F : com.itcmd.anotherweathermod.world.SirenBroadcast.speed(level, worldPosition);
	}

	/**
	 * A client's tick: tells the siren's voice its state while it is loaded (it plays from the voice, which the server's
	 * updates keep going out of loaded range too, 2026-11-14).
	 */
	public static void clientTick(Level level, BlockPos pos, BlockState state, WarningSirenBlockEntity siren) {
		if(state.getBlock() instanceof WarningSirenBlock block)
			com.itcmd.anotherweathermod.client.SirenVoices.fromBlock(pos, siren.mode, block.type() == WarningSirenBlock.Type.HORN, block.height(), siren.signal);
	}

	/** The strongest signal the controllers ask for, 0 to 15. */
	public int drive() {
		int best = 0;
		for(int signal : drives.values())
			best = Math.max(best, signal);
		return best;
	}

	@Override
	public void drive(BlockPos controller, int signal) {
		Integer before = signal > 0 ? drives.put(controller.immutable(), Math.min(signal, 15)) : drives.remove(controller);
		if(before == null ? signal > 0 : before != Math.min(signal, 15)) {
			setChanged();
			refresh();
		}
	}

	private void refresh() {
		if(level != null && !level.isClientSide && getBlockState().getBlock() instanceof WarningSirenBlock siren)
			siren.refresh(level, worldPosition);
	}

	/** Lets go of the controllers that are loaded but no longer drive this siren. */
	private void checkDrives(Level level, BlockPos pos) {
		if(drives.keySet().removeIf(controller -> level.isLoaded(controller)
				&& !(level.getBlockEntity(controller) instanceof SirenControllerBlockEntity c && c.drives(pos)))) {
			setChanged();
			refresh();
		}
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		tag.putString("mode", mode.key());
		tag.putInt("signal", signal);
		return tag;
	}

	@Nullable
	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putString("mode", mode.key());
		tag.putInt("signal", signal);
		if(!drives.isEmpty()) {
			long[] controllers = new long[drives.size()];
			int[] signals = new int[drives.size()];
			int i = 0;
			for(Map.Entry<BlockPos, Integer> drive : drives.entrySet()) {
				controllers[i] = drive.getKey().asLong();
				signals[i++] = drive.getValue();
			}
			tag.putLongArray("controllers", controllers);
			tag.putIntArray("drives", signals);
		}
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		if(tag.contains("mode"))
			mode = SirenMode.byName(tag.getString("mode"));
		signal = Math.max(0, Math.min(15, tag.getInt("signal")));
		drives.clear();
		long[] controllers = tag.getLongArray("controllers");
		int[] signals = tag.getIntArray("drives");
		for(int i = 0; i < Math.min(controllers.length, signals.length); i++)
			if(signals[i] > 0)
				drives.put(BlockPos.of(controllers[i]), Math.min(signals[i], 15));
	}
}
