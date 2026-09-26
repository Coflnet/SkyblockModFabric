# Backend trade command verification

Verified on September 26, 2026 for Minecraft 26.3 and SkyCofl 2.0.0.

## Matching source

* SkyModCommands: `a4ba436fb8f6b8744aed181e8a76ec920e09e75a` in [MCEnvision/SkyModCommands](https://github.com/MCEnvision/SkyModCommands/tree/envy/tradegui-command).
* CoflSkyCore: `e3660b25fb3f8ac8dafe0a43f6446b31b4870aa2` in [MCEnvision/CoflSkyCore](https://github.com/MCEnvision/CoflSkyCore/tree/envy/tradegui-command), resolved from JitPack and bundled in the client.
* Fabric: [the autocomplete branch](https://github.com/MCEnvision/SkyblockModFabric/tree/envy/fabric-26.3/2.0.0/trade-autocomplete).

The tested JAR is `SkyCofl-2.0.0-backend-tradegui-Fabric-26.3.jar`.
Its SHA256 is `d1977aedc214def0f16b536a785b116d611ef124ae9cbe76902f3a0b52c551f1`.

## Results

The backend build passed all 340 ordinary NUnit tests. Core's test and build
passed, followed by its separate live WebSocket integration test against the
backend fixture. That test received the real command registry and forwarded on,
off, and status through `CoflSkyCommand.processCommand`.

Fabric's `./gradlew --no-daemon test build` passed 98 tests across the client and
test server modules. The packaged JAR contains the matching Core library and no
local `com.coflnet.commands.TradeGuiCommand` class.

The matching Minecraft 26.2 build received separate laptop verification through
Trident against a disposable authenticated dedicated server and the local command
fixture. The owner confirmed enable, disable, status, and Tab completion of on
and off. Client logs showed typed responses and saved preference changes.
The Minecraft 26.3 candidate passed headless checks and packaging inspection;
it was not launched for this rehearsal.

## Limits and rehearsal lifecycle

The command fixture inherits the production MinecraftSocket message handler,
registry, command implementation, and serializer. It skips production login and
external service bootstrap. This verifies the command protocol, not production
authentication, deployment, real Hypixel trading, or frame rates.

The Minecraft 26.2 rehearsal also reproduced a shutdown watchdog crash about
fifteen seconds after closing. A non daemon `Timer-1` remained waiting in
`java.util.TimerThread.mainLoop`, and Trident reported exit code 248. Core's
existing flip housekeeping and chat batching code both create non daemon timers,
but the dump does not identify which owns this thread. These sites predate the
command changes. Normal client exit remains unverified until that lifecycle
issue is resolved. The Core fork has Issues disabled, so this finding is retained
here for review. All owned processes exited.

The explicit backend fixture has a nine minute lifetime. If it expires during
manual testing, commands cannot receive responses until it is restarted and the
client reconnects. A custom localhost endpoint still requires the existing
confirmation. Do not bypass that check. Retire the owned client, server, tunnel,
and fixture after the final consumer.

See [command ownership](../architecture/command-flow.md) for the complete flow.
