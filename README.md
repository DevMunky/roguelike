# Roguelike

A roguelike game server in minecraft.

## Overview
This is a multi-module Kotlin project that implements an experimental roguelike server on top of the Minestom Minecraft server. It includes:

- `server`: the Minestom-based game server and CLI/command scaffolding.
- `modelrenderer`: utilities for loading and rendering BlockBench models (and related animation/skeleton types) in Minestom.
- `common`: shared utilities such as logging, serialization helpers, text/terminal helpers, and coroutines utilities.

The current entrypoint lives in `server` and contains a `suspend fun main()` that boots Minestom and demonstrates loading a model entity.

## Prerequisites
- Java Development Kit (JDK) 25
  - Toolchains are configured to compile and run with JDK 25.
  - Recommended vendors: Temurin (Adoptium) or Oracle OpenJDK EA, matching `java.toolchain` settings.
- Git
- Internet access to download dependencies from Maven Central
- Optional (for IDE/run/debug): IntelliJ IDEA 2024.3+ with Kotlin plugin

Verify your Java:

- Windows PowerShell:
  ```powershell
  java -version
  echo $Env:JAVA_HOME
  ```
- macOS/Linux:
  ```bash
  java -version
  echo $JAVA_HOME
  ```
Expected `java -version` should report a Java 25 build.

## Getting started
1. Clone the repository
   ```bash
   git clone https://github.com/DevMunky/roguelike.git
   cd roguelike
   ```
2. (Recommended) Open the project in IntelliJ IDEA
   - File > Open… > select the project root (`build.gradle.kts` and `settings.gradle.kts` are in the root).
   - Let Gradle import finish.
3. Ensure your IDE uses JDK 25 for both Gradle and the Project SDK.

## Build
Use the Gradle wrapper; no local Gradle installation is required.

- Full build of all modules:
  - Windows:
    ```powershell
    .\gradlew.bat clean build
    ```
  - macOS/Linux:
    ```bash
    ./gradlew clean build
    ```

Artifacts will be under each module’s `build/` directory, for example:
- `server/build/libs/`
- `modelrenderer/build/libs/`
- `common/build/libs/`

### Fat/uber JARs (shadow)
This project applies the Gradle Shadow plugin to subprojects. To create fat JARs:

- Build all shadow JARs:
  - Windows:
    ```powershell
    .\gradlew.bat shadowJar
    ```
  - macOS/Linux:
    ```bash
    ./gradlew shadowJar
    ```

- Server-only shadow JAR:
  - Windows:
    ```powershell
    .\gradlew.bat :server:shadowJar
    ```
  - macOS/Linux:
    ```bash
    ./gradlew :server:shadowJar
    ```

Notes:
- The default classifier is typically `-all`. Example: `server-0.0.1-all.jar` in `server/build/libs/`.
- A `Main-Class` manifest may not yet be configured. You can run from an IDE or set an explicit main class when executing. The Kotlin entrypoint is `dev.munky.roguelike.server.MainKt`.

## Run (developer workflow)
The project currently does not configure the Gradle Application plugin. Common approaches:

- Run from IntelliJ IDEA:
  1. Create a new Kotlin/JVM run configuration with main class `dev.munky.roguelike.server.MainKt` (module: `server`).
  2. Run. The server will start Minestom bound to `localhost:25565` in online mode.

- Run from command line (if you add a manifest or specify the main class):
  ```bash
  java -cp server/build/libs/server-0.0.1-all.jar dev.munky.roguelike.server.MainKt
  ```
  Adjust the JAR name/version as needed.

## Testing
Test scaffolding is not finalized yet. This section intentionally leaves space for upcoming details.

- Unit tests: TODO
  - Command to run tests (when present):
    - Windows:
      ```powershell
      .\gradlew.bat test
      ```
    - macOS/Linux:
      ```bash
      ./gradlew test
      ```
- Integration/e2e tests: TODO
  - Planned to spin up a headless Minestom instance and validate basic gameplay loops and entity/model lifecycles.
- Test data and fixtures: TODO

## Configuration
- Networking
  - The server binds to `localhost:25565` by default in `server/src/main/kotlin/dev/munky/roguelike/server/Main.kt` (see `RoguelikeServer.build { init(Auth.Online()); start("localhost", 25565) }`).
  - To expose to other clients on your LAN, adjust the host to `0.0.0.0` and ensure your firewall allows inbound connections.
- Resources
  - Example BlockBench models are located under `server/src/main/resources/` and `modelrenderer/src/main/resources/`.

## Key libraries and how they’re used
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
- GraalVM Polyglot (`org.graalvm.polyglot:*`)
  - Foundation for potential scripting support (e.g., JavaScript). Included but not required to run the basic server demo.

## Project layout
```
roguelike/
├─ build.gradle.kts              # Root Gradle config, toolchains (JDK 25), shared plugins
├─ settings.gradle.kts
├─ dependencies.toml             # Centralized versions & bundles
├─ common/                       # Shared utilities
├─ modelrenderer/                # Model loading/rendering and math/types
└─ server/                       # Minestom server and entrypoint
```

## License
The GitHub License