package com.itcmd.anotherweathermod.block;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.GameMasterBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Function;

/** The mod's blocks, their items, and the sounds they make. */
public final class AwmBlocks {

	private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AnotherWeatherMod.MOD_ID);
	private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AnotherWeatherMod.MOD_ID);
	private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(
			Registries.BLOCK_ENTITY_TYPE, AnotherWeatherMod.MOD_ID);
	private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT,
			AnotherWeatherMod.MOD_ID);

	/** Keeps an area safe from storm damage. Creative only, like a command block. */
	public static final DeferredBlock<StormProtectorBlock> STORM_PROTECTOR = BLOCKS.register("storm_protector",
			() -> new StormProtectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
					.requiresCorrectToolForDrops().strength(-1, 3600000).noLootTable()));
	public static final DeferredItem<GameMasterBlockItem> STORM_PROTECTOR_ITEM = ITEMS.register("storm_protector",
			() -> new GameMasterBlockItem(STORM_PROTECTOR.get(), new Item.Properties().rarity(Rarity.EPIC)));

	public static final DeferredBlock<WindVaneBlock> WIND_VANE = instrument("wind_vane", WindVaneBlock::new);
	public static final DeferredBlock<BarometerBlock> BAROMETER = instrument("barometer", BarometerBlock::new);
	public static final DeferredBlock<ThermometerBlock> THERMOMETER = instrument("thermometer", ThermometerBlock::new);
	public static final DeferredBlock<RainGaugeBlock> RAIN_GAUGE = instrument("rain_gauge", RainGaugeBlock::new);
	public static final DeferredBlock<SolarDetectorBlock> SOLAR_DETECTOR = instrument("solar_detector",
			SolarDetectorBlock::new);
	public static final DeferredBlock<TornadoSirenBlock> TORNADO_SIREN = BLOCKS.register("tornado_siren",
			() -> new TornadoSirenBlock(instrumentProperties().strength(2.5f, 6)));
	public static final DeferredItem<DoubleHighBlockItem> TORNADO_SIREN_ITEM = ITEMS.register("tornado_siren",
			() -> new DoubleHighBlockItem(TORNADO_SIREN.get(), new Item.Properties()));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TornadoSirenBlockEntity>> TORNADO_SIREN_ENTITY =
			BLOCK_ENTITIES.register("tornado_siren", () -> BlockEntityType.Builder.of(TornadoSirenBlockEntity::new,
					TORNADO_SIREN.get()).build(null));
	public static final DeferredBlock<TornadoSensorBlock> TORNADO_SENSOR = instrument("tornado_sensor",
			TornadoSensorBlock::new);

	/** Sounds the sirens its wire reaches, at the strength of its redstone signal. */
	public static final DeferredBlock<SirenControllerBlock> SIREN_CONTROLLER = BLOCKS.register("siren_controller",
			() -> new SirenControllerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
					.sound(SoundType.METAL).requiresCorrectToolForDrops().strength(2.5f, 6)));
	public static final DeferredItem<BlockItem> SIREN_CONTROLLER_ITEM = ITEMS.register("siren_controller",
			() -> new BlockItem(SIREN_CONTROLLER.get(), new Item.Properties()));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SirenControllerBlockEntity>> SIREN_CONTROLLER_ENTITY =
			BLOCK_ENTITIES.register("siren_controller", () -> BlockEntityType.Builder.of(SirenControllerBlockEntity::new,
					SIREN_CONTROLLER.get()).build(null));
	/**
	 * Data wire from controller to sirens. Always registered (a world keeps its wire when RadioCraft comes or goes);
	 * hidden from the creative tab and uncraftable while RadioCraft, whose data wire does the job, is loaded.
	 */
	public static final DeferredBlock<DataWireBlock> DATA_WIRE = BLOCKS.register("data_wire",
			() -> new DataWireBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).noCollission()
					.instabreak().sound(SoundType.WOOL).pushReaction(PushReaction.DESTROY)));
	public static final DeferredItem<BlockItem> DATA_WIRE_ITEM = ITEMS.register("data_wire",
			() -> new BlockItem(DATA_WIRE.get(), new Item.Properties()));

	/**
	 * Electronic warning sirens (moved from RadioCraft, 2026-11-13): the head alone (two blocks) and on a pole with a
	 * control cabinet (twelve). Redstone, a key switch (the pole's cabinet) or a siren controller on their data wire.
	 */
	public static final DeferredBlock<WarningSirenBlock> OMNI_SIREN = siren("omni_siren", false, WarningSirenBlock.Type.OMNI);
	public static final DeferredBlock<WarningSirenBlock> HORN_SIREN = siren("horn_siren", false, WarningSirenBlock.Type.HORN);
	public static final DeferredBlock<WarningSirenBlock> OMNI_SIREN_POLE = siren("omni_siren_pole", true, WarningSirenBlock.Type.OMNI);
	public static final DeferredBlock<WarningSirenBlock> HORN_SIREN_POLE = siren("horn_siren_pole", true, WarningSirenBlock.Type.HORN);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WarningSirenBlockEntity>> WARNING_SIREN_ENTITY =
			BLOCK_ENTITIES.register("warning_siren", () -> BlockEntityType.Builder.of(WarningSirenBlockEntity::new,
					OMNI_SIREN.get(), HORN_SIREN.get(), OMNI_SIREN_POLE.get(), HORN_SIREN_POLE.get()).build(null));

	public static final DeferredHolder<SoundEvent, SoundEvent> TORNADO_SIREN_SOUND = SOUNDS.register("tornado_siren",
			SoundEvent::createVariableRangeEvent);
	/**
	 * The warning sirens' sounds (2026-11-13, from the maintainer's recording, made by {@code tools/siren_audio.py}):
	 * the omni's and the horn's (clipped harder), each also an octave down for the bottom of a wind-up, and a klaxon
	 * (a synthesised stand-in: replace {@code sounds/klaxon.ogg} with a recording, keeping the name).
	 */
	public static final DeferredHolder<SoundEvent, SoundEvent> WARNING_SIREN_SOUND = SOUNDS.register("warning_siren",
			SoundEvent::createVariableRangeEvent);
	public static final DeferredHolder<SoundEvent, SoundEvent> WARNING_SIREN_LOW_SOUND = SOUNDS.register("warning_siren_low",
			SoundEvent::createVariableRangeEvent);
	public static final DeferredHolder<SoundEvent, SoundEvent> WARNING_SIREN_HORN_SOUND = SOUNDS.register("warning_siren_horn",
			SoundEvent::createVariableRangeEvent);
	public static final DeferredHolder<SoundEvent, SoundEvent> WARNING_SIREN_HORN_LOW_SOUND = SOUNDS.register("warning_siren_horn_low",
			SoundEvent::createVariableRangeEvent);
	public static final DeferredHolder<SoundEvent, SoundEvent> KLAXON_SOUND = SOUNDS.register("klaxon",
			SoundEvent::createVariableRangeEvent);

	private AwmBlocks() {
	}

	private static BlockBehaviour.Properties instrumentProperties() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.LANTERN).noOcclusion()
				.requiresCorrectToolForDrops().strength(1.5f, 6).pushReaction(PushReaction.DESTROY);
	}

	private static DeferredBlock<WarningSirenBlock> siren(String name, boolean pole, WarningSirenBlock.Type type) {
		DeferredBlock<WarningSirenBlock> block = BLOCKS.register(name, () -> {
			BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f)
					.noOcclusion().sound(SoundType.METAL).pushReaction(PushReaction.BLOCK);
			return pole ? new WarningSirenBlock.Pole(properties, type) : new WarningSirenBlock.Head(properties, type);
		});
		ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
		return block;
	}

	private static <B extends InstrumentBlock> DeferredBlock<B> instrument(String name,
			Function<BlockBehaviour.Properties, B> factory) {
		DeferredBlock<B> block = BLOCKS.register(name, () -> factory.apply(instrumentProperties()));
		ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
		return block;
	}

	/** The instruments, for the creative tab: everything but the Storm Protector. */
	private static List<DeferredBlock<?>> instruments() {
		return List.of(WIND_VANE, BAROMETER, THERMOMETER, RAIN_GAUGE, SOLAR_DETECTOR, TORNADO_SENSOR, TORNADO_SIREN,
				OMNI_SIREN, HORN_SIREN, OMNI_SIREN_POLE, HORN_SIREN_POLE, SIREN_CONTROLLER);
	}

	/** True if RadioCraft is installed: then its data wire stands in for AWM's. */
	public static boolean radioCraftLoaded() {
		return ModList.get().isLoaded("radiocraft");
	}

	public static void register(IEventBus modBus) {
		BLOCKS.register(modBus);
		ITEMS.register(modBus);
		BLOCK_ENTITIES.register(modBus);
		SOUNDS.register(modBus);
		modBus.addListener(AwmBlocks::onCreativeTabs);
	}

	private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
		if(event.getTabKey() == CreativeModeTabs.OP_BLOCKS && event.hasPermissions())
			event.accept(STORM_PROTECTOR_ITEM.get());
		if(event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS || event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
			for(DeferredBlock<?> block : instruments())
				event.accept(block.get());
			if(!radioCraftLoaded())
				event.accept(DATA_WIRE_ITEM.get());
		}
	}
}
