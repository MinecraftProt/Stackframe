# Building Stackframe

Stackframe uses the committed Gradle 9.5.1 wrapper and a Java 25 toolchain. The
wrapper can provision a matching JDK through the configured Foojay resolver; the
machine running Gradle still needs a Java 17 or newer runtime and network access
for the first dependency resolution.

## Build and test

On Unix-like systems:

```shell
./gradlew --no-daemon --stacktrace --dependency-verification=strict clean build verifyModuleBoundaries
```

On Windows:

```powershell
.\gradlew.bat --no-daemon --stacktrace --dependency-verification=strict clean build verifyModuleBoundaries
```

The build includes `verifyModuleBoundaries`, which rejects disallowed project
edges and platform or logging dependencies in core and renderer. Dependency
reports can also be inspected directly:

```shell
./gradlew :stackframe-core:dependencies :stackframe-renderer:dependencies
```

The Fabric `check` task also verifies the final JAR's embedded dependency and
license inventory at `META-INF/stackframe/dependencies.tsv`. A standalone copy
is generated at `stackframe-fabric/build/generated/supply-chain/dependencies.tsv`.
See [Dependency and supply-chain review](SUPPLY_CHAIN.md) for update and
vulnerability triage steps.

## Continuous integration

GitHub Actions runs the exact command above for pull requests targeting `dev`
and pushes to `main` or `dev`. CI validates the committed wrapper before using
it, provisions Temurin Java 25, and preserves Gradle dependency verification and
locking. It compiles, tests, and packages the separate Fabric server and client
development artifacts, but does not start either game side or accept the EULA.

Strict Gradle verification covers downloaded build, Fabric, and library
artifacts. Loom also verifies its downloaded Minecraft JAR before transforming
it. The exact `net.minecraft:minecraft-server-deobf:26.2` and
`net.minecraft:minecraft-merged-deobf:26.2` JARs in Loom's local file-backed
repository are trusted without fixed checksums because Loom generates them from
verified inputs and their transformed bytes vary by build platform. Their POMs
remain checksum-pinned. Gradle routes both modules and their metadata
exclusively to Loom's exact local repository under the Gradle User Home. Every
other Maven repository excludes them. `verifyGeneratedMinecraftRepository`
fails the build unless each resolved JAR's real path is inside that repository.
These controls
prevent a remote repository, including one using artifact-only metadata, from
serving bytes covered by the exception. No other group, module, version, or file
is trusted without a checksum.

When verification fails, the workflow uploads any Gradle problem reports, test
reports, and test result XML as a `verification-reports-...` artifact on the
failed Actions run. These artifacts are retained for five days. Runtime server
directories, logs, and EULA files are never uploaded.

## Development dedicated server

Start the exact Minecraft 26.2 development server baseline with:

```shell
./gradlew :stackframe-fabric:runServer
```

The first launch creates `stackframe-fabric/run/eula.txt` and exits. Accept the
Minecraft EULA only for local development by changing that ignored file to
`eula=true`, then run the same command again. Stop the server with `stop` in its
console.

Successful startup prints:

```text
[Stackframe] Loaded Stackframe dedicated-server bootstrap.
```

This smoke test does not change the compatibility status from **Unknown**. Support
claims require exact, dated runtime evidence in the
[current public matrix](COMPATIBILITY.md#current-public-matrix). The CI build and
artifact packaging checks above are not runtime compatibility tests.

## Development Fabric client

Build the separate physical-client bootstrap artifact with:

```shell
./gradlew --no-daemon --dependency-verification=strict :stackframe-fabric-client:build
```

In a graphical session, launch the selected Minecraft 26.2 / Java 25 / Fabric
Loader 0.19.3 development client with:

```shell
./gradlew :stackframe-fabric-client:runClient
```

The client bootstrap prints `[Stackframe Fabric Client] Loaded client bootstrap.`
when its client entrypoint runs. This is a startup smoke test, not evidence of
failure capture, UI behavior, or a client support claim. The client module
declares `"environment": "client"`; Fabric Loader excludes it on dedicated
servers. See the [client module README](../stackframe-fabric-client/README.md)
and [client contract](CLIENT_EDITION.md) for the side and artifact boundaries.

## Modules and artifact

| Module | Production dependencies |
| --- | --- |
| `stackframe-core` | JDK only |
| `stackframe-renderer` | `stackframe-core` and ICU4J 78.3 |
| `stackframe-fabric` | core, renderer, ICU4J runtime, Minecraft, and Fabric Loader |
| `stackframe-fabric-client` | core, renderer, ICU4J runtime, Minecraft, and Fabric Loader; physical client only |
| `stackframe-testkit` | JDK only; shared test fixtures and snapshot helper |

Production modules can use testkit only from test configurations; it is excluded
from production artifacts. Forge modules are not part of this build. The client
module is separate from the server artifact and has no released compatibility
claim yet.

The development Fabric server artifact is:

```text
stackframe-fabric/build/libs/stackframe-fabric-0.1.0-SNAPSHOT.jar
```

The separate development Fabric client artifact is:

```text
stackframe-fabric-client/build/libs/stackframe-fabric-client-0.1.0-SNAPSHOT.jar
```

Both Fabric artifacts bundle ICU4J 78.3 for the renderer. ICU remains licensed
under `Unicode-3.0`; Stackframe does not relicense it under Apache-2.0. The
authoritative ICU 78 license and included third-party notices are committed at
[`THIRD-PARTY-NOTICES/icu4j-78.3-LICENSE.txt`](../THIRD-PARTY-NOTICES/icu4j-78.3-LICENSE.txt)
from the URL declared by the ICU4J 78.3 POM:
<https://raw.githubusercontent.com/unicode-org/icu/maint/maint-78/LICENSE>.
The same bytes are packaged in each artifact as
`META-INF/licenses/icu4j-78.3-LICENSE.txt`, separately from
`LICENSE_stackframe`.

Loom wraps the non-mod ICU JAR with generated `fabric.mod.json` metadata and
warns that upstream version `78.3` is not strict semantic-version syntax.
Stackframe keeps the actual Maven pin unchanged. The Fabric artifact tests open
both final JARs, verify licenses and nested modules, and check distinct server-
and client-only metadata. The server artifact test also proves Fabric Loader
accepts ICU's generated version string.
