# Villager Workers

NeoForge mod for Minecraft 1.21.1. You rename a villager with a name tag and hire them as a worker. Workers stay villagers: they can die, heal by eating food, and breed. A baby is a normal villager.

Hiring, ownership, the worker screen, chat commands, rest tracking, lumberjack, miner, and the Create schematic builder are implemented. See [docs/DESIGN.md](docs/DESIGN.md) and [docs/MILESTONES.md](docs/MILESTONES.md).

The mod is aimed at a pack that also runs Create 6.0.x and Create Aeronautics. Create is an optional dependency: the mod loads without it, and the builder starts only when Create is installed. Workers must not break Create or Aeronautics blocks unless that worker's whitelist names the block.

## Requirements

- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.233 or newer. This build is compiled with NeoForge 21.1.256.

## Build

```shell
./gradlew build
```

The mod jar is written to `build/libs/villager_workers-0.4.8.jar`.

A development client:

```shell
./gradlew runClient
```

## Layout

Loader-specific code lives in `com.vucelich.villagerworkers.neoforge`. Shared rules and saved data live in `com.vucelich.villagerworkers.common`, which must not import NeoForge, Forge, or Fabric. Another loader can be added later without rewriting the job rules.

## License

[MIT](LICENSE). Copyright (c) 2026 Joshua Vucelich.

The Gradle build files started from the NeoForge 1.21.1 ModDevGradle MDK, which is also MIT licensed.
