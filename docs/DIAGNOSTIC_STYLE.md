# Diagnostic style guide

Readable diagnostics are a functional contract, not decorative console output.
This guide defines how Stackframe messages communicate evidence and action.

## Information order

Every diagnostic should answer these questions in order:

1. **What failed?** A short title tied to an operation.
2. **Where?** The most useful verified location or component.
3. **Why?** Facts from exceptions, metadata, or validated context.
4. **What next?** A safe, specific action or a request for more evidence.
5. **Where are details?** A correlation ID or full-trace destination.

Do not lead with Java implementation details when a verified server concept is
available.

## Message anatomy

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

### Severity

- `error`: an operation failed or the server cannot continue safely.
- `warning`: operation continued with a meaningful risk or degraded behavior.
- `note`: supporting information attached to another diagnostic.

Severity is determined by impact, not by how alarming the exception text looks.

### Code

Codes are stable search keys. The code identifies a meaning, not a Java class or
wording revision. Reusing a code for a different cause is a breaking change.

### Title

Use a short lowercase sentence fragment:

- Good: `server port is already in use`
- Good: `datapack validation failed`
- Avoid: `An Error Has Occurred`
- Avoid: `java.net.BindException was thrown`
- Avoid: `Oops! This mod broke your server`

Titles state a verified failed operation. Do not include remediation in the
title.

### Location and context

Prefer the location the operator can act on:

1. configuration key or bounded source excerpt;
2. world, datapack, resource, or mod file;
3. verified mod and component;
4. mapped source frame;
5. lifecycle phase;
6. no location.

Never invent a source location. Redact and canonicalize paths before rendering.
Do not read a path only because untrusted exception text mentions it.

### Cause

Cause notes state facts:

```text
= cause: no registry entry matches "example:missing_block"
```

Avoid empty restatements:

```text
= cause: a registry error occurred
```

When several causes matter, order them from operator-facing cause toward lower
technical cause. Preserve the complete causal order in the debug record.

### Help

Help is optional. Incorrect advice is worse than no advice.

- Use an imperative, specific action.
- Explain prerequisites or risk.
- Never suggest deleting world data, disabling security, or broadly changing
  permissions as a routine first step.
- Never tell users to add memory when evidence only shows a generic crash.
- When confidence is insufficient, ask the operator to inspect the correlated
  trace or collect a sanitized bundle.

### Trace summary

If frames are collapsed, state how many and where full details live. A
correlation ID should be short enough to type and unique within the configured
retention window.

## Evidence and blame

Naming a responsible mod requires verified ownership metadata or an equivalent
strong signal. The first non-Minecraft frame alone is not proof.

Recommended evidence order:

1. typed platform exception with structured fields;
2. loader or mod metadata tied to the failing component;
3. validated resource/configuration structure;
4. mapped and version-matched source information;
5. bounded message pattern as supporting evidence only.

If classifiers disagree or evidence is weak, describe the failing component and
use the generic fallback. Development output may explain candidate classifiers;
operator output must not expose speculation as fact.

## Layout

- Default target width: adapt to the terminal, with a documented fallback.
- Minimum supported width: narrow output must wrap rather than truncate facts.
- Indent consistently with spaces.
- Align carets and labels by display width, not UTF-16 length.
- Bound excerpts and annotations; summarize omitted content.
- Sanitize control characters and terminal escape sequences in untrusted data.
- Keep the primary diagnostic visible before long notes.

## Color and symbols

Color reinforces structure but never provides unique meaning. Plain output must
retain severity words, labels, and relationships.

- Respect `NO_COLOR`.
- Do not emit ANSI escapes to redirected output in automatic mode.
- Pair symbols with words or position.
- Avoid rapid animation, cursor movement, terminal title changes, or hyperlinks
  that hide their destination.
- Test common color-vision deficiencies and low-contrast terminal themes.

## Tone

Messages are calm, direct, and non-judgmental.

- Address the failed operation, not the user's competence.
- Avoid jokes in errors.
- Avoid `obviously`, `simply`, and vague phrases such as `something went wrong`.
- Use Minecraft operator terminology before compiler or logging terminology.
- Do not promise that a suggestion will fix the issue when it is only a check.

## Before and after

### Port conflict

Before:

```text
java.net.BindException: Address already in use: bind
    at sun.nio.ch.Net.bind0(Native Method)
    ...
```

After:

```text
error[SF4001]: server port is already in use
  --> server.properties:server-port
   |
   = endpoint: 0.0.0.0:25565
   = cause: another listener already uses this endpoint
   = help: stop the conflicting service or configure a different server port
   = trace: full details saved as diagnostic A6D210
```

Stackframe must omit `another listener` if the platform only proves a generic
bind failure.

### Unknown exception

```text
error[SF0001]: an unexpected server operation failed
  --> server startup
   |
   = exception: com.example.CustomException
   = note: Stackframe has no specialized diagnostic for this failure
   = help: inspect diagnostic C9012E or attach a sanitized support bundle
   = trace: 31 frames collapsed; complete details preserved
```

Generic output stays useful without guessing.

## Review checklist

- Is every claim supported by evidence?
- Does the title describe an operation?
- Can an operator act on the selected location?
- Is help safe, specific, and optional when uncertain?
- Is all meaning present without color?
- Are untrusted values bounded, escaped, and redacted?
- Can the complete original failure be recovered?
- Does unknown or malformed input have a safe fallback?
