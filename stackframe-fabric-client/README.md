# Stackframe Fabric Client

This module builds a separate physical-client bootstrap artifact. It is not the
dedicated-server mod. Install only `stackframe-fabric-client-*.jar` on a Fabric
client running the exact selected Minecraft 26.2, Java 25, and Fabric Loader
0.19.3 development baseline. The `fabric.mod.json` declares
`"environment": "client"` and a client-only entrypoint, so Fabric Loader skips
it on a dedicated server; no client class is loaded there.

The bootstrap currently logs its initialization. Client failure capture,
in-game presentation, and compatibility claims are separate work in
[#61](https://github.com/MinecraftProt/Stackframe/issues/61),
[#62](https://github.com/MinecraftProt/Stackframe/issues/62), and
[#66](https://github.com/MinecraftProt/Stackframe/issues/66). No released client
support is claimed by this build.

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
