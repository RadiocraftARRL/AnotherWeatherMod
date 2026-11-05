package com.itcmd.anotherweathermod.block;

import com.itcmd.anotherweathermod.client.SirenSound;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The rotor of a tornado siren, in its lower half. It chases the speed its redstone signal asks for, winding up in
 * {@link #WIND_UP_TICKS} and coasting down in {@link #WIND_DOWN_TICKS}, and turns the horn once every
 * {@link #TURN_TICKS} at full speed. The clients hear it through {@link SirenSound}, which follows the
 * {@link TornadoSirenBlock#SPEED} the server sets.
 * <p>
 * Besides redstone, siren controllers wired to it ask for a speed ({@link #drive}); it runs at the strongest of them
 * and its own signal. What they ask is saved with the siren, so a siren whose chunk loads before its controller's
 * keeps sounding. Every {@link #CHECK_TICKS} it checks the loaded controllers still drive it, so one that was broken
 * or rewired while the siren was unloaded lets go.
 */
public class TornadoSirenBlockEntity extends BlockEntity implements WiredSiren {

	/** Ticks from still to full speed. */
	static final int WIND_UP_TICKS = 120;
	/** Ticks from full speed to still. */
	static final int WIND_DOWN_TICKS = 240;
	/** Ticks for a full turn of the horn at full speed. */
	static final int TURN_TICKS = 160;
	/** Ticks between checks that the controllers driving it still do. */
	static final int CHECK_TICKS = 100;

	/** 0 still to 1 full. */
	private float speed;
	/** Where the horn points, degrees clockwise from north. */
	private float angle;
	/** What each controller wired to it asks for, 1 to 15, by the controller's position. */
	private final Map<BlockPos, Integer> drives = new HashMap<>();
	/** The sound playing here, on a client. */
	@Nullable
	public Object sound;

	public TornadoSirenBlockEntity(BlockPos pos, BlockState state) {
		super(AwmBlocks.TORNADO_SIREN_ENTITY.get(), pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, TornadoSirenBlockEntity siren) {
		siren.serverTick(level, pos, state);
	}

	private void serverTick(Level level, BlockPos pos, BlockState state) {
		BlockPos top = pos.above();
		BlockState upper = level.getBlockState(top);
		if(!upper.is(state.getBlock()))
			return;
		if(!drives.isEmpty() && (level.getGameTime() + pos.hashCode()) % CHECK_TICKS == 0)
			checkDrives(level, pos);
		int signal = Math.max(Math.max(level.getBestNeighborSignal(pos), level.getBestNeighborSignal(top)), drive());
		float target = signal / 15f;
		float before = speed;
		speed = Mth.clamp(speed + Mth.clamp(target - speed, -1f / WIND_DOWN_TICKS, 1f / WIND_UP_TICKS), 0, 1);
		if(speed == 0 && before == 0)
			return;
		angle = (angle + speed * 360f / TURN_TICKS) % 360;
		setChanged();
		int step = Math.round(speed * 15);
		if(step != state.getValue(TornadoSirenBlock.SPEED))
			level.setBlock(pos, state.setValue(TornadoSirenBlock.SPEED, step), Block.UPDATE_CLIENTS);
		int direction = (int)(angle / 22.5f) % 16;
		if(direction != upper.getValue(TornadoSirenBlock.DIRECTION))
			level.setBlock(top, upper.setValue(TornadoSirenBlock.DIRECTION, direction), Block.UPDATE_CLIENTS);
	}

	/** The strongest speed the controllers ask for, 0 to 15. */
	public int drive() {
		int best = 0;
		for(int signal : drives.values())
			best = Math.max(best, signal);
		return best;
	}

	@Override
	public void drive(BlockPos controller, int signal) {
		Integer before = signal > 0 ? drives.put(controller.immutable(), Math.min(signal, 15)) : drives.remove(controller);
		if(before == null ? signal > 0 : before != Math.min(signal, 15))
			setChanged();
	}

	/** Lets go of the controllers that are loaded but no longer drive this siren. */
	private void checkDrives(Level level, BlockPos pos) {
		if(drives.keySet().removeIf(controller -> level.isLoaded(controller)
				&& !(level.getBlockEntity(controller) instanceof SirenControllerBlockEntity c && c.drives(pos))))
			setChanged();
	}

	public static void clientTick(Level level, BlockPos pos, BlockState state, TornadoSirenBlockEntity siren) {
		if(state.getValue(TornadoSirenBlock.SPEED) > 0)
			SirenSound.keepPlaying(siren);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putFloat("speed", speed);
		tag.putFloat("angle", angle);
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
		speed = tag.getFloat("speed");
		angle = tag.getFloat("angle");
		drives.clear();
		long[] controllers = tag.getLongArray("controllers");
		int[] signals = tag.getIntArray("drives");
		for(int i = 0; i < Math.min(controllers.length, signals.length); i++)
			if(signals[i] > 0)
				drives.put(BlockPos.of(controllers[i]), Math.min(signals[i], 15));
	}
}
