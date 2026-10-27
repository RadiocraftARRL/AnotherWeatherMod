# Another Weather Mod: design

Status: first draft, 2026-10-24. Phase 1 and most of phases 2 and 3 are in (see Phases). Companion mod to RadioCraft, but it stands alone and anyone can build on it.
License: MIT. Target: NeoForge 1.21.1 now, Minecraft 26.x later.

## Goals

1. **Localized weather with a predictable radar.** Storm cells move across the world. Each player gets the rain and
   thunder of the cell they stand in (the `/pweather` trick: the server sends each player their own rain and thunder
   level). Radar, forecasts and RadioCraft's WEFAX read the same cells, so a forecast is never wrong by more than the
   simulation itself.
2. **Heavier storms that depend on where they are.** Hail, blizzards and sandstorms are not separate systems: they are
   the same moving storm cell, and the ground under it decides what falls. A strong cell over a snowy biome is a
   blizzard, over a desert it is a sandstorm, over plains it can drop hail.
3. **Hurricanes.** Large, slow, ocean-born cells with an eye and rotating winds that break glass and leaves, scour
   grass, knock loose blocks down and damage antennas (through an event other mods listen to).
4. **Tornadoes** (later). The cell model leaves room for them (a supercell can spawn one), but they are not in the
   first versions.
5. **An open API.** Other mods ask what the weather is at a position, which storms exist, where they will be, and get
   events when storms form, strengthen, die or damage blocks.

Non-goals for now: seasons, temperature survival, other dimensions than the Overworld (data-driven later).

## The one idea that makes it cheap

Vanilla clients do not simulate weather. They draw whatever rain and thunder level the server last sent
(`ClientboundGameEventPacket.RAIN_LEVEL_CHANGE` / `THUNDER_LEVEL_CHANGE`, and `ClientLevel.setRainLevel` keeps no
smoothing of its own). So:

- The server turns off the vanilla global weather cycle in managed dimensions.
- Every tick, for each player, it works out the rain and thunder level at that player's position, eases toward it
  (0.01 per tick like vanilla, faster when the cell is strong) and sends the packets only when the value changes.
- **Players without the mod installed see localized weather too.** The mod works server-only for goal 1.
- Every visual and sound mod checked (Particle Rain, Immersive Storms, Dynamic Surroundings, AmbientSounds, Windy,
  Puddles and Floods) reads the client rain and thunder level, and the biome decides rain, snow or dust. They follow our
  storms with no compat code. Particle Rain already draws rain in deserts as a sandstorm and Immersive Storms draws
  thunder in snowy biomes as a whiteout.

The server side also has to agree with what the player sees:

- `Level.isRainingAt(pos)` (crops, fire, tridents, mobs, entity "in rain") asks the storm field, not the global flag.
- Lightning, snow layers, ice and cauldron filling come from a hook at the end of `ServerLevel.tickChunk`, which repeats
  vanilla's per-chunk rolls using the local storm instead of the global weather.
- `/weather` still works: it forces a world-wide "override" cell for its duration (so admins keep their tool).

## Storm model

Everything about a storm is a **pure function of its birth parameters and the game time**. Nothing reads the world to
move or grow a storm. That is what makes forecasts exact and the logic unit-testable.

A `StormCell` has:

| Field | Meaning |
|---|---|
| `id` | long, unique per level |
| `kind` | `SHOWER`, `THUNDERSTORM`, `SEVERE_THUNDERSTORM`, `SUPERCELL`, `HURRICANE` (and later `TORNADO` as a child of a supercell) |
| `birthTick`, `lifetimeTicks` | when it formed and how long it lives |
| `origin` (x, z) | where it formed |
| `drift` (vx, vz) | its own motion on top of the steering wind, in blocks per tick, fixed at birth |
| `peakRadius` | blocks |
| `peakIntensity` | 0..1 |
| `seed` | for anything random about this cell (lightning, gust phase) |

Derived, at any game time `t`:

