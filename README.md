# Roguelike

A roguelike game server in minecraft.

## Overview
This is a multi-module Kotlin project that implements an experimental roguelike server on top of the Minestom Minecraft server. It includes:

- `server`: the Minestom-based game server and CLI/command scaffolding.
- `modelrenderer`: utilities for loading and rendering BlockBench models (and related animation/skeleton types) in Minestom.
- `common`: shared utilities such as logging, serialization helpers, text/terminal helpers, and coroutines utilities.

The current entrypoint lives in `server` and contains a `suspend fun main()` that boots Minestom and demonstrates loading a model entity.

## TODO
- [ ] Refactor Dungeon Generation
- [ ] In-game polling for feedback (restricted to only players with `x` amount of playtime).
- [ ] Multi-part dungeon rooms.
- [ ] Flesh out character (lifetime and stats).
- [ ] More modifiers (and AttackCommands)
- [ ] More enemy AI.
- [ ] Refine gameplay loop.
- [ ] Add content.

## Key libraries
- Minestom (`net.minestom:minestom`)
  - Embedded Minecraft server foundation. Used to create instances, spawn entities, and run the game loop.
- Adventure MiniMessage (`net.kyori:adventure-text-minimessage`)
  - Text formatting for chat/messages and server-side UI text components.
- JOML (`org.joml:joml`)
  - Math primitives (vectors, matrices) for geometry, model transforms, and game calculations.
- Kotlinx Serialization (`kotlinx-serialization-*`)
  - Data formats (JSON/ProtoBuf) for model files, configs, and internal data exchange.
- Kotlinx Coroutines (`kotlinx-coroutines-core`)
  - Concurrency primitives. The main server loop and async tasks make use of coroutines.
- SLF4J + Logback (`slf4j-api`, `logback-core`, `logback-classic`)
  - Logging facade/implementation for server and library logs.

## License
The GitHub License