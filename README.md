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
| Y Range | 10-60 |
| Chunk Spawn Chance | 60% |
| Veins per Chunk | 2-3 |
| Vein Size | 3-7 blocks |

## Commands

All commands require Creative mode.

| Command | Description |
|---------|-------------|
| `/coalore spawn [size]` | Spawn a vein at your feet (size 1-20, default 8) |
| `/coalore generate [radius] [count]` | Generate multiple veins in an area |
| `/coalore fill [radius]` | Fill underground with veins in a grid pattern |

Alias: `/co`

## Building from Source

```bash
./gradlew build
```

The JAR will be in `build/libs/`.

## License

MIT
