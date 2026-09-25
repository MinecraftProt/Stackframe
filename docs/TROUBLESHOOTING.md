# Troubleshooting Stackframe diagnostics

Start with the exact `SF####` code and its
[operator catalog page](diagnostics/README.md). A code identifies one stable
meaning; its title and colors can change without changing that identity.
The [generated registry](diagnostic-registry/catalog.md) lists all allocated
codes and their evidence and safety contracts.

## Current availability

Stackframe is pre-alpha. The development Fabric server artifact observes
throwable-bearing `ERROR` and `FATAL` Log4j events after its `preLaunch` hook,
writes local full traces, and emits the generic `SF0001` diagnostic through the
existing appenders. It does not yet classify specialized failures or cover
loader errors before `preLaunch`. Full Fabric capture verification remains in
[issue #10](https://github.com/MinecraftProt/Stackframe/issues/10);
operator configuration is [issue #14](https://github.com/MinecraftProt/Stackframe/issues/14);
the sanitized support-bundle feature is
[issue #36](https://github.com/MinecraftProt/Stackframe/issues/36).
The adapter has an isolated test suite and a no-EULA startup smoke test, but no
released compatibility claim or full dedicated-server integration matrix.

## Read a diagnostic

Read a plain diagnostic from top to bottom: severity and code, any verified
location, evidence-backed cause or note, safe help, then `trace:`. ANSI adds
emphasis but no extra facts. For example, this is an illustrative generic
`SF0001` payload from the development Fabric path:

```text
error[SF0001]: an unexpected server operation failed
trace: complete details preserved as diagnostic C9012E0000000000; diagnostic
  DC9012E0000000000; correlation C9012E0000000000
```

The `C9012E0000000000` token is a synthetic correlation ID. It is not a server address,
timestamp, filename from your server, or proof of a cause. The complete event
may contain more causes and suppressed exceptions than the short view.

## Find the full trace

The Fabric adapter's core recorder writes a successful full trace to
`logs/stackframe-traces/<correlation-id>.trace`, relative to the server working
directory. It uses the same opaque correlation ID shown in the diagnostic.
The record contains the original Java throwable, causes, suppressed exceptions,
and frames. See [Full trace records](FULL_TRACES.md) for write guarantees,
permissions, and retention.

With the synthetic `C9012E0000000000` example, an administrator can read the local file
after a diagnostic has actually reported that it was preserved:

```powershell
Get-Content -LiteralPath 'logs/stackframe-traces/C9012E0000000000.trace'
```

```shell
less -- 'logs/stackframe-traces/C9012E0000000000.trace'
```

The directory is relative to the process working directory, which may differ
from the panel's displayed installation folder. Search for the correlation ID
in the diagnostic first; do not guess a path based on the short root diagnostic
ID. If `trace:` reports a write failure, no complete Stackframe trace is
promised. Inspect the original `latest.log` or Minecraft/loader crash report
instead and include the write-failure note in a sanitized report. The original
log route must remain active when Stackframe's recorder or formatter fails.

Raw `.trace` files are **not redacted**. They can expose tokens, player
identifiers, addresses, paths, and other private values. Keep them local,
restrict access, and never attach one unchanged to a public issue. The current
recorder does not automatically delete records, so administrators should apply
their own retention policy as described in [Full trace records](FULL_TRACES.md).

## Unknown diagnostic or formatter fallback

`SF0001` means Stackframe recognized a failed operation but cannot support a
more specific diagnosis from the available evidence. It is not evidence that
Stackframe itself failed, and it does not name a responsible mod. Follow the
[SF0001 safe checks](diagnostics/SF0001.md#safe-checks) and preserve the
original event.

If Stackframe cannot preserve or render a diagnostic at all, its
fail-open contract leaves the original error unchanged and reports its own
failure separately where safe. That original event may have no `SF####` header.
Do not relabel it as `SF0001` or infer that the first stack frame is the cause.
The current observer and worker preserve the original Log4j event in isolated
failure tests; full dedicated-server lifecycle evidence is still pending.

## Configuration errors

There is no released Stackframe configuration file or runtime command yet.
[Issue #14](https://github.com/MinecraftProt/Stackframe/issues/14) will define
its exact format, path, defaults, validation, and migrations. Until then, do
not invent a `stackframe.properties` file or assume a pasted setting is active.

When validated configuration is available, a rejected value should identify
the key, location, and expected form while retaining the previous valid
settings. Read the rejection before restarting. Compare against the
documentation for the installed artifact version, preserve a copy of the prior
file, and correct only the identified value. Do not delete unrelated server
configuration or include credentials when requesting help. Report silent
acceptance of an invalid value as a Stackframe bug.

## Color and plain output

The renderer library supports `PLAIN` and `ANSI`, and its capability selector
supports `AUTO`. In `AUTO`, redirected output, CI, `TERM=dumb`, unknown
terminals, nonempty `NO_COLOR`, or `CLICOLOR=0` select plain text. Explicit
`ANSI` is an intentional override, including over `NO_COLOR`. Explicit
`PLAIN` disables styling. The development Fabric adapter currently emits its
generic diagnostic in plain mode. Operator configuration and automatic mode
selection are not wired into a released output path yet.

If ANSI escape codes appear as characters in a future hosting panel or saved
log, first use that panel's raw log download or plain-text view. For automatic
mode, set `NO_COLOR=1` in the server process environment and restart the
process so the adapter can see it. If an explicit ANSI setting is active,
choose the plain output setting once [configuration](#configuration-errors)
ships; `NO_COLOR` will not override explicit ANSI. If plain output still
contains escapes, report the exact destination and setting. Do not strip
escapes by deleting diagnostic lines, because that loses evidence.

Color never carries unique meaning. The first line still contains the severity
word and code, and all help, omissions, and trace facts must survive in plain
text. Use the plain form when copying a report. Avoid screenshots of colored
output if text can be copied safely.

## Hosting panels and redirected logs

A panel may wrap, truncate, recolor, or combine lines and may have a different
working directory than the server process. Check the raw `latest.log` or panel
download when the on-screen view looks incomplete. Compare the code and
correlation ID across views; a shortened panel line is not evidence that the
original error vanished. Use the exact panel name/version, whether stdout is
redirected, and the output mode when reporting a formatting problem.

Automatic capability detection is conservative: an unknown or redirected
destination should receive plain output once the Fabric adapter uses the
renderer selector. Stackframe must leave other intended appenders and vanilla
crash reports working. Panel compatibility must be demonstrated in the
[compatibility policy](COMPATIBILITY.md), not assumed from a plain screenshot.

## Prepare a sanitized report

Use the [support guidance](../SUPPORT.md) and the
[issue chooser](https://github.com/MinecraftProt/Stackframe/issues/new/choose)
for a bug or new-diagnostic request. A useful report includes:

1. The code, exact Stackframe/Minecraft/Java/loader versions, server operating
   system family, and relevant mod versions.
2. The operation that failed, a small reproducible scenario, and whether the
   original event and complete trace were recoverable.
3. A copied **plain, sanitized** diagnostic excerpt and the correlation ID if
   present. Say whether the trace state was `PRESERVED` or `WRITE_FAILED`.
4. Output mode, hosting panel or service manager, and redirection details for
   layout or color bugs.

Inspect every pasted line manually. Remove or replace tokens, passwords,
private keys, account/player identifiers, chat, non-public addresses, private
path segments, and world data. Keep the replacement marker visible, such as
`<redacted:token>`, so readers know data was removed. Never attach a whole
server directory, raw trace, unsanitized `latest.log`, world save, or arbitrary
configuration file. A sanitized support bundle is planned in #36; it is not
currently available. Suspected secret exposure or another vulnerability
belongs in a [private security advisory](../SECURITY.md), not a public issue.

## Safe checks and recovery

| Safe inspection | State-changing recovery |
| --- | --- |
| Read the code guide, preserve the original log, inspect a local raw trace privately, compare exact versions, and test a minimal reproduction on a copy | Edit or remove a mod/configuration, restore a backup, change permissions, or delete files only when stronger evidence and a specific guide justify it |

`SF0001` permits inspection only. Before any action that can alter data or
availability, make a verified compatible backup, understand the impact, plan a
rollback, and use evidence that identifies the target. Never treat generic
advice as permission to delete world data or grant broad permissions.
