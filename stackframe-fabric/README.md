# Stackframe Fabric

Fabric and Minecraft dedicated-server integration.

## Owns

- lifecycle and logging capture;
- Fabric mod metadata and version evidence;
- mappings, environment paths, and commands;
- Fabric-backed built-in diagnostic classifiers;
- Fabric packaging and server compatibility.

## Dependency boundary

Fabric depends on core and renderer contracts. Fabric types remain in this
module and do not leak into shared public models. This module never depends on
Forge.

## Worker notes

- Preserve existing appenders, crash reports, and original event ordering.
- Guard recursive logging and duplicate observation.
- Keep client-only code out of dedicated-server paths.
- Use verified metadata before naming a mod.
- Cover early startup, runtime, reload, shutdown, and formatter failure.

## Current capture path

The server artifact installs a Log4j observer at Fabric's `preLaunch` entrypoint
and retries installation at `main` if the early hook could not complete. It
observes throwable-bearing `ERROR` and `FATAL` events, including events from
non-additive logger configurations. It does not replace or mutate original
appenders, log messages, throwable objects, or vanilla crash reports. An
observer failure leaves the original Log4j event on its normal path. Log4j's
own appender guard prevents recursive logging from repeatedly invoking the
observer; Stackframe also has a defensive re-entry guard and counters.

The observer only enqueues the `Throwable`. A bounded daemon worker writes a
private raw trace under `logs/stackframe-traces/` and emits a plain, generic
`SF0001` diagnostic with the correlation ID as one Log4j event. Existing
appenders choose the destination and serialize each complete event. The supplemental
diagnostic omits exception messages because these are not yet redacted. If
trace storage fails, the diagnostic says so and points back to the original
server log. A full queue or shutdown deadline may omit a supplemental
diagnostic, but the original event still reaches normal appenders. The worker
drains queued events for up to two seconds at shutdown.

Capture begins when Fabric calls `preLaunch`; loader failures before that hook
cannot be observed by this adapter. The current tests exercise Log4j startup,
runtime, reconfiguration, non-additive loggers, recursive logging, and the
shutdown drain in isolation. Real dedicated-server and hosting compatibility
remain unclaimed until the integration matrix in issue #17 runs.

See [`docs/COMPATIBILITY.md`](../docs/COMPATIBILITY.md) and
[`docs/WORKSTREAMS.md`](../docs/WORKSTREAMS.md).
