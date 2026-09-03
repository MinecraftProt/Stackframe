# Stackframe renderer

Deterministic terminal and plain-text output for completed diagnostics.

## Owns

- width-aware terminal layout and wrapping;
- excerpts, labels, notes, help, and trace summaries;
- ANSI styling and plain-text semantic equivalence;
- accessibility and localization rendering mechanics.

## Dependency boundary

Renderer depends on core contracts only. It does not import Fabric or Forge,
classify throwables, read server files, or bypass redaction.

## Worker notes

- Treat every displayed string as untrusted.
- Sanitize control characters and bound output.
- Calculate alignment by display width.
- Add focused golden files rather than one global snapshot.
- Prove that removing ANSI preserves all meaning.

## Terminal API

`DiagnosticRenderer` accepts only a completed `DiagnosticDocument`. Callers choose
`OutputMode.PLAIN` or `OutputMode.ANSI`, a known width or the documented unknown
width fallback, and the ambiguous-character width policy through `RenderOptions`.
Selection and terminal detection remain platform-adapter responsibilities.

Rendering writes incrementally to an `Appendable`; `renderToString` is a bounded
convenience. `RenderLimits` bounds UTF-8 output bytes, logical lines, and work.
Crossing a bound throws `RenderLimitException` instead of silently dropping facts.

Known widths target at most 100 columns. Unknown width targets 80 columns, widths
from 40 through 79 use the narrow layout, and smaller widths use that layout on a
best-effort basis. Indivisible identifiers, paths, quoted values, and other machine
tokens may exceed the target rather than being split.

## Unicode width policy

`UnicodeWidthPolicy.VERSION` identifies the renderer policy used by golden tests:
Unicode 17.0 grapheme and terminal-width policy revision 1. ICU4J 78.3 provides
the pinned Unicode 17.0 grapheme, East Asian Width, default-ignorable, and emoji
properties instead of maintaining partial handwritten tables. The renderer treats
wide/fullwidth and emoji-presentation clusters as two columns and uses the
configured one- or two-column policy for ambiguous characters. Tabs in excerpts
expand at four-column tab stops. Unsupported or uncertain clusters use linear
annotations instead of positional carets.

Model text is sanitized again at the output boundary. Controls, embedded line
breaks, bidi controls, and unsupported default-ignorable characters become visible
ASCII `\u{...}` escapes before measuring or wrapping. Renderer-owned ANSI output
uses only fixed SGR emphasis and reset sequences; it does not emit colors, OSC,
cursor movement, hyperlinks, or terminal state changes.

See [`docs/DIAGNOSTIC_STYLE.md`](../docs/DIAGNOSTIC_STYLE.md) and
[`docs/WORKSTREAMS.md`](../docs/WORKSTREAMS.md).
