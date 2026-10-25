package com.itcmd.anotherweathermod.block;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.GameMasterBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's blocks and their items. */
public final class AwmBlocks {

	private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AnotherWeatherMod.MOD_ID);
	private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AnotherWeatherMod.MOD_ID);

	/** Keeps an area safe from storm damage. Creative only, like a command block. */
	public static final DeferredBlock<StormProtectorBlock> STORM_PROTECTOR = BLOCKS.register("storm_protector",
			() -> new StormProtectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
					.requiresCorrectToolForDrops().strength(-1, 3600000).noLootTable()));
	public static final DeferredItem<GameMasterBlockItem> STORM_PROTECTOR_ITEM = ITEMS.register("storm_protector",
			() -> new GameMasterBlockItem(STORM_PROTECTOR.get(), new Item.Properties().rarity(Rarity.EPIC)));

	private AwmBlocks() {
	}

	public static void register(IEventBus modBus) {
		BLOCKS.register(modBus);
		ITEMS.register(modBus);
		modBus.addListener(AwmBlocks::onCreativeTabs);
	}

	private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
		if(event.getTabKey() == CreativeModeTabs.OP_BLOCKS && event.hasPermissions())
			event.accept(STORM_PROTECTOR_ITEM.get());
	}
}
