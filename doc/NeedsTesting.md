# Needs testing in game

Things the automated tests cannot check. Tick them off in a playtest.

## Storm scale, tornadoes, hurricane categories, fog, heat and cold (added 2026-10-25)

- [ ] Rain gets greyer, darker and heavier up the scale: `/awm spawn thunderstorm`, `strong_thunderstorm`,
  `severe_thunderstorm`, `supercell`. Thick dark bands mixed in, more of them and more distant rain the worse it is.
  Showers keep vanilla rain.
- [ ] `/awm hazard tornado_3` (try `tornado_0` and `tornado_5` too): a dark funnel 120 blocks upwind, thin at the
  ground and flaring into the cloud, spinning and snaking, a dust skirt and dirt and leaves whirling at its base. It
  comes your way; where it passes, leaves, crops, fences, glass and logs go, grass scours. Standing in it lifts you
  (a full set of gold armor keeps you down); mobs and items are lifted and flung. EF5 is far wider than EF0.
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
