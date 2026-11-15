package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.api.event.WindDamageEvent.DamageClass;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a block with no wind damage tag sits on the wind's ladder (2026-11-13, the maintainer: "it should probably go
 * over all the blocks in the game"), worked out from what it is: what it is made of (its sound), how hard it is,
 * whether anything can bump into it and whether it is a whole block. So every block, any mod's too, gets a sensible
 * place without a hand list: plants go first, glass next, loose wooden bits, then wooden posts and fences, whole
 * wooden and wool blocks only in extreme wind; stone, metal and earth never (an EF4 or EF5 tornado's core aside). Tags
 * ({@link AwmTags}) override it. Containers are left alone: the wind does not smash chests open.
 */
public final class WindClasses {

	/** What a block is made of, for the wind. */
	public enum Material { PLANT, GLASS, WOOD, WOOL, HARD, OTHER }

	/** The sound sets, apart so the pure ladder loads without the game's registries (unit tests). */
	private static final class Sounds {
	private static final Set<SoundType> WOOD = Set.of(SoundType.WOOD, SoundType.CHERRY_WOOD, SoundType.BAMBOO_WOOD,
			SoundType.NETHER_WOOD, SoundType.BAMBOO, SoundType.LADDER, SoundType.SCAFFOLDING);
	private static final Set<SoundType> PLANT = Set.of(SoundType.GRASS, SoundType.CROP, SoundType.HARD_CROP,
			SoundType.SWEET_BERRY_BUSH, SoundType.VINE, SoundType.CAVE_VINES, SoundType.AZALEA, SoundType.FLOWERING_AZALEA,
			SoundType.AZALEA_LEAVES, SoundType.CHERRY_LEAVES, SoundType.PINK_PETALS, SoundType.HANGING_ROOTS,
			SoundType.LILY_PAD, SoundType.ROOTS, SoundType.NETHER_SPROUTS, SoundType.WET_GRASS, SoundType.SPORE_BLOSSOM,
			SoundType.SMALL_DRIPLEAF, SoundType.BIG_DRIPLEAF, SoundType.FUNGUS, SoundType.MOSS, SoundType.MOSS_CARPET);
	}

	private static final Map<BlockState, DamageClass> CACHE = new ConcurrentHashMap<>();

	private WindClasses() {
	}

	/** {@code state}'s class from its properties (cached: a state's properties never change). */
	public static DamageClass byProperties(BlockState state) {
		return CACHE.computeIfAbsent(state, s -> {
			BlockPos zero = BlockPos.ZERO;
			float hardness = s.getDestroySpeed(EmptyBlockGetter.INSTANCE, zero);
			boolean collision = !s.getCollisionShape(EmptyBlockGetter.INSTANCE, zero).isEmpty();
			boolean full = s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, zero);
			return byProperties(material(s.getSoundType(), hardness), hardness, collision, full, s.hasBlockEntity(), s.isAir()
					|| !s.getFluidState().isEmpty());
		});
	}

	/** What {@code sound} says a block is made of. */
	static Material material(SoundType sound, float hardness) {
		if(Sounds.PLANT.contains(sound))
			return Material.PLANT;
		if(sound == SoundType.GLASS)
			return Material.GLASS;
		if(Sounds.WOOD.contains(sound))
			return Material.WOOD;
		if(sound == SoundType.WOOL)
			return Material.WOOL;
		return hardness >= 1.5f ? Material.HARD : Material.OTHER;
	}

	/**
	 * The ladder, from properties alone (pure, for tests): {@code hardness} as a pickaxe-less hand sees it (below 0:
	 * unbreakable), {@code collision} whether it has any, {@code fullBlock} whether it fills its block,
	 * {@code container} whether it holds a block entity, {@code empty} air or fluid.
	 */
	public static DamageClass byProperties(Material material, float hardness, boolean collision, boolean fullBlock,
			boolean container, boolean empty) {
		if(empty || hardness < 0 || container)
			return DamageClass.OTHER;
		if(material == Material.HARD)
			return DamageClass.OTHER; // Stone, metal, brick: the wind leaves them.
		if(!collision) // Nothing to bump into: a plant, or a torch, button or the like.
			return material == Material.PLANT ? DamageClass.FOLIAGE
					: material == Material.OTHER ? DamageClass.OTHER : DamageClass.LOOSE; // Not rails, redstone, plates.
		return switch(material) {
			case PLANT -> fullBlock ? DamageClass.STRUCTURE : DamageClass.FOLIAGE; // Bales and the like; cacti, bamboo.
			case GLASS -> !fullBlock && hardness <= 1 ? DamageClass.FRAGILE : DamageClass.OTHER; // Panes; glass blocks by tag, not lamps.
			case WOOD -> fullBlock ? DamageClass.STRUCTURE : DamageClass.STURDY; // Planks; posts, fences, ladders.
			case WOOL -> DamageClass.STRUCTURE;
			default -> DamageClass.OTHER;
		};
	}
}
