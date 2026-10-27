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

/**
 * The rotor of a tornado siren, in its lower half. It chases the speed its redstone signal asks for, winding up in
 * {@link #WIND_UP_TICKS} and coasting down in {@link #WIND_DOWN_TICKS}, and turns the horn once every
 * {@link #TURN_TICKS} at full speed. The clients hear it through {@link SirenSound}, which follows the
 * {@link TornadoSirenBlock#SPEED} the server sets.
 */
public class TornadoSirenBlockEntity extends BlockEntity {

	/** Ticks from still to full speed. */
	static final int WIND_UP_TICKS = 120;
	/** Ticks from full speed to still. */
	static final int WIND_DOWN_TICKS = 240;
	/** Ticks for a full turn of the horn at full speed. */
	static final int TURN_TICKS = 160;

	/** 0 still to 1 full. */
	private float speed;
	/** Where the horn points, degrees clockwise from north. */
	private float angle;
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
		int signal = Math.max(level.getBestNeighborSignal(pos), level.getBestNeighborSignal(top));
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

	public static void clientTick(Level level, BlockPos pos, BlockState state, TornadoSirenBlockEntity siren) {
		if(state.getValue(TornadoSirenBlock.SPEED) > 0)
			SirenSound.keepPlaying(siren);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putFloat("speed", speed);
		tag.putFloat("angle", angle);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		speed = tag.getFloat("speed");
		angle = tag.getFloat("angle");
	}
}
