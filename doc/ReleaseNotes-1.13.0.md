# Another Weather Mod 1.13.0

Minecraft 1.21.1, NeoForge, MIT licensed. Works server-only: vanilla clients still see the local rain and thunder.

## Storms across the world

- Storms are no longer aimed at players. They form all over the world, from the world seed, and go with the wind, so
  two players standing together see the same storms, and someone who walks a few thousand blocks away finds storms
  there too. Many more storms show on the horizon on a rainy day, but storms keep apart: no more piles of them on one
  spot.
- **Fronts**: long lines of storms (a squall line) form across the wind and sweep along with it, more on rainy days and
  round a wind shift.
- Rain now falls about 8 % of the time (about 15 % on days with a chance of rain, 20 % on rainy days).
- **Storms that feel the terrain**: storms swell and shrink a little and slowly change shape, grow (and strengthen a
  little) over the ocean and shrink over desert, easing into it as they cross a coast. `stormsFeelTerrain` in the server
  config turns it off.
- **Dimensions**: any overworld-like dimension (sky light, no ceiling, not ultrawarm) gets the mod's weather, never the
  Nether or the End. `overworldLikeDimensions` and `excludedDimensions` in the server config change that.

## Day types

- The sort of day is now the same everywhere in a dimension, so the sky no longer flips between grey and fair as you
  walk. `dayRegionSize` is gone from the server config.
- Each day leans lightly on the day before, so wet and dry spells come now and then (a wet day follows a wet one about
  63 % of the time). The share of each sort of day is unchanged.
- **Dry days** are now 35 % clear, 30 % a thin veil of high stratus, 25 % broken cloud and 10 % overcast. The stratus is
  drawn on a dome reaching the horizon, drifts with the wind and is tinted with the sky at dusk and night (client config
  `stratus`). Now and then a dry day has a lone shower or ordinary thunderstorm, never anything strong.
- Storms break up before a dry day instead of running on into it: they spend their last stretch decaying, shrinking to
  half their size, and are gone half a minute before the dry day starts.
- Sleeping, `/time set`, switching the daylight cycle or `/awm day` rolls the storms again for the days as they now are:
  storms that still belong stay, the rest die out. Dawn mist and leaf drips come only on rainy days and after rain.

## Hurricanes

- A hurricane's category now follows its wind. It forms as a tropical storm, is upgraded as it strengthens, never goes
  past its peak category, and falls back as it weakens or crosses land. `/awm list` shows its category now and its peak.
- Each hurricane starts as a tropical disturbance with a 50 to 90 % chance of developing; about 3 in 10 fizzle out or
  stay tropical storms. Watches and warnings go out up to two days ahead, which RadioCraft's weather radio reads out.
- Hurricanes are gone before a dry day, and form more often to make up for it: about 1 % of the time near the sea.
- Category 1 and 2 are toned down: steady heavy rain, less slant and murk. Hurricane rain is louder, with a drumming on
  the roof when you are under one, and slanted rain stops at roofs and stairs instead of coming in under them.
- In the eye the sky stays storm grey and the air murky, with light rain and little wind.

## Tornadoes and wind

- A tornado pulls you in and lifts you from further out, while its core is no stronger: an EF0 pulls from about 14
  blocks (was 8), an EF5 from about 150 (was 131), and you are carried up within about 1.7 times its radius.
- **Wind damage** has more steps: foliage, then glass, loose bits, fences and trapdoors, planks and wool, and logs, each
  in stronger wind and less often. Blocks without a tag are placed by what they are made of; stone, concrete, metal
  and earth never break, and chests are never smashed.
- In a gale wooden blocks nearby creak now and then (a placeholder sound for now).

## Warning sirens

- **Omnidirectional Siren** and **Horn Array Siren**, moved over from RadioCraft: two blocks tall, on a fence post, log
  or wall, sounding while either half has redstone power. A stronger signal winds them up faster and higher, and they
  coast down when the power goes.
