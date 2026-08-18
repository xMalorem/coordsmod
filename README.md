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
| `/cundo` | Put back the last coord you deleted |
| `/cren <old> <new>` | Rename a coord, keeping its position |
| `/sc <player>` | Whisper your current position to one player |
| `/sc <player> <name>` | Whisper a saved coord to one player |
| `/scall` | Send your current position to public chat |
| `/scall <name>` | Send a saved coord to public chat |
| `/cworld` | Which storage file this world is using, and how many coords are in it |
| `/cworld list` | Every stored world file |
| `/cworld merge <key>` | Pull coords in from another stored world |
| `/cadd <x> <y> <z> "<name>" <dimension>` | Save a shared coord — this is what the `[+Add]` button runs |
| `/chelp` | List every command; each usage is clickable and drops the command into the chat box |

All of these are **client commands**. They never reach the server, so they work
on any server and in singleplayer.

## What a listing shows

```
[Coords] Overworld (3)
 1. base    120, 64, -305    182m NW    →N 15, -38
 2. mine    -40, 12, 88      1.2km SE   →N -5, 11
```

* **Distance and bearing.** With nothing drawn on screen these numbers are your
  only sense of direction, so entries are sorted nearest-first. Distance is
  horizontal only, and is shown only when you are listing the dimension you are
  actually standing in — a distance across dimensions would be meaningless.
* **Portal ratio.** Overworld entries show their Nether equivalent and vice
  versa, floor-divided so negative coordinates land on the correct side.
* **Click any entry** to copy `x y z` to your clipboard. Hovering shows the
  portal conversion in full.
* Long lists stop at 20 entries per dimension with an `... and N more` line.

## Death locations

Dying auto-saves where you died as `death-1`, `death-2`, … and announces it in
chat. `/cs` is useless at the one moment you most need it — you are looking at
the death screen — so this happens without being asked.

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

Server addresses are normalised — lowercased, with a default `:25565` stripped —
so joining as `Play.Example.com`, `play.example.com` and `play.example.com:25565`
all resolve to the same file rather than looking like lost coordinates. If you
do end up with a split (a renamed world, a server that moved host), `/cworld
list` shows every file and `/cworld merge <key>` pulls one into another,
renaming on collision instead of overwriting.

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
