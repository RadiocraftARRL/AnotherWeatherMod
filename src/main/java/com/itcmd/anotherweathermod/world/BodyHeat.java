package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.core.component.DataComponents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * Overheating in a heat wave. How long a player has been out in its sun (less what they have cooled off) is tracked
 * per player in {@link LevelWeather}; this turns it into effects. After a minute they are slowed, after two weakened
 * too, and after three they suffer heatstroke: hurt now and then until they find shade or water.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID)
public final class BodyHeat {

	public static final ResourceKey<DamageType> HEATSTROKE = ResourceKey.create(Registries.DAMAGE_TYPE,
			AnotherWeatherMod.id("heatstroke"));

	/** Heat, in ticks out in the sun, at which each effect sets in. */
	static final int SLOWED = 1200;
	static final int WEAKENED = 2400;
	static final int MAX = 3600;
	/** How much heat a drink of water takes away. */
	static final int DRINK = 1800;

	private BodyHeat() {
	}

	/** Once a second: the effects of {@code heat}. */
	static void apply(ServerPlayer player, int heat, long now) {
		if(heat >= SLOWED) {
			if(!player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))
				player.displayClientMessage(Component.translatable("message.anotherweathermod.overheating"), true);
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, true, true));
		}
		if(heat >= WEAKENED)
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, true));
		if(heat >= MAX && now % 100 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, true, true));
			player.hurt(new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
					.getHolderOrThrow(HEATSTROKE)), 1);
		}
	}

	@SubscribeEvent
	public static void onDrink(LivingEntityUseItemEvent.Finish event) {
		if(!(event.getEntity() instanceof ServerPlayer player) || !AwmConfig.EXTREME_TEMPERATURE_EFFECTS.get()
				|| !event.getItem().is(Items.POTION))
			return;
		PotionContents contents = event.getItem().get(DataComponents.POTION_CONTENTS);
		if(contents == null || !contents.is(Potions.WATER))
			return;
		LevelWeather weather = WeatherManager.get(player.serverLevel());
		if(weather != null)
			weather.cool(player);
	}
}