- Pole-mounted versions stand on a twelve block pole with a control cabinet at the foot: right click it to turn the key
  switch. Breaking any part takes the whole siren down.
- Sneak and right click a siren to change its sound: air raid, tornado or klaxon.
- A **Siren Controller** sounds every siren on its **Data Wire** at the strength of its redstone signal. With RadioCraft
  installed its data wire does the job, and AWM's own wire is left out of the creative tab.
- The sound comes from the server, so it fades with distance (heard up to about 480 blocks), stays in step for everyone
  and stops for everyone when the siren breaks. It has echoes, a slight waver, and subtitles that point at the siren.

## Clouds and rain

- Clouds fade in and out over two seconds when they change instead of popping, and fair-weather cumulus no longer
  blink as they drift.
- A storm sky that has closed over stays closed through a tropical storm's rain bands instead of flickering between
  grey and clear.
- Inside a cloud the view fogs to its white (a storm's dark grey) instead of showing a hollow shell, and rain falls
  from the cloud base: above it none is drawn.
- Far storms whose clouds touch are drawn as one cloud mass, a front as one long band, and come apart smoothly as you
  get closer (client config `mergeDistantStorms`, `distantStormDistance`, `distantStormBlend`).
- Rain walls are faint layered sheets instead of a grey fog sheet, and a group of touching storms shows one curtain.
  They fade out when you go indoors.
- **First drops**: from about 140 blocks before a storm's rain reaches you, a drop falls here and there, more as it
  nears (client config `rainOnset`).
- Rainbows are wider and fainter, and seen from high up the whole ring shows. Under a severe storm or hurricane the
  sunset no longer shows through.

## Performance

- Storm rain and clouds remember what falls where instead of looking up the biome for every column every frame.
- Cloud tiles out of view are skipped and the rest drawn nearest first, and clouds with nothing fading use a shader
  that lets the graphics card reject hidden cloud before shading it.
- Cloud tiles are built on a background thread, so a storm moving in (a hurricane above all) no longer stutters.
- Below the clouds the faces turned away from you are not drawn, about half the cloud geometry.
- **Rain Detail** (client config `rainDetail`: `LOW`, `MEDIUM`, `HIGH` or `ULTRA`, default `HIGH`) trims the fainter
  rain further out and the sheets of the rain walls. Lower it if storms cost too much frame rate. Fast graphics means
  `LOW`.
- Fewer allocations per tick and per frame: weather samples, wind push, slanted rain and puddles do less work.

## Commands

- `/awmclient benchmark` measures the frame time each of the mod's effects costs where you stand: everything on, each
  effect off in turn, all off, no clouds at all, then everything on again. It prints the milliseconds a frame, frame
  rate and 1 % low for each, and what each one saves. It lifts vsync and the frame cap while it runs, puts every
  setting back afterwards and saves nothing. Keep still while it runs.
- `/awm pin [id]` holds a storm in place for testing (with no id, every storm reaching you); it still forms, lives and
  decays. `/awm unpin [id]` lets it go on with the wind from where it is (with no id, every pinned storm). Pins are
  saved, and `/awm list` marks pinned storms. Both need permission level 2.

## For mods

- `StormView.categoryAt(long gameTime)` gives a storm's category at a time: a hurricane's category now, which rises
  and falls with its wind. `category()` stays its peak.
- `AnotherWeatherApi.tropicalAdvisories(level, pos)` lists the tropical disturbances, storms and hurricanes whose
  forecast cone reaches a place within two days, as `TropicalAdvisory` records (disturbance, outlook, watch, warning)
  with the chance of developing, the forecast category and the forecast track.
- `DayOutlook.regionEdge` is always false now that the day is the same everywhere; it stays so the API does not break.
- `WindDamageEvent.DamageClass` gains `STURDY`, `STRUCTURE`, `RESILIENT` and `EXTREME`, with the block tags
  `anotherweathermod:wind_damage/sturdy`, `wind_damage/structure` and `wind_damage/resilient`.
