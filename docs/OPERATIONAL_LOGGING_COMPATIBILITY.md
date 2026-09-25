# Logging and output compatibility evidence

This page records the narrow Java-level evidence for [issue #16](https://github.com/MinecraftProt/Stackframe/issues/16).
It does not promote a server, container, or hosting-panel row in the
[public compatibility matrix](COMPATIBILITY.md): those rows still require exact
runtime versions and an immutable test report.

`OperationalLog4jCompatibilityTest` starts an isolated Log4j context with a
custom pattern and two real `OutputStreamAppender` instances. It verifies that
both original streams receive an error with its full throwable stack, an
already-rendered error string without a `Throwable` is not captured as a typed
failure, and the observer sees the original throwable object. It also checks
that the throwable's `printStackTrace` representation is unchanged and the
original appenders keep working after the observer closes. The existing capture
tests cover reconfiguration, a non-additive logger, a competing appender name,
and observer failure.

`OperationalOutputCompatibilityTest` writes a diagnostic through a redirected
`PrintStream`. It supplies destination-specific capability evidence for
redirected stdout, a service journal, a Docker log stream, a hosting-panel
stream, CI, and `NO_COLOR`. In each simulated environment, automatic selection
chooses plain text, no ANSI/CR reaches the destination, and an embedded forged
line remains escaped inside one diagnostic. The current Fabric pipeline itself
uses plain rendering for its emitted diagnostics. Explicit ANSI selection is a
user override and is not safe for an unknown file or panel destination.

These harnesses do not run a Minecraft dedicated server or the vanilla crash
report writer, nor do they run under actual systemd, Docker logging drivers, or
a named panel version. A custom Log4j XML file, rotating file appenders, and
other mods' logging changes also need runtime coverage. A later runtime report
should record the exact Stackframe JAR and checksum, Minecraft/Fabric/Java/OS
versions, destination and version, any added appenders or mods, the captured
original event and trace/crash report, and whether ANSI appeared. Until that
evidence exists, the corresponding matrix rows remain **Unknown**. The server
matrix in [issue #17](https://github.com/MinecraftProt/Stackframe/issues/17)
provides the place to collect it.
