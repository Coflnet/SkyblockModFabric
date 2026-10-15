# Testing

This project uses JUnit 5 for pure client-logic regression tests. Gradle selects JDK 26;
Java compilation targets release 25. CoflSkyCore is resolved from JitPack; no sibling checkout is required.

Run the unit tests locally with:

```sh
./gradlew --no-daemon test
```

The `testserver` subproject is included in both `test` and `build`. Its tests
lock the schema-1 scenario order, exact `26.2` branch dependency tuple, stable
observation labels, and the production `MenuClassifier`/`ScoreboardParser`
seams. `verifyScenarioJar` also rejects any resource outside the minimal
manifest, `fabric.mod.json`, owned scenario classes, and scenario index.

The resulting server-side scenario mod is
`testserver/build/libs/skycofl-scenario-server.jar`. It is not a Minecraft or
Fabric runtime and cannot be run standalone; licensed runtime validation is a
separate host-owned gate.

`CorePurityTest` scans both the Java sources and compiled classes in `com.coflnet.core`. It fails if any core class references `net.minecraft` or `com.mojang`, keeping the extracted logic runnable without a Minecraft client.

`DescriptionQueueTest` uses a loopback HTTP server to check stale-response rejection, reconnect
invalidation, bounded request coalescing, storage destinations, and malformed responses.
It also checks that Bazaar slot bursts upload their final inventory once, empty initial order views
wait for authoritative contents, and populated reinitializations still refresh.
The `com.coflnet.core.DescriptionQueue` logger reports queue, request (including decoding), dispatch,
apply and total milliseconds with a standardized menu category and outcome. Summaries are DEBUG;
requests taking at least one second also log at INFO, at most once per ten seconds. These summaries
contain no inventory contents, raw menu titles, player names, positions, or session identifiers.
`GoalProgressTriggerTest` covers login refresh before a goal HUD exists and Bazaar progress messages.
Auction actor and configuration tests cover exact ownership matching and recovery from malformed JSON.

Mixin signatures and Minecraft component initialization also need a client launch; unit tests do not
exercise transformed Minecraft classes. For a manual check, reconnect with an active goal while no
inventory is open, then verify that its HUD returns. Test a guided Bazaar flip through both claims,
including a partial fill and a cancelled replacement order, against the matching backend updates.

The core lifecycle fix was smoke-tested with a 2.0.0 build in Prism's Minecraft 26.3 instance. All 11 target
classes loaded, auction ownership and pre-player login-refresh assertions passed, and Minecraft
exited without its shutdown watchdog. The temporary test helper was removed afterward. Bundled
CoflSkyCore commit `911eb057e4` fixes the background workers that previously prevented shutdown;
its regression tests also cover first-message batching and worker reuse after disconnect.

## Regression baseline

The reserved comparison command targets `ScenarioServerContractTest`. That test uses only APIs
already available on pinned base `f766e850023edbc63fbb4747523154cd2f5e618e`, so it compiles there
and fails its assertions because `settings.gradle` has no `testserver` project and the
`bazaar-orders` scenario contract is absent. The patched command passed locally. The trusted host,
not the mutable implementation workspace, owns the exact-base execution and evidence.

On the pinned base commit these tests fail during `compileTestJava`, because the asserted `com.coflnet.core` classes do not exist and the logic is still inline in `CoflModClient`. With this extraction, the core regression suite compiles and passes under `./gradlew --no-daemon test`.
