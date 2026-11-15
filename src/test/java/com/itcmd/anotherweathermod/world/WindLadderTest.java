package com.itcmd.anotherweathermod.world;

import com.itcmd.anotherweathermod.api.event.WindDamageEvent.DamageClass;
import com.itcmd.anotherweathermod.world.WindClasses.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The wind's ladder (2026-11-13): what goes first, what hardly ever, what never. */
class WindLadderTest {

	/** A category 2 hurricane's top wind: nothing but foliage (and doors blowing open) goes below it. */
	static final double CATEGORY_2 = 49;

	@Test
	void classesBreakInASensibleOrder() {
		DamageClass[] ladder = { DamageClass.FOLIAGE, DamageClass.FRAGILE, DamageClass.LOOSE, DamageClass.STURDY,
				DamageClass.STRUCTURE, DamageClass.HEAVY };
		for(int i = 1; i < ladder.length; i++)
			assertTrue(AwmTags.threshold(ladder[i]) > AwmTags.threshold(ladder[i - 1]), ladder[i] + " after " + ladder[i - 1]);
		for(int i = 2; i < ladder.length; i++)
			assertTrue(AwmTags.chance(ladder[i]) <= AwmTags.chance(ladder[i - 1]), ladder[i] + " no likelier than " + ladder[i - 1]);
		assertTrue(AwmTags.chance(DamageClass.HEAVY) < AwmTags.chance(DamageClass.STRUCTURE), "logs rarer than planks");
		assertTrue(AwmTags.chance(DamageClass.RESILIENT) < AwmTags.chance(DamageClass.STURDY), "sirens and antennas rarely");
		for(DamageClass c : DamageClass.values())
			if(c != DamageClass.FOLIAGE && c != DamageClass.BLOWS_OPEN && c != DamageClass.OTHER)
				assertTrue(AwmTags.threshold(c) > CATEGORY_2, c + " survives a category 2");
		assertTrue(AwmTags.threshold(DamageClass.EXTREME) >= 70 && AwmTags.threshold(DamageClass.RESILIENT) >= 70, "cat 5 / EF4+ only");
	}

	@Test
	void chanceRisesWithTheWindAndIsSmall() {
		assertEquals(0, AwmTags.chanceAt(DamageClass.FRAGILE, 45));
		assertTrue(AwmTags.chanceAt(DamageClass.FRAGILE, 51) < AwmTags.chanceAt(DamageClass.FRAGILE, 70));
		assertTrue(AwmTags.chanceAt(DamageClass.HEAVY, 100) <= 0.02, "even an EF5 seldom takes a log");
		for(DamageClass c : DamageClass.values())
			assertTrue(AwmTags.chanceAt(c, 120) <= 1);
	}

	@Test
	void everyKindOfBlockHasAPlace() {
		// Wood: whole blocks only in extreme wind, posts and fences before them.
		assertEquals(DamageClass.STRUCTURE, WindClasses.byProperties(Material.WOOD, 2, true, true, false, false));
		assertEquals(DamageClass.STURDY, WindClasses.byProperties(Material.WOOD, 2, true, false, false, false));
		// Torches and wooden buttons: nothing to bump into.
		assertEquals(DamageClass.LOOSE, WindClasses.byProperties(Material.WOOD, 0, false, false, false, false));
		// Plants.
		assertEquals(DamageClass.FOLIAGE, WindClasses.byProperties(Material.PLANT, 0, false, false, false, false));
		// Glass panes, not glowstone or lamps.
		assertEquals(DamageClass.FRAGILE, WindClasses.byProperties(Material.GLASS, 0.3f, true, false, false, false));
		assertEquals(DamageClass.OTHER, WindClasses.byProperties(Material.GLASS, 0.3f, true, true, false, false));
		// Stone, concrete, metal, obsidian: never.
		assertEquals(DamageClass.OTHER, WindClasses.byProperties(Material.HARD, 1.8f, true, true, false, false));
		assertEquals(DamageClass.OTHER, WindClasses.byProperties(Material.HARD, 50, true, true, false, false));
		// Rails and redstone stay; chests are not smashed open; bedrock never.
		assertEquals(DamageClass.OTHER, WindClasses.byProperties(Material.OTHER, 0, false, false, false, false));
		assertEquals(DamageClass.OTHER, WindClasses.byProperties(Material.WOOD, 2.5f, true, false, true, false));
		assertEquals(DamageClass.OTHER, WindClasses.byProperties(Material.HARD, -1, true, true, false, false));
	}
}
