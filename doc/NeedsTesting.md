# Needs testing in game

Things the automated tests cannot check. Tick them off in a playtest.

## Distant storms merge into cloud masses (added 2026-11-12)

Far storms (edge beyond `distantStormDistance`, 600 blocks, fading over `distantStormBlend`, 250) are drawn as merged
masses: a front as one long band, a clump of storms as one wide mass. Near ones are unchanged.

- [ ] `/awm day rainy`, look round from a hill: far storms read as cloud bands and masses, near ones in full detail.
  Does a front look like one long band? Is anything obviously missing or blobby?
- [ ] Walk toward a far mass: its storms come out of it one by one with no popping, in the 3D clouds and in the rain
  walls. Walk away again: they melt back in.
- [ ] Watch a while from one spot: when storms drift together or apart far off, the masses change by fading, not
  jumping.
- [ ] Lightning in a far mass still lights it up.
- [ ] Frame rate on a busy rainy day with `mergeDistantStorms` on and off (F3): how much does it help?
- [ ] With vanilla clouds (`allowCustomClouds` off or the mod's clouds off): the dark decks under far masses look right.

## Storms belong to the world, fronts, dimensions (added 2026-11-12)

No storm is aimed at a player any more: storms form all over the world and go with the wind; players only decide
which are kept near them. Fronts (squall lines) now sweep across. Rain at one spot should still be about 20 % of the
time overall (a fifth on chance days, half on rainy days), but you now see many more storms around you on the horizon.

- [ ] `/awm day rainy`, wait a few minutes: storms scattered all round, coming in from upwind and passing by, some
  missing you. None seem to home in. Two players standing together see the same storms; one walking a few thousand
  blocks away finds storms there too, and coming back finds the first ones where they would be by then.
- [ ] A front: on a rainy day (more round a wind shift, `/awm forecast` shows the wind), look for a long line of storms
  across the wind sweeping over. Does it read as a front/squall line? Too many, too few, too regular?
- [ ] Performance: on a rainy day, with well over a hundred storms within sight, does the server keep 20 TPS (`/awm`
  status shows active/pending storms) and the client its frame rate with the mod's clouds and rain walls? A short hitch
  every 2 seconds would be the storms being worked out.
- [ ] `/awm clear` and `/awm calm`: the storms go and do not come back; new ones still form later.
- [ ] RadioCraft with AWM: weather radio, WEFAX and SKYWARN readers still report storms, arrivals and the day outlook
  sensibly near players. (Storms far from every player are not kept, so a station in a chunk-loaded spot far from
  everyone hears nothing there, as before.)
- [ ] Old world from before this: its old aimed storms play out, then only the world's storms come.
- [ ] Dimensions: in the Nether and the End no rain, rain walls, storm clouds or wind; coming back from the Overworld in
  a storm leaves nothing behind there. A modded overworld-like dimension (if you have one) gets weather;
  `overworldLikeDimensions = false` limits it to the `dimensions` list, and `excludedDimensions` turns one off.

## Rainbows, rain walls indoors, rainy-day mist, one day type everywhere (added 2026-11-12)

From the 2026-11-12 playtest.

- [ ] Rainbow from the ground (`/weather rain`, wait, `/weather clear` with the sun low behind you; half of clearings
  bring one): an arc, wider and fainter than before, colours blending with no hard rim; hills and trees hide it.
- [ ] Rainbow from high up (fly 100+ blocks over flat ground, or stand on a high peak): the whole ring shows as a halo,
  with no flat cut-off at eye level. Fly down: the ground covers the lower part again smoothly.
- [ ] Rain wall: `/awm approach` a thunderstorm, watch its rain curtain from a field, then walk into a house (or a cave):
  the curtain fades out within about half a second, also through windows and the open door; it comes back outside,
  and still shows standing under a tree.
- [ ] Dawn mist and leaf drips: `/awm day dry` (and `chance`), `/time set 23000`: no mist or dew drips at sunrise.
  `/awm day rainy`, `/time set 23000`: the mist and drips come as before. Mist after real rain still comes any day.
- [ ] Walk or fly a few thousand blocks in one direction (and over the hill where the sky flipped before): the sky's
  sort of day (clear, partly cloudy, grey deck) never changes as you go; it changes only when the day turns over.
  `/awm forecast` says the same Today/Tomorrow everywhere. Storms are still local and come and go as before.

## Siren Controller and data wire (added 2026-11-04)

A Siren Controller sounds every tornado siren its data wire reaches, at the strength of its redstone signal. AWM's
own Data Wire is in the creative tab only without RadioCraft; with RadioCraft its data wire does the job.

- [ ] AWM data wire looks right: a thin cable along the floor through the middle, joining wire, sirens and
  controllers round it; straight up through the middle to wire above; up a wall to wire on top of it, and down a
  ledge. Breaks at once, no collision.
- [ ] Lever on a controller, wire to two sirens 20 blocks apart: both wind up together; lever off: both coast down.
  The controller's front lamp lights while powered. Using it says how many sirens it sounds.
- [ ] A tornado acoustic sensor into the controller: the sirens rise and fall with the signal.
- [ ] With RadioCraft installed: RadioCraft's data wire (plain and a colour) links a controller to a siren and draws
  its link into both; AWM's wire is gone from the creative tab and has no recipe; AWM wire already laid still works
  and joins RadioCraft's.
- [ ] A siren's chunk unloaded and reloaded while the controller is on keeps sounding; break the controller while
  the siren is unloaded: it stops within 5 s of loading.
- [ ] Art: the controller and wire are placeholders (plain cabinet with a lamp, a grey 1 px cable and a coil item).

## Clouds fade instead of popping (added 2026-11-04)

Clouds used to pop in and out every few seconds, most around storms. Now a cloud that changes dissolves into the
next over 2 seconds, and fair-weather cumulus drift with their pattern instead of through it.

- [ ] Clear sky, standing still for a minute: the cumulus drift east and hardly change; none pop.
- [ ] `/awm approach` a thunderstorm and watch it pass: its deck's edge and the cumulus round it dissolve in and out
  rather than popping. Does 2 seconds feel right, and does the dither pattern look acceptable while fading?
- [ ] Fly so tiles cross between near and far detail: they dissolve between the two instead of snapping.
- [ ] Clouds look as before otherwise: storm decks still solid on the horizon, the grey deck still fades with a change
  of day, no flicker where nothing is changing.

## Day types and high wind (added 2026-10-30)

`/awm forecast` now starts with "Today: ... Tomorrow: ..." (dry, a chance of rain, rain likely, or cloudy with no
storms on a region's edge) and a tornado watch line when one is out. Fly a few thousand blocks to cross regions, or
wait a day (`/time add 24000`).

- [ ] Dry day: mostly clear sky, only a few small puffy clouds.
- [ ] Rainy day (and the strip where two regions meet): a thin grey deck all over, with storms darker under it. The
  storm clouds are not drawn lower on rainy days yet; say if it needs it.
- [x] Crossing from one region into another the sky changes at once. Superseded 2026-11-12: there are no regions
  now, the day is the same everywhere (see the 2026-11-12 section).
- [ ] High wind (`/awm hazard wind`): wind streaks and gusts, each gust pushes you about half a block on the ground
  (sneak to halve it, gold armor holds you), doors stay shut and nothing breaks. The client's `windPush` setting
  turns the shove off.
- [ ] Over a few in-game days, does the mix feel right: dry days, a chance of rain, rainy days? Overall it rains
  less than before (about 20 % of the time against 30 %); `weatherFrequency` 1.5 in the server config gives back the
  old amount.

## Storm deck overhead as a storm moves in (added 2026-10-28)

Reported: a severe thunderstorm raining on the player on a flat world, no cloud overhead, only some clouds far off.
The mod's clouds never reread their cells after a tile was first built (`CloudRenderer.resampleDue`: the "never read"
start value overflowed), so the sky kept whatever it was when you joined, and only tiles coming newly into range (far
off, or as you walk) showed the weather now. This was probably also behind "Rain with no storm" below. Now the cells
are read again every 4 ticks.

- [ ] Stand still in clear weather and `/awm approach` a thunderstorm: the deck builds overhead before its rain wall
  reaches you (fair cumulus thickening, then a light deck), is deep and dark by the time the rain starts, and clears
  again behind it. `/awmclient debug`'s "Cloud overhead" line matches what you see.
- [ ] Join a world while it is already raining under a storm: the deck is overhead within a few seconds.
- [ ] `/weather rain` then `/weather clear` without moving: the shower deck comes and goes overhead.
- [ ] No stutter from the clouds rebuilding (a couple of tiles a frame at most).

## Sable ships in storms (added 2026-10-28)

`./gradlew runClient -PwithSable`. Make a small ship with Sable's `/sable` command (assemble) and bring a storm to it
(`/awm spawn` or `/awm approach`).

- [ ] The game loads with Sable installed, and nothing changes without it.
- [ ] In a hurricane or severe thunderstorm's gusts (over 22 m/s) the ship drifts downwind, harder the stronger the
  wind; in a plain thunderstorm it does not move. `sableShipPush` 0 stops it; 1 pushes it as hard as a mob.
- [ ] A tornado passing under a low ship lifts it in the core; a ship more than 60 blocks above the ground is not lifted.
- [ ] Over a Storm Protector's area the ship is left alone.
- [ ] With Create Aeronautics: sails and propellers feel the wind (a sail ship runs downwind in a storm).

## Rain with no storm (added 2026-10-27, late)

Reported: a plain rain shower on joining a world, blue sky, fair-weather cumulus, no deck. Rain the storm field does
not account for is vanilla's `/weather rain` override (it rains on everyone everywhere until its timer runs out); the
clouds only followed storms. Now any rain with no storm behind it (`CloudCover.overcast`) draws a shower's deck over the
whole sky, and `/awmclient debug` says so on its first line.

- [ ] `/weather rain`: the sky goes to a shower deck everywhere within a few seconds; `/weather clear` brings the fair
  cumulus back. `/awmclient debug` reports "rain with no storm".

## Snow decks, rain shafts and `/awmclient debug` (added 2026-10-27, late)

- [ ] A storm over snowy ground: a shower's deck is a light grey-white, top and underside, not a thunderstorm's
  slate; the stronger the storm the greyer it gets (a severe snowstorm's deck was invisible against the white sky,
  reported 2026-10-27, so from scale 3 up it is a mid grey and a supercell's near a rain deck). Where the same deck
  reaches rain ground it is grey again (cells rebuild as they change). Hail and thunder decks over rain ground stay dark.
- [ ] A rain shaft seen from a few hundred blocks: several sheets of streaks one behind another (they slide past each
  other as you walk sideways), denser and faster than before, each sheet at its own speed; thick and dark under the
  cloud base, thinning toward the ground; a grey-blue as dark as the storm; a low haze along its foot, mistier the
  farther off it is. Walking in, it fades out smoothly over the first ~48 blocks of rain instead of popping.
- [ ] A snow shaft: white, falling slowly and drifting side to side.
- [ ] `/awmclient debug` prints the local weather, each storm the client knows (distance, radius, rain edge, whether
  you're in its rain), what cloud the mod puts overhead, and the cloud height, range and fade end. It works with the
  server's `/awm` commands still there.

## Storm deck overhead under the rain (added 2026-10-27)

- [ ] Under a raining storm (day and night, severe and not, with storm fog on): look straight up. There should be a
  dark, ragged, mottled cloud base overhead, lumps hanging at different heights and in different shades, drifting
  slowly, not a bare even sky with cumulus only near the horizon. In severe murk the deck stays solid overhead and
  closes into the murk toward the horizon.
- [ ] From outside, the deck's base is uneven rather than one flat plane; the storm still looks the same from afar.

## Storm clouds lead the rain, rain wall at the rain (added 2026-10-26, late)

- [ ] `/awm approach severe_thunderstorm 3` (and with a supercell): stand still and watch. The dark deck should build
  overhead and darken the sky well before any rain (about half the storm's radius ahead of its rain, so a minute or
  so for a slow storm), at its deepest by the time the rain starts, thinning out behind as it clears. The deck moves
  in along the storm's track rather than sitting still. Fair-weather cumulus thickens ahead of it.
- [ ] The rain wall: walking toward a storm (or standing as one comes in), the curtain should be where the rain
  starts, not a stretch ahead of it: you step through the wall and it is raining. Weak showers that barely rain show
  no wall. Hurricanes keep their wall near their outer edge.
- [ ] Unit tests to run when nobody is playing: `CloudCoverTest` (cloudLeadsTheStorm, rainEdgeIsInsideTheRadius).

## Lightning in the clouds, darkening toward storms, distant thunder, cloud colours (added 2026-10-26)

Ideas from Localized Weather (MIT). Each has a switch in the client config: `lightningInClouds`, `distantThunder`,
`stormHorizon`.

- [ ] `/awm approach supercell 5`, at dusk and at night: patches of the storm deck flicker bright blue-white from
  inside, a few strokes each, every few seconds, the underside and flanks most. No tile pops or stutter (it is all
  in the shader). A flash within about 220 blocks also lights the sky briefly.
- [ ] A bolt that comes down (`/summon lightning_bolt ~10 ~ ~`) lights the cloud above it, and you hear only the
  game's own thunder for it, not twice.
- [ ] Distant thunder (reworked after the first playtest, 2026-10-26): with a supercell 600 to 1200 blocks off, only
  some flashes are heard, as low rolling rumbles that swell in and grumble away over six seconds or so (no sudden
  loud spikes), from its side (turn your head: the sound pans), a few seconds after the flash. At most three roll at
  once. A strike within 100 blocks cracks first, then rolls. Nothing past about 1400 blocks. Sounds synthesized by
  `tools/thunder_sound.py` (swap in recordings any time).
- [ ] Storms coming in (fixed 2026-10-26: they used to appear overhead): `/awm approach severe_thunderstorm` (and with
  `5` minutes). The storm's dark deck is visible on the horizon upwind straight away and drifts in, building as it
  comes; storm cloud stays solid much further out than fair-weather cumulus before fading into the fog.
- [ ] Darkening toward storms: with a severe storm a few hundred blocks off and coming, face it and the fog, sky and
  clouds turn slate grey; turn away and the sky clears. A dark band sits low on the horizon on its side whichever way
  you look, behind terrain and clouds, gone with a shader pack. Once under the storm the old storm darkness takes
  over with no jump.
- [ ] Cloud colours: a storm over snowy ground (`/awm hazard blizzard` in a snowy biome) has a pale deck; a
  supercell over plains (`/awm hazard hail`) a greenish-grey one; over a desert (`/awm hazard sandstorm`) tan;
  ordinary rain stays grey.

## Weather instruments and wind shifts (added 2026-10-26)

All seven are in the Redstone and Functional creative tabs. Right click any of them for its reading.

- [ ] Wind vane: the arrow points the way the wind comes FROM (`/awm here` gives the bearing). Check a few
  directions, especially the in-between points (NNE, ENE): the arrow frames are turned by the element rotation and
  the blockstate in opposite senses, and the blockstate script assumes vanilla's element rotation is
  counter-clockwise seen from above. If the arrow is mirrored, flip the sign in `tools/instrument_blockstates.py`
  (`r - angle` to `r + angle`) and rerun it.
- [ ] Barometer and thermometer hang on the wall they were placed against, in all four directions, needle and column
  in their cases. `/awm approach supercell 3`: the needle drops toward RAIN or STORMY before the storm arrives.
- [ ] Rain gauge: the water rises in four steps under a storm in the open (`/awm spawn thunderstorm`), roughly a
  step a minute in heavy rain; sneak-use empties it with a splash. A comparator reads the fill.
- [ ] Solar detector: strong signal at noon in clear sky, weaker under a storm or dense fog, nothing at night.
- [ ] Tornado siren: places as two blocks. A redstone block beside it: the wail climbs in pitch and loudness over
  about six seconds and the horn turns faster as it goes, a full turn in 8 seconds at full speed; take the signal
  away and it sags and coasts down over about twelve. A weaker signal holds it at a lower speed and pitch. Walking
  round it, the sound swells as the horn faces you. Heard out to about 240 blocks. Breaking either half breaks both;
  only one drops. Does the tone sound right and loop without a seam? (Synthesized by `tools/siren_sound.py`.)
- [ ] Tornado acoustic sensor: the half-height cabinet with its white radome looks right (model drawn by
  `tools/weather_instruments_model.py`). `/awm hazard tornado` near it: its signal climbs as the funnel comes
  closer (a comparator or redstone dust shows it), 1 while a tornado is only coming. Wired to a siren it runs the
  siren faster the nearer the tornado.
- [ ] Recipes in the recipe book: all seven instruments.
- [ ] Item models: the instruments look right in the hand and inventory, the siren shrunk to fit a slot.
- [ ] Wind shifts: `/awm status` names the next shift; `/time add` to just past it and the steering wind (and
  storm tracks) swing round 60 to 150 degrees over two in-game hours. In general storms come from the west
  (`prevailingWindFrom` in the server config).

## Storm scale, tornadoes, hurricane categories, fog, heat and cold (added 2026-10-25)

- [ ] Rain gets greyer, darker and heavier up the scale: `/awm spawn thunderstorm`, `strong_thunderstorm`,
  `severe_thunderstorm`, `supercell`. Thick dark bands mixed in, more of them and more distant rain the worse it is.
  Showers keep vanilla rain.
- [ ] `/awm hazard tornado_3` (try `tornado_0` and `tornado_5` too): a dark funnel 120 blocks upwind, thin at the
  ground and flaring into the cloud, spinning and snaking, a dust skirt and dirt and leaves whirling at its base. It
  comes your way; where it passes, leaves, crops, fences, glass and logs go, grass scours. Standing in it lifts you
  (a full set of gold armor keeps you down); mobs and items are lifted and flung. EF5 is far wider than EF0.
- [ ] Tornado reach (2026-11-05): a tornado now pulls you in and lifts you from further out, while the core is no
  stronger. Pull reach at full strength: EF0 ~14 blocks (was 8), EF1 ~32 (17), EF2 ~64 (34), EF3 ~109 (58), EF4
  ~130 (88), EF5 ~150 (131); you are carried up within about 1.7 radii (was 0.86). Check it feels right walking
  toward `tornado_0`, `tornado_2` and `tornado_5`, with no sudden tug where the pull starts, and that mobs and items
  round it are drawn in and lifted the same way.
- [ ] Tornado, second pass: the funnel is dark grey (not tan), layered and lumpy, its shells turning at different
  speeds with dark puffs whirling up it; its foot glides and hugs the ground instead of jumping as it crosses trees
  and dips. `tornado_4` and `tornado_5` level what they cross down to stone, earth and water: whole trees and
  houses (containers still spill their contents).
- [ ] Dense fog, sandstorms, blizzards and the worst storm murk hide the sky too (the sun may still show faintly).
- [ ] `/awm hazard hurricane_0` feels like a severe thunderstorm; `hurricane_5` is far worse: lightning almost
  without a break, trees losing logs, grass scoured, fog close in. Now and then hail comes down in a hurricane.
- [ ] Wind pushes you downwind in strong gusts and hurricanes (less sneaking, more in the air), not under a roof or
  behind a wall. Each piece of gold armor cuts it by a quarter; a full set, not at all. Mobs and items drift too;
  iron golems do not. `windPush` (client) and `windPushesEntities` (server) turn it off.
- [ ] `/awm hazard fog`: the view closes in to about a dozen blocks, grey; less at night it closes further.
- [ ] Early morning (`/time set 23500`) and after rain: a light mist, and water dripping from leaves.
- [ ] `/awm hazard heat` in a desert by day: warm haze; you get hungry faster out in the sun. Nothing at night.
- [ ] `/awm hazard cold` on snowy ground at night: blue tint, you start freezing out in the open (not with leather
  armor, not near a campfire, fire, lava or magma); still water freezes over. Nothing by day.
- [ ] Hail patters louder: a clang on metal and glass, a thud on wood, a click on stone, soft on grass, a plip on
  water. Stones lie a while before melting, longer on HIGH and ULTRA.

## Severe thunderstorms, doors and graphics settings (added 2026-10-25)

- [ ] `/awm hazard severe` near a house with an oak door in an outside wall: within a minute or so the door swings
  open with its sound; close it and it blows open again. An iron door and a door deep indoors stay shut.
- [ ] In the same storm, a few leaves come off nearby trees, far fewer than in `/awm hazard hurricane`; glass and
  fences are untouched. `/awm hazard hurricane` blows the door open too.
- [ ] Wind is light (a breeze) under a clear sky, picks up just before a storm's rain arrives, and gusts: in
  `/awm hazard severe` it swells and drops every few seconds, the sound with it. Leaves come off in the gusts, not
  in the lulls. `/awm hazard end` (or `/awm calm`) ends it; the rain fades out.
- [ ] In a severe thunderstorm, supercell or hurricane the rain is darker and slants downwind, more the harder it
  blows. Snowy and dry biomes are unchanged. `stormRain = false` in the graphics settings brings back vanilla's.
- [ ] Hail: small distant stones fill the view out to about 28 blocks; stones patter as they land and lie on the
  ground a while before melting (longest on HIGH and ULTRA). Gusts throw white wisps past you.
- [ ] Storm fog: severe thunderstorms close the view in to about 90 blocks, hail to about 65, hurricanes to about 40, and closer still
  at night. Rain slants about 10 degrees in severe storms and much flatter in hurricanes.
- [ ] Puddles: after a minute or so of rain, patches of water with soft edges appear on open grass, stone and paths
  (not on leaves, snow or under roofs); they grow while it rains and dry up a few minutes after. They look right by
  day and are not glowing at night. Fabulous graphics too.
- [ ] Mods list > Another Weather Mod > Config: the graphics settings show with readable names. Turning off storm
  clouds, rain walls, hail, wind streaks, blown leaves, the wind sound or the sandstorm fog takes effect at once;
  particle quality LOW visibly thins hail and wind streaks, ULTRA thickens them.
- [ ] With `hurricanesWeakenOverLand = true`, a hurricane spawned inland (`/awm spawn hurricane`) loses strength over
  a few in-game hours (`/awm here` shows the wind dropping); one over the ocean does not.

## First playtest (added 2026-10-24)

- [ ] Join a world with the mod on the server and a **vanilla** client (no mod): it connects, and rain starts and
  stops as storms pass (`/awm spawn thunderstorm` on yourself, then walk 200 blocks away).
- [ ] The same with the mod on the client too.
- [ ] Rain fades in over a few seconds, not instantly, when walking into a storm's edge and when teleporting in.
- [ ] Thunder darkens the sky under a thunderstorm; lightning strikes inside it and not outside.
- [ ] `/awm list` and `/awm forecast`: a storm listed as arriving in N minutes arrives about then.
- [ ] Snow piles up under a storm in a snowy biome; cauldrons fill in rain.
- [ ] `/weather rain 60` makes it rain everywhere for a minute, then local weather comes back.
- [ ] Hurricane: `/awm spawn hurricane 1 600 20` near some glass panes, trees and fences. Panes break, leaves go,
  fences and torches pop off; nothing is destroyed in the calm eye. `/gamerule doWindDamage false` stops it.
- [ ] Supercell: `/awm spawn supercell 1`. Hail hurts you out in the open and not under a roof; crops break now and
  then. The death message reads well.
- [ ] Particle Rain installed on the client: deserts show a sandstorm under a strong storm, snowy biomes a blizzard.
- [ ] Sleep: the night can still be skipped with storms about.
- [ ] Client half (mod on the client): hail falls as small ice chips that bounce and tick on the ground, not under a
  roof; sandstorms (`/awm spawn supercell 1` in a desert) and blizzards (in a snowy biome) bring thick tinted fog that
  thins indoors and fades in and out.
- [ ] Regrowth: `/awm windburst 60 12` in a forest strips leaves and scours grass; `/awm regrow` brings leaves and
  grass back over the next seconds (torches, fences and glass stay gone). Without the command, it takes one to three
  in-game days.
- [ ] Hail on a wheat field (`/awm hazard hail` while standing in it): ripe wheat drops a stage or two, young wheat is
  sometimes flattened.
- [ ] `/awm approach thunderstorm 3`: `/awm forecast` says about 3 minutes, and the rain does arrive then.
- [ ] Storms from outside (mod on the client): `/awm approach thunderstorm 5`, then look toward it. A dark cloud deck
  sits under the white clouds over the storm, and a grey curtain of falling rain hangs from its edge facing you; both
  fade with distance and move with the storm. Walking in, the curtain gives way to vanilla rain.
- [ ] Wind: in a strong wind (`/awm hazard hurricane`, or a supercell's gust front) the rushing-air sound rises and
  falls in gusts (quieter indoors), faint streaks blow past, sand streaks in a sandstorm, and torn leaves above 22 m/s.
- [ ] Tornado funnel (`/awm hazard tornado`): it lowers out of the cloud as a thin rope over about 12 seconds and
  widens as it comes down; the debris cloud and flying dirt start only once it touches the ground. The core is solid
  (nothing shows through it), wrapped in many small grey and near-black puffs; no large odd puffs.
- [ ] Tornado roar: a deep rushing woosh from the funnel's direction, heard from several hundred blocks, loudest
  close by, dying away as it lifts. Off with the wind sound setting.
- [ ] Severe storms (`/awm hazard severe`) and hurricanes: under the storm, the white vanilla clouds are hidden; they
  come back as the storm passes.
- [ ] 3D clouds (on by default): puffy white cumulus with flat grey bases in fair weather, patchy, drifting slowly east,
  fading into the fog far off; no vanilla clouds. Under `/awm approach thunderstorm 3` a thick dark deck moves in, deeper
  and darker for severe storms (`/awm hazard severe`), with towers here and there; a hurricane's eye is clear. No
  stutter as storms move (tiles rebuild a couple a frame). Sun side lit by day, dark at night, flashes with lightning.
- [ ] Cloud detail LOW, MEDIUM, HIGH, ULTRA: LOW is visibly low-poly and reaches least far; ULTRA lumpiest and furthest.
  Vanilla Clouds: Fast forces LOW; Off shows none.
- [ ] 3D clouds off (client setting, or `allowCustomClouds = false` on the server, which takes effect on reload or
  rejoin): vanilla clouds, which fade out (not pop) going under a severe storm and fade back in after.
- [ ] With Complementary (Reimagined, Unbound) in a severe storm: storm rain easy to see; murk fog closes in (fog
  layers), no visible shell edges or seams, and it clears smoothly after. Same in a fog bank, sandstorm and blizzard.
- [ ] Shader compatibility: with Iris and a pack, the mod's clouds show unless "3D Clouds with Shader Packs" is off;
  "Storm Darkness" at 1 makes a severe storm nearly night-dark (sky, clouds and light); "Storms Count as Thunder" makes
  packs show thunder weather; each brightness slider changes its effect.
- [ ] With Sodium (no shader pack): the mod's 3D clouds show, not Sodium's; with 3D clouds off, Sodium's clouds fade
  out under a severe storm and back in after.
- [ ] Storm Protector (Operator Utilities tab, needs op and creative): using it opens a radius slider (8 to 256); a
  tornado (`/awm hazard tornado_3`) or hurricane crossing the area breaks nothing in it and does not push or lift you
  or mobs there, but does outside it. Survival players cannot break or use it.
- [ ] `/awm forecast` as a non-op player: works (the rest of `/awm` does not), describes now and up to three storms in
  plain words, no storm numbers.
- [ ] Heat wave (`/awm hazard heat` in a desert by day): after a minute in the sun a message and Slowness, after two
  Weakness too, after three heatstroke (Nausea, a heart now and then). Shade, water or a water bottle cool you down.
  Wheat on dry farmland stops growing; on wet farmland it grows at about half speed.
- [ ] Blizzard (`/awm hazard blizzard` in a snowy biome): you freeze like in powder snow unless in leather or near a
  fire.
- [ ] Rainbow: after a storm's rain clears with the sun low (morning or evening), about half the time a faint
  rainbow opposite the sun, red outside, fading in and gone in a couple of minutes; hidden by nearby hills. Off with
  the Rainbows setting.
- [ ] Waterspouts: a thunderstorm over the ocean (`/awm approach thunderstorm 2` while at sea) now and then puts down a
  thin pale funnel to the water with white spray round its foot; never over land.
- [ ] Dust devils: in a desert, badlands or savanna on a clear day, thin sand-coloured whirls wander about (more in a
  heat wave), kicking up sand, gone after half a minute or so.

## Warning sirens (moved from RadioCraft, added 2026-11-13)

- [ ] Omnidirectional and Horn Array Sirens (redstone tab, after the tornado siren): two blocks tall, on a fence post or
  log; on while either half has redstone. Their item icons show the whole siren.
- [ ] Pole-mounted versions: a cabinet, nine blocks of pole and the head, placed at once; right click the cabinet for the
  key switch (its lamp lights); redstone at the cabinet; breaking any part takes it all down and drops one item.
- [ ] A Siren Controller with redstone sounds them over data wire touching any part of them (the pole too).
- [ ] Recipes: omni `INI` / `NXN` / `IBI` (iron, note blocks, redstone block, iron block); horn `IHI` / `INI` / `IXI`
  (iron, hopper, note block, redstone); pole versions: the head and six iron bars, shapeless.
- [ ] Still silent: their sounds are to come. Later, RadioCraft: a commercial radio wired to one plays through it.

## Storms keep apart, one rain curtain, rain about 8 % (added 2026-11-13)

From the 2026-11-13 playtest: a dozen storms stacked on one spot, each with its own rain curtain. Scattered storms no
longer form where an older storm already is (`StormGenerator.SPACING`); at most a few touch at their edges. Where they
touch, each storm's rain wall fades out inside the other's rain (`RainWall.outsideOthers`), so a group shows one curtain
round it. Rain is down to about 8 % of the time (maintainer: 20 % was a little high), severe weather with it.
- [ ] On a rainy day (`/awm day rainy`), look round: storms spread out rather than piled up; no spot under a stack.
- [ ] Two storms touching: one outer curtain round both, no sheets crossing inside.
- [ ] Rain feels about right: roughly 1 day in 12 of standing still; more on rainy days, little on dry ones.
- [ ] Server TPS on a rainy day (`/awm status`): the spacing check adds a few ms a top-up.

## No fog sheet; faint layered rain; first drops; stratus on dry days (added 2026-11-13)

From the maintainer: no grey fog sheet ahead of storms; a faint rain curtain with depth; the rain coming on drop by
drop as a storm nears; flat stratus on dry days.
- [ ] A storm in the distance: no grey haze sheet along its foot. Its rain is faint streaks in five sheets one behind
  another, each bowing in and out and leaning a little (`RainWall.WOBBLE` 7, `LEAN` 6), so moving sideways the sheets
  slide past each other and nothing looks flat. Too faint or too strong? (`RainWall.LAYER_ALPHA` 0.24 front to 0.12 back.)
- [ ] Far merged storm masses: two faint sheets, no haze.
- [ ] Walk toward a storm (or let one come): from about 140 blocks outside its rain a drop now and then falls round you
  and splashes, more and more as it nears, until its rain starts (`RainOnset`; off: client config `rainOnset`).
  Not in snow or desert. Drops start above the highest block in each column, so none should appear under a roof.
- [ ] Dry days (`/awm day dry`, then try a few days): about 4 in 10 clear, 3.5 in 10 a thin broken veil of high stratus,
  the rest a high grey overcast, flat, over the cumulus, drifting with the wind, fading toward the horizon and away
  under rain, tinted with the sky at dusk and night (client config `stratus`).

## Warning sirens: the wail, wind-up, siren types, klaxon, echoes (added 2026-11-13)

From the maintainer's recording (tools/sounds/siren_loop.ogg, made into the game's sounds by tools/siren_audio.py).
- [ ] Power an omni siren with a lever: it winds up over about 7 s from a very low, quiet moan to a full wail, and
  holds; cut the power and it coasts down over about 15 s. A weaker signal winds it up only part way (signal 7:
  about half speed, lower and quieter). A circuit that raises and lowers the signal makes it rise and fall.
- [ ] Sneak and right click any part of any siren (head or pole): Siren: Air raid / Tornado / Klaxon. Tornado is a
  little lower than air raid. Klaxon: brrt-brrt-brrt at once, no wind-up, louder on a stronger signal (the klaxon
  sound is a synthesised stand-in: sounds/klaxon.ogg, replace with a recording).
- [ ] The bottom of a wind-up: no jump or click where the sound passes from the octave-down recording to the normal
  one (around a third of the way up).
- [ ] The horn sounds harsher (more distorted) than the omni.
- [ ] Heard far off: 100, 250, 400 blocks; nothing past about 480. Far away, echoes trail it from either side.
- [ ] The pole's key switch sounds it at full strength; a Siren Controller on its data wire at its signal.
- [ ] Several sirens at once: no sounds dropping out.

## Dry days, drip sounds, hurricane rain and creaking wood (added 2026-11-13)

- [ ] Dry day (`/awm day dry`): mostly clear; now and then a lone shower or ordinary thunderstorm, never anything
  strong, no fronts setting off storms, no hurricanes. Storms from the day before die out within about 2 minutes
  (a twelfth of a day) of a dry day starting.
- [ ] First drops before a storm: each lone drop now drips audibly where it lands; closer in, a soft patter. Quieter
  indoors. Fades into the storm's own rain.
- [ ] Hurricane, category 1-2: rain looks like a later category's (full far curtain, heavier dark bands, three sheets
  a column, flatter slant), thicker murk (46 / 38 blocks). Much louder rain, louder still in higher categories and
  stronger wind; under a roof a drumming on it.
- [ ] In a gale (17 m/s and up) wooden blocks nearby creak now and then, more often as it blows harder. Placeholder
  sound for now (vanilla door and chest pitched down).

## Slanted rain stops at roofs (added 2026-11-13)

From the playtest: sideways hurricane rain came in through ceilings and stair roofs. Each rain column is one slanted
strip; it used to run down to its own column's ground, so its foot hung under the neighbouring roof downwind. It now
stops where its slant first runs under a roof, stair or hill (StormRain.slantedBottom).
- [ ] In a hurricane, under a roof (plank, slab, stair roof), open sides downwind and upwind: no rain inside, rain
  still falls right up to the eaves outside.
- [ ] No gaps or flicker in the rain outside in open country; frame rate unchanged.

## Hurricanes toned down; a wind ladder for every block; no sunset through storms (added 2026-11-13)

From the playtest: hurricanes too harsh (a category 2 is mostly nonstop rain), fences went before glass, logs before
planks, everything broke too fast, and at dusk the sunset showed past the storm.
- [ ] Category 1-2: steady heavy rain, modest slant (about 25 and 33 degrees), fog at 75 / 62 blocks, loud but not
  a roar, no extra rain sheet; only leaves and plants torn off. Category 3 clearly worse; 4-5 dramatic, a deeper
  stacked layer (one in a 4, two in a 5).
- [ ] Wind ladder (m/s; chance a pick does it): foliage 25 (0.5), glass 50 (0.35), loose bits 54 (0.3), scour 58,
  fences/gates/trapdoors/ladders 62 (0.12), planks/wooden stairs and slabs/wool 70 (0.06), logs 80 (0.015); sirens and
  RadioCraft's mounted antennas 70 (0.03); coax only in a category 5 or EF5 (0.04). Every untagged block is placed by
  what it is made of: stone, concrete, obsidian, metal, earth never; chests never smashed. Windows on walls under
  eaves now get picked, so glass goes before fences. EF4/5 cores level the rest but leave obsidian and metal blocks.
- [ ] Under a hurricane or severe storm at dawn and dusk: no sunset glow, the sky stays storm grey, the fog does not
  turn orange.

## Storms that swell, and grow at sea (added 2026-11-14)

- [ ] Watch a storm on RadioCraft's WXSAT or the forecast over a few hours: it grows and shrinks a little, and its radar outline slowly changes shape.
- [ ] A storm that crosses from land to sea grows over half a day or so (and gets a little stronger); one over a desert shrinks. `stormsFeelTerrain` off: neither.

## Sirens: heard far, from the server; new sounds (added 2026-11-14)

- [ ] Wind a siren up and walk (or fly) away: it fades out smoothly with distance (up to 480 blocks), never cutting out where its chunk unloads. Come back while it is winding up or down: it is where the server has it, not starting over.
- [ ] The air raid and tornado wails sound grittier, with more echo (three echoes now, stronger near by), waver very slightly in pitch, and top out about 15% higher (the tornado still lower than the air raid); they start as low as before.
- [ ] The klaxon is lower (about 220 Hz) and each brrt longer (0.55 s).
- [ ] No pop every few seconds on the horn siren (its loop clicked; now crossfaded). Note any pop that is left and when.
- [ ] Break a sounding siren: it stops for everyone at once.
- [ ] Subtitles on (Accessibility): a sounding siren keeps its subtitle up the whole time it can be heard, with the arrow pointing at the siren (not at its echoes): "Air raid siren winds up / wails / winds down", the same for the tornado siren (and the older tornado siren instrument), "Klaxon blares". It goes when the siren is out of earshot or silent.

