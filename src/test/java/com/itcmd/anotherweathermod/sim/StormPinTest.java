package com.itcmd.anotherweathermod.sim;

import com.itcmd.anotherweathermod.api.StormKind;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /awm pin} (2026-11-18): a pinned storm stays put and lives on; let go, it moves on from where it was. */
class StormPinTest {

	private final SteeringWind wind = new SteeringWind(1234);

	private StormCell hurricane() {
		return StormCell.matureAt(StormKind.HURRICANE, 100, 200, 10_000, 4 * 24000, 900, 1f, 42, wind).withCategory(0);
	}

	@Test
	void aPinnedStormStaysPut() {
		long now = 20_000;
		StormCell storm = hurricane();
		double x = storm.centerX(now), z = storm.centerZ(now);
		StormCell pinned = storm.pinnedAt(now);
		assertTrue(pinned.pinned());
		for(long t = now; t < now + 24000; t += 1200) {
			assertEquals(x, pinned.centerX(t), 1e-6, "moved by " + t);
			assertEquals(z, pinned.centerZ(t), 1e-6);
		}
		assertEquals(0, pinned.velocityX(now), 1e-12);
		assertEquals(storm.category(), pinned.category());
		assertEquals(storm.deathTick(), pinned.deathTick(), "it lives no longer or shorter");
		assertEquals(storm.intensityAt(now + 5000), pinned.intensityAt(now + 5000), 1e-6, "it strengthens and weakens as before");
	}

	@Test
	void letGoItMovesOnFromWhereItWas() {
		long now = 20_000, later = now + 6000;
		StormCell pinned = hurricane().pinnedAt(now);
		StormCell free = pinned.unpinnedAt(later);
		assertFalse(free.pinned());
		assertEquals(pinned.centerX(later), free.centerX(later), 1e-6, "it jumped when let go");
		assertEquals(pinned.centerZ(later), free.centerZ(later), 1e-6);
		assertTrue(Math.hypot(free.centerX(later + 24000) - free.centerX(later), free.centerZ(later + 24000) - free.centerZ(later)) > 1,
				"it did not move again");
	}

	@Test
	void pinnedSurvivesSavingAndCopies() {
		StormCell pinned = hurricane().pinnedAt(20_000);
		CompoundTag tag = pinned.save();
		StormCell loaded = StormCell.load(tag, wind);
		assertNotNull(loaded);
		assertTrue(loaded.pinned());
		assertEquals(pinned.centerX(30_000), loaded.centerX(30_000), 1e-6);
		assertTrue(pinned.withCategory(2).pinned(), "a copy let go");
		assertFalse(hurricane().save().contains("pinned"), "an unpinned storm saves a pin");
	}
}
