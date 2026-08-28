# Diagnostic style guide

Readable diagnostics are a functional contract, not decorative console output.
This guide defines the English operator-facing presentation contract for terminal
and plain-text renderers. It does not define the diagnostic data model, select a
classifier, change classifier confidence, or authorize a renderer to infer facts.

The key words **must**, **must not**, **required**, **should**, and **should not**
describe requirements that renderer snapshots and reviews can test.

## Contract boundaries

- The completed diagnostic model supplies severity, code, title, locations,
  annotations, facts, help, and trace information. Renderers preserve that
  meaning and order.
- The classifier owns diagnostic selection, confidence, and blame. A renderer
  must not strengthen `may`, `possible`, or other uncertain text into a fact.
- Redaction occurs before rendering. A renderer must not recover, re-read, or
  expose a redacted value.
- Structured output follows its versioned schema. It carries the same facts but
  does not reproduce terminal decoration.
- If safe layout is impossible, output must degrade to the linear plain format.
  Formatting failure must never replace or hide the original server error.

See [PROJECT.md](PROJECT.md), [SECURITY_AND_PRIVACY.md](SECURITY_AND_PRIVACY.md),
and the renderer [ownership boundary](../stackframe-renderer/README.md).

## Information order

Every diagnostic answers these questions in order:

1. **What failed?** A short title tied to an operation.
2. **Where?** The most useful verified location or component.
3. **Why?** Facts from exceptions, metadata, or validated context.
4. **What next?** A safe, specific action or a request for more evidence.
5. **Where are details?** A correlation ID or full-trace destination.

The primary diagnostic must appear before excerpts or long supporting notes. Do
not lead with a Java class, logger name, or stack frame when the model contains a
verified server concept.

## Message anatomy

The styled terminal form may use the rustc-inspired ASCII gutter:

```text
error[SF3004]: mod dependency is missing
  --> mods/example-addon.jar
   |
   = mod: example-addon 2.1.0
   = requires: example-core >=3.0.0
   = found: example-core is not installed
   = help: install a compatible example-core release, then restart the server
   = trace: full details saved as diagnostic 7F2A91
```

The plain form must serialize the same information in a quiet, linear reading
order:

```text
error[SF3004]: mod dependency is missing
location: mods/example-addon.jar
mod: example-addon 2.1.0
requires: example-core >=3.0.0
found: example-core is not installed
help: install a compatible example-core release, then restart the server
trace: full details saved as diagnostic 7F2A91
```

