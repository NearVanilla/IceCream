# Architecture

## Overview

IceCream is a Paper Minecraft plugin built around a module system. Each module is an independently toggleable feature. The plugin has no runtime module loading; all modules are compiled in and registered at startup, but can be disabled via configuration.

## Entry Point

`IceCream.java` (extends `JavaPlugin`) is the plugin entry point. It initialises a set of static singletons used across the whole codebase:

| Static Field | Type | Purpose |
|---|---|---|
| `IceCream.instance` | `IceCream` | Plugin instance (needed for listener registration, tasks, resources) |
| `IceCream.config` | `FileConfiguration` | Loaded `config.yml` |
| `IceCream.logger` | `Logger` | Plugin logger |
| `IceCream.commandManager` | `PaperCommandManager<CommandSourceStack>` | Cloud command framework manager |
| `IceCream.annotationParser` | `AnnotationParser<CommandSourceStack>` | Parses `@Command`-annotated classes |

`onEnable()` initialises all statics, then calls `.register()` on every module. `onDisable()` only calls `.unregister()` on modules that hold persistent state (currently only `VanishModule`).

## Module System

Every module implements `Module`:

```
Module (interface)
├── shouldEnable()        → reads config, returns false by default
├── isEnabled()           → runtime flag
├── registerCommands()    → calls annotationParser.parse(new SomeCommand())
├── registerEvents()      → calls Bukkit.getPluginManager().registerEvents(...)
└── register()            → checks shouldEnable(), then wires commands + events
```

The standard `register()` implementation:
1. Calls `shouldEnable()`.
2. If enabled, runs `registerCommands()` and `registerEvents()` inside a `try/catch`.
3. Sets an `isEnabled` boolean field.
4. Logs success or failure.

There is no module registry or dynamic dispatch; modules are concrete fields on `IceCream`.

## Commands

Commands use the [Incendo Cloud](https://github.com/incendo/cloud) annotation framework. A command class has one or more methods annotated with `@Command`, `@CommandDescription`, and `@Permission`. Arguments are declared as method parameters annotated with `@Argument`. The class is registered by calling `IceCream.annotationParser.parse(new MyCommand())`.

All commands receive a `CommandSourceStack` and must cast `commandSourceStack.getSender()` to `Player` where a player context is required.

```
modules/<name>/commands/MyCommand.java
    @Command("name|alias <arg>")
    @Permission("icecream.modules.<name>.<verb>")
    void handler(CommandSourceStack stack, @Argument("arg") ArgType arg)
```

Permission nodes follow the pattern `icecream.modules.<modulename>.<verb>`.

## Event Listeners

Listeners implement Bukkit's `Listener` and are registered via `Bukkit.getPluginManager().registerEvents(listener, IceCream.instance)`. Event priority is `EventPriority.HIGH` when the module needs to act before other plugins, `MONITOR` for read-only observation.

## Persistent State

Player state that must survive across sessions is stored in Paper's Persistent Data Container (PDC) on the `Player` entity. Each module declares its own `NamespacedKey` constants. Common types used: `BOOLEAN`, `BYTE_ARRAY` (serialised inventories), `DOUBLE`/`FLOAT` (locations), `STRING`.

## Optional Integrations

The plugin soft-depends on Dynmap, DiscordSRV, and CarbonChat. Each integration follows the same pattern:

1. Look up the plugin via `Bukkit.getPluginManager().getPlugin("Name")`.
2. Check with `instanceof` before casting to the API type.
3. If absent, log a warning and return; the parent module continues without the integration.
4. Expose a `cleanup()` method called from the module's `unregister()`.

## Data Loading

`JsonLoader` (in `libs/`) is a thin static wrapper around a Jackson `ObjectMapper`. It exposes `load(File/InputStream, Class<T>)` and `load(File/InputStream, TypeReference<T>)`. The `WanderingTradesModule` uses it to load head trade definitions from a bundled JSON resource (`data/wandering_trades.json`) at startup.

## Package Layout

```
com.nearvanilla.iceCream
├── IceCream.java
├── libs/
│   └── JsonLoader.java
└── modules/
    ├── Module.java
    ├── desertMobs/
    ├── example/
    ├── isSlimeChunk/
    ├── lightning/
    ├── muteDeaths/
    ├── staffMode/
    ├── vanish/
    │   ├── commands/
    │   ├── events/
    │   └── integrations/   ← Dynmap, DiscordSRV, CarbonChat
    ├── wanderful/
    │   └── commands/
    └── wanderingTrades/
        └── events/
```

## Build & Packaging

Gradle with the Shadow plugin produces a fat JAR. All bundled dependencies are relocated to `com.nearvanilla.icecream.lib.*` to avoid classpath conflicts with other plugins. The Paper API itself is `compileOnly` and is not included in the fat JAR.
