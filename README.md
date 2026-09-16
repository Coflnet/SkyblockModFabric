# SkyblockModFabric

SkyblockModFabric is a Hypixel SkyBlock mod for Minecraft Fabric (1.21.5+).
It provides in-game access to [sky.coflnet.com](https://sky.coflnet.com) auction house and bazaar data.

For the Forge edition, see [Coflnet/Skyblockmod](https://github.com/Coflnet/Skyblockmod).

Current version: **2.0.0-pre1** | [Releases](https://github.com/Coflnet/SkyblockModFabric/releases)

## License

[GNU AGPL-3.0](LICENSE)

## Compatibility

Earlier Minecraft versions were tested with the following mods as of 2026-06-01; this is not a 26.3 compatibility guarantee:

| Mod | Version | Status |
| --- | --- | --- |
| [SkyHanni](https://github.com/hannibal002/SkyHanni) | 6.0.0 | Compatible |
| [Sodium](https://modrinth.com/mod/sodium) | 0.8.12 | Compatible |
| [Iris Shaders](https://modrinth.com/mod/iris) | 1.7.3+1.21 | Compatible |
| [Essential](https://essential.gg/versions) | 1.3.10.9 | Compatible |
| [YACL](https://modrinth.com/mod/yacl) | 3.9.4+26.2 | Optional — required for the in-game settings GUI |
| [Text Tunnels](https://modrinth.com/mod/text-tunnels) | 1.3.3 | Optional — supported for own SkyCofl text channel |

Compatibility may change with future updates of any of these mods.

## Installation

1. Download the latest JAR from [Releases](https://github.com/Coflnet/SkyblockModFabric/releases)
2. Place it in `.minecraft/mods`
3. Launch Minecraft with Fabric Loader

Alternatively find it on CurseForge or Modrinth directly or in Prism Launcher.

For local development, build and install with:

```sh
./gradlew installMod -PmodsDir=/path/to/minecraft/mods
```

This replaces the jar atomically. Copying over a jar while Minecraft is running can break
resource loading, including the zero-width Text Tunnels marker font, with `invalid LOC header`.
Restart Minecraft after installing to load the new build.

Requirements for `main`:

- Minecraft 26.3 (use the `26.2` branch for Minecraft 26.2)
- Java 25+
- Fabric Loader 0.19.5+
- Fabric API 0.160.6+26.3
- Optional settings GUI: YACL 3.9.6+26.3-fabric. Its release metadata lists snapshots, but its Minecraft dependency accepts 26.3.

## Usage

Run `/cofl` in-game to open settings. See [sky.coflnet.com](https://sky.coflnet.com/wiki) for documentation.

### Info displays

Up to 3 permanent, backend-updatable HUD panels ("info displays") can be shown at all times in-game — for
example a running list of flips, bazaar margins, or connection status. Each one can be moved, rescaled, and
given its own background/text transparency.

**Transport:** the backend sends an `infoDisplay` message over the existing websocket connection.
The envelope is `{"type":"infoDisplay","data":"<JSON payload>"}`; `data` is a JSON-encoded string,
as with other protocol messages. The client validates the payload and updates the HUD on the render thread.
CoflSkyCore dispatches this message through its regular command event; no protocol mixin is needed.
Gradle downloads and bundles the published CoflSkyCore dependency; no sibling checkout is required.
`/cofl display <json>` remains available for local testing.

With SkyModCommands, `/cofl test display` sends a test panel to slot 1 for 60 seconds;
`/cofl test display clear` clears it through the same protocol.

**Bazaar orders (2.0.0-pre1):** placing a buy order or sell offer introduces the order display. 


Click **Disable display**, or run `/cofl set modhideBazaarOrderDisplay true`, to save a preference
that hides slot 2's Bazaar content and suppresses its tutorial. Set it to `false` to re-enable.

**JSON payload shape:**

```json
{
  "id": 1,
  "title": "§6Flips",
  "lines": [
    "§aplain colored line",
    {"text": "clickable line", "hover": "shown on hover", "onClick": "suggest:/viewauction abc"}
  ],
  "ttl": 30,
  "clear": false
}
```

- `id` (required): which display slot, `1`-`3`.
- `title` (optional): a single header line.
- `lines` (optional): up to 30 entries, each either a plain string or an object with `text`/`hover`/`onClick`
  (mirroring the existing chat `TextElement` format). `onClick` supports `http(s)://` URLs, `suggest:`,
  `copy:`, or is otherwise run as a command. Each line is capped to 200 characters.
- `ttl` (optional): seconds until the content expires and the display goes blank again. Omitted or `0` means
  it stays until replaced or cleared.
- `clear` (optional): `true` clears that display instead of setting content.

**Commands:**

To use a display line's click action, open chat with **T**, then left-click the text.
Hover over a line while chat is open to show its `hover` tooltip, including on lines without a click action.
The layout editor uses clicks for selecting and dragging, rather than running actions.

- `/cofl displays` — opens the layout editor.
- `/cofl display <json>` — pushes a payload (same shape as above).
- `/cofl display clear [id]` — clears one display, or all three if `id` is omitted.
- `/cofl display demo` — fills all three with sample content, for trying out the editor.
- `/cofl display help` — prints the JSON shape and controls in chat.

**Editor controls** (`/cofl displays`, or "Edit display layout" in `/cofl` → SkyCofl settings): left-click a
display to select it, left-drag to move it; mouse wheel over a display rescales it, Shift+wheel adjusts its
background transparency, Ctrl+wheel its text transparency; arrow keys nudge the selected display by 1px
(Shift: 10px); `H` toggles it enabled, `R` resets it to defaults, `Escape` closes the editor. Positions are
saved as fractions of the screen, so they hold up across resolution and GUI-scale changes.

"Permanent" means the displays stay up while a container/other GUI is open too, not just with nothing (or
chat) open — toggle this with "Show info displays while a GUI is open" in `/cofl` → SkyCofl settings.

## Links

- [Releases](https://github.com/Coflnet/SkyblockModFabric/releases)
- [Issue Tracker](https://github.com/Coflnet/SkyblockModFabric/issues)
