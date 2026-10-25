# Needs testing in game

Things the automated tests cannot check. Tick them off in a playtest.

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
