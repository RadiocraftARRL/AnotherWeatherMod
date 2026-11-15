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

Non-goals for now: seasons, temperature survival. Dimensions: the Overworld and any overworld-like one (sky light,
no ceiling, not ultrawarm), never the Nether or the End; `dimensions`, `overworldLikeDimensions` and
`excludedDimensions` in the server config.

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

## Forming storms: the world's storms (reworked 2026-11-12)

Storms belong to the world, not to the players. Until 2026-11-12 every storm was rolled per player and placed upwind
so its path crossed where that player stood; now **no storm is ever aimed at or placed relative to anyone**
(`sim/StormGenerator`). Each storm is rolled from the world seed, a patch of the map and a stretch of time, forms where
it was rolled, and goes with the steering wind (times its kind's share) plus a small drift of its own. Players only
decide which of the world's storms are kept in the level's lists: those that come within sight of someone
(`VIEW_REACH`, 1800 blocks plus the storm's reach) within the forecast horizon. A storm put in late is mid-life,
exactly where it would have been; one nobody will see is dropped and comes back the same when someone goes there. Two
players together share the same storms, and the number of storms does not grow with the number of players.

- **Scattered storms**: every 2048-block square, every 2 minutes, rolls a Poisson number of storms by the day type
  (none on dry days, three times the setting on rainy days, living 1.6 times as long), with the severe kinds weighted up
  near a wind shift and tornadoes on watch days. The rate per square follows from the setting, which is now "storms
  passing over any one place per hour": each storm sweeps about `2 * 0.6r * speed * life + pi (0.6r)^2` of ground.
  Fog banks, high wind (not on rainy days), heat waves and cold snaps (by the biome where they form) are rolled the
  same way on their own squares.
- **Fronts**: on 12000-block squares every quarter day, lines 2500 to 7000 blocks long form across the wind and sweep
  along with it for half a day to a day, setting off cells along their length every 75 seconds (a squall line, 80
  blocks either side of the line), fewer toward the ends. Their cells are more often severe (fewer showers), live 0.6
  of a lone storm's life, and belong to the day like any other. Three times as many fronts on rainy days, twice as many
  round a wind shift (a cold front).
- **Hurricanes**: rolled on 16384-block squares every day, kept only if they form over the ocean (biome source, no
  chunk loads); they wander with the wind and a drift of their own for days, so coasts see them and inland rarely.
- **Kept and dropped**: every 2 seconds (and at once when someone joins, respawns or changes dimension) the level puts
  in the world's storms that will come within sight of a player and drops the ones that will not come within 2600
  blocks of anyone. Working them out costs a few milliseconds: for each stretch of time only the squares upwind of a
  player by as far as the wind carries a storm from then are rolled (`WindTrack`).
- A storm removed on purpose (`/awm kill`, `/awm calm`, `/awm clear`) is remembered until it would have died, so it is
  not put back. Storms from commands (`/awm spawn`, `/awm approach`) are not the world's and are never dropped.
- Both lists are saved with the level (`SavedData`), with which storms are the world's and which were removed.
- A cap (`maxStorms`, 160) keeps it bounded: the storms nearest to someone win. There is no spacing rule any more (it
  depended on what was already in the lists, so on where players had been).
- Sleeping moves the day boundaries in game time, so storms not yet put in can come out differently after a night
  skipped; ones already in stay.

Because the storms are worked out ahead and are the world's, "a thunderstorm is forecast to form 2 km west in 6 minutes" is a true statement, and a radar
that shows pending cells as outlines is honest.

### Day types (2026-10-30)

Storms belong to the day. Every in-game day (counted by day time, so a new one starts at sunrise; game time stands in
when the daylight cycle is off) the dimension rolls a type: **dry** (50 %: no storms form, few small clouds),
**chance of rain** (30 %: about today's old weather, a few more storms per hour than the setting says) or **rainy**
(20 %: five times the storms, living 1.6 times as long, under a grey deck). The type is the same everywhere: it used
to vary by region (a jittered grid 2000 blocks apart, with a cloudy strip two chunks wide where two met), but walking
along that strip flipped the sky between grey and fair chunk by chunk (2026-11-12). Storms themselves stay local. All
of it is a pure function of seed and day (`sim/DayTypes`), so it is known days
ahead, and `/awm forecast` and the API (`outlook`, `tornadoWatch`) say "rain likely tomorrow".

The generator takes the day where and when a storm forms: no storm on a dry day, and a storm dies out by a quarter
of the way into the next day (an evening storm rains on into the night), or is not made if its day has too little
left. A **tornado watch** is out on rainy days with a wind shift, and on a quarter of the other rainy days (about 7 %
of days in all); tornado odds are four times the setting on watch days and a quarter of it on others, so most
tornadoes come on watch days and most watch days see none. Overall rain falls to about 20 % of the time (half the days
are dry); `weatherFrequency` (one multiplier on storms, fog, high wind, heat and cold, hurricanes) at 1.5 gives back
about the old amount.

**High wind** (`StormKind.HIGH_WIND`, `Hazard.HIGH_WIND`) is a big slow cell of clear, gusty wind along the steering
wind on days that are not rainy, gusting to just under what opens doors (no damage). The client shoves the player
downwind in each gust (`WindPush.GUST_SHOVE`), about half a block.

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

### Distant storms merge (2026-11-12)

With storms all over the world, a busy rainy day has 80 to 150 storms within sight, and each was drawn in full: a rain
wall of about 900 quads in two draw calls, a deck with vanilla clouds, and a share of every cloud cell's work. Now
(`client/StormLod`, `client/DistantStorms`) a storm whose edge is more than `distantStormDistance` (600) blocks off
fades over `distantStormBlend` (250) blocks into a merged mass: storms whose clouds touch (centres within 1.15 times
the sum of their radii) form one capsule, a weighted fit of the members (its line along their long axis, its length
and width from their spread plus their radius), so a front's cells become one long band and a clump of scattered
storms one wide mass. Each mass is one simple rain wall (one flat sheet and one haze sheet, 48 quads), one deck, and
one entry in each cloud cell's sums in place of its storms.

- Only drawing changes: rain, wind, lightning, the API and the storm lists are untouched. Lightning still lights the
  merged cloud, since the cloud shader lights by where the flashes are, whatever drew the cloud.
- No popping: a storm's own drawing fades out as its weight in the mass fades in, and the mass's shape is continuous in
  those weights. Which storms make a mass depends only on the storms, not the camera; when that changes (two storms
  drift together or apart) the old mass fades out and the new one in over 2 seconds. In the 3D clouds the switch is
  at half detail, and the clouds' own 2 second cell dissolve smooths it.
- Measured in `StormLodTest` on a rainy day (81 storms in sight): each cloud cell weighs 32 storms or masses instead of
  81, and rain walls drop from 72576 quads in 162 draw calls to 28992 in 80. Near storms (within the 600 + 250
  blocks) are the rest; masses themselves are cheap.
- `mergeDistantStorms` (client config) turns it off.

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
DayOutlook tomorrow = api.outlook(level, pos, 1);       // day type and tornado watch; any day ahead
boolean watch = api.tornadoWatch(level, pos);           // today's watch
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

## Siren controller and data wire (2026-11-04)

- **Siren Controller** (`siren_controller`): a redstone-powered cabinet that sounds every siren its data wire reaches,
  at its signal's strength (so a tornado acoustic sensor's rising signal still winds the sirens up and down). Sirens
  touching it count. Sirens still run from plain redstone too: a siren runs at the strongest of its own signal and
  what its controllers ask (`WiredSiren`).
- **Wire** is anything in the block tag `anotherweathermod:siren_wire`: AWM's `data_wire`, and as optional entries
  RadioCraft's `data_wire` (all 16 colours) and `passthrough`. A colour joins only itself; AWM's wire and the
  passthrough (`siren_wire_joins_all`) join any. The walk (`SirenWiring`, pure and unit-tested) follows the six
  neighbours and, like RadioCraft's wire and redstone dust, the climb up a wall or down a ledge. RadioCraft's wire
  bundles (several cables in one block) are not followed. AWM adds its controller, sirens and wire to
  `radiocraft:data_blocks` so RadioCraft's wire draws its link to them.
- **No scans:** a controller walks its wire only when powered and its answer is stale: on load, and after a block
  anywhere in the level turns into or out of siren wire or a siren (one hook on `ServerLevel.onBlockStateChange`; a
  block changing only its state does not count). Changes are gathered to the end of the tick. The sirens reached are
  cached on the controller and pushed to each siren when the input changes. The walk stops at unloaded chunks (it
  never loads them) and at 4096 wire blocks; a walk cut short tries again when chunks load, at most once a second.
  Sirens save what their controllers asked, and every 5 s check that loaded controllers still ask it.
- **Choice: AWM's data wire is always registered**, not only when RadioCraft is absent. Registering it conditionally
  would change the block registry with the mod list: a world that later adds RadioCraft would lose every AWM wire
  (missing-block warnings, the blocks turn to air), and a server and client disagreeing about RadioCraft would not
  match. Always registered, laid wire survives RadioCraft coming or going and joins RadioCraft's wire. With
  RadioCraft loaded, AWM's wire is left out of the creative tab and its recipe (and recipe advancement) is off
  (`neoforge:not` / `neoforge:mod_loaded`), so players use RadioCraft's.

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
