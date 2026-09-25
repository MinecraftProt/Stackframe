# Sanitized support bundle staging

The renderer module now provides `SupportBundle.plan(...)` for a caller that has
already selected completed, redacted `DiagnosticDocument` events and safe
metadata. It produces an immutable plan and a preview. The preview lists every
archive entry and its uncompressed byte count, selected diagnostic count, exact
UTC time range, and aggregate redaction categories. `Plan.export(target)` then
writes those already planned bytes to a new local ZIP file. It never uploads the
archive or overwrites an existing destination.

This is a staging API, not yet an in-game or server operator command. A future
adapter must present the preview to the operator and wait for an explicit export
action. It must not silently attach this archive to an issue or send it to a
service. See [issue #36](https://github.com/MinecraftProt/Stackframe/issues/36)
for the remaining operator flow and optional sanitized trace policy.

## Archive contents

| Entry | Content |
| --- | --- |
| `manifest.txt` | Bundle format version, configuration schema version, diagnostic count and time range, and explicit raw-trace exclusion |
| `diagnostics.ndjson` | Selected completed diagnostics serialized by the same structured renderer used for operator JSON output |
| `metadata.tsv` | Caller-selected, post-policy environment versions and at most 32 mod ID/version pairs |
| `README.txt` | Sharing and deletion guidance |

Raw `latest.log`, `.trace` files, world/player data, arbitrary configuration
contents, screenshots, and server directories are never read or included by this
API. A caller supplies the configuration **schema version**, not the
configuration file. Metadata values are `DisplayText` instances and must pass
the same redaction policy as diagnostic fields before planning. No installed-mod
inventory is gathered automatically.

One plan accepts 1-64 diagnostics spanning at most 24 hours and at most 2 MiB
of uncompressed entry data. It serializes each structured event once, then
freezes the exact bytes for preview and export. Too many records, a wider time
range, or an oversized payload fail before creating a ZIP. ZIP entry names are
fixed by Stackframe; caller text cannot add paths. Export creates a temporary
file beside the destination and removes it if writing fails. The caller receives
an `IOException` rather than a partial published archive. An existing target is
left untouched.

Before sharing a bundle, review its entries and contents manually. Generated
diagnostics can still be wrong if a producer incorrectly marks untrusted text as
public. Send only to a recipient you trust, keep a local copy only as long as
needed, and delete the ZIP afterward. Raw traces require a separate, explicit
sanitization design and are not accepted by this API.
