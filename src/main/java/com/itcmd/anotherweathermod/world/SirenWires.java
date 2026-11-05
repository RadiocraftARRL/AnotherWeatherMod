package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.block.SirenControllerBlockEntity;
import com.itcmd.anotherweathermod.block.SirenWiring;
import com.itcmd.anotherweathermod.block.WiredSiren;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Keeps siren controllers' answers fresh without scanning. The server level tells it of every block that turns into
 * or out of siren wire or a siren ({@link #blockChanged}, from {@code MixinServerLevel}; a block only changing its
 * state, like a siren's rotor speeding up, does not count). At the end of that tick every loaded controller in the
 * level is marked stale, and the powered ones walk their wire again. A chunk loading lets controllers whose last walk
 * ran into unloaded blocks try again, at most once a second. Server thread only.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID)
public final class SirenWires {

	/** The most wire blocks one walk follows, so a huge web of wire cannot stall a tick. */
	public static final int MAX_WIRE = 4096;

	private static final Map<Level, Set<SirenControllerBlockEntity>> LOADED = new WeakHashMap<>();
	private static final Map<Level, Set<SirenControllerBlockEntity>> PENDING = new WeakHashMap<>();
	private static final Set<Level> CHANGED = Collections.newSetFromMap(new WeakHashMap<>());

	private SirenWires() {
	}

	/** A controller was loaded or placed: it updates at the end of the tick. */
	public static void loaded(SirenControllerBlockEntity controller) {
		Level level = controller.getLevel();
		if(level == null)
			return;
		LOADED.computeIfAbsent(level, l -> new LinkedHashSet<>()).add(controller);
		schedule(controller);
	}

	/** A controller was unloaded or broken. */
	public static void unloaded(SirenControllerBlockEntity controller) {
		Level level = controller.getLevel();
		if(level == null)
			return;
		Set<SirenControllerBlockEntity> loaded = LOADED.get(level);
		if(loaded != null)
			loaded.remove(controller);
		Set<SirenControllerBlockEntity> pending = PENDING.get(level);
		if(pending != null)
			pending.remove(controller);
	}

	/** The controller updates at the end of this tick (its input may have changed). */
	public static void schedule(SirenControllerBlockEntity controller) {
		Level level = controller.getLevel();
		if(level != null && !level.isClientSide)
			PENDING.computeIfAbsent(level, l -> new LinkedHashSet<>()).add(controller);
	}

	/** {@code pos} in {@code level} changed from {@code before} to {@code after}. */
	public static void blockChanged(Level level, BlockState before, BlockState after) {
		if(before.getBlock() != after.getBlock() && (counts(before) || counts(after)))
			CHANGED.add(level);
	}

	private static boolean counts(BlockState state) {
		return state.is(AwmTags.SIREN_WIRE) || state.is(AwmTags.SIRENS);
	}

	/** Walks the wire from the controller at {@code pos} ({@link SirenWiring}). */
	public static SirenWiring.Result flood(Level level, BlockPos pos) {
		return SirenWiring.flood(at -> cell(level, at), pos, MAX_WIRE);
	}

	private static SirenWiring.Cell cell(Level level, BlockPos pos) {
		if(!level.isLoaded(pos))
			return SirenWiring.Nothing.UNLOADED;
		BlockState state = level.getBlockState(pos);
		if(state.is(AwmTags.SIREN_WIRE))
			return new SirenWiring.Wire(state.getBlock(), state.is(AwmTags.SIREN_WIRE_JOINS_ALL));
		if(!state.is(AwmTags.SIRENS))
			return SirenWiring.Nothing.NOTHING;
		if(level.getBlockEntity(pos) instanceof WiredSiren)
			return new SirenWiring.Siren(pos.immutable());
		BlockPos below = pos.below();
		if(level.isLoaded(below) && level.getBlockState(below).is(state.getBlock())
				&& level.getBlockEntity(below) instanceof WiredSiren)
			return new SirenWiring.Siren(below);
		return SirenWiring.Nothing.NOTHING;
	}

	@SubscribeEvent
	public static void onChunkLoad(ChunkEvent.Load event) {
		LevelAccessor accessor = event.getLevel();
		if(accessor.isClientSide() || !(accessor instanceof Level level))
			return;
		Set<SirenControllerBlockEntity> loaded = LOADED.get(level);
		if(loaded == null)
			return;
		for(SirenControllerBlockEntity controller : loaded)
			if(controller.wantsRetry())
				schedule(controller);
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		Level level = event.getLevel();
		if(level.isClientSide)
			return;
		if(CHANGED.remove(level)) {
			Set<SirenControllerBlockEntity> loaded = LOADED.get(level);
			if(loaded != null)
				for(SirenControllerBlockEntity controller : loaded) {
					controller.markStale();
					if(controller.input() > 0 || level.hasNeighborSignal(controller.getBlockPos()))
						schedule(controller);
				}
		}
		Set<SirenControllerBlockEntity> pending = PENDING.get(level);
		if(pending == null || pending.isEmpty())
			return;
		for(SirenControllerBlockEntity controller : new ArrayList<>(pending))
			if(controller.isRemoved() || controller.update())
				pending.remove(controller);
	}
}
