# Backend command ownership

## Trace before changing commands

The command flow spans three repositories. SkyModCommands owns the public command
registry in `Commands/MinecraftSocket.cs`. `HelpCommand` sends command descriptions
and declared argument suggestions as a `commandUpdate` dictionary. CoflSkyCore's
`WSClient` caches the dictionary in `LocalConfig.knownCommands`. Fabric's generic
`args` suggestion provider consumes it for both `/cofl` and `/cl`.

The trade overlay command has no Fabric Brigadier literal, local command handler,
or Core switch case. Execution follows the existing generic path through
`CoflSkyCommand.processCommand` and `sendCommandToServer` to SkyModCommands.
`TradeGuiCommand` validates on, off, or an empty status argument and sends a typed
`tradeGui` response. Core validates the Boolean or null field and emits `OnTradeGui`.
`EventSubscribers.onTradeGui` schedules the preference update and chat feedback on
the Minecraft client thread. `TradeGuiManager` retains client persistence ownership.

The backend supplies full suggestion strings such as `tradegui on`. Core matches
against the complete greedy argument prefix. Fabric uses those strings directly,
so Brigadier replaces only the arguments and preserves the root command alias.
Other backend commands and settings retain their existing forwarding paths.
There is no injected Fabric completion fallback when a backend update omits a command.

## Compatibility and testing

Use the matching Commands branch and the exact Core commit pinned in `build.gradle`.
The library is bundled in the client JAR. The command needs an active backend
connection and a server that includes the command. A fork does not update the
running Coflnet service. Older clients ignore the new response type.

The backend NUnit suite checks registry ownership, response serialization, invalid
arguments, and a real WebSocket round trip using MinecraftSocket's inherited
message handler. The Core integration test receives that server's actual command
list and forwards on, off, and status through the production Core command path.
The backend fixture skips login and external service bootstrap, so it does not
prove production authentication or deployment.

Fabric's `BackendCommandSuggestionsTest` checks cursor replacement ranges, both
aliases, case handling, argument completion, list replacement, and full argument
forwarding. Run `./gradlew --no-daemon test build` before packaging.

Minecraft verification uses a disposable dedicated server on node-1 and the
matching client through Trident on the laptop. Check actual client feedback and
persisted overlay state after the backend response. Preserve personal instances
and remove only owned runtimes after their final consumers.

The full craft cost pricing feature remains separate from command routing. It
reads visible item quotes or requests hidden `FullCraftCost` fields from the
description API. Do not confuse the SkyApi HTTP command catalogue with the runtime
WebSocket completion registry.
