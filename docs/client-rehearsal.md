# Client rehearsal

Inside the DevServer sandbox, build the client mod and scenario server:

```sh
./gradlew build
```

Start the scenario server in manual mode:

```sh
coflnet-minecraft-test-server local \
  --jar testserver/build/libs/skycofl-scenario-server.jar \
  --mode manual \
  --port 25565 \
  --scenario-id bazaar-orders
```

While the server is running, launch the client and capture screenshots after 20 and 40 seconds:

```sh
JAVA_TOOL_OPTIONS=-Dcoflnet.description.base-url=http://127.0.0.1:PORT \
coflnet-minecraft-client run \
  --mod build/libs/SkyCofl-2.0.0-pre1.jar \
  --server-port 25565 \
  --screenshots /workspace/.coflnet-client-run-source-description-final-v4 \
  --mode debug \
  --width 1280 \
  --height 720 \
  --capture-after 20,40 \
  --expect-text "Order details:" \
  --expect-text "Total buy:" \
  --expect-text "Total sell:"
```

Inspect the PNG files and `client.log` under
`/workspace/.coflnet-client-run-source-description-final-v4`.

## Minecraft 26.3 upgrade smoke (2026-09-17)

`./gradlew build` passed all 55 client regression tests and 24 scenario-server tests.
The built SkyCofl 2.0.0-pre1 JAR was installed in the new Prism instance `26.3`, with
Fabric Loader 0.19.5, Fabric API 0.160.6+26.3, YACL 3.9.6+26.3-fabric, and Java 25.
The previous Minecraft 26.2 code is preserved on branch `26.2` at `9e1fb02`.

A temporary client helper connected an offline test player to the repository's
`bazaar-orders` scenario server on `127.0.0.1:25576`. The final run completed with
`COFL_SMOKE COMPLETE` after checking startup, the rendered Bazaar container,
inventory HUD hover hit-testing and copy action, SDL hotkey lookup, display-editor
selection/drag/resize, rendered YACL settings, and sign autofill. The runtime test
caught and verified the fix for the scrollbar mixin's moved RenderPipeline target.
This is a local client smoke test; it does not validate live Hypixel/backend behavior
or compatibility with the other optional mods from older Prism instances.

Final screenshots are in the Prism instance's `minecraft/screenshots` directory,
from `2026-09-17_00.01.02.png` through `2026-09-17_00.01.21.png`. The helper source,
result, and final client log are retained under `/tmp/skycofl-26.3-smoke`; the helper
JAR was removed from the instance after testing and the localhost server stopped.

## Last Bazaar rehearsal (2026-09-03)

The loopback stub was started with `./gradlew --no-daemon runDescriptionStub`, and only its
origin was supplied through `JAVA_TOOL_OPTIONS`. The built SkyCofl client JAR was loaded; the
server-only scenario JAR was not passed as the client mod. The runner completed with
`status: "captured"`, and `client.log` recorded this join marker:

```text
[13:35:53] [Render thread/INFO]: Loaded 3 advancements
```

The v4 local-Tesseract result was exactly `"Order details:": matched`, `"Total buy:": matched`,
and `"Total sell:": matched`. The command exited zero; when an expected phrase is absent it
returns `error_code=visual_text_missing` and retains the PNGs for inspection. Two PNGs were
retained under `/workspace/.coflnet-client-run-source-description-final-v4`, both 1280 by 720
pixels.

```text
/workspace/.coflnet-client-run-source-description-final-v4/01-capture.png
  sha256 9d7be8c0504550b10b4a58cec1d2ba62ab745eefba600aa92bdef7a5b4a67b80
/workspace/.coflnet-client-run-source-description-final-v4/02-capture.png
  sha256 f11b3902bd460ede0e32266005024d0833542de4280cd0613c24e19ede5bb478
```

The files remain retained on the host from the canceled predecessor task and will be attached to
this retry after review; they are intentionally not copied into Git or regenerated.

