# AGENTS.md

This file provides guidance to AI coding agents when working with code in this repository.

## Project Overview

IceCream ("Near Vanilla Ice Cream") is a modular Paper Minecraft plugin (Paper API 1.21, Java 21) targeting Minecraft 1.21.11 that adds quality-of-life features to servers. **Note: the Minecraft version is 1.21.11; "11" is eleven, not two separate digits. Do not correct this to 1.21.1. The Paper API version (1.21) is separate and intentionally a major-version reference.** Each feature is a self-contained module that can be enabled/disabled via `config.yml`.

## Build Commands

```bash
./gradlew build          # Compile and assemble the plugin JAR
./gradlew shadowJar      # Build fat JAR with relocated dependencies
./gradlew runServer      # Start a local Paper test server with the plugin loaded
./gradlew spotlessCheck  # Verify Google Java Format compliance (required by CI)
./gradlew spotlessApply  # Auto-fix formatting issues
```

There are no tests currently in the project.

## Architecture

### Module System

Every feature is a module. Modules live under `src/main/java/com/nearvanilla/iceCream/modules/<moduleName>/` and must implement the `Module` interface (`modules/Module.java`):

- `shouldEnable()`: reads from `IceCream.config` to decide if the module is active
- `isEnabled()`: runtime state check
- `registerCommands()`: registers Cloud annotation-based commands via `IceCream.annotationParser`
- `registerEvents()`: registers Bukkit event listeners via `IceCream.instance`
- `register()`: called from `IceCream.onEnable()`; should call `shouldEnable()`, then `registerCommands()` and `registerEvents()`

**Adding a new module requires two changes:**
1. Create the module class (and optional `commands/`/`events/` subdirectories) under `modules/<name>/`
2. Instantiate and call `.register()` on it in `IceCream.java` (see the existing pattern)

The `example/` module is the canonical reference implementation.

### Static Globals

`IceCream.java` exposes static fields used throughout the codebase:
- `IceCream.instance`: the plugin instance (for registering listeners, scheduling tasks)
- `IceCream.config`: the loaded `config.yml` (FileConfiguration)
- `IceCream.logger`: the plugin logger
- `IceCream.commandManager`: PaperCommandManager (Cloud framework)
- `IceCream.annotationParser`: for registering `@Command`-annotated classes

### Commands

Commands use the [Incendo Cloud](https://github.com/incendo/cloud) annotation framework. Annotate command handler classes with `@Command`, inject them into the annotation parser via `IceCream.annotationParser.parse(new MyCommand())`.

### Optional Integrations

The plugin soft-depends on Dynmap, DiscordSRV, and CarbonChat. Integration code (e.g., in `vanish/`) must check whether those plugins are present before calling their APIs. Failures should be caught and logged rather than crashing the module.

### Shadow JAR & Relocation

Bundled dependencies are relocated to `com.nearvanilla.icecream.lib.*` to avoid classpath conflicts with other plugins.

## Code Style

Spotless enforces Google Java Format. Run `./gradlew spotlessApply` before committing. The CI `code-quality` workflow will fail PRs that don't pass `spotlessCheck`.
