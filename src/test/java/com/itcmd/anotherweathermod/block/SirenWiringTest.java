package com.itcmd.anotherweathermod.block;

import com.itcmd.anotherweathermod.block.SirenWiring.Cell;
import com.itcmd.anotherweathermod.block.SirenWiring.Nothing;
import com.itcmd.anotherweathermod.block.SirenWiring.Result;
import com.itcmd.anotherweathermod.block.SirenWiring.Siren;
import com.itcmd.anotherweathermod.block.SirenWiring.Wire;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SirenWiringTest {

	private static final BlockPos CONTROLLER = BlockPos.ZERO;
	private static final Wire PLAIN = new Wire("plain", false);
	private static final Wire RED = new Wire("red", false);
	private static final Wire AWM = new Wire("awm", true);

	/** A little world: what is not set is nothing, and blocks in {@code unloaded} cannot be read. */
	private static final class World implements SirenWiring.Grid {
		final Map<BlockPos, Cell> cells = new HashMap<>();
		Set<BlockPos> unloaded = Set.of();

		World wire(Wire kind, int x, int y, int z) {
			cells.put(new BlockPos(x, y, z), kind);
			return this;
		}

		World line(Wire kind, int fromX, int toX) {
			for(int x = fromX; x <= toX; x++)
				wire(kind, x, 0, 0);
			return this;
		}

		/** A two-high siren standing at {@code x, y, z}. */
		World siren(int x, int y, int z) {
			BlockPos rotor = new BlockPos(x, y, z);
			cells.put(rotor, new Siren(rotor));
			cells.put(rotor.above(), new Siren(rotor));
			return this;
		}

		@Override
		public Cell at(BlockPos pos) {
			if(unloaded.contains(pos))
				return Nothing.UNLOADED;
			return cells.getOrDefault(pos, Nothing.NOTHING);
		}

		Result flood() {
			return SirenWiring.flood(this, CONTROLLER, 4096);
		}
	}

	@Test
	void findsSirensAlongTheWire() {
		Result result = new World().line(PLAIN, 1, 10).siren(11, 0, 0).siren(5, 0, 1).flood();
		assertEquals(List.of(new BlockPos(5, 0, 1), new BlockPos(11, 0, 0)), result.sirens(), "nearest first");
		assertEquals(10, result.wires());
		assertFalse(result.incomplete());
	}

	@Test
	void aSirenTouchingTheControllerCounts() {
		assertEquals(List.of(new BlockPos(-1, 0, 0)), new World().siren(-1, 0, 0).flood().sirens());
		assertEquals(List.of(new BlockPos(0, 1, 0)), new World().siren(0, 1, 0).flood().sirens(), "standing on it");
		assertTrue(new World().siren(1, 0, 1).flood().sirens().isEmpty(), "but not diagonally");
	}

	@Test
	void wireReachingTheUpperHalfFindsTheRotor() {
		Result result = new World().line(PLAIN, 1, 3).wire(PLAIN, 3, 1, 0).wire(PLAIN, 4, 1, 0).siren(5, 0, 0)
				.flood();
		assertEquals(List.of(new BlockPos(5, 0, 0)), result.sirens(), "the siren once, by its rotor");
	}

	@Test
	void aGapStopsTheWire() {
		Result result = new World().line(PLAIN, 1, 3).line(PLAIN, 5, 8).siren(9, 0, 0).flood();
		assertTrue(result.sirens().isEmpty());
		assertEquals(3, result.wires());
	}

	@Test
	void coloursStayApartButAwmWireJoinsAll() {
		assertTrue(new World().line(PLAIN, 1, 3).line(RED, 4, 6).siren(7, 0, 0).flood().sirens().isEmpty(),
				"plain and red do not join");
		assertEquals(1, new World().line(PLAIN, 1, 3).wire(AWM, 4, 0, 0).line(RED, 5, 6).siren(7, 0, 0).flood()
				.sirens().size(), "AWM's wire joins both");
		assertEquals(1, new World().line(RED, 1, 6).siren(7, 0, 0).flood().sirens().size(),
				"the controller takes any colour");
	}

	@Test
	void climbsAWallAndStepsDownALedge() {
		// Up a wall at x = 2 (stone there is just nothing to the walk) to wire on top, then down the far side.
		World world = new World().wire(PLAIN, 1, 0, 0).wire(PLAIN, 2, 1, 0).wire(PLAIN, 3, 0, 0).siren(4, 0, 0);
		assertEquals(List.of(new BlockPos(4, 0, 0)), world.flood().sirens());
	}

	@Test
	void noDiagonalWhereWireAlreadyTurnsTheCorner() {
		// Wire at (1,0,0), (1,1,0) and (2,1,0): the walk goes up and across; (2,1,0) is not reached twice.
		World world = new World().wire(PLAIN, 1, 0, 0).wire(PLAIN, 1, 1, 0).wire(PLAIN, 2, 1, 0).siren(3, 1, 0);
		Result result = world.flood();
		assertEquals(3, result.wires());
		assertEquals(List.of(new BlockPos(3, 1, 0)), result.sirens());
	}

	@Test
	void unloadedBlocksStopTheWalkAndSaySo() {
		World world = new World().line(PLAIN, 1, 10).siren(11, 0, 0);
		world.unloaded = Set.of(new BlockPos(6, 0, 0));
		Result result = world.flood();
		assertTrue(result.sirens().isEmpty());
		assertTrue(result.incomplete());
	}

	@Test
	void theLimitStopsAHugeWeb() {
		World world = new World().line(PLAIN, 1, 100).siren(101, 0, 0);
		Result result = SirenWiring.flood(world, CONTROLLER, 50);
		assertEquals(50, result.wires());
		assertTrue(result.incomplete());
		assertTrue(result.sirens().isEmpty());
	}

	@Test
	void loopsEndAndEachSirenComesOnce() {
		World world = new World();
		for(int x = 1; x <= 4; x++) {
			world.wire(PLAIN, x, 0, 0);
			world.wire(PLAIN, x, 0, 3);
		}
		for(int z = 1; z <= 2; z++) {
			world.wire(PLAIN, 1, 0, z);
			world.wire(PLAIN, 4, 0, z);
		}
		world.siren(5, 0, 0).siren(5, 0, 3).siren(2, 0, 1);
		Result result = world.flood();
		assertEquals(3, result.sirens().size());
		assertEquals(12, result.wires());
	}
}
