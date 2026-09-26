# Compatibility policy and public matrix

Stackframe must earn compatibility claims with repeatable evidence. Similar
versions are not automatically treated as supported.

## Status definitions

| Status | Meaning |
| --- | --- |
| Supported | An exact released-artifact row passed the release test matrix, has linked evidence, and is eligible for fixes |
| Tested | An exact combination passed documented tests, but is not a release support promise |
| Expected-compatible | An exact combination has a dated, linked compatibility review but has not passed the full test matrix |
| Degraded | An exact tested combination works with documented missing or altered behavior and a linked limitation |
| Unsupported | Outside policy or known not to work; the matrix names which reason applies |
| Unknown | No reliable compatibility evidence for this exact combination |

`Expected-compatible` and `unknown` are not synonyms for supported.

## Compatibility dimensions

Each release records exact values for:

- Minecraft dedicated server version;
- Java runtime and vendor assumptions;
- Fabric Loader and, when required, Fabric API;
- Forge version after Forge support exists;
- Stackframe artifact and configuration schema;
- operating system families;
- terminal/output mode and hosting environment;
- tested logging, crash-report, and representative server mods.

## Selected foundation baseline

The first Fabric implementation targets this exact baseline, selected on
2026-08-28:

| Dimension | Selected value | Boundary |
| --- | --- | --- |
| Environment | Dedicated server | Client-only operation and client-only crashes are outside the first release and require the separate planned client artifact |
| Minecraft Java Edition | `26.2` | Exact version; snapshots, release candidates, and other stable lines are not implied compatible |
| Java runtime | Java `25` | Minimum runtime; use a current patched Java 25 build |
| Java toolchain | Java `25`, `--release 25` | Eclipse Temurin is the reference CI vendor; other conforming Java 25 vendors require their own evidence |
| Fabric Loader | `0.19.3` | Minimum candidate for the first test row; later Loader versions are not implied compatible |
| Fabric API | `0.158.0+26.2` | Approved pin when the Fabric module uses Fabric API; it is not an operator requirement if the artifact has no Fabric API dependency |
| Fabric Loom | `1.17.20` | Build-time pin in the Fabric-recommended `1.17` line |
| Gradle Wrapper | `9.5.1` | Build-time pin; use the committed wrapper rather than a system Gradle |
| Minecraft names | Native Mojang names | Minecraft 26.2 is unobfuscated; do not add Yarn or legacy Intermediary mappings |

[`gradle/libs.versions.toml`](../gradle/libs.versions.toml) is the authoritative,
Gradle-readable location for the numeric pins. Build configuration introduced by
issue #7 consumes those values. The Gradle wrapper URL necessarily repeats the
Gradle version during bootstrap and must be checked against the catalog.

Java 25 is both the minimum server runtime and compilation target: Mojang's 26.2
metadata declares Java major version 25. Stackframe does not promise compatibility
with older bytecode targets because the dedicated server itself cannot run there.
The baseline chooses a Java feature version rather than one vendor patch so
operators can apply Java 25 security updates. Each test record still names the
exact vendor and patch used.

Fabric API remains conditional because Fabric Loader does not include it and a
server operator should not install an unnecessary mod. If implementation imports
Fabric API, the artifact metadata must declare it and the exact version above
becomes part of the tested combination.

## Current public matrix

**As checked on 2026-09-25, there are no published Stackframe releases or tags.**
The `0.1.0-SNAPSHOT` Fabric JAR is a development bootstrap, not an operator
release. The [dedicated server matrix](SERVER_MATRIX.md) tests failure capture,
fallback, and trace preservation on Loom's development classpath. It does not
execute a released JAR or prove coexistence with other mods and hosts. No row is
currently supported, tested, expected-compatible, or degraded.

