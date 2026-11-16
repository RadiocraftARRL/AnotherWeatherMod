package com.itcmd.anotherweathermod.api;

import java.util.List;

/**
 * An advisory about a tropical storm or hurricane for one place (2026-11-15): sent while the place lies within the
 * storm's forecast cone (its track, plus or minus twice the storm's width) up to two days ahead. A disturbance may not
 * develop, and a storm may pass by inside the cone without hitting: some advisories are false alarms, as real ones are.
 *
 * @param id            the storm, the same for all its advisories (its seed)
 * @param stage         what is said: a disturbance forming, a watch, a warning
 * @param hurricane     whether it is (or is forecast to become) a hurricane; false for a tropical storm. Not known for
 *                      a disturbance (false then): its {@code chancePercent} says how likely it is
 * @param category      its forecast peak category, 1 to 5, 0 for a tropical storm; -1 while a disturbance
 * @param chancePercent the chance a disturbance develops into a hurricane, percent; 100 or 0 once it has formed
 * @param formsAt       the game time it forms (or formed)
 * @param arrivalTick   when it is forecast to reach the place (its cone, that is), game time; now if it already has
 * @param coneRadius    the cone's half width round the track, blocks
 * @param track         where it is forecast to be, from now (or from where it forms) every {@link #TRACK_STEP} ticks
 */
public record TropicalAdvisory(long id, Stage stage, boolean hurricane, int category, int chancePercent, long formsAt,
		long arrivalTick, double coneRadius, List<TrackPoint> track) {

	/** Ticks between the points of a track (an eighth of a day). */
	public static final long TRACK_STEP = 3000;

	/** What an advisory says. */
	public enum Stage {
		/** A tropical disturbance over the sea, not yet a storm, that may develop and come this way. */
		DISTURBANCE,
		/** A tropical storm or hurricane that has formed and may reach the place within a day or two. */
		OUTLOOK,
		/** It may reach the place within a day: a tropical storm or hurricane watch. */
		WATCH,
		/** It may reach the place within half a day, or is here: a tropical storm or hurricane warning. */
		WARNING
	}

	/** A point of a forecast track: the time, the centre and the storm's radius there. */
	public record TrackPoint(long tick, double x, double z, double radius) {
	}
}
