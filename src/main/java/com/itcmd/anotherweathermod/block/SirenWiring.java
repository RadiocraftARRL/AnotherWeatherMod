package com.itcmd.anotherweathermod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which sirens a siren controller reaches over data wire. Pure: it reads the world only through a {@link Grid}, so
 * it is unit-tested without the game ({@code SirenWiringTest}); {@code world.SirenWires} runs it in a level.
 * <p>
 * The walk starts from the six blocks round the controller (a siren touching it counts, as does wire touching it)
 * and follows the wire through its six neighbours, and like RadioCraft's data wire and redstone dust, diagonally up
 * or down one block where the wire climbs a wall or steps down a ledge: a diagonal link counts when neither block at
 * its corner holds wire (otherwise the wire goes round through that block anyway). Wire of two kinds joins only if
 * they are the same block (one colour of RadioCraft's dyed data wire stays apart from another) or one of them joins
 * everything (AWM's own data wire, RadioCraft's passthrough). Sirens are found on the orthogonal sides of the wire
 * only. The walk never reads past a block that is not loaded; it marks the answer {@link Result#incomplete()} so the
 * controller tries again when chunks load.
 */
public final class SirenWiring {

	/** What a block is to the walk. */
	public sealed interface Cell permits Wire, Siren, Nothing {}

	/**
	 * Wire. {@code kind} tells kinds apart (the block); {@code joinsAll} for wire that joins every kind.
	 */
	public record Wire(Object kind, boolean joinsAll) implements Cell {
		boolean joins(Wire other) {
			return joinsAll || other.joinsAll || kind.equals(other.kind);
		}
	}

	/** A siren, any part of it: {@code rotor} is where its rotor (block entity) is. */
	public record Siren(BlockPos rotor) implements Cell {}

	/** Neither wire nor siren; {@link #UNLOADED} for a block the walk may not read. */
	public enum Nothing implements Cell { NOTHING, UNLOADED }

	/** The world as the walk sees it. */
	@FunctionalInterface
	public interface Grid {
		Cell at(BlockPos pos);
	}

	/**
	 * What a walk found: the rotors of the sirens reached, nearest along the wire first; how many wire blocks it
	 * followed; and whether it stopped short (an unloaded block, or more than the limit of wire).
	 */
	public record Result(List<BlockPos> sirens, int wires, boolean incomplete) {}

	private SirenWiring() {
	}

	/** Walks the wire out from a controller at {@code start}, following at most {@code limit} wire blocks. */
	public static Result flood(Grid grid, BlockPos start, int limit) {
		Set<BlockPos> sirens = new LinkedHashSet<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		boolean incomplete = false;
		seen.add(start);
		for(Direction direction : Direction.values()) {
			BlockPos next = start.relative(direction);
			Cell cell = grid.at(next);
			if(cell instanceof Siren siren)
				sirens.add(siren.rotor());
			else if(cell instanceof Wire && seen.add(next))
				queue.add(next);
			else if(cell == Nothing.UNLOADED)
				incomplete = true;
		}
		int wires = 0;
		while(!queue.isEmpty()) {
			if(wires >= limit) {
				incomplete = true;
				break;
			}
			BlockPos pos = queue.poll();
			if(!(grid.at(pos) instanceof Wire wire))
				continue;
			wires++;
			for(Direction direction : Direction.values()) {
				BlockPos next = pos.relative(direction);
				Cell cell = grid.at(next);
				if(cell instanceof Siren siren)
					sirens.add(siren.rotor());
				else if(cell instanceof Wire other && wire.joins(other) && seen.add(next))
					queue.add(next);
				else if(cell == Nothing.UNLOADED)
					incomplete = true;
			}
			for(Direction direction : Direction.Plane.HORIZONTAL)
				for(Direction vertical : new Direction[] {Direction.UP, Direction.DOWN}) {
					BlockPos side = pos.relative(direction);
					BlockPos next = side.relative(vertical);
					Cell cell = grid.at(next);
					if(cell == Nothing.UNLOADED) {
						incomplete = true;
						continue;
					}
					if(!(cell instanceof Wire other) || !wire.joins(other) || seen.contains(next))
						continue;
					if(grid.at(side) instanceof Wire || grid.at(pos.relative(vertical)) instanceof Wire)
						continue;
					seen.add(next);
					queue.add(next);
				}
		}
		return new Result(List.copyOf(sirens), wires, incomplete);
	}
}
