# Stackframe Fabric Client

This module builds a separate physical-client bootstrap artifact. It is not the
dedicated-server mod. Install only `stackframe-fabric-client-*.jar` on a Fabric
client running the exact selected Minecraft 26.2, Java 25, and Fabric Loader
0.19.3 development baseline. The `fabric.mod.json` declares
`"environment": "client"` and a client-only entrypoint, so Fabric Loader skips
it on a dedicated server; no client class is loaded there.

The development adapter installs a passive Log4j observer at Fabric
`preLaunch`, then observes throwable-bearing `ERROR`/`FATAL` logs, failed
resource-reload futures, client connection exceptions, and vanilla crash-report
creation/crash handling. It queues bounded work off the calling thread, emits
the generic `SF0001` log supplement with generated text only, and writes the
original throwable to a private local trace. Repeated observation of the same
throwable while retained in the bounded identity cache produces one supplement.
Minecraft's original log, disconnect, and
crash behavior remains in control. The original files and Stackframe's private
trace are raw and may contain sensitive data.

This covers Java failures at the listed hooks after Stackframe installs. An
error before `preLaunch`, an unlogged worker exception, or a native/process
failure may have no Stackframe supplement. There is no client UI yet; in-game
presentation and compatibility evidence remain in
[#62](https://github.com/MinecraftProt/Stackframe/issues/62) and
[#66](https://github.com/MinecraftProt/Stackframe/issues/66). No released client
support is claimed by this build.

| Caller | Capture behavior |
| --- | --- |
| Pre-window startup and mod initialization | `preLaunch` installs the observer; only plain log output is possible. Loader failures before this point remain vanilla-only. |
| Render/main task and resource reload | A bounded queue offer occurs on the caller or completion thread; trace writing and rendering run on the daemon worker. Fatal exceptions continue through Minecraft's own handling. |
| Network and other worker threads | The client-facing connection exception hook and throwable-bearing error logs offer without touching UI or waiting for disk. Unlogged worker exceptions are not observed. |
| Shutdown | The observer detaches and the worker gets up to 500 ms to drain accepted work. Queue saturation or worker failure loses only the supplement; original logging or crash reporting remains responsible for the failure. |

Build with Java 25 and the committed wrapper:

```shell
./gradlew --no-daemon --dependency-verification=strict :stackframe-fabric-client:build
```

Launch the exact development client in a graphical session:

```shell
./gradlew :stackframe-fabric-client:runClient
```

Use `./gradlew :stackframe-fabric-client:tasks --all` for the Loom launch and
packaging tasks. The development JAR is
`stackframe-fabric-client/build/libs/stackframe-fabric-client-0.1.0-SNAPSHOT.jar`.
It embeds the shared core and renderer plus ICU4J with their reviewed license
inventory, independently of the server artifact. Keep client-only Fabric,
Minecraft, UI, graphics, account, and connection types within this module.

See the [client edition contract](../docs/CLIENT_EDITION.md),
[build instructions](../docs/BUILDING.md), and
[compatibility policy](../docs/COMPATIBILITY.md).
