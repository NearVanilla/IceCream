# Mini Blocks

Mini blocks are player heads textured like blocks. There are 214, listed in [wandering_trades.json](../../src/main/resources/data/wandering_trades.json), including every mini block from the Vanilla Tweaks Mini Blocks datapack.

| Source | Cost | Result |
| --- | --- | --- |
| Stonecutter | 1 base block | 8 mini blocks |
| Wandering trader | 1 emerald + 1 base block | 8 mini blocks, once per trader |

Some blocks have more than one mini block, such as lit and unlit redstone lamps and copper bulbs. The stonecutter shows all of them. Mini blocks of the same kind stack, whether they come from the stonecutter or a trader.

## Config

| Key | Default | Description |
| --- | --- | --- |
| `modules.wanderingtrades.enabled` | `true` | Enable the module. |
| `modules.wanderingtrades.stonecutter` | `true` | Add the stonecutter recipes. |
| `modules.wanderingtrades.min-heads` | `5` | Fewest mini blocks a trader offers. |
| `modules.wanderingtrades.max-heads` | `10` | Most mini blocks a trader offers. |
