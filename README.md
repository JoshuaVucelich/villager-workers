# Villager Workers

NeoForge mod for Minecraft 1.21.1. You rename a villager with a name tag and hire them as a worker. Workers stay villagers: they can die, heal by eating food, and breed. A baby is a normal villager.

Milestone 1 is implemented: hiring, ownership, the worker screen, chat commands, and rest tracking. Lumberjack, miner, and builder do not break or place blocks yet. See [docs/DESIGN.md](docs/DESIGN.md) and [docs/MILESTONES.md](docs/MILESTONES.md).

The mod is aimed at a pack that also runs Create 6.0.x and Create Aeronautics. Create is not a compile dependency. The builder job will treat it as optional. Workers must not break Create or Aeronautics blocks unless that worker's whitelist names the block.

## Requirements

- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.256, built with ModDevGradle

## Build

```shell
./gradlew build
```

The mod jar is written to `build/libs/villager_workers-0.1.0.jar`.

A development client:

```shell
./gradlew runClient
```

## Layout

Loader-specific code lives in `com.vucelich.villagerworkers.neoforge`. Shared rules and saved data live in `com.vucelich.villagerworkers.common`, which must not import NeoForge, Forge, or Fabric. Another loader can be added later without rewriting the job rules.

## License

[MIT](LICENSE). Copyright (c) 2026 Joshua Vucelich.

The Gradle build files started from the NeoForge 1.21.1 ModDevGradle MDK, which is also MIT licensed.
