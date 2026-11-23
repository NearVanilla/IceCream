# Map Tools

Players with the manage permission can put named claims and markers on the [squaremap](https://github.com/jpenilla/squaremap) web map, from typed coordinates or forms. Claims are visual only and do not protect blocks. To draw claims on a map and in the world, use the NearVanilla Tools mod, which sends these same commands (see [NearVanilla Tools mod](#nearvanilla-tools-mod)).

Requires squaremap. Permissions: `icecream.modules.maptools.manage` for everything, `icecream.modules.maptools.tp` for `/maptools tp` and the `[TP]` buttons.

## Quick start

1. `/maptools create claim rect` opens a form, filled in with where you stand.
2. Type the corners, id and label, and click Create.

Change the corners, center and radius of a claim afterwards by typing new coordinates in `/maptools manage claim <id>`, or with `/maptools set`.

`/maptools create claim rect|circle` and `/maptools create marker` open a form filled in with where you stand. Polygons are created by typing their points: `/maptools create claim polygon "<x z; x z; x z>" <id> <color> <label>`.

## Commands

Each command names the kind after the action: `claim` or `marker`. Tab completion only suggests ids of that kind.

| Command | Description |
| --- | --- |
| `/maptools list claims [search]` | List claims (the first 10 without a search), or only those whose id, label or owners match. Click `[TP]` to teleport. Also `list markers`. |
| `/maptools manage claim <id>` | Form to change details and typed coordinates (center and radius, or corners), or delete. Also `manage marker <id>`, with the marker's spot. |
| `/maptools tp claim <id>` | Teleport to the middle of a claim. Also `tp marker <id>`. |
| `/maptools set claim <id> <field> [value]` | Change `id`, `label`, `color`, `owners` (comma separated), `description`, `opacity` (0 to 100) or `nearby` (`yes` lets others claim close to it). Leave out the value to clear it. |
| `/maptools set claim <id> rect <x1> <z1> <x2> <z2>` | Replace the shape with a rectangle. Also `circle <x> <z> <radius>` and `points <x z; x z; x z>`. |
| `/maptools set claim <id> radius <radius>` | Resize a circle, keeping its center. |
| `/maptools set claim <id> center <x> <z>` | Move a circle, keeping its size. |
| `/maptools set marker <id> <field> [value]` | Change `id`, `label`, `color`, `owners`, `description` or `icon`, or `move <x> <z>`. Use `none` as the icon for a dot. |
| `/maptools delete claim <id>` | Delete a claim. Players confirm in a dialog. The shape is printed so it can be recreated. Also `delete marker <id>`. |
| `/maptools create claim rect <x1> <z1> <x2> <z2> <id> <color> <label>` | Create a rectangle from coordinates. |
| `/maptools create claim circle <x> <z> <radius> <id> <color> <label>` | Create a circle from its center and radius. |
| `/maptools create claim polygon "<x z; x z; x z>" <id> <color> <label>` | Create a polygon from 3 to 200 points, in quotes. |
| `/maptools create marker <x> <z> <id> <icon> <label>` | Place a marker at a block. Use `none` as the icon for a dot in the default claim color. |

Ids use `a-z`, `0-9`, `_` and `-`, up to 32 characters, and are unique across claims and markers. Colors are a name like `gold` or a hex code like `ff8800` (leave out the `#` in commands). Labels, owners and descriptions can contain any characters, including `'`. `list`, `set`, `delete`, `create claim rect|circle|polygon ...` and `create marker ...` also work from the console. Creating or reshaping a claim that overlaps or is within 300 blocks (`nearby-claim-distance` in the config) of other claims asks you to confirm, listing their id, label and owners. Claims whose owners let others claim nearby (`nearby yes`, or the checkbox in the form) only count when they overlap; their map popup says "Others may claim within 300 blocks". Claims and markers created from coordinates go in the overworld. Typed rectangles are at most 3000 blocks wide and long, and typed circles have a radius of at most 3000.

## Markers

Markers are single spots with an icon and the same popup as claims, on their own map layer.

Icons are the PNG files in `plugins/IceCream/map-icons`, named after the file (`shop.png` is `shop`) and shown at their own size. The folder starts with `pin`, `home`, `shop`, `flag`, `star`, `portal` and `tree`; add, replace or remove files freely. New icons are loaded on restart. A marker without an icon shows as a dot in its color.

Pick an icon with Pick icon in the form. In the icon menu each icon is shown as a Minecraft item: the one set under `marker-icon-items`, else the item with the icon's name (`chest.png` is a chest), else paper.

## NearVanilla Tools mod

The NearVanilla Tools client mod draws claims on the map and in the world, and sends the `/maptools` commands to the server over the `nvtools:maptools` plugin channel instead of chat.

Each message is one command without its slash, as UTF-8 text, starting with `maptools `. It runs as the player, exactly as if typed: players without `icecream.modules.maptools.manage` are ignored, and every subcommand checks its own permission as usual. Replies, forms and dialogs appear as for a typed command. Commands sent this way are not seen by plugins that log typed commands, so IceCream logs each one to the console as `[maptools via mod] <player>: /<command>`.

## Configuration

```yaml
maptools:
  enabled: true
  layer-label: 'Claims'
  marker-layer-label: 'Markers'
  marker-icon-items:
    shop: gold_nugget
  claim-color: 'gold'
  claim-fill-opacity: 0.2
```

`claim-color` and `claim-fill-opacity` are the defaults; each claim can set its own in `/maptools manage` or with `/maptools set <id> color` and `opacity`.

## Data

Claims are stored in `plugins/IceCream/map-areas.json`. Hand edits are only loaded on restart and are overwritten by the next change made in game. Each save keeps the previous version as `map-areas.json.bak`. If the file cannot be read, Map Tools does not start and the file is left untouched.
