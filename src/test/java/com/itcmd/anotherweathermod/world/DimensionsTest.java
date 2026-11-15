package com.itcmd.anotherweathermod.world;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionsTest {

	private static final List<String> LISTED = List.of("minecraft:overworld");

	private static boolean managed(String id, boolean sky, boolean ceiling, boolean warm, boolean auto, List<String> excluded) {
		return Dimensions.managed(id, sky, ceiling, warm, auto, LISTED, excluded);
	}

	@Test
	void overworldAndOverworldLikeButNeverNetherOrEnd() {
		assertTrue(managed("minecraft:overworld", true, false, false, true, List.of()));
		assertFalse(managed("minecraft:the_nether", false, true, true, true, List.of()));
		assertFalse(managed("minecraft:the_end", false, false, false, true, List.of()));
		assertTrue(managed("somemod:frontier", true, false, false, true, List.of()), "a modded overworld");
		assertFalse(managed("somemod:frontier", true, false, false, false, List.of()), "only listed ones with the rule off");
		assertFalse(managed("somemod:frontier", true, false, false, true, List.of("somemod:frontier")), "excluded");
		assertFalse(managed("somemod:caves", true, true, false, true, List.of()), "a ceiling");
		assertFalse(managed("somemod:inferno", true, false, true, true, List.of()), "ultrawarm");
		// Even listed, a dimension needs a sky.
		assertFalse(Dimensions.managed("minecraft:the_nether", false, true, true, false, List.of("minecraft:the_nether"),
				List.of()));
	}
}
