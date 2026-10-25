package com.itcmd.anotherweathermod.api;

/**
 * When a storm reaches a position.
 *
 * @param storm         the storm
 * @param arrivalTick   the first tick it is over the position
 * @param departureTick the last tick it is over the position (or the end of the forecast horizon)
 * @param peak          the worst weather it brings there
 * @param peakTick      when the worst arrives
 */
public record StormArrival(StormView storm, long arrivalTick, long departureTick, WeatherSample peak, long peakTick) {
}
