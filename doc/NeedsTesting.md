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
