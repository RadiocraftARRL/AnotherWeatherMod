# Todo

## Ideas to decide on

- **Seasons, maybe.** The catch: snow piling up in winter and never melting in chunks nobody visits in spring.
  - Client-side snow only: cheap and never stuck, but it does not block movement, crops or mobs, and
    other mods (and the server) cannot see it.
  - Catch-up on load: store when each chunk last had its snow updated (a chunk data attachment). When the chunk
    loads, settle the snow (add or melt layers) for the weather it missed. Real snow that is right whenever you
    get there. Costs a little work per chunk load.
  - Leave snow to vanilla and only change temperatures, day length and storm odds.
- **Storm Protector**: done, with a command block look until it has its own art (RadioCraft's
  `doc/ModelDesignBounty.md`).

## Ideas (maintainer, 2026-10-30)

- **Shutters** that can be closed over windows so hail and wind do not break the glass behind them.
- **Tornado watch** in the forecast: a tornado is possible but probably will not happen. RadioCraft prints it on
  WEFAX and gives it its own redstone level, so it needs to be in the API.
- **Flash flood**: a rare, brief event during rainstorms and severe thunderstorms. Water fills low ground a bit at a
  time, at most two blocks deep. It is not real flowing water. It never forms in caves: each flood block must see
  the sky or be within 5 blocks of another flood block. Maybe drawn on the client only, to spare the server. It does
  not hurt crops. Players can only swim in it briefly before they sink.
- **High wind**: clear weather with gusty wind. A gust can push a player about half a block.
- **Weather frequency setting**: one server setting that scales how often weather happens, on top of everything
  else. It defaults to no change. Once the day types below are in, leave the default as it is for now.
- **Storms that belong to the day**, not ones that seem to appear at random. Each day gets a type:
  - Dry (50% of days): no rain, mostly clear skies with a few puffy clouds.
  - Chance of rain (30%): like today's weather, with fewer storms.
  - Rainy (20%): still only a chance of rain, but storms are more frequent and last longer. A grey sky and a base
    layer of cloud cover the region all day, with the storm clouds lower and darker.
  - The type varies by region, but the regions are big. Where two meet, a strip a couple of chunks wide stays cloudy
    with no storms.
  - The forecast knows the type ahead of time (`/awm forecast`, and RadioCraft's WEFAX through the API), so it can
    say "rain likely tomorrow".

## Instruments

- Done (2026-10-26): wind vane, barometer, thermometer, rain gauge, solar detector, tornado acoustic sensor and
  tornado siren, with recipes, each with a reading on use and a redstone signal; the siren runs on redstone (the
  sensor drives it) and winds up and down (`block/`, `InstrumentGameTests`). Needs a playtest
  (`doc/NeedsTesting.md`).
- [x] From Localized Weather (MIT now, 2026-10-26): lightning flashing inside our clouds, the sky darkening toward
  an approaching storm, thunder heard from the storm's bearing, cloud colour by precipitation (`StormLightning`,
  `StormHorizon`, `CloudCover`; THIRD_PARTY.md). Needs a playtest.
- A forecast item (barometer-like) for players without RadioCraft; `/awm forecast` already works for everyone.

## Common in other weather mods; worth considering

- Lightning: strikes drawn to lightning rods and tall metal, more strikes in severe storms, now and then a wildfire
  in dry forests.
- Rain filling cauldrons and water barrels faster in heavy storms; floods are much harder.
- Wet clothes making you colder. (Auroras are RadioCraft's.)

## Done from this list

- Waterspouts and dust devils (for looks, drawn by each client), rainbows, overheating in heat waves and freezing in
  blizzards, crops stalling in heat waves, `/awm forecast` for everyone. Hail already beat crops back and broke panes.

## Rendering

- 3D clouds: storm structure (anvils, shelf and wall clouds); lit tops, sunset colours, lightning glowing inside.
- Shader packs: check more packs (and Distant Horizons, which draws past the fog).
