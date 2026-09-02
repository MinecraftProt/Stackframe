# Dependency waves

This document gives future contributors and agent sessions a safe starting order.
The GitHub `parallel-ready` label is the live queue; this file explains why work
is or is not ready.

Milestones define release order. Workstreams define ownership. Dependency waves
define execution order.

## Wave 0 - Decisions and contracts

These tasks can start independently from the current repository state:

| Issue | Deliverable | Unlocks |
| --- | --- | --- |
| #2 | Supported Java, Minecraft, Fabric, and Gradle policy | #7, #17, #34 |
| #6 | Loader-neutral diagnostic model | #1, #3, #5, #13, #19, #27 |
| #8 | Approved project license | contribution and publication work |
| #41 | Classifier confidence and conflict rules | specialized classifiers |
| #42 | Console accessibility and message rules | renderer snapshots and catalog |

Each worker stays in its issue and workstream. If two proposals need the same
public term or type, record the shared choice in the diagnostic-model issue
rather than allowing incompatible local definitions.

## Wave 1 - Foundation implementation

Start after the corresponding Wave 0 output is merged into `dev`:

| Issue | Requires | Parallel notes |
| --- | --- | --- |
| #7 Gradle modules | #2, license decision from #8 | One owner for root build files |
| #1 Throwable normalization | core model from #6 | Core-only implementation |
| #3 Terminal renderer | display model from #6, style from #42 | Renderer-only implementation |
| #5 Code registry | diagnostic identity from #6 | Coordinate generated catalog format |
| #9 Fixtures and golden tests | initial #1 and #3 contracts | Own testkit and area snapshots |
| #4 CI | build commands from #7 | Avoid workflow edits by other workers |

The recommended integration order is #7, #6, #1/#3/#5 in parallel, #9, then #4.
Decision documentation may merge before executable changes.

## Wave 2 - Fabric vertical slice

Build one end-to-end generic path before many specialized classifiers:

1. #10 captures one Fabric server error safely.
2. #13 preserves the complete trace and creates a correlation ID.
3. #14 loads validated configuration.
4. #11 selects ANSI or plain output.
5. #17 proves the path in a real dedicated server.

#10 depends on the module layout and core model. #13 and #14 can proceed in
parallel after their core contracts exist. #11 is renderer/platform boundary
work. #17 starts its harness earlier but only asserts the complete vertical slice
after the provider changes merge.

## Wave 3 - Parallel Fabric diagnostics

After capture, classifier arbitration, code allocation, and fixtures stabilize,
separate workers may own independent diagnostic families:

- #12 mod loading and dependencies;
- #18 Mixin transformation;
- #20 world, datapack, resource, and registry;
- #35 Java runtime and memory;
- #38 network binding and ports;
- #44 permissions, disk, and file locking.

These issues should use separate classifier packages and golden files. They do
not edit one shared registry manually; code registration uses the mechanism from
#5.

In parallel:

- #19 builds bounded enrichment contracts;
- #39 implements source mapping through those contracts;
- #15 adds atomic runtime configuration reload;
- #21 implements bounded correlation and deduplication;
- #16 validates external logging and hosting compatibility.

## Wave 4 - Production hardening

After the Fabric vertical slice works, these workstreams can fan out:

| Area | Issues |
| --- | --- |
| Privacy and support | #27, #36 |
| Performance and resilience | #25, #32, #43 |
| Structured and localized output | #30, #46 |
| Ecosystem API | #40 |
| Compatibility evidence | #24, #45 |
| Documentation and releases | #29, #33, #37 |

The redaction contract from #27 is a provider for support bundles, structured
output, and extension-provided values. Release automation must wait for the
license, build, and complete release test command.

## Wave 5 - Forge

Forge work has a strict provider chain:

1. #26 extracts and stabilizes the loader SPI from proven Fabric behavior.
2. #22 implements Forge capture through the SPI.
3. #23 adds Forge metadata and classifiers.
4. #28 runs shared contracts against both loaders.
5. #31 publishes the Forge alpha.

Research and disposable fixtures may begin earlier, but no worker changes the
shared SPI from an unpublished Forge assumption.

## Wave 6 - Client edition

Client work begins after the production server contracts are stable:

1. #63 defines client scope, UI, privacy, threading, and artifact boundaries.
2. #60 adds the separate Fabric client module after #7 establishes the build.
3. #61 implements fail-open Fabric client capture.
4. #62 and #65 can proceed in parallel after capture and model contracts are
   available.
5. #66 proves exact client compatibility rows.
6. #59 publishes the first Fabric client alpha.
7. #64 adds Forge client support only after #26 and the Forge server capture
   contracts are stable.

Client workers reuse core and renderer contracts. They do not place client-only
types into shared modules or infer client support from server test evidence.

## Selecting work for a new subchat

Give each new session:

- one issue number and its parent workstream epic;
- the exact base branch, normally `dev`;
- owned paths from [WORKSTREAMS.md](WORKSTREAMS.md);
- provider commits or pull requests it may rely on;
- required validation and documentation;
- an instruction to use [HANDOFF.md](HANDOFF.md) when done.

Do not send two workers to the same issue or root configuration file. If a task
needs a provider that is not merged, create a dependent session only after the
provider branch is stable and pass that branch explicitly as its base.
