# Folia

IceCream runs on both Paper and Folia from the same jar. On Folia every module works as on Paper,
with the exceptions below.

## Platform notes

- Folia 26.3 builds are published as ALPHA. Test on a copy of the server first.
- The Locator Bar module stays off on Folia, and logs why at startup. Folia does not run the
  server's waypoint system, so the locator bar cannot work there at all.
- squaremap, DiscordSRV, CarbonChat and LuckPerms all support Folia.

## What happens at shutdown

Folia stops its region threads before plugins are disabled, so some cleanup moves to a later point:

- World Tour: a tour still running at shutdown has its weather, time and keepInventory restored
  when the server next starts. The same happens on Paper after a crash during a tour.
- Spectator: a player still spectating at shutdown leaves spectator mode when they next join. They
  are returned to where they started spectating, with their previous gamemode.
- Map Tools: edit mode outlines are not removed at shutdown. They are never saved with the world,
  so they are gone after the restart.

## Testing locally

`./gradlew runFolia` starts a Folia server with the same plugins as `./gradlew runServer`, in its
own `run-folia` folder.

## Manual checklist

Run this with two players, on both platforms. On Folia, put the players several thousand blocks
apart, so they are in different regions.

1. `/spectator` on and off while the other player watches the tab list and the world. Rejoin while
   spectating.
2. `/strike` the other player.
3. `/restoredeath` on the other player and restore an inventory.
4. World Tour: start, weather, time, glow on the other player, tpall, takeover, end. Then start a
   tour and stop the server; after the restart, weather, time and keepInventory are back to normal.
5. Spectate, stop the server and rejoin: the player is back in their previous gamemode and location.
6. One player sleeps and the night speeds up; the other player clicks `[Wake up]`.
7. Map Tools: edit mode, create and edit a claim while the other player edits a nearby claim,
   save, delete, `/maptools tp`, and the icon menu.
8. Staff mode on and off, the fly toggle, and paging through the death database menu.
