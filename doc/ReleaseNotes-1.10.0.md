# Another Weather Mod 1.10.0

Minecraft 1.21.1, NeoForge, MIT licensed. Works server-only: vanilla clients still see the local rain and thunder.

## Day types

- Every in-game day each region (about 2000 blocks across, `dayRegionSize` in the server config) is **dry** (half of
  days), has a **chance of rain** (three in ten) or is **rainy** (one in five). Storms only form on days that are not
  dry, and rainy days bring more of them and longer ones. Where two regions meet, a strip stays cloudy with no storms.
- `/awm forecast` reports today's and tomorrow's outlook. Ops can try each sort of day with
  `/awm day <dry|chance|rainy|auto>` (for the day, everywhere in the dimension).
- `weatherFrequency` scales how much weather there is (default 1). Rain now falls about a fifth of the time.

## Storms and wind

- **Tornado watches**: rainy days with a wind shift (and some others) carry one, and tornadoes are likelier on them.
- **High wind**: a new clear-weather kind whose gusts shove players about half a block, without damage.
- A storm's outflow now mixes with the prevailing wind across its rain's edge and is gone 10 blocks past it, so once a
  storm has passed, wind vanes point the way the clouds move. Out of the storms a vane swings a little either side of
  the prevailing wind.

## Clouds

- A rainy day's grey sky is its own thin, smooth deck, higher than storm cloud, so storms show up below it, darker and
  lower. It no longer flickers where its puffs overlapped.
- When the sort of day changes (or you cross a region's edge) the fair-weather clouds and the grey deck fade out and
  back in, instead of popping in tile by tile.

## For mods

- `AnotherWeatherApi.outlook(level, pos, daysAhead)` gives a place's day type, region edge and tornado watch, and
  `tornadoWatch(level, pos)` answers that alone. Storm reports gain the high wind hazard.
