# Diagnostic extension API

Issue #40 introduces a loader-neutral, in-process contribution API in
`org.minecraftprot.stackframe.extension`. It is an integration contract for
future loader adapters and the diagnostic pipeline, not an automatic mod
discovery system or a supported released feature. No Fabric, Forge, or client
adapter calls it yet.

## Registration and lifecycle

An adapter verifies a mod's stable ID and creates an `ExtensionNamespace` from
it. `ExtensionRegistration` declares API major version `1`, up to 32
`ExtensionCode`s in that namespace, and one `DiagnosticExtension` callback.
Each code has the machine form `mod_id:local-name`. The adapter calls
`ExtensionHost.register` during setup, then `freeze` before observing failures.
Late registration is rejected. `close` stops accepting events and requests
worker interruption.

`stackframe` and `minecraft` namespaces are reserved. Duplicate namespaces
disable **all** claimants for that namespace, regardless of registration order;
neither callback is invoked. A callback returning an undeclared code or a
duplicate finding is malformed and quarantined. Registrations and outcomes are
evaluated in namespace order. An adapter must bind registration to verified mod
metadata: a namespace string by itself is not proof that the caller owns a mod.

Extension codes are separate from the governed `SF####` diagnostic registry.
Mods cannot allocate, replace, or strengthen a Stackframe code through this
API. A namespaced finding is a candidate for later validation, not an operator
diagnosis. Assigning a final Stackframe code, title, blame, help, or remediation
requires the canonical registry and classifier arbitration contracts. The
reference extension deliberately supplies only a heuristic/contextual hint.

## Input and output safety

`ExtensionEvent` contains a `DisplayText` failure type and at most 16 keyed
`DisplayText` context values. The trusted pipeline must classify and redact
them before constructing the event. A callback never receives the original
`Throwable`, raw normalized graph, server object, file handle, renderer, or
output stream through this API. Context is copied into an immutable snapshot.

A callback returns up to eight `ExtensionFinding`s by default. Each finding has
one namespaced code and typed `ExtensionEvidence`: source key, `EvidenceKind`,
strength, claim capabilities, and `CandidateText`. Candidate text remains
untrusted even if the extension labels it public. Contextual or heuristic
evidence cannot assert ownership or a remedy, and message patterns are always
heuristic. The eventual arbiter must still verify source bindings, independence,
confidence, and the chosen diagnostic's evidence contract under
[ADR 004](decisions/004-classifier-arbitration.md).

The current host **never returns raw extension text**. It validates the bounded
contribution and replaces every proposed evidence value with the canonical
`<omitted:extension_data>` marker (`DisplayText`, `SECRET`, `EXTENSION_DATA`).
Only `SafeExtensionFinding` values leave the host. This is the conservative
boundary until [redaction policy #27](https://github.com/MinecraftProt/Stackframe/issues/27)
provides a reviewed way to transform extension data. No extension title,
template, ANSI sequence, or help text enters a completed `DiagnosticDocument`
through this API. Renderers continue to accept only completed documents.

This contract controls data routed **through Stackframe**. An installed mod is
ordinary in-process Java code and can independently write to its own logger or
`System.out`; Stackframe cannot sandbox or prevent that unrelated behavior.
Operators must trust the mods they install.

## Isolation and fallback

Default limits are 32 distinct namespaces, eight findings per callback, 16
evidence items per finding, 16,384 proposed evidence code points per callback,
four daemon workers, a 50 ms callback wait, and a 200 ms event wait. The worker
queue has no backlog. Validation runs in the same worker budget as the callback.
An exception, malformed return, or timeout quarantines that extension for the
host lifetime. Busy workers and an exhausted event budget produce status-only
outcomes, with no accepted finding. Statuses never include callback exception
messages or candidate values.

The platform keeps the original error path independent of extension evaluation.
If no later governed candidate is eligible, the platform uses the generic
`SF0001` fallback; it never suppresses the original event because an extension
failed.
This host supplies bounded enhancement results and failure statuses. It does
not itself log the original error or create a completed diagnostic.

Java interruption is cooperative. A callback that ignores interruption may
continue on a daemon worker after its timeout; the host quarantines it and caps
the worker pool, so later calls fail open when capacity is exhausted. This is a
bounded in-process integration, not a hard CPU, memory, I/O, or security sandbox.
An adapter requiring stronger containment must use an external process.

## Versioning and localization

API major `1` is required at registration. An unknown major is rejected before
any callback runs. Changes to code identity, evidence meaning, redaction, or
fallback require compatibility review and a new major when they break existing
extensions. Adding loader-specific types to this package is forbidden.

Version 1 supplies namespaced machine codes and no extension-provided prose or
locale bundles. Future English templates and locale variants must use
namespaced keys, typed placeholders, registration-time validation, English
fallback, and the same redaction and layout boundary defined by
[localization policy](LOCALIZATION.md). A mod cannot replace core labels,
diagnostic titles, or `SF####` catalog entries at runtime.

The small [reference extension](../stackframe-core/src/test/java/org/minecraftprot/stackframe/extension/ReferenceExtension.java)
and [contract tests](../stackframe-core/src/test/java/org/minecraftprot/stackframe/extension/ExtensionHostTest.java)
show registration, safe context, typed evidence, collisions, malformed returns,
timeouts, quarantine, and omission of hostile text. Production integration
remains dependent on [#10](https://github.com/MinecraftProt/Stackframe/issues/10),
[#27](https://github.com/MinecraftProt/Stackframe/issues/27), and
[#41](https://github.com/MinecraftProt/Stackframe/issues/41).
