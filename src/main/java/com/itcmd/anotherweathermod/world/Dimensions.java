package com.itcmd.anotherweathermod.world;

import java.util.List;

/**
 * Which dimensions get localized weather. Weather needs a sky: a dimension with no sky light, or with a ceiling, never
 * gets it (the Nether, the End). Beyond that, the ones named in the {@code dimensions} setting do, and so (with
 * {@code overworldLikeDimensions} on) does any other that looks like an overworld (sky light, no ceiling, not
 * ultrawarm), such as a modded overworld-style world, unless it is named in {@code excludedDimensions}.
 */
public final class Dimensions {

	private Dimensions() {
	}

	/**
	 * @param id        the dimension's id, as {@code minecraft:overworld}
	 * @param skyLight  its dimension type has sky light
	 * @param ceiling   its dimension type has a ceiling
	 * @param ultraWarm its dimension type is ultrawarm (water boils), like the Nether
	 */
	public static boolean managed(String id, boolean skyLight, boolean ceiling, boolean ultraWarm, boolean overworldLike,
			List<? extends String> listed, List<? extends String> excluded) {
		if(!skyLight || ceiling || excluded.contains(id))
			return false;
		return listed.contains(id) || overworldLike && !ultraWarm;
	}
}
