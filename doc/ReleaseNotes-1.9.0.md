# Another Weather Mod 1.9.0

The first published release. Minecraft 1.21.1, NeoForge, MIT licensed. Its version number tracks RadioCraft's,
which reads this mod for its weather reports and WEFAX charts, but it stands on its own and needs nothing else.
Works server-only: vanilla clients still see the local rain and thunder.

## Weather that moves

- Storm cells drift across the world on a prevailing wind, so it rains where a storm is and nowhere else. The wind
  shifts sharply about every four in-game days, like a front passing, and storms born round a shift are more often
  severe.
- Storms are decided ahead of time, so forecasts and radar are exact: `/awm forecast` tells anyone the weather where
  they stand and the next storms due there.
- The ground decides what falls: rain, snow, hail under strong supercells, dust and sandstorms over deserts,
  blizzards over snow.
- Default frequencies: rain about 30% of the time, severe thunderstorms 8%, hail or damaging wind 5%, hurricanes 1%.

## Storms

- A scale of 0 to 5: rainstorm, thunderstorm, strong thunderstorm, severe thunderstorm (strips some leaves, blows
  wooden doors open), hailstorm, and a supercell that puts down a tornado.
- Tornadoes rated EF0 to EF5: solid, swirling funnels that lower from the cloud with a roar, wider and fiercer up
  the scale, tearing up what they cross and lifting what they catch. EF4 and EF5 level all but stone, earth and water.
- Hurricanes form over the ocean and rotate round a calm eye, with categories 0 (a tropical storm) to 5 (near-constant
  lightning, trees torn down), hail bursts, broken glass, stripped leaves and loose blocks knocked down. Optionally
  they weaken over land.
- Gust fronts blow before the rain arrives; strong wind pushes players, mobs, items and Sable ships about (gold
  armor weighs you down). Hail beats crops back; stripped leaves and scoured grass regrow in a few days.
- Dense fog banks, heat waves (overheat in the sun, dry crops stall), cold snaps and blizzards that freeze you,
  waterspouts over open water, dust devils over hot dry ground, morning mist, dripping leaves, puddles and the
  occasional rainbow as rain clears.

## Looks and sounds

- The mod's own 3D clouds in four detail levels, with a storm deck that builds overhead as a storm moves in and
  clears behind it, rain walls and dark decks seen from outside a storm, lightning inside the clouds and distant
  thunder.
- Sideways storm rain by severity, hail that patters and lingers with a sound per material, white gust wisps, wind
  streaks and blown debris, a gusty wind sound, sandstorm and blizzard fog, and the sky darkening toward night under
  a severe storm.
- Per-player graphics config (particle quality and a switch for each effect) and a shader compatibility section for
  Iris and Oculus packs: keep or drop the mod's clouds and storm rain, thicken the rain, draw fog in layers, report
  severe storms as thunder, and brightness sliders. Sodium's clouds fade under severe storms.

## Blocks

- Weather instruments, each with a reading on use and a redstone signal: Wind Vane, Barometer, Thermometer, Rain
  Gauge and Solar Detector.
- The Tornado Acoustic Sensor hears a tornado coming, and the Tornado Siren runs on redstone, winding up and down
  with the signal. All have recipes.
- The creative-only Storm Protector keeps the ground round it (8 to 256 blocks) safe from wind and hail damage, for
  spawns and builds.

## For servers and other mods

- Server config per world: dimensions, storm frequency and mix, forecast horizon, hurricane interval and weakening,
  lightning, hail and wind damage, doors blowing open, the Sable push. Game rules `doWindDamage` and
  `allowCustomClouds`.
- Operator commands: `/awm status | here | list | spawn | kill | clear | approach | hazard | calm | windburst | regrow`.
  Vanilla `/weather` still works as a world-wide override.
- Visual mods that read the vanilla rain level (Particle Rain, Immersive Storms, Dynamic Surroundings, AmbientSounds)
  follow the storms with no setup.
- An open API: weather at a position, rain and thunder levels cheap enough for radar pictures, storms, forecasts and
  events (`StormEvent`, `HazardEvent`, cancelable `WindDamageEvent`). With Sable installed, the wind reaches sails
  and propellers.

## Fixes before release

- Clouds that never reread their cells after first loading, so no deck built overhead as a storm moved in.
- A client crash in strong wind (particle atlas), storm rain scrolling upward, and the mod's clouds drawn behind
  Sodium's.
- Fake and GameTest players are skipped, so mods that run GameTests alongside this one no longer crash.
