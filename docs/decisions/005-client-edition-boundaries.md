# ADR 005: Separate client edition and preserve vanilla failure paths

- **Status:** Proposed
- **Date:** 2026-09-25
- **Issue:** [#63](https://github.com/MinecraftProt/Stackframe/issues/63)
- **Owners:** Client platform and security/privacy maintainers

## Context

Stackframe's first artifact and capture policy target a dedicated server. A
Minecraft client has additional lifecycle phases, a render thread and UI that
can fail independently, remote-server and network signals that are easy to
misattribute, and personal data absent from a server-only console contract.
Putting client hooks in the server artifact or treating an in-game screen as the
only output would make side safety, privacy, and original crash recovery hard to
prove. [Issue #58](https://github.com/MinecraftProt/Stackframe/issues/58)
requires Fabric first and Forge after the shared loader SPI is stable.

The [diagnostic model](../DIAGNOSTIC_MODEL.md) and
[ADR 004](004-classifier-arbitration.md) already require bounded, evidence-based,
redacted, loader-neutral results. The client edition needs a platform and
presentation contract without changing those meanings.

## Decision

Adopt [CLIENT_EDITION.md](../CLIENT_EDITION.md) as the normative client scope,
lifecycle, origin, UI, and failure contract.

1. Publish separate, unmistakably named Fabric server and Fabric client
   artifacts. Declare the client side in loader metadata and make dedicated
   server installation of the client artifact an explicit rejection or safe
   failure. Neither side requires Stackframe on the other side of a multiplayer
   connection. Integrated-server failures remain distinct from client failures.
2. Keep all Minecraft/Fabric client hooks, UI, graphics, and connection state in
   `stackframe-fabric-client`. It translates verified observations into the
   shared `stackframe-core` model and `stackframe-renderer` output. Core and
   renderer do not depend on client or loader types. The eventual
   `stackframe-forge-client` follows the accepted shared platform SPI after #26
   and Forge server capture are stable; it shares diagnostic meanings and has
   no Fabric-client dependency.
3. Observe supported startup, mod initialization, resource reload, runtime,
   rendering, connection, crash, worker, and shutdown failure points without
   taking ownership of vanilla exception/log/crash-report handling. Original
   local log entries and crash reports remain recoverable through their normal
   paths when Stackframe fails. A Stackframe diagnostic is supplementary.
4. Use plain local log output as the universal presentation fallback. Add
   crash-screen context only when the original crash route remains usable, and
   an in-game view only after UI services are ready for a recoverable failure.
   Never block fatal handling on UI. Redaction precedes every screen, narrator,
   copy, export, and structured output.
5. Classify client-local, remote-reported, network, and unresolved observations
   using structured evidence. A remote reason string cannot prove server root
   cause, and a local stack frame cannot prove mod ownership. Conflicts use a
   generic fallback. The client privacy additions in
   [SECURITY_AND_PRIVACY.md](../SECURITY_AND_PRIVACY.md#client-edition-data-handling)
   apply before any client implementation.

This decision does not add a client-only field or code range to the shared
model. The current `SERVER_SENSITIVE` model class also represents private
endpoint and environment values on a client; a later rename or new class
requires a separate reviewed model/schema decision. Compatibility remains
unclaimed until client-specific evidence exists.

## Alternatives considered

### One universal mod artifact

One jar could reduce the number of downloads, but side-specific class loading
and metadata would be difficult to audit and operators could infer client
support from a server test. Separate artifacts make installation and test
boundaries explicit.

### Replace vanilla crash handling with a Stackframe screen

A replacement could control layout completely, but a UI/render failure would
hide the original report and might disrupt other crash tools. Supplementary
presentation with a plain/original fallback preserves recovery.

### Use disconnect text or first non-Minecraft frame to infer responsibility

These signals are easy to obtain but can be controlled by a remote server or
reflect an unrelated wrapper. Typed/structured evidence plus conservative
fallback is required by the shared classifier policy.

### Share Fabric client internals with a later Forge adapter

This could speed a port but would import loader-specific lifecycle and UI
assumptions into the wrong module. Forge instead implements the stable platform
contract and proves equivalent behavior with shared fixtures.

## Consequences

### Positive

- Artifact identity, side installation, and dependency direction are reviewable
  before client code exists.
- Original failures remain the recovery source if formatting or UI fails.
- The same diagnostic facts can reach log, crash, and in-game surfaces without
  privacy or blame changing between them.
- Forge client work has an explicit provider boundary.

### Negative

- Separate artifacts, metadata, release notes, and compatibility rows add
  maintenance cost.
- Conservative origin wording may be less specific than a user's suspicion.
- Some startup or process-fatal failures cannot show an in-game view.

### Risks

- A client hook or UI action could interfere with vanilla crash reporting.
  Integration fixtures must inject formatter and UI failures and compare the
  original log/report paths.
- Original vanilla logs and crash reports can include sensitive information.
  They stay local, are clearly labeled as raw, and are excluded from sanitized
  copy/export unless reviewed separately.
- Work queued from a render callback could freeze or outlive teardown. Bounded
  work, thread ownership, and overload fallback must be verified on real
  client launches.

## Validation

This decision changes documentation only. Review links and terminology against
the shared model, style guide, privacy policy, roadmap, and release gates. The
implementation issues must prove the phase, threading, side-installation,
fallback, privacy, UI accessibility, and exact compatibility cases listed in
[CLIENT_EDITION.md](../CLIENT_EDITION.md#acceptance-evidence-for-implementation-issues).
A server test matrix cannot stand in for a client test matrix.

## Follow-up

- [#60](https://github.com/MinecraftProt/Stackframe/issues/60): create and
  identify the separate Fabric client artifact.
- [#61](https://github.com/MinecraftProt/Stackframe/issues/61): prove capture,
  thread safety, original recovery, and formatter fallback.
- [#62](https://github.com/MinecraftProt/Stackframe/issues/62): prove the
  accessible in-game and crash views plus sanitized preview/copy/export.
- [#65](https://github.com/MinecraftProt/Stackframe/issues/65): classify
  client, remote-reported, and network failures with verified evidence.
- [#66](https://github.com/MinecraftProt/Stackframe/issues/66) and
  [#59](https://github.com/MinecraftProt/Stackframe/issues/59): earn and
  publish Fabric client compatibility claims.
- [#64](https://github.com/MinecraftProt/Stackframe/issues/64): add Forge
  client support only after its shared SPI and Forge server prerequisites.