The candidate row below is a selected build target, not a support claim.
`Never` means no qualifying runtime test has been recorded; `N/A (policy)` means
the row is excluded by policy and has no last-tested date. Every positive claim
must replace `Never` with a `YYYY-MM-DD` UTC test date and an immutable CI-run or
reviewed manual-test link. Dates belong to the exact versions in the row, not a
nearby version or a build-only run.

| Row | Exact scope or missing input | Status | Last tested (UTC) | Evidence / limitation | Freshness |
| --- | --- | --- | --- | --- | --- |
| `FS-26.2-BASE` | Development `stackframe-fabric-0.1.0-SNAPSHOT`; Minecraft `26.2`; Java feature `25` (vendor and patch not yet selected for a qualifying artifact test); Fabric Loader `0.19.3`; Fabric API not declared; OS, output destination, and other mods not yet selected | **Unknown** | Never | [Development-classpath server matrix](SERVER_MATRIX.md) tests capture behavior; exact released-artifact and hosting evidence is still required | **No qualifying artifact evidence** |
| `FS-JAVA-VENDOR` | Any exact Java 25 vendor or patch not covered by a dated server row | **Unknown** | Never | Conformance alone does not prove Stackframe behavior | **No runtime evidence** |
| `FS-LOADER-PATCH` | Fabric Loader versions other than `0.19.3`, or Fabric API versions if later required | **Unknown** | Never | Dependency patches need their own exact evidence | **No runtime evidence** |
| `FS-OTHER-MC` | Minecraft versions other than `26.2`, including snapshots and release candidates | **Unsupported (policy)** | N/A (policy) | Exact-version policy; no second platform artifact | **Policy** |
| `FS-OLD-JAVA` | Java feature versions below `25` with Minecraft `26.2` | **Unsupported (policy)** | N/A (policy) | Minecraft `26.2` requires Java 25 | **Policy** |
| `FORGE-SERVER` | Any Forge version; no Forge server artifact exists | **Unsupported (policy)** | N/A (policy) | Forge adapter [#22](https://github.com/MinecraftProt/Stackframe/issues/22) is planned | **Policy** |
| `CLIENT` | Client-only failures with the server artifact | **Unsupported (policy)** | N/A (policy) | Client compatibility requires a separate artifact and matrix | **Policy** |

Runtime coverage is still absent for the following destinations and mod
combinations. These rows inherit the candidate baseline above; their unspecified
versions are explicit gaps, not wildcard support claims.

| Row | Destination or combination | Status | Last tested (UTC) | Evidence / limitation | Freshness |
| --- | --- | --- | --- | --- | --- |
| `OUT-TERMINAL` | Interactive terminal, exact terminal and OS versions not recorded | **Unknown** | Never | Rendering unit tests do not prove server console behavior; [#16](https://github.com/MinecraftProt/Stackframe/issues/16) | **No runtime evidence** |
| `OUT-REDIRECTED` | Redirected file or CI log, exact environment not recorded | **Unknown** | Never | Original error and plain-output coexistence need runtime tests; [#16](https://github.com/MinecraftProt/Stackframe/issues/16) | **No runtime evidence** |
| `OUT-HOSTING` | Hosting panel, container, or service manager, exact product/version not recorded | **Unknown** | Never | No panel or service evidence; [#16](https://github.com/MinecraftProt/Stackframe/issues/16) | **No runtime evidence** |
| `MOD-COMBINATIONS` | Logging, crash, performance, permission, world, or extension mods; exact mod/version pairs not recorded | **Unknown** | Never | Representative combinations need minimal reproducible tests; [#24](https://github.com/MinecraftProt/Stackframe/issues/24) | **No runtime evidence** |

An exact row becomes **tested** only after its evidence is recorded. It becomes
**supported** only when a Stackframe release includes that row in its immutable
release matrix and maintainers accept fixes for it. Untested Java 25 vendor
changes and accepted dependency patches may be **expected-compatible** after a
dated, linked build and startup review, but that status never replaces the full
dedicated-server matrix.

## Loader policy

Fabric is the first supported loader. Forge work begins only after Fabric proves
the loader-independent contracts and the platform SPI is stabilized.

Loader modules own lifecycle hooks, platform metadata, commands, and environment
paths. Core diagnostic meanings and renderer behavior remain shared.

The client edition is published as a separate artifact with its own exact
Minecraft, Java, loader, operating-system, graphics, and UI evidence. A server
support claim never implies client support. Fabric client support comes first;
Forge client support requires the shared platform SPI and Forge server adapter.

## Minecraft version policy

Version-specific integrations should be isolated behind small adapters. A new
Minecraft version is supported only when:

- the project builds against selected mappings and loader versions;
- dedicated-server startup and shutdown pass;
- capture, generic fallback, and full-trace preservation pass;
- representative specialized diagnostics pass;
- configuration migration behavior is known;
- limitations and exact test date are published.

One Stackframe platform artifact targets one exact Minecraft line. Minecraft and
Fabric types stay in `stackframe-fabric`; core and renderer contracts remain
version-neutral. Within the Fabric module:

- lifecycle, logging, metadata, and other Minecraft touchpoints are kept behind a
  small platform-adapter boundary rather than scattered through diagnostics;
- native Mojang names are used for 26.2, without a remapping compatibility layer;
- a second source set or subproject is added only when supporting a second
  Minecraft line requires source-incompatible code;
- runtime version checks and reflection are not used to turn an untested line into
  an expected-compatible or supported claim;
- shared behavior is proved with the same core contracts and dedicated-server
  scenarios for every adapter.

This policy avoids speculative multi-version machinery in the first
implementation while preserving a clear extraction point when another line earns
support.

## Logging and hosting environments

Automatic ANSI output must account for redirected output, CI logs, containers,
service managers, custom Log4j appenders, and hosting panels. When capability is
unknown, Stackframe falls back to plain text.

Compatibility requires that existing intended appenders and crash reports still
receive complete errors. Merely producing a pretty console message is not enough.

## Mod compatibility

Testing focuses on integration risk rather than popularity alone:

- mods that install logging appenders;
- crash and diagnostics tools;
- performance mods that alter threading or logging;
- permissions and command frameworks;
- world, datapack, and lifecycle managers;
- mods that produce custom exceptions or Stackframe extensions.

A mod is named incompatible only with a reproducible minimal case. The matrix
records exact versions and whether the failure belongs to Stackframe, the other
mod, or an unresolved interaction.

## Claim lifecycle

For each positive or degraded row, record the exact Stackframe artifact name,
version, source revision, and checksum; Minecraft version; Java vendor and patch;
loader and API versions; operating-system version and architecture; output mode,
terminal, CI, panel, or service-manager product/version; and every other mod and
version in the tested combination. Use `none` for an absent component. A missing
value is `not recorded`, never a wildcard. Keep a stable row ID so a regression
issue can identify the affected combination.

The row also records a `YYYY-MM-DD` UTC last-tested date, an immutable CI run
or reviewed manual-test report, the test scenarios and outcomes, linked
limitations/regressions, status, and freshness. A build-only CI run proves the
build, not server compatibility. Manual evidence must give sanitized reproduction
steps and the exact artifact and environment. **Expected-compatible** rows still
show `Never` for tests they did not run and include a separate dated review link;
they cannot be promoted from silence or a version range.

`Current` freshness requires that the evidence still matches every relevant
version and behavior in the row and is at most 90 days old. Mark a row **Stale**
as soon as any relevant artifact, Minecraft, Java patch/vendor, loader, API,
operating system, terminal/host, configuration, or other-mod version changes;
the tested behavior changes; its evidence link becomes unavailable; or its last
runtime test exceeds 90 days. A previously positive row is then shown as
**Unknown (stale; formerly tested/supported/expected-compatible/degraded)**
until retested. Keep its old evidence, date, and limitation link visible so the
reason for the downgrade can be audited. An unsupported policy row stays
unsupported until the policy and implementation change.

If a regression is reproducible, link its issue from the affected row, identify
the exact failing versions and scenario, and change the current status to
**Degraded**, **Unsupported**, or **Unknown** according to observed behavior.
Do not leave a supported claim in place solely because an older CI run passed.
The immutable matrix revision used by a past release remains historical; the
current matrix shows later regressions and staleness.

### Release-to-matrix link

The live table above changes as evidence changes. For every published binary,
the release page must list that artifact's filename and SHA-256, source commit,
relevant row IDs, and a **commit-SHA permalink** to the exact
`docs/COMPATIBILITY.md` revision included in the release tag. A `dev`, `main`, or
moving branch link is insufficient. The [release-entry template](compatibility/RELEASE_ENTRY_TEMPLATE.md)
provides the per-artifact table. Build and runtime evidence for the tagged source
revision must also be linked before publication; otherwise the artifact cannot
claim `Supported`. If an artifact has no supported row, the release page says so
explicitly. Later edits to this live matrix do not rewrite the release's claim.

## Upgrade and deprecation policy

- Review Java 25 patches and Fabric Loader, Fabric API, Loom, and Gradle releases
  at least monthly and promptly after a relevant security advisory.
- Accept a dependency update only through a reviewed pin change. Runtime updates
  require dependency resolution, build, and dedicated-server startup/shutdown
  evidence before promotion to tested.
- Evaluate each stable Minecraft release after stable Fabric tooling is available.
  Snapshots and release candidates may inform research but never create a support
  claim.
- Do not deprecate the current Minecraft line until a replacement line is tested
  and published in a Stackframe release.
- Announce removal at least 90 days and one Stackframe release in advance. During
  that window, document whether the old line receives full fixes or
  security/critical fixes only.
- A severe vulnerability or unavailable upstream dependency may shorten the
  window. The release notes must name the reason, impact, and safe migration.
- Minecraft has no LTS designation in this policy. Age or popularity alone does
  not extend a line's support.

Changing the selected Minecraft or Java feature version is a compatibility
decision and requires an ADR. Patch pin updates do not require a new ADR unless
they alter a compatibility or release guarantee.

## Baseline sources

Sources were checked on 2026-08-28. Dynamic metadata is cited alongside dated
release guidance so a future update can distinguish what was selected from what
is current then.

| Decision | Primary source |
| --- | --- |
| Minecraft 26.2 is the current stable release and requires Java 25 | [Mojang version manifest and per-version metadata](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) |
| Java 25 is an LTS release | [Oracle Java SE support roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html) |
| Temurin reference builds | [Adoptium Java 25 HotSpot API](https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&heap_size=normal&image_type=jdk&os=linux&vendor=eclipse) |
| Fabric recommends Loader 0.19.3, Loom 1.17, and Gradle 9.5.1 for 26.2 | [Fabric for Minecraft 26.2](https://fabricmc.net/2026/06/15/262.html) |
| Loader 0.19.3 is stable for 26.2 | [Fabric Meta loader data](https://meta.fabricmc.net/v2/versions/loader/26.2/0.19.3) |
| Fabric API 0.158.0+26.2 is a stable release | [Fabric API release](https://github.com/FabricMC/fabric-api/releases/tag/0.158.0%2B26.2) |
| Loom 1.17.20 is the selected stable patch in the 1.17 line | [Fabric Maven metadata](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.17.20/fabric-loom-1.17.20.module) and [Loom 1.17 release](https://github.com/FabricMC/fabric-loom/releases/tag/1.17) |
| Gradle 9.5.1 supports running and toolchains on Java 25 | [Gradle Java compatibility matrix](https://docs.gradle.org/9.5.1/userguide/compatibility.html) |
| Pins match Fabric's maintained 26.2 example | [Fabric example mod 26.2 branch](https://github.com/FabricMC/fabric-example-mod/tree/26.2) |
