# CoordsMod

A client-side Fabric mod for **Minecraft 26.2**. It saves coordinates and prints
them to chat. Nothing is rendered on screen — no HUD, no overlay, no beacons.

## Commands

| Command | What it does |
| --- | --- |
| `/cs` | Save your current position with an auto name (`ow-1`, `nether-2`, …) |
| `/cs <name>` | Save your current position under a name (re-using a name overwrites it) |
| `/cl` | List coords in **whatever dimension you are currently in** |
| `/cl ow` / `/cl overworld` | List Overworld coords |
| `/cl nether` / `/cl n` | List Nether coords |
| `/cl end` / `/cl e` | List End coords |
| `/cl all` | List every dimension |
| `/cdel <name>` | Delete a coord from the current dimension |
| `/sc <player>` | Whisper your current position to one player |
| `/sc <player> <name>` | Whisper a saved coord to one player |
| `/scall` | Send your current position to public chat |
| `/scall <name>` | Send a saved coord to public chat |
| `/cadd <x> <y> <z> "<name>" <dimension>` | Save a shared coord — this is what the `[+Add]` button runs |
| `/chelp` | List every command; each usage is clickable and drops the command into the chat box |

All of these are **client commands**. They never reach the server, so they work
on any server and in singleplayer.

## How sharing works

Sharing rides on normal chat so it works on vanilla servers. `/sc` and `/scall`
send a marked plain-text line:

```
[CM] base | 120 64 -305 | minecraft:overworld
```

* Players **with** this mod: the line is swallowed and re-printed as a tidy
  message ending in a green **`[+Add]`** button. Clicking it saves the coord.
  If they already have a coord with that name, it is saved as `base-2` rather
  than overwriting theirs.
* Players **without** this mod: they just see the readable text above.

`/sc` whispers using `/msg`. If your server uses `/w` or `/tell` instead, change
`WHISPER_COMMAND` at the top of `ShareHandler.java`.

## Storage

Coords are stored per world/server so saves never mix:

```
.minecraft/config/coordsmod/<server-address>.json
.minecraft/config/coordsmod/sp_<world-name>.json
```

## Building

Requires **JDK 25** (Minecraft 26.2 targets Java 25).

```powershell
.\gradlew build
```

The mod jar lands in `build/libs/coordsmod-1.0.0.jar`. Drop it in your `mods`
folder along with [Fabric API](https://modrinth.com/mod/fabric-api).

To test in a dev client:

```powershell
.\gradlew runClient
```

## Versions

Pinned in `gradle.properties`, matching the official Fabric example mod for 26.2:

* Minecraft `26.2`
* Fabric Loader `0.19.3`
* Fabric Loom `1.17-SNAPSHOT`
* Fabric API `0.157.0+26.2`

Note that 26.1+ is shipped unobfuscated with parameter names, so there is no
Yarn/mappings dependency — the code below uses Mojang names directly.
