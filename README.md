# Another Weather Mod

Localized, moving, forecastable weather for Minecraft (NeoForge 1.21.1). MIT licensed.

- Storm cells drift across the world on a prevailing wind. It rains where a storm is and nowhere else.
- The ground decides what falls: rain, snow, hail under strong supercells, dust and sandstorms over deserts,
  blizzards over snow.
- Storms run up a scale of 0 to 5: rainstorm, thunderstorm, strong thunderstorm, severe thunderstorm (strips some
  leaves, blows doors open), hailstorm, and a supercell that puts down a tornado (EF0 to EF5: wider and fiercer
  up the scale) that tears up what it crosses and lifts what it catches.
- Hurricanes form over the ocean, rotate around a calm eye and break glass, strip leaves and knock loose blocks
  down. Each has a category, 0 (a tropical storm) to 5 (near-constant lightning, trees torn down), and they throw
  down hail now and then.
- Strong wind pushes players, mobs and items about (gold armor weighs you down). Dense fog banks roll in; heat waves
  bake deserts by day (stay out in the sun and you overheat; dry crops stop growing) and cold snaps and blizzards
  freeze you.
- Mist in the early morning and after rain, with dripping leaves and puddles; now and then a rainbow as rain clears
  with the sun low. Waterspouts hang from thunderstorms over open water; dust devils wander over hot, dry ground.
- A creative-only **Storm Protector** block (in the Operator Utilities tab) keeps the ground round it, 8 to 256
  blocks, safe from wind and hail damage, for spawns and builds.
- Storms are decided ahead of time, so forecasts and radar are exact.
- Works on the server alone: vanilla clients see the local rain and thunder. Visual mods that read the vanilla rain
  level (Particle Rain, Immersive Storms, Dynamic Surroundings, AmbientSounds) follow the storms with no extra setup.
- An open API (`com.itcmd.anotherweathermod.api`) for other mods: weather at a position, storms, forecasts, events.

See [doc/Design.md](doc/Design.md) for how it works and what is planned.

## Commands

`/awm forecast` is for everyone: the weather where you stand and the next few storms due there.

`/awm status | here | list | spawn <kind> [intensity] [radius] [minutes] | kill <id> | clear`
(permission level 2). Kinds: `shower`, `thunderstorm`, `strong_thunderstorm`, `severe_thunderstorm`,
`supercell`, `tornado` (or `tornado_0` to `tornado_5` for an EF rating), `hurricane` (or `hurricane_0` to
`hurricane_5`), `dense_fog`, `heat_wave`, `cold_snap`. For testing: `approach <kind> [minutes]`, `hazard severe|hail|blizzard|sandstorm|tornado[_N]|hurricane[_N]|eye|fog|heat|cold`, `hazard end` or `calm` (removes the storms over you),
`windburst <speed> [radius]`, `regrow`.

`/weather rain|thunder [duration]` still works, as a world-wide override for its duration.

## Config

`serverconfig/anotherweathermod-server.toml` in each world: dimensions, storm frequency and mix, forecast horizon,
hurricane interval, hurricanes weakening over land (off by default), lightning, hail and wind damage, doors blowing
open. The `doWindDamage` game rule turns wind damage off, and `allowCustomClouds` lets a server keep everyone on vanilla's
clouds.

`config/anotherweathermod-client.toml` (also the config screen in the mod list) is each player's graphics: particle
quality (low, medium, high, ultra) and switches for storm clouds, rain walls, hail, wind streaks, blown leaves, the
wind sound, sandstorm and blizzard fog, sideways storm rain and puddles; the mod's 3D clouds and their detail (low is
plain low-poly; vanilla's Clouds setting applies on top, and with them off vanilla's clouds fade out under severe
storms). A **shader compatibility** section helps with Iris/Oculus shader packs: keep or drop the mod's clouds and storm
rain while a pack is on (and thicken the rain, which packs often draw faintly), draw the weather's fog as layers
round you (packs often ignore it), darken the sky under severe storms toward night, report severe storms as thunder (packs
darken and wet the world for it), and brightness sliders for clouds, rain, hail, wind particles and tornadoes.

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
