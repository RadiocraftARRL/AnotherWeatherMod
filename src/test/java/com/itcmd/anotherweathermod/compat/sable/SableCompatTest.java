package com.itcmd.anotherweathermod.compat.sable;

import com.itcmd.anotherweathermod.world.Tornadoes;
import com.itcmd.anotherweathermod.world.WindPush;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How hard storms push and lift Sable ships. */
class SableCompatTest {

	@Test
	void notLoadedInUnitTests() {
		assertFalse(SableCompat.isLoaded());
	}

	@Test
	void calmAndBreezyWindLeavesShipsAlone() {
		assertEquals(0, SableCompat.shipAcceleration(0, 1));
		assertEquals(0, SableCompat.shipAcceleration(WindPush.FROM, 1));
	}

	@Test
	void pushGrowsWithWindAndIsCappedAtFullStrength() {
		double gale = SableCompat.shipAcceleration(35, 1), hurricane = SableCompat.shipAcceleration(60, 1);
		assertTrue(gale > 0 && hurricane > gale);
		// At full strength a ship gets what a mob gets, per second squared: 0.05 blocks/tick/tick is 20 m/s/s.
		assertEquals(WindPush.MAX * 400, SableCompat.shipAcceleration(WindPush.FULL, 1), 1e-9);
		assertEquals(SableCompat.shipAcceleration(WindPush.FULL, 1), SableCompat.shipAcceleration(WindPush.FULL + 40, 1), 1e-9);
	}

	@Test
	void shareScalesAndZeroTurnsItOff() {
		assertEquals(SableCompat.shipAcceleration(50, 1) / 4, SableCompat.shipAcceleration(50, 0.25), 1e-12);
		assertEquals(0, SableCompat.shipAcceleration(50, 0));
		assertEquals(0, SableCompat.shipAcceleration(50, -1));
		assertEquals(0, SableCompat.tornadoLift(1, 0));
	}

	@Test
	void tornadoLiftIsStrongestAtTheCore() {
		assertEquals(Tornadoes.LIFT * 400 * 0.25, SableCompat.tornadoLift(1, 0.25), 1e-12);
		assertTrue(SableCompat.tornadoLift(0.3, 1) < SableCompat.tornadoLift(1, 1));
		assertEquals(0, SableCompat.tornadoLift(0, 1));
		assertEquals(0, SableCompat.tornadoLift(-0.5, 1));
	}
}
