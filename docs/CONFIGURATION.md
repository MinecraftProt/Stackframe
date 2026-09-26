# Fabric server configuration (schema 1)

The development Fabric adapter reads `config/stackframe.properties` relative to
the server process working directory when its capture hook installs. A missing
file uses the defaults below. A present invalid or unreadable file disables
Stackframe's supplemental capture and prints a safe error to stderr with the
file, line, key, and expected form. Minecraft's original Log4j event, appenders,
and crash reports continue normally. Correct the file and restart the server;
there is no live reload. This behavior has isolated tests, but not yet a
released dedicated-server compatibility claim.

Save the file as UTF-8. Use one `key=value` assignment per line; blank lines
and lines starting with `#` are comments. Keys and values are case-sensitive.
`schema_version=1` must be the first setting after comments. An unrecognized
key, duplicate key, malformed line, malformed UTF-8, overlong file or line, or
unsupported schema is an error. The parser reports the source line and does
not echo an invalid value, which could contain private data. It does not
silently replace a rejected setting with a default.

This sample states the safe defaults explicitly:

```properties
schema_version=1
output=plain
trace_directory=logs/stackframe-traces
trace_retention=manual
include_codes=SF0001
exclude_codes=none
dedup_window_ms=30000
dedup_max_entries=256
```

| Setting | Accepted values and live effect |
| --- | --- |
| `output` | `plain` (default) or `ansi` selects the renderer used for supplemental diagnostics. `auto` conservatively emits plain because one Log4j event can reach both terminal and file appenders, so Fabric cannot prove every destination supports ANSI. Explicit `ansi` also overrides `NO_COLOR`; use it only when all destinations handle escape codes. |
| `trace_directory` | Relative path under the server working directory, default `logs/stackframe-traces`. No absolute path, drive, backslash, `..`, or control character is accepted. Raw trace records can contain secrets; keep the destination private. |
| `trace_retention` | `manual` (default) never deletes files. `bounded` deletes old Stackframe-named complete `.trace` files at startup if the directory exists and after a successful write. It requires both limits below. |
| `trace_max_age_days` | With `bounded`, decimal integer `1` through `365`. Traces older than this are eligible for deletion. |
| `trace_max_files` | With `bounded`, decimal integer `1` through `8192`. If more complete traces exist, oldest are eligible for deletion. Cleanup scans at most 8192 directory entries and reports a limit failure instead of deleting an incomplete set. |
| `include_codes` | `SF0001` (default) or `none`. `none` stops supplemental diagnostics and trace writes from this Fabric pipeline; original Log4j events continue. |
| `exclude_codes` | `none` (default) or `SF0001`. `SF0001` suppresses that supplemental code and its trace; an exclusion that has no effect with `include_codes=none` is rejected. |
| `dedup_window_ms` | Decimal integer `0` through `300000`, default `30000`. `0` disables supplemental duplicate suppression; the original Log4j event is always independent of this setting. |
| `dedup_max_entries` | Decimal integer `1` through `4096`, default `256`, bounding in-memory correlation state. It is rejected when `dedup_window_ms=0` because it would have no effect. |

Only `SF0001` is emitted by this Fabric path today. An unrecognized future code
in either filter is rejected so a typo cannot silently disable diagnostics.
`output=json` is rejected: Log4j layouts may prepend text, so this adapter
cannot promise a valid standalone JSON stream. `redaction=off`, verbosity, and
frame-collapse settings are likewise rejected. This path emits a safe generic
diagnostic without raw throwable text; a configurable redaction mode or
specialized verbosity/collapse behavior needs a real pipeline implementation
and privacy review before it can become a schema field. Raw `.trace` files are
never renderer-redacted.

Automatic cleanup considers only regular, Stackframe-ID-named `.trace` files
with the `Stackframe trace v1` header in the configured directory. It does not
descend into directories or follow known symbolic links, and it leaves
`.partial`, older unmarked traces, and unrelated files alone.
Unsafe directories, scan limits, and deletion failures produce an operator
warning without exposing filenames. A cleanup warning does not remove the
original server error. Manual retention is safer when another process controls
that directory. Always back up traces you need for investigation before
enabling bounded deletion.

Schema 1 is the first configuration format. No schema 0 migration
exists; a `schema_version=0` file is rejected with an instruction to create a
schema 1 file. Future schema versions and unknown fields are rejected, not
guessed or downgraded. Upgrade Stackframe before applying settings from a
newer release. The repository includes schema 0, future-version, malformed,
duplicate, and unknown-field fixtures that exercise this policy.
