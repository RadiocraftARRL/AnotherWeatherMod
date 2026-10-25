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

## Planned: instruments (waiting on models, see RadioCraft's `doc/ModelDesignBounty.md`)

- Tornado siren: sounds when a tornado comes within range, or on a redstone signal.
- Wind vane (turns to the wind), barometer, rain gauge, thermometer: blocks giving a reading and a redstone signal.
  They would pair well with RadioCraft's weather reports.
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
