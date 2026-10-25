# Repository Guidelines

Another Weather Mod: localized weather for NeoForge 1.21.1, MIT licensed. Companion to RadioCraft, but it must stand
on its own. The design and the phase plan are in [doc/Design.md](doc/Design.md).

## Layout
- `api/`: the public API. Javadoc everything; changes follow semver.
- `sim/`: the storm model. Pure functions of birth parameters and game time; no world access except through
  `Climate`. Unit-testable without the game.
- `world/`: glue to a running server (per-level state, saved data, player sync, chunk effects, wind damage).
- `mixin/`: as few and as small as possible.

## Rules
- The server-only promise: a vanilla client must be able to join. No synced registry entries (blocks, items, particle
  types, entity types) without a separate client-side feature flag; network payloads must be optional.
- Only MIT code may be copied in, with its notice recorded in `THIRD_PARTY.md`. MPL-2.0 files keep their license in
  their own files. Never copy GPL, LGPL (unless kept as a separate component), ARR or PolyForm code.
- Java 21, tabs, `-Xlint:all` clean.

## Testing
- `./gradlew test` for the model, `./gradlew runGameTestServer` for in-world behaviour (storms are level-wide: give
  each test that adds one a batch of its own and clear the storms).
- Anything that needs a human (rendering, sound, feel) goes in [doc/NeedsTesting.md](doc/NeedsTesting.md).
