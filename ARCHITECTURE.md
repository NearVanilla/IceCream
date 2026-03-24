## ARCHITECTURE.md

IceCream is built as a modular plugin, meaning that each module is an independently togglable feature. All modules are
compiled together and registered according to the configuration when the plugin starts up.

## Module Structure

Note: An example module can be found within the `example` package under the `modules` package.

By default, all modules should implement the `Module` interface, which contains the methods that must be implemented
when creating a module within the plugin. Modules are also required to be included within the `onEnable` method of the
main class, `IceCream.java`.

## Data Persistence

All player state that is required to survive across sessions should be stored with Paper's Persistent Data Container
(PDC), within the `Player` class. Each module should declare its own `NamespacedKey` objects to facilitate storing
data. Common types of data stored within PDC include `BOOLEAN`, `BYTE_ARRAY`, `DOUBLE`/`FLOAT` and `STRING`. It should
be noted that PDC is also not exclusive to `Player`.

## Optional Integrations

Some modules, both new and pre-existing, may require integrations. Currently, the plugin soft-depends on `Dynmap`,
`DiscordSRV` and `CarbonChat` to provide full functionality. In cases where integrations are required, they should
follow this pattern:
- Lookup the plugin via Bukkit's `getPluginManager().getPlugin("NAME")` method.
- Use `instanceof` to validate before casting to the API type.
- If absent, log a warning and return, the parent module should continue without integration.
- Expose a `cleanup()` method called from the Module's `unregister()` method.