# Building Stackframe

Stackframe uses the committed Gradle 9.5.1 wrapper and a Java 25 toolchain. The
wrapper can provision a matching JDK through the configured Foojay resolver; the
machine running Gradle still needs a Java 17 or newer runtime and network access
for the first dependency resolution.

## Build and test

On Unix-like systems:

```shell
./gradlew clean build
```

On Windows:

```powershell
.\gradlew.bat clean build
```

The build includes `verifyModuleBoundaries`, which rejects disallowed project
edges and platform or logging dependencies in core and renderer. Dependency
reports can also be inspected directly:

```shell
./gradlew :stackframe-core:dependencies :stackframe-renderer:dependencies
```

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
claims require the release evidence defined in
[`COMPATIBILITY.md`](COMPATIBILITY.md).

## Modules and artifact

| Module | Production dependencies |
| --- | --- |
| `stackframe-core` | JDK only |
| `stackframe-renderer` | `stackframe-core` |
| `stackframe-fabric` | core, renderer, Minecraft, and Fabric Loader |
| `stackframe-testkit` | none in the initial scaffold |

Production modules cannot depend on testkit. Forge and client modules are not
part of this server foundation build.

The development Fabric server artifact is:

```text
stackframe-fabric/build/libs/stackframe-fabric-0.1.0-SNAPSHOT.jar
```
