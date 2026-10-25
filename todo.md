# Todo

## Ideas to decide on

- **Seasons, maybe.** The catch: snow piling up in winter and never melting in chunks nobody visits in spring.
  - Client-side snow only: cheap and never stuck, but it does not block movement, crops or mobs, and
    other mods (and the server) cannot see it.
  - Catch-up on load: store when each chunk last had its snow updated (a chunk data attachment). When the chunk
    loads, settle the snow (add or melt layers) for the weather it missed. Real snow that is right whenever you
    get there. Costs a little work per chunk load.
  - Leave snow to vanilla and only change temperatures, day length and storm odds.
- **Storm protection block** (creative only): a block with a range slider that makes the area round it immune to
  tornado and hurricane damage, for spawns, builds and adventure maps.
  - Keep a fast lookup of protected areas (saved data) for `Tornadoes.tick` and hurricane damage to check.
  - Maybe also stop wind push and debris inside it.

## Common in other weather mods; worth considering

- Weather instruments: barometer, anemometer, rain gauge, thermometer, wind vane. Blocks giving a redstone signal
  or a reading. They would pair well with RadioCraft's weather reports.
- Tornado siren: sounds when a tornado comes within range, or on a redstone signal.
- Waterspouts (tornadoes over water) and dust devils (small, harmless whirls on hot, dry days).
- Lightning: strikes drawn to lightning rods and tall metal, more strikes in severe storms, now and then a wildfire
  in dry forests.
- Hail damaging crops and breaking glass panes; drought slowing crops in heat waves.
- Rain filling cauldrons and water barrels faster in heavy storms; floods are much harder.
- Rainbows after a rain shower clears with the sun out; aurora on clear, cold nights.
- Optional survival effects (off by default): getting cold in blizzards and wet clothes, heatstroke in heat waves.
- A `/awm forecast` command and a forecast item (barometer-like) for players without RadioCraft.

## Rendering

- 3D clouds: storm structure (anvils, shelf and wall clouds); lit tops, sunset colours, lightning glowing inside.
- Shader packs: check more packs (and Distant Horizons, which draws past the fog).
