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

`DiagnosticRenderer` accepts only a completed `DiagnosticDocument`. Callers pass
the resolved `OutputMode.PLAIN` or `OutputMode.ANSI`, a known width or the
documented unknown width fallback, and the ambiguous-character width policy
through `RenderOptions`. `OutputModeSelector` resolves the configured
`OutputPreference` (`AUTO`, `ANSI`, or `PLAIN`) before rendering.

Explicit `PLAIN` always disables styling. Explicit `ANSI` always enables fixed
SGR styling, even with `NO_COLOR`, redirected output, or CI; this is the deliberate
override for an operator who knows the destination supports it. In `AUTO`, a
nonempty `NO_COLOR`, `CLICOLOR=0`, a CI indicator, redirected output, `TERM=dumb`,
or an unknown terminal selects plain. Automatic ANSI requires an interactive
destination and a recognized terminal (`xterm`, `screen`, `tmux`, `rxvt`, `vt`,
`linux`, `cygwin`, `konsole`, `alacritty`, `kitty`, `foot`, `wezterm`, or `ansi`),
or a Windows Terminal, ANSICON, or enabled ConEmu signal. CI indicators include
`CI`, `GITHUB_ACTIONS`, `GITLAB_CI`, `TF_BUILD`, `JENKINS_URL`,
`TEAMCITY_VERSION`, `BUILDKITE`, and `CIRCLECI`.

`TerminalCapabilities.forSystemOut()` uses `System.console()` as a conservative
probe for direct standard output and snapshots only variables used by the policy.
Adapters writing through a logger, file, or hosting panel must supply the actual
destination's terminal status with `new TerminalCapabilities(isTerminal, env)`;
an uncertain status should be `false`. Capability evidence is captured once per
selection so output does not change halfway through a diagnostic. Fabric capture
and configuration will call this selector when their issues add the output path.

Rendering writes incrementally to an `Appendable`; `renderToString` is a bounded
convenience. `RenderLimits` bounds UTF-8 output bytes, logical lines, and work.
Crossing a bound throws `RenderLimitException` instead of silently dropping facts.

`SupportBundle.plan(...)` stages selected completed diagnostics and caller-supplied,
post-policy metadata as a bounded local ZIP. Its preview lists exact entry names,
uncompressed sizes, time bounds, and aggregate redaction counts before export.
The staging API has no operator command or network transport; see
[`docs/SUPPORT_BUNDLES.md`](../docs/SUPPORT_BUNDLES.md) for its format and limits.

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

ICU4J 78.3 remains under its `Unicode-3.0` license and included third-party
notices; Stackframe's Apache-2.0 license does not relicense it. The authoritative
notice is stored in
[`THIRD-PARTY-NOTICES/icu4j-78.3-LICENSE.txt`](../THIRD-PARTY-NOTICES/icu4j-78.3-LICENSE.txt)
and is copied into the published Fabric artifact.

Model text is sanitized again at the output boundary. Controls, embedded line
breaks, bidi controls, and unsupported default-ignorable characters become visible
ASCII `\u{...}` escapes before measuring or wrapping. Renderer-owned ANSI output
uses only fixed SGR emphasis and reset sequences; it does not emit colors, OSC,
cursor movement, hyperlinks, or terminal state changes.

See [`docs/DIAGNOSTIC_STYLE.md`](../docs/DIAGNOSTIC_STYLE.md) and
[`docs/WORKSTREAMS.md`](../docs/WORKSTREAMS.md).
