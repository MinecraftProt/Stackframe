# Parallel workstreams

Stackframe is organized so multiple contributors or agent sessions can work in
parallel without sharing a branch or repeatedly editing the same files.

The operating rule is:

> One active worker owns one issue, one purpose-named branch, and one focused
> pull request.

An epic groups related issues but is not itself an implementation branch.

## Workstream map

| Workstream | Primary owned paths | May depend on | Must not import |
| --- | --- | --- | --- |
| Core diagnostic engine | `stackframe-core/` | JDK and approved neutral libraries | Fabric, Forge, Minecraft, Log4j implementations |
| Rendering and operator UX | `stackframe-renderer/` | `stackframe-core` | Fabric or Forge |
| Fabric platform | `stackframe-fabric/` | core, renderer, Fabric/Minecraft APIs | Forge |
| Verification and compatibility | `stackframe-testkit/`, test fixtures, scoped CI | all modules in test scope | production-only testkit dependencies |
| Documentation and release | `docs/`, root policy files, scoped release config | public contracts from all modules | unpublished assumptions presented as fact |
| Forge platform | `stackframe-forge/` | core, renderer, stable platform SPI | Fabric |
| Client platform | planned `stackframe-fabric-client/` and `stackframe-forge-client/` | core, renderer, client contract, stable platform SPI for Forge | dedicated-server internals as shared contracts |

Root Gradle settings, version catalogs, shared CI, README navigation, and public
contract documents are coordination hotspots. An issue touching one names the
required reviewers and dependent work before implementation.

## Workstream responsibilities

### Core diagnostic engine

Owns:

- immutable diagnostic and normalized throwable models;
- code registry, classifier arbitration, and generic fallback;
- correlation, deduplication, enrichment contracts, and redaction;
- resource budgets, extension isolation, and internal fallback diagnostics.

It exposes loader-neutral contracts. Platform-specific metadata is translated at
the adapter boundary rather than added to core types casually.

### Rendering and operator UX

Owns:

- terminal layout, wrapping, excerpts, labels, and trace summaries;
- ANSI and plain-text semantic equivalence;
- JSON/NDJSON rendering;
- message style, accessibility, and localization mechanics.

Renderers consume a completed, redacted diagnostic. They do not classify
exceptions, read server files, or bypass redaction.

### Fabric platform

Owns:

- Fabric/Minecraft lifecycle and logging capture;
- mod metadata, mappings, environment paths, and commands;
- built-in diagnostics requiring Fabric evidence;
- dedicated-server packaging and Fabric-specific compatibility.

The adapter emits core contracts and invokes renderers. Loader behavior does not
leak into loader-neutral modules.

### Verification and compatibility

Owns:

- reusable exception and platform fixtures;
- golden rendering tests and hostile-input corpora;
- dedicated-server integration harnesses;
- compatibility evidence, build verification, and supply-chain checks.

Testkit may depend on production modules for testing. Production modules never
depend on testkit.

### Documentation and release

Owns:

- operator catalog, support guidance, policies, and ADR organization;
- compatibility publication and release notes;
- artifact metadata, checksums, provenance, and publication workflow.

This workstream validates claims with the owning implementation workstream. It
does not declare support or behavior based only on intended design.

### Forge platform

Owns:

- the Forge implementation of the stable loader SPI;
- Forge lifecycle, logging, metadata, mappings, and commands;
- Forge-specific classifiers and packaging;
- cross-loader evidence together with verification.

Forge work starts after the relevant core and SPI contracts are accepted. It
does not copy Fabric internals into shared modules.

### Client platform

Owns:

- client lifecycle, uncaught failure, crash, and resource-reload capture;
- accessible in-game diagnostics and crash presentation;
- client-only metadata, environment, graphics, and UI integration;
- separately identified Fabric and later Forge client artifacts;
- client compatibility and privacy evidence.

Client work reuses shared diagnostic meanings and renderers. Client-only
Minecraft, Fabric, Forge, UI, graphics, account, and connection state remains in
client platform modules. Forge client work waits for the shared platform SPI.

## Starting an issue

Before a worker starts:

1. Confirm all parent issue acceptance criteria relevant to the task are clear.
2. Check linked blockers, ADRs, and prerequisite pull requests.
3. Confirm the issue has a milestone, parent epic, area/priority labels, and
   `Workstream` project value.
4. Set project status to In progress and assign or comment with the active owner.
5. Create a purpose-named branch from `dev`.
6. Write down any path outside the primary ownership boundary that must change.

If a public contract is unknown, mark `needs-decision` rather than inventing it
inside an implementation pull request.

Use [DEPENDENCIES.md](DEPENDENCIES.md) to choose work whose provider contracts
are already available.

## Parallel-safe work

Work is `parallel-ready` when:

- required input contracts are merged or explicitly fixed by an accepted ADR;
- owned paths do not overlap another active issue, or overlap is coordinated;
- acceptance criteria can be validated independently;
- no unpublished branch output is required;
- fallback and information-preservation behavior are known.

Examples of good parallel work after core contracts stabilize:

- terminal layout and throwable fixtures;
- independent built-in diagnostic classifiers;
- Fabric integration scenarios and operator documentation;
- Forge research while Fabric implementation continues, without changing SPI.

## Dependency handoff

A provider issue is complete enough to unblock a consumer only when:

- the required contract is committed and published to the consumer's base;
- behavior and compatibility expectations are documented;
- focused tests demonstrate the promised contract;
- the provider records known limitations;
- the consumer receives a handoff using [HANDOFF.md](HANDOFF.md).

Do not ask another worker to build against an uncommitted local assumption.

## Avoiding conflicts

- One worker owns root build configuration at a time.
- Diagnostic model changes land before renderers or adapters consume them.
- Diagnostic-code allocation is coordinated through the registry.
- Golden files are split by diagnostic area instead of one global snapshot.
- Platform fixtures live under their platform directory.
- Generated files are changed by their generator, never edited independently.
- Documentation changes stay with the behavior pull request when they describe
  that behavior.

If two tasks require the same public interface, split them into a small provider
pull request followed by independent consumer pull requests.

## Finishing or pausing work

The worker:

1. updates tests and directly affected documentation;
2. records deviations from issue scope;
3. opens a pull request against `dev`;
4. links the issue and any dependent issues;
5. completes the [handoff](HANDOFF.md);
6. moves status to Done only after merge and acceptance criteria are satisfied.

Paused work returns to Todo unless an active owner is resolving a documented
block. Never leave a branch as the only record of an important decision.
