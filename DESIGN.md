# Design

This document describes the design conventions and patterns used across IceCream modules. Follow these when adding or modifying modules.

## Module Design

### One Directory Per Module

Each module lives in `modules/<name>/` and contains:
- A main `<Name>Module.java` implementing `Module`
- Optionally a `commands/` subdirectory
- Optionally an `events/` subdirectory
- Optionally an `integrations/` subdirectory (only for complex cross-plugin work)

### Configuration-Gated Enablement

`shouldEnable()` must read from `IceCream.config` and **default to `false`** if the key is absent. Config keys use lowercase module names under `modules.<name>.enabled`.

### Error Isolation

`register()` wraps its body in a `try/catch (Exception e)`. A broken module logs a `SEVERE` message and returns early; it must not crash the whole plugin. Other modules are unaffected.

### Registering a New Module

1. Create the module class and subdirectories.
2. Declare a `private final MyModule myModule = new MyModule();` field in `IceCream.java`.
3. Call `myModule.register();` in `onEnable()`.
4. If the module holds persistent state across sessions, implement `unregister()` and call it in `onDisable()`.

## Command Design

### Annotation Style

```java
@Command("commandname|alias <optionalArg>")
@CommandDescription("One-line description.")
@Permission("icecream.modules.<name>.<verb>")
```

Permission nodes always start with `icecream.modules.`.

### Player-Only Commands

Cast `commandSourceStack.getSender()` and check with `instanceof Player`. Send an error component if the sender is not a player; do not throw.

### Suggestions

Use `@Suggestions("key")` on the command method and define a separate `@Suggestions("key")` provider method on the same class. The provider receives a `CommandContext` and returns a list of string suggestions.

### Message Formatting

All user-facing strings go through MiniMessage. Never concatenate raw strings into chat output. Deserialise with `MiniMessage.miniMessage().deserialize(string)` and pass the result as a `Component`. Placeholder values use `Placeholder.component("key", component)`.

## Event Design

### Priority Choice

- Use `EventPriority.HIGH` when the module needs to override game behaviour (e.g., cancelling spawns, replacing entities).
- Use `EventPriority.MONITOR` for read-only side effects where the final outcome is already decided.
- Default to `EventPriority.NORMAL` otherwise.

### Do Not Modify in MONITOR

`MONITOR` priority is for observation only. Never cancel an event or change its state in a `MONITOR` handler.

## Persistent State

### Use PDC for Per-Player State

All per-player state that must survive log-outs uses the Persistent Data Container on the `Player` entity. Module-level static `NamespacedKey` constants are declared in the module class:

```java
public static final NamespacedKey MY_KEY = new NamespacedKey(IceCream.instance, "my.key");
```

Keys scoped to a module's own namespace (not the plugin instance) use `new NamespacedKey("mymodule", "key")`.

### Always Check Before Reading

PDC `get()` returns `null` if the key is absent. Always null-check or use `has()` first.

### Serialise Complex State

Inventories and locations too complex for a single PDC type are serialised manually (see `StaffModeUtils`): inventories as `BYTE_ARRAY` via `DataOutputStream`, location components as individual `DOUBLE`/`FLOAT` keys.

## Optional Integration Design

### Detection Pattern

```java
Plugin plugin = Bukkit.getPluginManager().getPlugin("PluginName");
if (!(plugin instanceof PluginAPI api)) {
    IceCream.logger.warning("PluginName not found, integration disabled.");
    return;
}
// use api
```

Never assume an optional dependency is present. Always use `instanceof` before casting.

### Cleanup

Integrations that subscribe to third-party events or modify external state must expose a `cleanup()` method. The parent module calls `cleanup()` from its own `unregister()`.

### Failure Scope

A failed optional integration must not prevent the parent module from enabling. Initialise each integration in its own `try/catch` block.

## Vanish Module Specifics

Vanish is the most complex module and serves as the reference for integration-heavy design:

- **Fake join/quit messages** are broadcast using `MiniMessage` with a `<player>` placeholder substituted from config templates.
- **BossBar** is used to give a persistent on-screen reminder; it is attached and detached using `Player.showBossBar()` / `Player.hideBossBar()`.
- **Proxy communication** uses the `"icecream:vanish"` plugin messaging channel (Velocity-compatible) to synchronise vanish state cross-server.
- **Dynmap visibility** state before vanishing is saved to PDC (`DYNMAP_WAS_HIDDEN_KEY`) so it can be restored on reveal, rather than always making the player visible.
- **CarbonChat** channel filtering uses an allowlist from config (`modules.vanish.carbon-chat.allowed-channels`), not a blocklist, to keep the default safe.

## Wanderful Module Specifics

- Custom wands are defined entirely in `config.yml` (material, name, lore, crafting shape and ingredients). No wand properties are hardcoded.
- Identity is determined by a `NamespacedKey` in the item's PDC, not by material or display name.
- `LURE` enchantment with hidden flags is used solely to produce the visual glint effect.
- The `ArmorStandEditorWrapper` uses reflection to configure a third-party plugin; this is intentional and should only be modified if the target plugin's internals change.

## WanderingTrades Module Specifics

- Trade definitions live in `src/main/resources/data/wandering_trades.json` and are loaded once at startup into a static pool.
- Each head item uses a random UUID profile with a custom `"textures"` `ProfileProperty`; this is the standard way to set custom skull textures without a real player account.
- The number of custom trades added per wandering trader spawn is randomised between `min-heads` and `max-heads` from config.
