# Diagnostic registry governance

The canonical registry is the Java declaration in
`CanonicalDiagnosticRegistry`. It uses the loader-neutral `DiagnosticCode` model
and immutable declarative contracts; entries contain no callbacks or platform
types.

`catalog.md` is generated from that declaration. It includes every range,
classifier registration, combination policy, arbitration reason code, governed
remediation action, and allocated diagnostic. Do not edit it by hand:

```powershell
.\gradlew.bat :stackframe-core:generateDiagnosticRegistryCatalog
```

`check` regenerates the catalog in memory and fails on drift. It also compares the
canonical declaration with the committed compatibility baseline. The baseline
records every stable code, symbolic key, area, owner, lifecycle, title contract,
meaning, evidence requirement, fallback guarantee, remediation ceiling,
replacement, and documentation anchor. It also records every range definition,
including areas with no allocated entries, plus the meanings of combination
policies, arbitration reason codes, and remediation actions.

## Allocation and lifecycle

- `RESERVED` allocates an identity for future use but is not emitted.
- `ACTIVE` may be emitted when its evidence contract is satisfied.
- `DEPRECATED` retains its old meaning and names an active replacement.
- Available codes are valid range members absent from every lifecycle. Reserved
  and deprecated codes never become available for semantic reuse.
- `SF0000` is available for allocation; `SF0xxx` governs all 1,000 codes.
- `SF0001` is the always-available active generic fallback. It cannot infer cause,
  blame, or a state-changing remedy.

Specialized active or deprecated entries require identity and scope evidence,
must degrade to `SF0001`, and may authorize state-changing advice only when
their governed `RemediationAction` requires it. Safety, remedy evidence,
confirmation, and backup requirements are derived from the action and cannot be
selected independently. Unsupported actions such as deleting world data,
disabling security, broad permission changes, or automatic downloads cannot be
represented. Policy prose is generated from the typed actions and cannot define
or disguise a different safety level.

Classifier registrations separately govern stable classifier keys, diagnostic
identity, reviewed precedence from `-100` through `100`, and combination policy.
The registry defines stable machine-readable arbitration reason codes, but does
not execute arbitration; ADR 004 remains the selection contract.

## Intentional compatibility migrations

Adding or changing an entry requires one reviewed change that:

1. updates only the canonical Java declaration;
2. regenerates `catalog.md`;
3. explains why the stable meaning or safety contract is compatible, deprecated,
   or intentionally migrated; and
4. deliberately updates the baseline:

```powershell
.\gradlew.bat :stackframe-core:updateDiagnosticRegistryBaseline
```

Never update the baseline merely to make `check` pass. Deletion, reassignment,
range or area movement, key changes, and weakened evidence, fallback, or
remediation contracts require explicit compatibility review. Deprecated meanings
remain registered permanently.
