# Coal Ore Plugin for Hytale

A Hytale plugin that adds coal ore with natural world generation.

## Features

- **Natural Generation** - Coal ore spawns automatically in newly generated chunks
- **Balanced Rarity** - 60% of chunks contain ore, with 2-3 small veins each
- **Depth-based Distribution** - Ore spawns between Y=10-60, more common at lower depths
- **Multiple Rock Types** - Supports stone, sandstone, basalt, marble, granite, and more
- **Creative Commands** - Manual spawning tools for builders and testing

## Installation

1. Download `CoalOre-2.0.0.jar` from the [Releases](https://github.com/Jordansbored/coal-ore-hytale/releases) page
2. Place the JAR in your Hytale `UserData/Mods/` folder
3. Launch Hytale and explore new terrain

## Generation Settings

| Setting | Value |
|---------|-------|
# Coal Ore Plugin for Hytale

Coal Ore is a small Hytale server mod that generates veins of coal ore during world generation and via commands. The project focuses on performant generation: heavy work is deferred off chunk pre-load hooks, placements are batched per chunk, and replaceable block checks use a Set for fast lookup.

**Repository**: https://github.com/Jordansbored/coal-ore-hytale

## Features

- Natural generation of coal veins during chunk generation
- Command-driven vein spawning for testing and editing
- Performance-minded: deferred generation, batched placements, and fast lookups
- Configurable generation parameters in source code

## Quick Install

1. Build the plugin:

```bash
./gradlew build -x test
```

2. Copy the produced JAR into your Hytale `UserData/Mods/` folder:

```
build/libs/CoalOre-<version>.jar -> ~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/UserData/Mods/
```

3. Back up any previous versions from the Mods folder before copying the new JAR.

## Generation Settings (summary)

These are approximate defaults used in the plugin source. See `src/main/java/com/jordansbored/coalore/CoalOrePlugin.java` for exact parameters.

- Y range: ~10–60
- Chunk spawn chance: ~60%
- Veins per chunk: typically small (2–3)
- Vein size: small clusters (3–8 blocks typical)

## Commands

All commands require Creative mode.

| Command | Description |
|---------|-------------|
| `/coalore spawn [size]` | Spawn a vein at your feet (size 1-20, default 8) |
| `/coalore generate [radius] [count]` | Generate multiple veins in an area |
| `/coalore fill [radius]` | Fill underground with veins in a grid pattern |

Alias: `/co`

## Development notes

- Project uses Gradle. Use the included wrapper: `./gradlew`.
- Main code: `src/main/java/com/jordansbored/coalore/CoalOrePlugin.java`.
- Version: see `gradle.properties` (`version` property).

## Release notes — v2.0.5

- Deferred natural generation to avoid blocking chunk pre-load hooks
- Batched placements for command path to reduce repeated chunk lookups
- Replaced linear block id scans with `Set.contains(...)` for O(1) checks
- Promoted runtime instrumentation to INFO for easier observation in logs

## Creating a GitHub release (local instructions)

1. Ensure your local changes are committed and pushed.
2. Tag the repo using the version in `gradle.properties`:

```bash
git tag -a v2.0.5 -m "Release v2.0.5"
git push origin v2.0.5
```

3. Create a GitHub release (recommended using GitHub CLI `gh`):

```bash
gh release create v2.0.5 build/libs/CoalOre-2.0.5.jar -t "CoalOre v2.0.5" -n "Performance improvements: deferral and batching"
```

If you don't have `gh` installed, you can create the release through the GitHub web UI or install `gh` and authenticate with `gh auth login`.

## License

MIT — add a `LICENSE` file if you want this repository to include a formal license text.

---

If you'd like, I can commit this README, push it to GitHub, tag `v2.0.5`, and attempt to create the release (using `gh` if available). If you prefer a different release version or repo URL, tell me which one to use.
