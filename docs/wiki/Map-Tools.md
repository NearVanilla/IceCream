# Map Tools

Players with the manage permission can draw named claims on the [squaremap](https://github.com/jpenilla/squaremap) web map and see them in game. Claims are visual only and do not protect blocks.

Requires squaremap. Permissions: `icecream.modules.maptools.manage` for everything, `icecream.modules.maptools.tp` for `/maptools tp` and the `[TP]` buttons.

## Quick start

1. `/maptools create claim rect`
2. Left click one corner, shift + left click the opposite corner.
3. `/maptools save` and fill in the form.

## Modes

Only left click is used. Clicks never use items or break blocks. `/maptools stop` leaves any mode.

| Command | Left click | Shift + left click |
| --- | --- | --- |
| `/maptools editmode` | Edit the claim or marker you click | |
| `/maptools create claim rect` | Corner 1 | Corner 2 |
| `/maptools create claim polygon` | Add a point | Undo the last point |
| `/maptools create claim circle` | Center | Radius |
| `/maptools create marker` | Spot | |

Save a new shape with `/maptools save`, which opens a form.

### Editing

In `/maptools editmode`, left click a claim (or use Edit shape in its form), then `/maptools save` to save and go back to edit mode. Stopping or switching claims with unsaved changes asks whether to save or discard them. Only one person can edit a claim at a time. Left click more than 8 blocks outside the claim, or at the sky, to stop editing it. With a corner picked up, clicking the sky lets go of it without moving it.

| Shape | Left click | Shift + left click |
| --- | --- | --- |
| Rectangle | Pick up a corner, then click where it goes | |
| Polygon | Pick up a point, then click where it goes | On a point: remove it. Elsewhere: add a point to the nearest line |
| Circle | Pick up the center, then click where it goes | Radius |
| Marker | Pick up the marker, then click where it goes | |

## Commands

Each command names the kind after the action: `claim` or `marker`. Tab completion only suggests ids of that kind.

| Command | Description |
| --- | --- |
| `/maptools list claims [search]` | List claims (the first 10 without a search), or only those whose id, label or owners match. Click `[TP]` to teleport. Also `list markers`. |
| `/maptools manage claim <id>` | Form to change details, edit the shape or delete. Also `manage marker <id>`, or `/maptools manage` for what you are editing. |
| `/maptools tp claim <id>` | Teleport to the middle of a claim. Also `tp marker <id>`. |
| `/maptools set claim <id> <field> [value]` | Change `id`, `label`, `color`, `owners` (comma separated), `description` or `opacity` (0 to 100). Leave out the value to clear it. |
| `/maptools set claim <id> rect <x1> <z1> <x2> <z2>` | Replace the shape with a rectangle. Also `circle <x> <z> <radius>` and `points <x z; x z; x z>`. |
| `/maptools set marker <id> <field> [value]` | Change `id`, `label`, `color`, `owners`, `description` or `icon`, or `move <x> <z>`. |
| `/maptools delete claim <id>` | Delete a claim. Players confirm in a dialog. The shape is printed so it can be recreated. Also `delete marker <id>`. |
| `/maptools create claim rect <world> <x1> <z1> <x2> <z2> <id> <color> <label>` | Create a rectangle from coordinates. |

Ids use `a-z`, `0-9`, `_` and `-`, up to 32 characters, and are unique across claims and markers. Colors are a name like `gold` or a hex code like `#ff8800`. `list`, `set`, `delete` and `create claim rect <world> ...` also work from the console. The world is its key, like `minecraft:overworld`.

## Markers

Markers are single spots with an icon and the same popup as claims, on their own map layer. In game they show as a lodestone.

Icons are the PNG files in `plugins/IceCream/map-icons`, named after the file (`shop.png` is `shop`) and shown at their own size. The folder starts with `pin`, `home`, `shop`, `flag`, `star`, `portal` and `tree`; add, replace or remove files freely. New icons are loaded on restart. A marker without an icon shows as a dot in its color.

Pick an icon with Pick icon in the form. In game each icon is shown as a Minecraft item: the one set under `marker-icon-items`, else the item with the icon's name (`chest.png` is a chest), else paper.

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
