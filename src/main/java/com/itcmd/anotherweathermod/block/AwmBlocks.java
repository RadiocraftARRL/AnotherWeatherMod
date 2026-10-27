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

	public static final DeferredHolder<SoundEvent, SoundEvent> TORNADO_SIREN_SOUND = SOUNDS.register("tornado_siren",
			SoundEvent::createVariableRangeEvent);

	private AwmBlocks() {
	}

	private static BlockBehaviour.Properties instrumentProperties() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.LANTERN).noOcclusion()
				.requiresCorrectToolForDrops().strength(1.5f, 6).pushReaction(PushReaction.DESTROY);
	}

	private static <B extends InstrumentBlock> DeferredBlock<B> instrument(String name,
			Function<BlockBehaviour.Properties, B> factory) {
		DeferredBlock<B> block = BLOCKS.register(name, () -> factory.apply(instrumentProperties()));
		ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
		return block;
	}

	/** The instruments, for the creative tab: everything but the Storm Protector. */
	private static List<DeferredBlock<?>> instruments() {
		return List.of(WIND_VANE, BAROMETER, THERMOMETER, RAIN_GAUGE, SOLAR_DETECTOR, TORNADO_SENSOR, TORNADO_SIREN);
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
		if(event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS || event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS)
			for(DeferredBlock<?> block : instruments())
				event.accept(block.get());
	}
}
