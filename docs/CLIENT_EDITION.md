# Client edition contract

This document specifies the client edition for
[issue #63](https://github.com/MinecraftProt/Stackframe/issues/63). A separate
Fabric client development bootstrap now exists, but it is not a released client
artifact or a client compatibility claim. The terms **MUST**, **MUST NOT**,
**SHOULD**, and **MAY** are
normative. [ADR 005](decisions/005-client-edition-boundaries.md) records the
choice. The shared [diagnostic model](DIAGNOSTIC_MODEL.md),
[classifier policy](decisions/004-classifier-arbitration.md),
[style guide](DIAGNOSTIC_STYLE.md), and
[privacy policy](SECURITY_AND_PRIVACY.md) govern every client output.

## Product and installation boundary

- `stackframe-fabric-client` is a separately named and published client
  artifact. Its filename, mod metadata, loader declaration, documentation, and
  release page MUST identify Fabric and client use. The dedicated-server
  artifact remains separately identified. Neither artifact implies support on
  the other's side.
- Install the client artifact on a physical Fabric client only. The client
  artifact MUST declare its client environment and MUST be rejected explicitly
  or fail safely if placed on a dedicated server; it MUST NOT load client
  classes there. The server artifact MUST NOT require a client installation. A
  multiplayer server MUST NOT require Stackframe on connecting clients, and a
  client MUST NOT require Stackframe on the remote server.
- In singleplayer, an integrated server may run in the same process. Client
  observation does not turn an integrated-server failure into a verified client
  failure. Any future integrated-server adapter must identify the executing side
  and avoid duplicate capture before claiming support.
- The client module may depend on `stackframe-core` and `stackframe-renderer`.
  Fabric/Minecraft/UI/graphics/account/connection types stay in the Fabric
  client module; the core model and renderer remain loader- and side-neutral. A
  later `stackframe-forge-client` uses the accepted shared platform SPI and
  shared diagnostic meanings after #26 and the Forge server capture contracts
  stabilize. It MUST NOT import or copy Fabric client internals.
- Client and server compatibility are tested and published independently under
  [COMPATIBILITY.md](COMPATIBILITY.md). The server baseline is not a client
  support claim. Client rows require exact Minecraft, Java, loader and API,
  operating-system, and relevant graphics/UI evidence.

## Eligible failure boundaries

Capture observes verified platform hooks and preserves the original failure
path. It MUST NOT parse arbitrary log lines as a substitute for an available
typed event, wrap every callback, or rewrite normal informational logs.
Observation of the same underlying failure at several hooks produces at most one
Stackframe diagnostic, with a correlation ID joining the safe summary and
recoverable details. Unsupported or ambiguous cases use the generic diagnostic
without invented cause.

| Phase | Eligible observation | Boundary and safe fallback |
| --- | --- | --- |
| Early startup | Loader/client initialization and uncaught startup failures at a verified hook, including failures before the game window exists | Log/console only until safe UI services exist; preserve the loader's normal termination and report behavior |
| Mod initialization | Typed initialization, dependency, and Mixin failures exposed to the client adapter | Name a mod only with verified ownership or loader metadata; do not infer ownership from the first non-Minecraft frame |
| Resource reload | A failed client resource, model, texture, shader, or pack reload with a verified reload result | Distinguish an operation that continued from one that cannot continue; do not trigger automatic retry or read arbitrary resource contents |
| Runtime/rendering | Uncaught main/render task failures and typed graphics initialization/capability failures at supported hooks | Never swallow an exception to keep a broken render loop alive; GPU process/driver failures below Java may have no observable hook |
| Connection/disconnect | Failed connection attempts, typed protocol rejections, and disconnect events with structured evidence | Distinguish the source of the observed signal from the underlying cause; no capture of normal traffic, chat, or packet payloads as diagnostic context |
| Crash | Uncaught client failure before or alongside vanilla crash handling, where the loader exposes a safe hook | Vanilla logging, crash-report creation, exit handling, and any usable original crash screen continue; Stackframe adds only a supplementary safe summary |
| Shutdown/worker thread | Uncaught errors at explicitly supported worker or shutdown hooks | Log-only if the main thread or UI is gone; never delay shutdown to build a view |

A handled exception is eligible only when a supported platform operation reports
a real failed result. Ordinary warnings, expected disconnects, user
cancellation, and successful reloads do not create error diagnostics. A client
hook cannot promise to observe failures in native code, another process, a
killed JVM, or hooks that fail before Stackframe loads.

### Origin and blame

The diagnostic describes what the client actually observed. Origin is separate
from mod ownership and from the root cause of a remote failure.

| Observed category | Evidence required | Permitted wording |
| --- | --- | --- |
| Local client operation failed | A typed local lifecycle/reload/render exception or structured local failure tied to the operation | "client resource reload failed" or the verified local operation; name a mod only with separate ownership evidence |
| Remote server rejected/reported a failure | A validated protocol response or server-origin status tied to this connection | "server rejected the connection" or "server reported …"; do not claim the server crashed, name its mod, or endorse its free-text explanation |
| Network transport failed | A typed connect, timeout, reset, DNS, or transport result without a verified protocol rejection | "connection timed out" or "connection was interrupted"; do not assign fault to the client, server, router, or ISP |
| Cause or origin unresolved | Only untrusted disconnect text, a stack frame, a generic I/O exception, conflicting evidence, or no reliable signal | State the observed disconnect/failure and request evidence; use the generic fallback |

A remote-supplied reason is untrusted text. It is sanitized and redacted before
display and cannot by itself establish root cause or local mod blame. If
evidence conflicts, use the less specific category and avoid a prescriptive fix.
Server-side claims require server-side evidence; client Stackframe does not
inspect a remote server.

## Presentation responsibilities

All Stackframe views consume the same completed, redacted `DiagnosticDocument`.
The diagnostic's severity, code, facts, uncertainty, omissions, and trace state
keep the same meaning in log, crash, in-game, and future structured output. The
renderer does not classify or access client state.

| Surface | Responsibility |
| --- | --- |
| Console and local log | Baseline for every observable failure, including startup, headless/no-window, and UI failure. Emit a bounded plain diagnostic as a supplement. Leave original Minecraft/loader/error log entries intact and recoverable. ANSI is optional only where capability is known. |
| Crash screen | When Minecraft can still render it, retain the original crash route and report. A Stackframe summary may be added or linked without obscuring the original error, crash-report location, or exit controls. If screen construction/rendering fails, use the original crash/log path. |
| In-game view | Only for a recoverable failure after client UI services are ready. Show a concise summary, evidence, safe next steps, and correlation ID; expandable detail is redacted and bounded. It MUST NOT automatically retry, change mods/config/world data, or keep the game running after a fatal failure. |

Stackframe content in the in-game and crash views MUST work without color alone,
support keyboard-only focus/order/activation, respect GUI scaling and narrow
layouts, and expose equivalent facts through narration. Narration and on-screen
text receive the same redaction. A view failure or disabled narrator never
prevents plain log output. Copy and export require an explicit user action, a
visible preview of the exact sanitized payload, and no access to raw logs or
crash reports through that control. Clipboard content is never read. Export is
local only; there is no automatic upload.

## Failure, lifecycle, and threading

- Register observation at platform-supported points without replacing the
  original exception handler, logger, or crash-report writer. The original
  throwable, causes, suppressed exceptions, and stack frames MUST reach the
  normal log and crash-report routes unmodified. Resulting entries and reports
  remain available through their normal local paths. Stackframe's shortened
  output is supplementary; its trace summary must truthfully describe whether
  its own full debug record was preserved.
- A Stackframe normalization, classification, redaction, render, UI, copy, or
  export failure MUST leave the original error/report route active. Use a
  bounded, recursion-guarded plain fallback or skip the enhancement and report
  Stackframe's own failure separately where safe. Never recursively diagnose
  that fallback failure. If the platform or filesystem itself cannot write
  original files, Stackframe cannot guarantee recovery and must not claim that
  it did.
- Capture on render and main threads performs only bounded, nonblocking work.
  Potentially expensive enrichment, writing, and view preparation are deferred
  when safe, with bounded queues and a defined overload fallback to original
  logging. UI state is created and updated only on the client UI thread; worker
  and shutdown threads never touch it directly. Fatal handling must not await UI
  work or deadlock on a stopped render thread.
- A view is unavailable during early startup, before a window/UI service exists,
  during unsafe rendering, after teardown, or when the relevant thread has
  failed. In those states the plain log and the original crash path are
  authoritative. No UI promise is made for native or process-fatal failures.

## Client privacy boundary

[SECURITY_AND_PRIVACY.md](SECURITY_AND_PRIVACY.md#client-edition-data-handling)
classifies account identifiers, chat, server addresses, screenshots, clipboard
contents, local paths/files, and client environment data. Client capture MUST
classify every candidate value before constructing a completed diagnostic,
including exception messages, causes, suppressed exceptions, evidence summaries,
excerpts, narration, copied text, structured records, and exported bundles.
Unknown data is treated as protected. A renderer or view cannot opt out of
redaction.

The initial client edition reads no arbitrary local file for enrichment and
captures no screenshot, clipboard content, chat, packet body, player/world
state, or authentication state. It performs no telemetry or automatic upload.
Original Minecraft/loader logs and crash reports can contain sensitive data
because they are outside Stackframe's redacted presentation; the UI must label
raw-file locations accordingly and never include those files in a safe
copy/export payload by implication.

## Non-goals

The initial Fabric client edition does not diagnose every mod or driver fault,
inspect a remote server, rewrite general logs, replace vanilla crash handling,
repair or retry failed operations automatically, gather gameplay/account
content, or promise an in-game view when rendering is unavailable. Forge client
support is a later separately tested artifact, not an implicit mode of the
Fabric client release.

## Acceptance evidence for implementation issues

- [#60](https://github.com/MinecraftProt/Stackframe/issues/60) proves separate
  metadata, side rejection/safe failure, clean build, and exact Fabric client
  startup.
- [#61](https://github.com/MinecraftProt/Stackframe/issues/61) exercises every
  eligible phase, duplicate observation, main/render/worker/shutdown behavior,
  formatter failure, and preservation of original logs and crash reports.
- [#62](https://github.com/MinecraftProt/Stackframe/issues/62) checks keyboard,
  narration, scaling, color-independent meaning, sanitized preview/copy/export,
  and no-UI fallback.
- [#65](https://github.com/MinecraftProt/Stackframe/issues/65) tests local,
  remote-reported, network, conflicting, and unknown evidence without
  unsupported blame.
- [#66](https://github.com/MinecraftProt/Stackframe/issues/66) records exact
  client compatibility rows and controlled manual graphics/UI evidence without
  real accounts or public servers.
- [#59](https://github.com/MinecraftProt/Stackframe/issues/59) publishes privacy
  guidance and limitations with the separate artifact.
- [#64](https://github.com/MinecraftProt/Stackframe/issues/64) later proves
  Forge client behavior against the same contract.
