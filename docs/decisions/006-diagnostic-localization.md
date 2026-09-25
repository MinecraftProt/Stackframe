# ADR 006: Localize catalog prose after typed redaction

- **Status:** Proposed
- **Date:** 2026-09-25
- **Issue:** [#46](https://github.com/MinecraftProt/Stackframe/issues/46)
- **Owners:** Core diagnostic, renderer, and documentation maintainers

## Context

Stackframe's first operator output is English. The shared model already keeps a
stable `DiagnosticCode` apart from a keyed, resolved `CatalogText.value` and
requires external values to pass from `CandidateText` through redaction into
`DisplayText`. The renderer must not classify failures or bypass redaction.
Translation introduced at the wrong boundary could alter responsibility or help,
inject control sequences through placeholders, change machine identifiers, or
produce text that no longer fits the plain accessibility layout.

[DIAGNOSTIC_STYLE.md](../DIAGNOSTIC_STYLE.md) defines English grammar and
width behavior. The registry governs stable codes and canonical English titles.
A localization choice is needed before message catalogs and extension messages
become implementation contracts.

## Decision

Adopt [LOCALIZATION.md](../LOCALIZATION.md) as the diagnostic localization
contract.

1. Ship the first release in English (`en`) only. Future explicit BCP 47 locale
   selection falls back through a language variant and finally bundled English.
   Do not infer locale from the JVM, host, server, or account.
2. Keep diagnostic code, symbolic key, severity, evidence, remediation policy,
   trace state, and structured machine fields independent of translated prose.
   The existing registry title key remains symbolic key plus `.title`.
3. Give each approved message key a named, typed placeholder schema. Redact
   candidate values before template resolution; only visible public values
   enter interpolated prose. Protected markers retain separate typed fields.
   Compile and validate complete templates and plural branches before use.
   Titles remain fixed project-owned `CatalogText` without external
   placeholders; assembled dynamic prose becomes safe `DisplayText`. Text is
   sanitized and bounded before it becomes a completed document.
4. Treat a missing or invalid localized message as a per-message fallback to
   the next locale, then the English source. Do not mix partial translations.
   If the English source is unusable, preserve the original event and isolate
   Stackframe's enhancement failure rather than rendering unverified claims.
5. Localize complete operator prose, not raw exception data, identifiers,
   paths, versions, codes, correlation IDs, or redaction markers. Renderers wrap
   the final text according to the existing Unicode/display-width policy.
   Future extension catalogs use namespaced keys and the same safety boundary.

The current `DiagnosticDocument` remains the renderer input. This ADR does not
add a model field or choose a file format or formatting library. Non-English
structured text needs an explicit locale signal in the separately versioned
schema work for #30 before it can be claimed supported.

## Alternatives considered

### Translate final terminal lines

This is easy to graft onto one output mode, but it would parse layout as data,
risk changing technical tokens, and let ANSI, plain, JSON, and client screens
disagree. Translation belongs before the completed document is rendered.

### Translate raw exceptions or external metadata

Machine translation or string replacement could make a Java exception easier to
read, but it would change evidence, damage exact identifiers, and expose
unredacted data to a translation system. Raw data stays technical and protected.

### Let each renderer choose templates

Renderer-specific catalogs would allow different facts or fallback behavior in
ANSI, plain, structured, and narrated output. One resolved completed document
keeps their semantics aligned.

### Require complete translations or reject the whole locale pack

This avoids mixed languages but one missing message could disable many valid
translations. Per-message English fallback preserves an accurate diagnostic
while still requiring full coverage before a locale is advertised as supported.

## Consequences

### Positive

- Stable codes and documentation remain searchable across languages.
- Typed safe placeholders and catalog validation keep translation outside the
  redaction and classifier trust boundaries.
- English-only launch does not require a later model or code-identity rewrite.
- A broken locale entry has a bounded fallback.

### Negative

- Catalog keys, placeholder schemas, plural rules, and reviews add maintenance.
- One diagnostic may contain an English fallback message inside an otherwise
  translated view until the catalog is repaired.
- The current structured schema has no locale field; non-English structured
  output waits for an explicit schema decision.

### Risks

- A translation could strengthen blame or weaken a safety prerequisite. A
  domain-aware review and evidence/remediation fixtures gate supported locales.
- A long translation or right-to-left text could break alignment or technical
  token order. Golden layout, narration, bidi, and copy/paste fixtures gate
  support; plain linear output is the safe fallback.
- An extension could provide malicious or incompatible templates. Namespace,
  schema, budget, and control-character validation isolate it before rendering.

## Validation

This is a documentation contract, so validate local links and consistency with
the model, style, registry, and privacy policy. Implementation must test invalid
locale tags, missing/invalid templates, every plural category, typed placeholder
mismatch, hostile controls, redaction markers, stable codes, English fallback,
narrow Unicode layout, and equivalent ANSI/plain facts. No translated locale is
claimed supported by this ADR alone.

## Follow-up

- [#27](https://github.com/MinecraftProt/Stackframe/issues/27) supplies the
  redaction boundary consumed by safe placeholder resolution.
- [#30](https://github.com/MinecraftProt/Stackframe/issues/30) defines the
  structured locale signal before non-English structured output ships.
- [#40](https://github.com/MinecraftProt/Stackframe/issues/40) applies
  namespace and catalog validation to extension messages.
- Renderer/catalog implementation and future locale releases add fixtures and
  language review required by [LOCALIZATION.md](../LOCALIZATION.md).
