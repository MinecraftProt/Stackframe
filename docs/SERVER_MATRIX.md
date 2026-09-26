# Dedicated Fabric server matrix

CI boots actual Minecraft Java Edition `26.2` dedicated servers with Fabric
Loader `0.19.3`, Java `25`, Stackframe's Loom development classpath, and one
test-only failure mod. These exact versions come from
[`gradle/libs.versions.toml`](../gradle/libs.versions.toml). The fixture JAR is
built from checked-in source under `stackframe-fabric/src/serverMatrixFixture/`;
it is never bundled in a Stackframe release. Every server invocation uses Gradle
`--offline --dependency-verification=strict`, so a fixture run cannot download
new artifacts. The build step may download only the pinned, verified dependencies
before the matrix starts.

| Scenario | Actual server phase and trigger | Expected result |
| --- | --- | --- |
| `clean-start` | No injected failure; reach ready state and stop | Bootstrap and clean shutdown |
| `mod-loading` | Fixture `preLaunch` entrypoint, after Stackframe's own hook | Two original Log4j errors, one supplemental diagnostic and trace, one repeat summary |
| `startup` | Fixture `main` mod entrypoint | Same correlation and trace contract |
| `world` | Mixin on `MinecraftServer.loadLevel` | Same correlation and trace contract during world load |
| `registry` | Mixin on `MinecraftServer.registryAccess` | Same contract during registry access |
| `datapack` | Console `reload` invokes Mixin on `MinecraftServer.reloadResources` | Same contract during reload |
| `runtime` | Mixin on `MinecraftServer.tickServer` | Same contract during a live tick |
| `mixin` | Mixin-injected tick callback throws | Vanilla crash report, original failure, one Stackframe diagnostic and full trace |
| `shutdown` | Console `stop` invokes Mixin on `MinecraftServer.stopServer` | Same contract, including shutdown drain |

The fixture logs the **same throwable object** twice in every nonfatal failure
scenario. The runner requires both original events, one matching `SF0001`
diagnostic, exactly one complete raw trace with the original exception and stack
frame, and a repeat summary of exactly one. This follows the identity-based
correlation behavior in [#21](CORRELATION.md). The fatal Mixin case instead
requires the original crash log and a vanilla crash report containing its failure.
Minecraft 26.2 handles this server-thread crash by writing the report and
allowing the process to exit with code zero, so the runner does not infer crash
handling from the exit code.
Other host errors may generate unrelated diagnostics; assertions match the
fixture's unique marker and trace ID rather than counting every server error.

The shutdown fixture exposed a real lifecycle boundary: on Linux the JVM's
shutdown hooks can stop Log4j before Stackframe publishes its pending repeat
summary. Stackframe now drains at the tail of Minecraft's graceful `stopServer`
method, while Log4j is active. The JVM hook remains an idempotent fallback for
crashes that never reach normal server stop; an abrupt JVM halt still cannot
promise a final supplemental summary.

Run locally after building the normal artifact and fixture:

```shell
./gradlew --no-daemon --dependency-verification=strict clean build verifyModuleBoundaries :stackframe-fabric:serverMatrixFixtureJar
python3 scripts/server_matrix/run.py
```

The runner requires Python 3.11 or newer and a Java 25 runtime. It accepts
`--scenario runtime` for a focused run and `--timeout-seconds 180` to adjust a
local per-process deadline; every process still has a hard limit. It refuses to
reuse a scenario output directory, so use Gradle `clean` before a new full run.
Each scenario writes `console.log`, `result.json`, and its isolated server files
under `stackframe-fabric/build/server-matrix/<scenario>/`. CI uploads only logs,
traces, crash reports, and JSON results for 14 days, including on failure. A
runner timeout kills the complete process group before the next scenario.

The matrix exercises the failure-capture path after Fabric invokes Stackframe's
`preLaunch` hook. Loader discovery and resolution failures before that hook are
outside the current capture boundary. The phase fixture deliberately emits or
throws a failure; it does not claim to reproduce every naturally occurring
world, registry, datapack, or third-party Mixin failure. The matrix is one
evidence source for the exact candidate row in
[the compatibility policy](COMPATIBILITY.md), not a release support claim or a
substitute for a released-JAR test, other-mod testing, or hosting-environment
testing. Each snapshot identifies the source revision, built JAR checksum,
execution mode, Java runtime, and host system so that development-classpath
evidence cannot be mistaken for a released-artifact result.