Plain output is the accessibility and compatibility baseline. Styled output may
add color, weight, indentation, and ASCII gutters, but it must not add, remove,
or alter a fact. Reading the plain lines from top to bottom must communicate the
relationships without relying on color, shape, indentation, or cursor position.
This implements the principles behind WCAG 2.2
[Info and Relationships](https://www.w3.org/TR/WCAG22/#info-and-relationships),
[Sensory Characteristics](https://www.w3.org/TR/WCAG22/#sensory-characteristics),
and [Use of Color](https://www.w3.org/TR/WCAG22/#use-of-color) for console
output.

## Severity vocabulary

Only these lowercase severity words are approved:

| Severity | Meaning | Must not be used merely because |
| --- | --- | --- |
| `error` | An operation failed, data could not be accepted, or the server cannot continue safely. | Exception text sounds alarming. |
| `warning` | The operation continued with a verified risk, limitation, or degraded behavior. | Advice may be useful. |
| `note` | Supporting information is attached to another diagnostic. | The renderer needs a synonym for informational logging. |

Do not emit `fatal`, `critical`, `info`, `debug`, `success`, or custom severity
synonyms in the diagnostic header. Ordinary informational logs are outside
Stackframe's scope.

Severity is an impact supplied by the completed model. The renderer must not
promote or demote it based on title text, exception type, color availability, or
terminal capabilities. The full word remains visible in every output mode.

## Diagnostic code

A code is a stable, uppercase search key in the header:

```text
error[SF2003]: datapack validation failed
```

The code identifies diagnostic meaning, not a Java class or wording revision.
Render it without added spaces or localized digits. Do not abbreviate or hide it
behind a hyperlink. Reusing a code for a different cause is a model/catalog
breaking change, not an editorial change.

## Grammar by message part

This contract applies to English output. Future localization may change grammar,
but it must preserve field meaning and the accessibility requirements.

| Part | Required grammar | Punctuation |
| --- | --- | --- |
| Title | Lowercase concise clause naming the failed or degraded operation. | No terminal period, exclamation mark, or remediation. |
| Location | Canonical operator-actionable noun or path supplied by the model. | No invented prefix beyond `location:` or `-->`. |
| Fact label | Lowercase concrete noun or short noun phrase. | One colon after the label. |
| `cause` | Lowercase declarative fact that materially explains the outcome. | No terminal period for a single sentence. |
| `note` | Lowercase declarative supporting fact. | No terminal period for a single sentence. |
| `help` | Lowercase imperative action, including prerequisites or risk when needed. | No terminal period for a single sentence. |
| `trace` | Lowercase declarative statement of preservation, omitted-frame count, or details location. | No terminal period for a single sentence. |

Use sentence punctuation when a field genuinely contains multiple sentences.
Do not remove punctuation from quoted source text. Keep one space after `:` and
one space between words. Do not use all caps for emphasis.

### Titles

Approved:

```text
server port is already in use
datapack validation failed
level metadata could not be read
```

Rejected:

| Text | Reason |
| --- | --- |
| `An Error Has Occurred` | Title case, vague, and repeats severity. |
| `java.net.BindException was thrown` | Leads with an implementation detail instead of the operation. |
| `Oops! This mod broke your server` | Joke, unsupported blame, and alarmist punctuation. |
| `Fix your server.properties` | Remediation belongs in `help`; the title does not say what failed. |
| `CRITICAL FAILURE!!!` | Unapproved severity, all caps, and punctuation as alarm. |

Prefer a concrete verb such as `failed`, `could not be read`, `is missing`, or
`is already in use`. Use `crashed` only when the process actually terminated.
Use `corrupted` only when evidence proves corruption; `unreadable` is not
equivalent.

### Facts, causes, and notes

Facts state what the evidence proves:

```text
cause: no registry entry matches "example:missing_block"
note: the server continued without the optional resource pack
```

Rejected:

```text
cause: a registry error occurred
note: something strange happened
cause: ExampleMod is probably buggy
```

The first two lines add no actionable evidence. The last exposes speculation as
blame. When several causes matter, order them from the operator-facing cause
toward the lower technical cause. Preserve complete causal order in the debug
record.

`note` as a field label and `note` as a model severity are spelled the same but
occupy unambiguous positions: a severity starts a diagnostic header; a note field
appears as `note:` within a diagnostic.

### Help and remediation tone

Help is optional. Incorrect advice is worse than no advice.

Approved:

```text
help: install example-core 3.x, then restart the server
help: check diagnostic C9012E for the complete exception and configuration key
help: restore level.dat_old only after making a copy of the world directory
```

Rejected:

| Text | Reason |
| --- | --- |
| `help: fix the config` | Vague; neither value nor action is identified. |
| `help: simply delete the world` | Destructive, dismissive, and missing risk. |
| `help: give the server all permissions` | Unsafe and broader than the evidence. |
| `help: add more RAM` | Unsupported unless memory evidence specifically justifies it. |
| `help: this will fix it` | Promises an outcome the diagnostic cannot guarantee. |
| `help: contact the idiot who made ExampleMod` | Abusive and assigns blame. |

Use calm, direct language:

- Address the failed operation, not the operator's competence.
- Use `check`, `verify`, or `try` when the outcome is uncertain.
- Name a prerequisite before a destructive or availability-affecting action.
- Never recommend deleting world data, disabling security, exposing secrets,
  granting broad permissions, or downloading from an unverified source as a
  routine first step.
- Do not use jokes, sarcasm, `obviously`, `simply`, `just`, `user error`, or
  `something went wrong`.
- When evidence is insufficient, direct the operator to the correlated trace or
  a sanitized, local support bundle. Do not invent a likely fix.

## Operator terminology

Prefer terms an operator sees in server files, hosting panels, and loader output.
Use one term consistently within a diagnostic.

| Prefer | Use only when specifically meant | Avoid |
| --- | --- | --- |
| `operator` | `player`, `developer`, `hosting provider` | `end user`, `customer` |
| `server` or `dedicated server` | `JVM` for the Java process/runtime | `client` for a server event |
| `mod` and the verified display name | `mod ID` for the exact machine identifier | `plugin` unless it is actually a plugin |
| `configuration`, `configuration key`, `configuration file` | Exact filename such as `server.properties` | `config stuff` |
| `world`, `datapack`, `resource pack`, `registry entry` | `level` only for the Minecraft level concept or exact filename | Generic `data` when a known concept exists |
| `file`, `directory`, `path` | `folder` only when quoting another interface | `location on disk thing` |
| `full trace`, `debug record`, `diagnostic ID` | `stack frame` in technical detail | `details` as a field label |

Use `trace:` as the standard field label for collapsed-frame counts and the
location or identifier of complete details. Do not alternate between `details:`,
`stack:`, and `trace:`. Keep exact identifiers, filenames, versions,
configuration keys, and command fragments in their source spelling.

## Location and source context

Prefer the most operator-actionable verified location supplied by the model:

1. configuration key or bounded source excerpt;
2. world, datapack, resource, or mod file;
3. verified mod and component;
4. mapped and version-matched source frame;
5. lifecycle phase;
6. no location.

Never invent a location. Paths must already be canonicalized, allowed, and
redacted according to [SECURITY_AND_PRIVACY.md](SECURITY_AND_PRIVACY.md). An
exception-provided path is not permission to read a file.

Excerpts must be bounded and must identify omitted content. Line numbers remain
decimal and must not be zero-padded solely for alignment. If sanitization or
display-width uncertainty prevents an accurate caret, use a linear annotation:

```text
location: world/datapacks/example/data/example/worldgen/item.json
context line 12: "type": "minecraft:unknown"
annotation line 12: "minecraft:unknown" has no matching registry entry
```

An inaccurate caret is worse than no caret.

## Width and wrapping

Width is a layout constraint, not permission to discard meaning.

### Width selection

- For an interactive terminal with a known positive column count, target the
  smaller of the terminal width and 100 columns.
- When width is unknown, use an 80-column fallback.
- At 40 through 79 columns, switch to the narrow layout: one field per line,
  hanging indentation for wrapped prose, and no side-by-side content.
- Below 40 columns, use the same linear layout on a best-effort basis. Do not
  truncate required facts to claim support for an unusably small width.
- Redirected files, CI logs, and hosting panels use plain layout unless a
  capability is explicitly configured. They use a known configured width or the
  80-column fallback; they must not receive cursor-control sequences.

### Wrapping algorithm

- Wrap prose at extended grapheme-cluster boundaries, preferring whitespace and
  punctuation before the target width.
- Continuation lines use a deterministic hanging indent of two spaces after the
  field label's indentation. Do not repeat the label unless a new field begins.
- Never split a diagnostic code, correlation ID, version, configuration key,
  command fragment, quoted value, or path merely to meet the target. A single
  indivisible token may exceed the target width.
- Bound oversized values according to the model's output budget. Show an
  explicit omission marker and put the recoverable full value only in the
  appropriately protected debug record.
- Never use terminal cursor movement to simulate wrapping. Emit logical lines
  separated by line feed.
- Do not pad trailing spaces. Output uses `LF`; platform-specific file adapters
  may translate line endings only at the final file boundary.

Wrapping must be deterministic for the same diagnostic, width, Unicode-data
version, and output profile.

### Narrow example

At 40 columns, prefer:

```text
error[SF3004]: mod dependency is
  missing
location:
  mods/example-addon.jar
mod:
  example-addon 2.1.0
requires:
  example-core >=3.0.0
found:
  example-core is not installed
help:
  install a compatible example-core
  release, then restart the server
trace:
  full details saved as diagnostic
  7F2A91
```

Do not compress the same diagnostic into unexplained icons or truncate the help
line.

## Unicode and display width

String length, UTF-16 code-unit count, code-point count, and displayed columns
are different measurements. Alignment and wrapping must use one renderer-wide
display-width service.

- Segment text into extended grapheme clusters using
  [Unicode Standard Annex #29](https://www.unicode.org/reports/tr29/). Never wrap
  inside a combining sequence, emoji sequence, or other recognized cluster.
- Base column-width decisions on a documented Unicode data version and
  [Unicode Standard Annex #11](https://www.unicode.org/reports/tr11/), with a
  terminal-specific policy. UAX #11 explicitly requires tailoring for actual
  display environments; its East Asian Width property alone is not a complete
  terminal-width algorithm.
- Treat wide and fullwidth characters as two columns and ordinary narrow
  characters as one under the selected terminal policy. Handle ambiguous-width
  characters consistently as one or two columns according to an explicit
  renderer setting or detected environment.
- Record the Unicode-data and width-policy version in golden-test diagnostics so
  dependency updates cannot silently move carets.
- Expand allowed excerpt tabs to spaces at fixed four-column tab stops before
  measuring. Tabs in ordinary model values are escaped instead.
- Do not normalize, case-fold, transliterate, or replace a machine identifier
  merely to improve alignment. Such changes can make copied values incorrect.
- If the renderer cannot measure a cluster reliably, omit positional art and use
  the linear annotation form. Preserve the sanitized text.

Golden fixtures must include ASCII, a combining sequence, CJK wide characters,
an emoji sequence, and an ambiguous-width character.

## Untrusted text and control characters

Every external string remains untrusted after it enters the renderer. Redaction
and rendering sanitization solve different problems and both are required.

For interpolated values:

- Escape C0 controls, `DEL`, C1 controls, carriage return, escape, Unicode line
  separator, Unicode paragraph separator, and bidirectional embedding,
  override, and isolate controls as visible ASCII code-point escapes such as
  `\u{001B}`.
- Render embedded line feed as the two visible characters `\n`. Only the
  renderer's own layout may emit a physical line feed.
- Render tab as `\t`, except for the bounded source-excerpt expansion described
  above.
- Preserve joiners and variation selectors only as part of a valid bounded
  grapheme cluster. Escape orphaned or unsupported default-ignorable characters.
- Apply sanitization before measuring, wrapping, padding, or positioning labels.
- Never pass through an ANSI, OSC, hyperlink, terminal-title, clipboard, cursor,
  or erase sequence from model data. ANSI styling may come only from fixed
  renderer templates after values are sanitized.
- Do not let an untrusted value create a fake severity header, field, log record,
  or JSON member.

For example, an exception value containing an escape sequence and newline:

```text
bad\u{001B}[2J\nerror[SF9999]: forged
```

must remain one logical value. It must not clear the terminal or create a second
diagnostic. This also addresses the log neutralization risk described by
[CWE-117](https://cwe.mitre.org/data/definitions/117.html).

## Color, emphasis, and contrast

Color is optional emphasis. It never conveys unique meaning.

- Severity words, diagnostic codes, labels, omission notices, selected source
  spans, and relationships remain explicit when all styling is removed.
- Do not describe an item only as `the red line`, `the highlighted value`, or
  `the item above`. Name the item or its label.
- Respect `NO_COLOR`. Stackframe's explicit plain setting also disables ANSI.
  Do not emit ANSI in automatic mode when output is redirected or capability is
  unknown.
- When Stackframe controls both text and background colors, normal text must
  meet the WCAG 2.2 [Contrast Minimum](https://www.w3.org/TR/WCAG22/#contrast-minimum)
  ratio of at least 4.5:1. Stackframe-drawn non-text indicators needed to locate
  content must meet the
  [Non-text Contrast](https://www.w3.org/TR/WCAG22/#non-text-contrast) ratio of
  at least 3:1 against adjacent colors.
- When the terminal background or palette is unknown, required text uses the
  terminal's default foreground/background pair. Optional color may reinforce
  it but must not replace text, punctuation, or position.
- Bold, dim, italic, and underline are optional. Do not depend on them because
  terminal support and readability vary. Do not use blink, rapid animation,
  repeated flashing, cursor movement, or terminal-title changes.
- Never use an OSC 8 hyperlink that hides its destination. Print a safe,
  sanitized destination when a URL is required.

Contrast review covers the declared reference light and dark themes plus
high-contrast and representative color-vision-deficiency simulations. A failing
theme disables the affected optional color rather than changing the words.

## Symbols and punctuation

The portable terminal alphabet is ASCII:

- `-->` introduces a styled location;
- `|` forms an optional excerpt gutter;
- `=` introduces a styled fact;
- `^` points to a span only when paired with a textual annotation;
- `...` appears only with text explaining what was omitted.

The plain profile uses words such as `location:`, `cause:`, and `help:` instead
of requiring those symbols to be interpreted. Do not use a standalone emoji,
check mark, cross, warning triangle, box-drawing character, or color swatch as a
severity or status. Decorative Unicode symbols may not change spacing,
relationships, or meaning and should be omitted by default.

Avoid long punctuation runs, decorative boxes, and ASCII-art banners. They are
noisy in screen readers, consume narrow columns, and copy poorly.

## Plain and screen-reader-friendly output

Plain output must:

- contain no ANSI/OSC sequence, carriage return, tab, cursor command, hidden
  text, or trailing spaces;
- expose one labeled fact per logical line in reading order;
- keep severity and code in the first line and trace information last;
- replace visual carets with a line/column reference or a quoted textual span
  when the relationship would otherwise be ambiguous;
- use textual omission counts or descriptions rather than a bare ellipsis;
- avoid tables, columns, repeated gutter characters, and indentation as the only
  expression of hierarchy;
- remain understandable when read linearly without pauses inferred from color
  or typography.

Screen-reader review is performed on the plain profile, not on a capture of
colored terminal cells. Product documentation must not claim compatibility with
a named assistive technology until that exact combination has repeatable
evidence under [COMPATIBILITY.md](COMPATIBILITY.md).

## Copy and paste behavior

- Copying a line from plain output must yield the visible sanitized characters;
  there must be no hidden control or hyperlink payload.
- Keep a machine token contiguous and in source spelling. Do not insert styling
  inside it or substitute typographic quotes, dashes, or look-alike characters.
- Put punctuation outside a token when it is not part of the value.
- A visual terminal wrap must not be represented as a newline in redirected
  output. Renderer-inserted prose wraps use the deterministic rules above.
- Never present destructive text as an unlabeled shell-ready command. State the
  action, prerequisite, and risk in prose.
- Values omitted for security or size remain visibly marked; copy/paste must not
  appear to recover them.

## Evidence and blame

Naming a responsible mod requires verified ownership metadata or an equivalent
strong signal from the completed diagnostic. The first non-Minecraft frame is
not proof.

Recommended evidence order is documented for classifier authors, not recomputed
by renderers:

1. typed platform exception with structured fields;
2. loader or mod metadata tied to the failing component;
3. validated resource or configuration structure;
4. mapped and version-matched source information;
5. bounded message pattern as supporting evidence only.

If classifiers disagree or evidence is weak, the model supplies a generic
fallback. Development output may describe candidate classifiers according to
the classifier contract; normal operator output must not expose speculation as
fact.

## Approved complete examples

### Port conflict

```text
error[SF4001]: server port is already in use
location: server.properties:server-port
endpoint: 0.0.0.0:25565
cause: another listener already uses this endpoint
help: stop the conflicting service or configure a different server port
trace: full details saved as diagnostic A6D210
```

Omit `another listener` if the evidence proves only a generic bind failure.

### Warning with color-independent meaning

```text
warning[SF2008]: optional resource pack was skipped
location: world/resources/example.zip
cause: pack format 22 is not supported by this server version
note: server startup continued without this resource pack
help: install a resource pack compatible with the configured Minecraft version
trace: full details saved as diagnostic B40D12
```

The words `warning` and `startup continued` preserve impact when yellow or bold
styling is unavailable.

### Unknown exception

```text
error[SF0001]: an unexpected server operation failed
location: server startup
exception: com.example.CustomException
note: Stackframe has no specialized diagnostic for this failure
help: inspect diagnostic C9012E or create a sanitized local support bundle
trace: 31 frames collapsed; complete details preserved as diagnostic C9012E
```

Generic output stays useful without guessing.

## Rejected complete example

```text
X FATAL: ExampleMod destroyed everything!!!
Try adding RAM or delete the broken world.
See the red text above.
```

This is rejected because it uses an unsupported severity, symbol- and
color-dependent meaning, unsupported blame, alarmist wording, vague references,
and destructive remediation without evidence or safeguards.

## Snapshot and review contract

Golden coverage for each layout-changing renderer change must include:

| Dimension | Required cases |
| --- | --- |
| Output profile | styled ANSI, styled with ANSI disabled, linear plain |
| Width | 40, 80, and 120 columns; one best-effort case below 40 |
| Destination | interactive terminal, redirected file/CI, representative hosting panel |
| Severity | `error`, `warning`, and a `note` header attached to a parent diagnostic |
| Content | no location, excerpt, wrapped help, long indivisible token, collapsed trace |
| Unicode | combining sequence, CJK width, emoji sequence, ambiguous-width character |
| Hostile input | ANSI/OSC, CR/LF/tab, bidi controls, long value, fake diagnostic header |
| Privacy | typed redaction markers in title, fact, location, excerpt, and trace fields |

Automated assertions must prove:

- ANSI removal preserves every semantic field and its order;
- plain output contains no forbidden controls or styling sequences;
- wrapping is deterministic and no grapheme cluster or machine token is split;
- lines fit the selected width except a documented indivisible token or
  best-effort width below 40;
- caret positions match display columns, or the renderer uses a linear
  annotation;
- optional color removal leaves severity, selection, omission, and relationship
  explicit;
- sanitization occurs before layout and prevents injected records;
- bounded omissions are visible and complete details remain recoverable under
  the applicable security policy.

## Reviewer checklist

- [ ] Every claim and remediation is present in the completed model and supported
      by evidence; the renderer adds no inference or blame.
- [ ] The approved severity word is visible and reflects impact.
- [ ] The title is a lowercase operation clause without remediation, jokes, or
      alarmist punctuation.
- [ ] Locations and terms are operator-actionable, consistent, allowed, and
      redacted.
- [ ] Causes and notes are concrete facts; help is safe, specific, imperative,
      and omitted when confidence is insufficient.
- [ ] `trace:` identifies omissions and where recoverable details live.
- [ ] Styled and plain profiles carry the same semantic fields in the same order.
- [ ] Meaning survives removal of color, symbols, emphasis, indentation, and
      positional art.
- [ ] Chosen colors satisfy the applicable contrast rule when Stackframe controls
      the color pair; unknown palettes retain default terminal text.
- [ ] The plain profile is understandable in linear reading order and contains no
      hidden controls.
- [ ] Width behavior is deterministic at 40, 80, and 120 columns; narrow output
      wraps instead of truncating facts.
- [ ] Unicode alignment uses grapheme boundaries and the documented display-width
      policy, with a linear fallback when position is uncertain.
- [ ] Untrusted controls, newlines, bidi controls, and terminal sequences are
      visibly escaped before layout.
- [ ] Copying identifiers preserves their sanitized source spelling and does not
      include hidden data.
- [ ] Complete original failure details remain recoverable according to the
      security and privacy policy.