- **Life stage and intensity.** `age = t - birthTick`. Forming (first 20 % of the life, intensity ramps up from 0),
  mature (to 70 %), decaying (to 100 %). Stages are reported as `FORMING`, `MATURE`, `DECAYING`, `DISSIPATED`.
- **Radius** grows from 40 % to 100 % of the peak while forming and stays.
- **Centre** = origin + integral of (steering wind + drift) from birth to `t`. The steering wind is itself a pure
  function of time (below), so the integral is computed in fixed steps and cached per cell.
- **Intensity at a point**: the cell's intensity times a smooth falloff: 1 inside 60 % of the radius, cosine down to 0 at
  the edge. Hurricanes have an eye: calm inside 8 % of the radius, a peak eyewall at 15 %, then fall off.

### Kinds

| Kind | Typical radius | Life | Rain | Thunder | Wind (m/s) | Notes |
|---|---|---|---|---|---|---|
| Shower | 96–192 | 8–20 min | 0.4–0.8 | none | 4–8 | the common one |
| Thunderstorm | 128–256 | 10–25 min | 0.7–1.0 | yes | 8–18 (gust front) | lightning |
| Supercell | 160–320 | 15–30 min | 1.0 | yes | 15–30 | hail over rain biomes; future tornado parent |
| Hurricane | 600–1200 | 2–4 Minecraft days | 1.0 | eyewall | 33–70 at the eyewall | forms only over ocean, slow, rare |

Times are real minutes at 20 tps. All of these are config values, and the table is a starting point to tune by play.

### What falls: ground decides

