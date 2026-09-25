# ADR 008: Loader-neutral diagnostic extension API

- **Status:** Proposed
- **Date:** 2026-09-25
- **Issue:** [#40](https://github.com/MinecraftProt/Stackframe/issues/40)
- **Owners:** core diagnostic and platform integration maintainers

## Context

Other mods need a way to contribute evidence and diagnostic hints without
depending on Fabric capture classes or Stackframe's renderer internals. The
completed diagnostic model permits only governed `SF####` identities and
post-redaction text. Dynamic third-party registration must not bypass those
boundaries or delay the original server error indefinitely. Redaction policy,
classifier execution, and loader capture are not yet integrated.

## Decision

Adopt the loader-neutral package and lifecycle in [EXTENSIONS.md](../EXTENSIONS.md).
An adapter binds a registration to a verified mod ID, registers one callback and
its declared `mod_id:local-name` codes, then freezes the host before events.
Duplicate namespace registrations disable all claimants, not whichever one
registered later. Registration requires API major 1; incompatible majors fail
before invocation.

Callbacks receive only a bounded `ExtensionEvent` of already-redacted
`DisplayText` values. They return bounded, typed findings with pre-redaction
`CandidateText` evidence. The host validates code ownership and evidence budgets
inside a worker, then converts all extension evidence values to the canonical
`<omitted:extension_data>` marker before returning. It exposes no callback path
to a renderer or completed document. Later redaction policy may permit more
informative transformations through a separately reviewed contract.

Extension codes are advisory machine identities, not governed Stackframe
`SF####` codes. The normal registry and arbiter determine whether evidence
justifies a final diagnosis, blame, help, or remediation. Version 1 accepts no
extension-provided prose or locale pack. Future namespaced catalogs must follow
ADR 006's typed-placeholder and fallback policy.

The host uses a bounded no-backlog daemon worker pool and per-callback/event
waits. Faulty, malformed, or timed-out callbacks are quarantined. A busy pool
or exhausted budget yields no finding, so the platform's independent original
error path and generic fallback remain available. Interruption is cooperative;
in-process code cannot be forcibly sandboxed for CPU, memory, I/O, or unrelated
logging.

## Alternatives considered

### Let mods emit completed diagnostics or own `SF####` codes

This would make integration easy but allow unreviewed titles, help, evidence
strength, remediation, and sensitive text to cross the renderer boundary.
Namespaced candidate codes keep ownership separate from canonical diagnosis.

### Pass raw throwables or normalized candidate graphs to callbacks

These values may contain secrets, server paths, hostile controls, and source
objects. Already-redacted context is sufficient for the initial API and avoids
giving every extension a new raw-data channel.

### Invoke callbacks synchronously on the logging thread

One slow or broken extension could delay the original error indefinitely. The
bounded worker pool and wait budget let the caller proceed conservatively.

### Claim hard sandboxing inside the JVM

Interrupting a thread cannot stop arbitrary Java code. Process isolation could
provide stronger boundaries but adds a separate protocol and deployment cost;
this API instead documents the in-process limitation and caps worker capacity.

## Consequences

### Positive

- Extensions have a stable loader-neutral registration and typed evidence shape.
- Namespace collisions, invalid output, and slow callbacks have bounded
  fail-open statuses without revealing candidate values or exception messages.
- Renderer, registry, and redaction ownership stay with Stackframe.

### Negative

- Evidence values are omitted until the redaction policy can transform them.
- A finding cannot become an operator diagnosis without a governed registry and
  arbitration integration.
- Adapters must verify mod ownership and preserve original logging themselves.

### Risks

- An extension can ignore interruption or write to its own output stream.
  Quarantine and bounded daemon workers protect Stackframe's call path but do
  not constitute a security sandbox.
- A trusted adapter could construct `DisplayText.visible` from unredacted data.
  Platform integration and redaction tests must prove the pre-callback boundary.
- A future bridge might mistake an extension code for a canonical `SF####` code.
  Registry validation and contract tests must block that promotion.

## Validation

The reference extension and core contract tests cover safe input, typed
evidence, namespace collisions, undeclared codes, malformed returns, exceptions,
timeouts, quarantine, hostile candidate text, and lifecycle/version rejection.
Platform integration must later demonstrate that an extension failure never
suppresses the original log or crash report, that pre-callback values are
redacted, and that the arbiter applies the governed evidence ceiling.

## Follow-up

- [#10](https://github.com/MinecraftProt/Stackframe/issues/10) supplies Fabric
  capture and an independent original-error path.
- [#27](https://github.com/MinecraftProt/Stackframe/issues/27) supplies reviewed
  redaction of extension-proposed values.
- [#41](https://github.com/MinecraftProt/Stackframe/issues/41) supplies
  classifier arbitration before any specialized extension finding is emitted.
- [#46](https://github.com/MinecraftProt/Stackframe/issues/46) governs any later
  extension messages or locale bundles.
