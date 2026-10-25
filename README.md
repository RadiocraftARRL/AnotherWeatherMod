# Another Weather Mod

Localized, moving, forecastable weather for Minecraft (NeoForge 1.21.1). MIT licensed.

- Storm cells drift across the world on a prevailing wind. It rains where a storm is and nowhere else.
- The ground decides what falls: rain, snow, hail under strong supercells, dust and sandstorms over deserts,
  blizzards over snow.
- Hurricanes form over the ocean, rotate around a calm eye and break glass, strip leaves and knock loose blocks down.
- Storms are decided ahead of time, so forecasts and radar are exact.
- Works on the server alone: vanilla clients see the local rain and thunder. Visual mods that read the vanilla rain
  level (Particle Rain, Immersive Storms, Dynamic Surroundings, AmbientSounds) follow the storms with no extra setup.
- An open API (`com.itcmd.anotherweathermod.api`) for other mods: weather at a position, storms, forecasts, events.

See [doc/Design.md](doc/Design.md) for how it works and what is planned.

## Commands

`/awm status | here | list | forecast | spawn <kind> [intensity] [radius] [minutes] | kill <id> | clear`
(permission level 2). Kinds: `shower`, `thunderstorm`, `supercell`, `hurricane`.

`/weather rain|thunder [duration]` still works, as a world-wide override for its duration.

## Config

`serverconfig/anotherweathermod-server.toml` in each world: dimensions, storm frequency and mix, forecast horizon,
hurricane interval, lightning, hail and wind damage. The `doWindDamage` game rule turns wind damage off.

## Using the API

```java
AnotherWeatherApi api = AnotherWeatherApi.get();
if(api.isManaged(level)) {
    WeatherSample here = api.sample(level, pos);
    Optional<StormArrival> next = api.nextArrival(level, pos, api.forecastHorizonTicks(level));
}
```

Events on `NeoForge.EVENT_BUS`: `StormEvent.Forecast / Formed / StageChanged / Dissipated`, `HazardEvent.Entered /
Left`, and the cancelable `WindDamageEvent`.

## Building

- `./gradlew build` builds the jar into `build/libs/`.
- `./gradlew test` runs the unit tests (the storm model, no game).
- `./gradlew runGameTestServer` runs the in-world GameTests.
- `./gradlew runClient` / `runServer` start a dev game in `runs/`.
