# Testing

This project uses JUnit 5 for pure client-logic regression tests. Gradle selects JDK 26;
Java compilation targets release 25. CoflSkyCore is resolved from JitPack; no sibling checkout is required.

Run the unit tests locally with:

```sh
./gradlew --no-daemon test
```

The `testserver` subproject is included in both `test` and `build`. Its tests
lock the schema-1 scenario order, exact `main` branch dependency tuple, stable
observation labels, and the production `MenuClassifier`/`ScoreboardParser`
seams. `verifyScenarioJar` also rejects any resource outside the minimal
manifest, `fabric.mod.json`, owned scenario classes, and scenario index.

The resulting server-side scenario mod is
`testserver/build/libs/skycofl-scenario-server.jar`. It is not a Minecraft or
Fabric runtime and cannot be run standalone; licensed runtime validation is a
separate host-owned gate.

`CorePurityTest` scans both the Java sources and compiled classes in `com.coflnet.core`. It fails if any core class references `net.minecraft` or `com.mojang`, keeping the extracted logic runnable without a Minecraft client.

## Regression baseline

The reserved comparison command targets `ScenarioServerContractTest`. That test uses only APIs
already available on pinned base `f766e850023edbc63fbb4747523154cd2f5e618e`, so it compiles there
and fails its assertions because `settings.gradle` has no `testserver` project and the
`bazaar-orders` scenario contract is absent. The patched command passed locally. The trusted host,
not the mutable implementation workspace, owns the exact-base execution and evidence.

On the pinned base commit these tests fail during `compileTestJava`, because the asserted `com.coflnet.core` classes do not exist and the logic is still inline in `CoflModClient`. With this extraction, the core regression suite compiles and passes under `./gradlew --no-daemon test`.