The precipitation at a point is decided by the biome there (vanilla's `Biome.getPrecipitationAt`):

| Ground | Light | Strong (intensity > 0.6) | Very strong and windy |
|---|---|---|---|
| Rain biome | rain | heavy rain + thunder | **hail** (supercell) |
| Snow biome | snow | heavy snow | **blizzard** (wind > 15 m/s, low visibility, faster snow layers) |
| No precipitation (desert, badlands, savanna) | nothing | dust | **sandstorm** (wind > 12 m/s) |

The API reports this as a `Precipitation` value (`NONE`, `RAIN`, `SNOW`, `HAIL`, `DUST`) and a set of `Hazard` flags
(`LIGHTNING`, `HAIL`, `BLIZZARD`, `SANDSTORM`, `DAMAGING_WIND`, `HURRICANE_EYE`).

Vanilla clients have no hail or dust: they see rain (hail) or nothing (dust in deserts, since vanilla shows no rain
there). The client half of the mod, or Particle Rain, draws them properly.

### Wind

- **Steering (prevailing) wind**: direction and speed are smooth noise of game time, seeded from the world seed. It
  turns slowly (over a few in-game days) and blows 3–10 m/s. Being a function of time only, any future value is known.
  In general it blows from the west (westerlies), drifting up to 90 degrees either way.
  About once every four in-game days it shifts sharply, as when a front passes: 60 to 150 degrees over two in-game
  hours, then holds (shifts take turns veering and backing). Storms forming from an in-game hour and a half before a
  shift to three after are half again as likely to be severe, supercells or tornadic. `/awm status` shows the next one.
- **Storm wind**: each cell adds wind around it. Showers and thunderstorms push outward from the centre (outflow, a
  gust front at the leading edge). Hurricanes rotate (counter-clockwise, looking down, like the northern hemisphere)
  with a speed profile that peaks at the eyewall.
- `wind(level, pos)` returns a vector in m/s. Speed is also reported on the Beaufort scale for text reports.
- One block per tick = 20 m/s. Storm motion uses the steering wind at 60 % (cells move slower than the wind aloft
  would suggest, which keeps them on screen long enough to matter).

### Temperature, pressure, humidity (for reports)

These do not drive anything yet; they exist so weather stations and APRS WX packets have numbers to send.

- Temperature: biome base temperature mapped to °C (vanilla 0.8 ≈ 20 °C), minus height above sea level (6.5 °C per
  km, a block is a metre), minus a night swing, minus up to 8 °C under a strong cell.
- Pressure: 1013 hPa, minus up to 15 hPa in thunderstorms, down to 950 hPa at a hurricane's centre, smoothly.
- Humidity: 40 % base in dry biomes, 70 % in wet ones, rising to 100 % in rain.

## Forming storms: the forecast queue

Storms form where they matter, near players, and **are decided ahead of time** so a forecast can see them coming.

- The level keeps a **pending queue** of storms that have been decided but have not formed yet, each with its exact
  birth parameters. The forecast horizon is 20 minutes (config).
- Every 30 s the generator tops the queue up to the horizon. For each new slot it rolls (from a seeded random) whether
  a storm forms, its kind, life, size and intensity, and places its origin **upwind** of a random online player, at
  a distance that means it reaches them partway through its life.
- Hurricanes are rolled separately and rarely (config: about one every 5 in-game days), only with an origin in an ocean
  biome (checked once, when the slot is decided, from the biome source: no chunk loads).
- When a pending storm's birth tick arrives it moves to the active list and fires `StormEvent.Formed`.
- Both lists are saved with the level (`SavedData`), so restarting does not change the forecast.
- A cap on active cells (config, 24) and a minimum spacing keep it cheap and keep the sky from being always grey.

Because the queue is real, "a thunderstorm is forecast to form 2 km west in 6 minutes" is a true statement, and a radar
that shows pending cells as outlines is honest.

## Server work per tick

All cheap; no chunk is loaded by the simulation.

1. Advance: move pending storms whose time has come to active, drop dissipated ones (fire events), fire stage-change
   events.
2. Players: compute local rain and thunder for each player in a managed dimension, ease, send packets on change.
3. Chunk hook (`ServerLevel.tickChunk` tail, mixin): only for chunks with a storm over them.
   - Lightning: vanilla's roll is 1 in 100 000 per chunk tick while thundering. Ours uses the local thunder level times
     a config multiplier, and goes through the vanilla lightning target search (rods, mobs).
   - Precipitation: vanilla's 1 in 48 roll per random tick, then snow layers, ice and `handlePrecipitation`
     (cauldrons), scaled by the local intensity. Blizzards may add a second snow layer roll.
   - Wind damage: when the local wind passes the damage threshold, a few surface blocks per chunk tick are checked
     against data-driven rules (below). Off by default for anything but hurricanes (config).
   - Hail: damages exposed living entities lightly (half a heart, rarely), and breaks crops and glass panes sometimes.

## Hurricane damage

Driven by data, so packs and other mods can change it. A block tag per damage class and a wind threshold each:

| Class | Default blocks | Threshold | What happens |
|---|---|---|---|
| `fragile` | glass panes, glass, flower pots, lanterns | 33 m/s | breaks (drops nothing for glass, as vanilla) |
| `foliage` | leaves, flowers, tall grass, crops | 25 m/s | removed, no drops for leaves (sapling chance kept) |
| `scour` | grass block, podzol, mycelium | 50 m/s | becomes coarse dirt (rare) |
| `loose` | fences, torches, signs, carpets, snow layers | 40 m/s | popped off as an item |

- Every candidate block fires a cancelable `WindDamageEvent` first (position, state, wind speed, damage class). Mods
  protect their blocks or react to them: RadioCraft damages antennas and drops wires this way.
- Blocks with no sky above them, or with a solid block upwind within 2 blocks, are sheltered.
- A gamerule `doWindDamage` (default true) and a config cap on blocks per chunk per second.

"Scoring" is read here as scouring the ground (grass torn off to coarse dirt). Tell me if it meant something else.

## Regrowth

Nature heals: leaves, flowers and grass the wind tore away grow back, and scoured ground grasses over, each after a
random one to three in-game days (config), if nothing else has changed the block meanwhile and no storm is overhead.
Crops (farmers replant), tall two-block plants, and built things (glass, fences, torches, signs) do not come back.
Up to 20 000 damaged blocks are remembered per level, saved with it.

## Hail and crops

Hail beats ripe crops back one or two growth stages and flattens young ones a third of the time, a few columns per
chunk per second under the hail.

## Client half (optional install)

Not needed for goal 1. With the mod on the client too:

- The server sends a light storm list (centre, radius, kind, intensity, every second) and the local `Precipitation` and
  hazards for the player.
- Hail: falling ice particles and hits sound, inside hail areas.
- Sandstorm and blizzard: fog density and colour by local wind and intensity (Immersive Storms, LGPL, is the reference).
- Hurricane sky: darker, faster clouds; the eye clears the sky.
- Rain walls at cell edges: a curtain of rain texture hanging from the cloud deck along the edge facing the camera.
  Storms near the player are sent every two seconds (centre, velocity, radius, intensity) and moved on client side.
- Dark storm clouds: two dark layers of the vanilla cloud texture under the clouds over each storm, fading at the edge.
- Wind: a rushing-air loop (the elytra sound until the mod has its own) that rises and falls with gusts, faint air
  streaks, blown sand in dust storms, and leaf debris in gales.
- Later: clouds that follow the cells (Better Clouds, MPL, and sebh's MIT cloud noise are the references).

## API

Package `com.itcmd.anotherweathermod.api`. The rest of the mod is not API and can change.

```java
AnotherWeatherApi api = AnotherWeatherApi.get();       // never null once the mod is loaded

api.isManaged(level)                                    // localized weather runs in this level
WeatherSample s = api.sample(level, pos);               // now
WeatherSample f = api.sampleAt(level, pos, gameTime);   // any future time within the horizon
Vec3 wind = api.wind(level, pos);                       // m/s
List<StormView> active = api.storms(level);
List<StormView> pending = api.pendingStorms(level);     // forecast queue
List<StormView> near = api.stormsNear(level, pos, radius);
Optional<StormArrival> next = api.nextArrival(level, pos, horizonTicks);
```

- `WeatherSample`: precipitation, rain level, thunder level, wind vector and Beaufort force, temperature °C, pressure
  hPa, humidity %, hazards, the strongest storm id at the point.
- `StormView`: id, kind, stage, birth tick, lifetime, and `centerAt(t)`, `radiusAt(t)`, `intensityAt(t)`, plus
  `velocityAt(t)`. Read-only.
- `StormArrival`: storm, arrival tick, departure tick, peak sample.
- Events on `NeoForge.EVENT_BUS`: `StormEvent.Formed`, `StormEvent.StageChanged`, `StormEvent.Dissipated`,
  `WindDamageEvent` (cancelable), `HazardEvent.Entered` / `.Left` for players (sirens, alerts).
- Server-side only; on a client without the server mod every call returns "not managed" values.
- API changes follow semver; the `api` package gets Javadoc.

## RadioCraft integration (done in RadioCraft, not here)

RadioCraft is GPLv3 and may depend on this MIT API (not the other way round). RadioCraft gets its own small API so any
weather mod can feed it:

- `WeatherSource` (`isAvailable`, `sample`, `storms`, `nextArrival`), with a vanilla source (global rain and thunder
  timers: "rain expected in about 4 minutes") and an Another Weather Mod source. Weather2 could get an adapter later.
- WEFAX: a `WefaxChartProvider` that paints a chart (radar, forecast track, surface wind) for a region; the built-in one
  uses `WeatherSource`.
- Weather station block: APRS WX packets (`_` positionless weather format: wind direction, speed, gust, temperature,
  rain), and an alert tone (1050 Hz attention signal) over SSB when a `HazardEvent` hits its area.
- WEFAX auto-print: a station sending a warning chart makes receiving WEFAX machines print it.
- Antenna damage: a `WindDamageEvent` listener on RadioCraft antenna blocks.

## Phases

Done so far (2026-10-24): all of phase 1; phase 2's hurricanes, damage rules and event, hail damage and blizzard snow;
phase 3's local weather payload, hail particles and sandstorm and blizzard fog. Not done: the storm list sync, the
hurricane sky, rain walls, and everything in phases 4 and 5. 19 unit tests, 5 GameTests.

1. **Core:** project, storm model, wind, forecast queue, save data, per-player rain and thunder,
   `isRainingAt`, lightning and precipitation in the chunk hook, `/awm` debug commands, API and events, unit tests.
2. **Hazards:** hurricanes with damage rules and the event, hail damage, blizzard snow. GameTests.
3. **Client half:** storm sync, hail particles, sandstorm and blizzard fog, hurricane sky.
4. **RadioCraft:** `WeatherSource`, WEFAX charts, weather station, APRS WX, alert tones, antenna damage.
5. **Later:** tornadoes from supercells, clouds that follow cells, data-driven storm kinds and dimensions, 26.x port.

## Borrowing from open mods

Only code under MIT is copied directly (keep its notice in `THIRD_PARTY.md`). MPL-2.0 files stay MPL in their own
files. LGPL code is a reference only unless kept as a separate component. GPL, ARR and PolyForm code is never copied.

| Use | Project | License |
|---|---|---|
| Storm cells, rain walls, hail, lightning in clouds, directional thunder | Localized Weather (github.com/rustybusgaming/LocalizedWeather) | MIT (LICENSE added 2026-10-26, "Copyright (c) 2026 fentbusgaming and contributors") |
| Storm systems, radar, tornado | Enhanced Weather (TalonFloof) | MIT |
| Localized weather plumbing | Atmosphere (PssbleTrngle) | code MIT, assets ARR |
| Rain, snow, sand particles | Particle Rain (PigCart) | MIT (sounds of unknown origin: not copied) |
| Snow accumulation | Snow! Real Magic (Snownee) | MIT |
| Clouds | Better Clouds (Qendolin) | MPL-2.0 |
| Cloud noise | sebh/TileableVolumeNoise | MIT |
| Fog, wind particles, overlays | Immersive Storms, Windy, Enhanced Visuals | LGPL (reference) |
| Sounds | freesound.org CC0, Dynamic Surroundings' CC0 entries | CC0 |

## Questions for the maintainer

1. ~~Is "scoring" scouring~~ (answered: yes). Scoured ground and stripped plants grow back (see Regrowth).
2. ~~Storm frequency~~ (answered 2026-10-24): a player standing still has rain about 30 % of the time, hail and the
   like about 5 %, a hurricane about 1 %. `WeatherFrequencyTest` holds the defaults to that.
3. ~~Hurricanes weakening over land~~ (answered 2026-10-25): a config switch, off by default. On, a hurricane loses
   about half its strength in half an in-game day over land and slowly wins it back at sea; still exact to forecast.
6. ~~Storm scale~~ (2026-10-25): rain-bearing storms run 0 to 5 (rainstorm, thunderstorm, strong, severe, hailstorm,
   tornado; `StormKind.scale()`); hurricanes have a category 0 to 5 per storm, tornadoes an EF rating 0 to 5, both
   saved with the storm. Dense fog, heat waves and cold snaps are storm cells that bring no rain. Defaults: rain
   about 31 % of the time, severe thunderstorm 7.6 %, hail 5.1 %, strong thunderstorm 2.7 %, under a tornadic storm
   0.5 % (its tornado is down a third of that and narrow), dense fog 2.8 %, hurricane 1 %; heat waves or cold snaps
   about once in 8 in-game days on their ground.
4. ~~Wind damage from thunderstorms~~ (answered 2026-10-25): plain thunderstorms damage nothing. A new kind, the
   severe thunderstorm (about 8 % of the time), strips some leaves (a tenth of a hurricane's rate at the same speed,
   and its gusts never reach glass or fences) and, like supercells and hurricanes, blows wooden doors open.
5. Is the mod id `anotherweathermod` and package `com.itcmd.anotherweathermod` right?
