# Diagnostic localization strategy

This is the design contract for [issue #46](https://github.com/MinecraftProt/Stackframe/issues/46). It defines how future translations may
change operator wording without changing a diagnosis, its evidence, or its
safety rules. It does not claim that translated artifacts ship today. The terms
**MUST**, **MUST NOT**, **SHOULD**, and **MAY** are normative. [ADR 006](decisions/006-diagnostic-localization.md) records the
decision.

## First release and locale selection

The first Stackframe release emits English operator text only. Its bundled
canonical locale is `en`. It does not advertise translation support or select a
language from the JVM, operating system, server location, or a player's account.
The English catalog is version controlled and required for every emitted message
key.

A future release may accept an explicit BCP 47 locale tag such as `de-CH`.
Resolution follows the requested locale, then its language fallback (for example
`de-CH` to `de`), then bundled `en`. With no explicit selection, use `en`. Unsupported
or malformed locale tags use `en` and report the configuration problem without
altering the diagnostic. Locale selection is a presentation choice, never an
input to classification, severity, code selection, evidence, redaction, or
remediation.

A locale is called supported only after its required catalog keys, plural
branches, accessibility text, layout fixtures, and terminology review pass for a
published artifact. A partial catalog may be loaded as a development preview,
but missing messages fall back per complete message to English and do not
justify a support claim.

## Identity and message ownership

A `DiagnosticCode` and the registry's symbolic key identify a diagnostic. Translation keys
identify pieces of approved prose; neither translated text nor a title hash
identifies the diagnostic. Codes such as `SF0001`, symbolic keys, evidence
references, correlation IDs, schema member names, and documentation anchors
remain ASCII and stable in every locale. The title key for a registry entry
remains its symbolic key plus `.title`, as required by the current registry.
Translating a title changes only the resolved `CatalogText.value`, never its key or the code's
meaning.

| Owner | Owns |
| --- | --- |
| Core registry and catalog | Canonical English diagnostic title, approved cause/help/note message keys, named placeholder schemas, evidence and remediation constraints |
| Renderer catalog | Fixed severity and field labels, omission and trace wording, accessibility phrasing, and layout that does not infer facts |
| Platform adapter | Verified technical values and operation scope translated into loader-neutral inputs; no locale-specific classifier logic |
| Extension author | Namespaced canonical English templates and optional translations under the same validation and redaction rules |
| Documentation/review | Terminology glossary, code-indexed guidance, translation review, and evidence for a locale support claim |

Catalog keys are stable lower-case ASCII identifiers matching the existing `CatalogText`
key grammar. Diagnostic messages use the registry's existing symbolic key as
their stem; fixed renderer labels use a separate renderer namespace. A key is
not constructed from an exception message, mod name, path, remote response, or
other external text. Key removal or semantic reuse requires compatibility
review. Wording corrections that preserve the same claim do not allocate a new
diagnostic code.

The registry's canonical English title and title contract remain governed data.
Other catalog templates are selected only after the classifier and arbiter have
chosen supported claims. Translation cannot add a cause, name a responsible mod,
weaken uncertainty, create remediation, or turn a question into a verified
assertion. A translated help sentence must retain the same prerequisite, risk,
and action as the reviewed English source. The English grammar rules in [DIAGNOSTIC_STYLE.md](DIAGNOSTIC_STYLE.md)
apply to English; another locale may use its own word order, case, and
punctuation while preserving semantic fields and accessibility.

Registry titles have an empty placeholder schema. A `CatalogText.value` is project-owned wording
and never incorporates external input; dynamic values belong in typed locations,
notes, or help as safe `DisplayText`. A template that combines approved prose with visible
public values must produce a value satisfying the completed model's `DisplayText`
invariants, never a `CatalogText` containing copied exception or platform data. Redacted,
generalized, or omitted values retain their own `DisplayText` disposition and marker in
separate fields; they are not flattened into a visible public sentence to make
interpolation convenient.

## Typed placeholder contract

Each message key has a versioned, reviewable schema of named placeholders. Names
are stable ASCII identifiers. Each entry declares a type, requiredness,
sensitivity/visibility policy, and permitted use. Templates may reorder
placeholders to fit grammar; they cannot invent one, change its type, omit a
required one, or interpolate an arbitrary object. Runtime positional formatting,
string concatenation of translated fragments, and translator-supplied format
specifiers are not part of the contract.

| Placeholder type | Example | Formatting rule |
| --- | --- | --- |
| `COUNT` | omitted frame count | Approved non-negative count, never a secret's length; locale cardinal plural rule and human-readable number format may be used in prose |
| `TECHNICAL_TOKEN` | mod ID, registry key, configuration key, version, port | Preserve sanitized source spelling and digits; no translation, case folding, grouping, or bidi reordering of the token |
| `SAFE_TEXT` | bounded, public detail or verified component name | Accept only post-policy, visible/public `DisplayText`; insert as inert text, never as template syntax or renderer markup |
| `SAFE_LOCATION` | verified relative path or location | Accept only an approved, visible/public location; preserve its technical spelling and indivisible-token behavior |
| `OPAQUE_ID` | diagnostic/correlation ID | Preserve exact ASCII token and searchability |

A template interpolates only values that policy has made visible/public.
Protected values instead remain separate redacted, generalized, or omitted `DisplayText`
fields, and the approved message uses wording that remains accurate without the
hidden value. A producer must not relabel a protected value as public to satisfy
a placeholder. `CandidateText`, raw throwable messages, platform objects, and file contents
cannot be passed to the resolver. Required placeholders must occur in every
applicable template branch. Optional placeholders may be omitted only when the
message still has the same meaning. Validation rejects unknown names, missing
required names, type mismatches, unsupported syntax, excessive repetition,
unbounded output, and control characters.

For example, a reviewed message could declare `count: COUNT` and `mod: TECHNICAL_TOKEN` with a semantic
English source equivalent to "one frame from {mod} was omitted" or "{count}
frames from {mod} were omitted". A translator supplies complete locale-specific
singular and plural branches; the numeric count remains typed and the mod ID
remains unchanged. The actual message keys and wording are governed with the
owning diagnostic or renderer, not inferred from this example.

## Resolution, pluralization, and fallback

Localization resolves catalog text before constructing the completed `DiagnosticDocument`. The
safe order is: select the diagnostic and approved message keys; classify and
redact candidate placeholder values; select a validated locale template and
plural branch; interpolate only typed, visible/public values; sanitize and bound
the assembled text; then create the fixed `CatalogText` title or safe `DisplayText` message in the
immutable document. Protected marker fields remain separate. Renderers receive
only that completed document. No locale pack can see an unredacted candidate or
call back into platform state.

Use named plural/select branches for whole messages under a pinned, documented
CLDR-compatible rule set. The `other` branch is required, and every category
reachable under a supported locale is validated. The count drives plural
selection; translators cannot supply a numeric expression. A message with
plural-dependent grammar is translated as a whole, including units and
surrounding words. Do not append an English suffix or assemble a sentence from
independently translated fragments. The exact library and catalog file format
are implementation choices, but their version and accepted syntax must be pinned
for repeatable tests.

Catalog loading validates every template and branch against its schema before
use. An absent or invalid localized message falls back as one complete message
to the next locale in the chain, then to canonical English. It must not mix a
translated clause with an English suffix or silently drop a required fact.
Fallback is recorded in bounded development diagnostics without printing
protected placeholder values. One bad entry is isolated; it does not disable
valid messages in the same catalog.

If the required English source or its schema is absent or invalid, no unverified
string is rendered as a diagnostic. The enhancement fails open to the original
event and reports Stackframe's own failure separately where safe, under [PROJECT.md](PROJECT.md) and
[SECURITY_AND_PRIVACY.md](SECURITY_AND_PRIVACY.md). Translation failure alone does not change the chosen diagnostic code or
authorize a generic cause or remedy.

## Raw data and output boundary

Only approved operator prose is localized. Exception class names and messages,
stack frames, file paths, mod IDs, registry IDs, versions, commands, source
excerpts, server addresses, diagnostic/correlation IDs, and redaction markers
are technical or external data. They remain in source spelling after the
existing validation, sanitization, and redaction policy; translation cannot
reinterpret or reverse a typed marker. A localized label may explain a marker,
but the marker itself remains a stable machine-visible token.

The English title, localized title, notes, and help must express the same
evidence-backed facts. Plain and ANSI profiles, narration, client views, and
future structured projections consume the same completed values; style changes
cannot alter the words' claims. Structured field names, enums, numeric data,
codes, and identifiers remain stable machine data. Before shipping non-English
structured text, the versioned structured schema work in [issue #30](https://github.com/MinecraftProt/Stackframe/issues/30) must define an
explicit locale signal so consumers never guess from prose.

Catalog templates and placeholder values cannot contain live ANSI, OSC, terminal
control, cursor, hyperlink, or styling directives. Placeholders are escaped as
inert text; only fixed renderer templates may add approved styling. A malicious
value such as a newline followed by a fake diagnostic header remains one
sanitized value. The completed text still passes the model's length and document
budgets.

## Layout, accessibility, and documentation

Choose a plural branch and resolve the complete message before wrapping. The
renderer measures the final sanitized grapheme clusters and display width using
the policy in [DIAGNOSTIC_STYLE.md](DIAGNOSTIC_STYLE.md#width-and-wrapping). Translation files contain no manual padding, hard line breaks
for terminal width, or positional carets. Long translated prose uses the same
narrow linear fallback; required facts are not truncated to fit. Machine tokens
stay indivisible even when a line exceeds the target width. Source ranges refer
to post-redaction display text, as the shared model already requires.

Severity words, labels, omissions, relationships, and trace state remain
explicit in plain text and narration. A locale cannot rely on color or symbol
shape to convey a fact. Right-to-left language support is not implied by
accepting a locale tag: it requires separate bidi, screen-reader, copy/paste,
and technical-token evidence while still rejecting untrusted bidi controls.

Search and support documentation remain indexed by the stable diagnostic code
and symbolic key. English guidance is the canonical fallback. A future
translated page keeps the same code and meaning and links back to canonical
guidance; changing its URL or title cannot change diagnostic identity.

## Extension messages and review

The future extension API in [issue #40](https://github.com/MinecraftProt/Stackframe/issues/40) may supply only namespaced message keys, a
canonical English template, declared typed placeholders, and optional locale
variants. The namespace is owned by a stable extension identifier; collisions
and attempts to replace Stackframe core labels, keys, or codes are rejected.
Extension templates use the same preflight validation, redaction boundary,
budgets, fallback chain, and renderer sanitization. An invalid extension
translation falls back to its valid English source; an invalid English source
isolates that extension's diagnostic and preserves the original event.
Extensions cannot grant themselves an `SF####` code outside registry governance or
load translations from a network endpoint at diagnostic time.

Every shipped translation change reviews technical terms, evidence strength,
blame, remediation safety, privacy markers, and accessibility with a
domain-aware reviewer fluent in that locale. Tests must include missing and
malformed entries, unknown and mismatched placeholders, hostile text, plural
boundaries (including zero and large counts), narrow/Unicode wrapping,
ANSI/plain semantic equivalence, and stable code/search behavior. Exact locale,
catalog, and plural-rule versions belong in the test record. This design creates
no new runtime language option or release claim in the English-first MVP.