Both captures visibly show the `Co-op Bazaar Orders` container and its paper order fixture, plus
the exact `Order details:`, `Total buy: 75.05M`, and `Total sell: 3.02M` InfoDisplay lines. At full
resolution the InfoDisplay rectangle is wholly inside the upper-left viewport gutter and has a
visible gap from the centered container; it does not intersect the container. The lower-level
layout test separately checks those bounds at 1280x720, logical scale-2 640x360, and the observed
scale-3 426x240.

The first failed selection rehearsal was retained as requested under
`/workspace/.coflnet-client-run-first-failure/`; its two PNG SHA-256 digests are
`e42bfc1b5ae8c89862bcd9e7f2788849b29a55c2451b440a47b6103e39295e4c` and
`45a4f6dc2dbb93f7afc8eef9472820a137c020cd5e88b79c2686817e74cdf574`.

## Last automated rehearsal

The automated runner printed this `result.json` summary:

Result path: `/run/coflnet-task-scratch/minecraft-local/run_efcb8aa6803fc532fee0fff9d24672fe/result.json`.
Scenario JAR SHA-256: `089a842d4eda6bd621e2c7728ec8b7579583f783554874e2300fc985371dc91e`.

```json
{
  "passed": true,
  "scenarios": [
    {
      "id": "ender-chest-sequence",
      "passed": true,
      "summary": "labels=room.rebuilt:true,menu.slot-8.skyblock-menu:true,menu.initial-contents:true,room.structure:true,fixture.transition:true,menu.slot-update-newer:true,menu.slot-update-older:true,menu.close:true,menu.reopen:true,menu.packet-order:true,network.clientbound-packets:true"
    },
    {
      "id": "bazaar-menu",
      "passed": true,
      "summary": "labels=room.rebuilt:true,menu.slot-8.skyblock-menu:true,menu.initial-contents:true,room.structure:true,fixture.transition:true,menu.slot-update-newer:true,menu.slot-update-older:true,menu.close:true,menu.reopen:true,menu.packet-order:true,network.clientbound-packets:true"
    },
    {
      "id": "bazaar-orders",
      "passed": true,
      "summary": "labels=room.rebuilt:true,menu.slot-8.skyblock-menu:true,fixture.bazaar-order-tag:true,fixture.bazaar-order-description:true,menu.initial-contents:true,room.structure:true,fixture.transition:true,menu.slot-update-newer:true,menu.slot-update-older:true,menu.close:true,menu.reopen:true,menu.packet-order:true,network.clientbound-packets:true"
    },
    {
      "id": "auction-house-menu",
      "passed": true,
      "summary": "labels=room.rebuilt:true,menu.slot-8.skyblock-menu:true,menu.initial-contents:true,room.structure:true,fixture.transition:true,menu.slot-update-newer:true,menu.slot-update-older:true,menu.close:true,menu.reopen:true,menu.packet-order:true,network.clientbound-packets:true"
    },
    {
      "id": "trade-divider",
      "passed": true,
      "summary": "labels=room.rebuilt:true,menu.slot-8.skyblock-menu:true,menu.initial-contents:true,room.structure:true,fixture.transition:true,trade.dividers:true,menu.slot-update-newer:true,menu.slot-update-older:true,menu.close:true,menu.reopen:true,menu.packet-order:true,network.clientbound-packets:true"
    },
    {
      "id": "world-signals",
      "passed": true,
      "summary": "labels=room.rebuilt:true,scoreboard.objective-team-score:true,entity.named:true,hud.chat-actionbar-title-tab-sound:true,room.structure:true,fixture.transition:true,world.entities:true,scoreboard.sidebar:true,entity.metadata-update:true,chunk.unload:true,chunk.reload:true,world.packet-order:true,network.clientbound-packets:true"
    }
  ]
}
```

The final server log lines were:

```text
[14:03:52] [Server thread/INFO]: ThreadedAnvilChunkStorage (world): All chunks are saved
[14:03:52] [Server thread/INFO]: ThreadedAnvilChunkStorage (DIM1): All chunks are saved
[14:03:52] [Server thread/INFO]: ThreadedAnvilChunkStorage (DIM-1): All chunks are saved
[14:03:52] [Server thread/INFO]: ThreadedAnvilChunkStorage: All dimensions are saved
```
