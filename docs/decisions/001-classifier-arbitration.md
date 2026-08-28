# ADR 001: Deterministic classifier arbitration

- **Status:** Proposed
- **Date:** 2026-08-28
- **Issue:** [#41](https://github.com/MinecraftProt/Stackframe/issues/41)
- **Owners:** Core diagnostic engine maintainers

## Context

Several classifiers may recognize the same normalized failure. A message pattern
may resemble a known failure while typed exception data points elsewhere, two
equally strong classifiers may disagree, or one event may contain several
independent validation failures. Selecting the first match would make results
depend on registration order and could turn weak signals into unsupported blame
or unsafe advice.

Stackframe therefore needs one loader-neutral arbitration contract before
specialized classifiers are implemented. The contract must be deterministic,
bounded, explainable, and safe when classifiers disagree or misbehave. It must
also preserve independent failures rather than forcing an event into one
diagnostic.

Issue [#6](https://github.com/MinecraftProt/Stackframe/issues/6) owns the concrete
immutable diagnostic and evidence model. This decision specifies the values and
invariants that model must make representable at the classifier boundary; it does
not define the complete diagnostic schema.

## Decision

### Boundary and terminology

The core classifier stage consumes one immutable, bounded normalized failure and
a deterministic snapshot of already-validated metadata supplied by an adapter.
It does not read files, query platform APIs, use post-classification enrichment,
or depend on Fabric, Forge, Minecraft, or logging implementation types.

A classifier returns zero or more **candidates**. A candidate is a proposal, not
an operator diagnostic. The eventual model from #6 must allow the arbitration
boundary to carry at least:

- a stable classifier key and proposed diagnostic identity;
- a stable **failure unit** identifying the normalized node or structured
  validation item the candidate explains;
- an asserted confidence tier;
- evidence references and the claims each reference supports;
- a registry-reviewed precedence value;
- evidence-backed facts, locations, blame claims, and help proposals; and
- whether candidates with the same diagnostic identity may be combined.

Concrete type names, collection types, and renderer-facing fields remain owned
by #6. Diagnostic-code allocation remains owned by
[#5](https://github.com/MinecraftProt/Stackframe/issues/5).

A **failure unit** is the smallest independently actionable failure represented
by normalized input. Examples are one causal node, one suppressed exception, or
one item in a structured validation result. Its key is assigned during
normalization from canonical traversal position or a structured item identifier,
never from object identity or an unordered collection.

Candidates for different failure units are independent. Candidates for the same
failure unit compete unless they propose the same diagnostic identity and are
safe to combine. A classifier must not manufacture separate units merely to
avoid conflict resolution.

### Evidence taxonomy

Every evidence reference has a stable source key, a strength, and one or more
capabilities: `identity`, `scope`, `fact`, `ownership`, or `remedy`. Evidence is
relevant only when it supports a claim the candidate actually makes.

| Strength | Allowed sources | What it may establish |
| --- | --- | --- |
| **Direct** | A typed exception with validated structured fields; an explicit platform or operating-system result code translated by the adapter | Diagnostic identity, affected scope, and facts represented directly by the typed value |
| **Corroborating** | Loader/mod metadata bound to the failing artifact or component; validated resource or configuration structure; version-matched source mapping | Identity or facts when the binding is explicit; ownership only when metadata proves the component-to-mod relationship |
| **Contextual** | Causal-chain position, wrapper relationship, suppressed relationship, or categorized frame provenance | Scope and supporting facts, but not responsibility or a mutating remedy by itself |
| **Heuristic** | A bounded exact message pattern, exception class-name pattern, or the first external/non-Minecraft frame | Candidate discovery and supporting explanation only |

An unvalidated path, free-form message, frame package prefix, classifier assertion,
or registration priority is not ownership evidence. Repeated observations from
the same source key count once; copying one message into several patterns does
not create independent evidence.

All evidence values are untrusted. They remain subject to
[redaction and output safety](../SECURITY_AND_PRIVACY.md) before operator,
structured, or development output. Evidence does not authorize file access.

### Confidence representation

Confidence is the ordinal set `high`, `medium`, and `low`, not a floating-point
probability. A classifier asserts a tier, and the arbiter computes an
evidence-derived ceiling. Effective confidence is the lower of those values.
Unknown tiers are malformed.

The ceiling is computed only from relevant, independently sourced evidence:

| Ceiling | Minimum proof |
| --- | --- |
| **High** | One direct item that jointly supports identity and scope, or at least two independent corroborating items that jointly support identity and scope |
| **Medium** | One corroborating item supporting identity, or two independent items where at least one is contextual and together they support identity and scope |
| **Low** | Any other valid match, including every heuristic-only match |

Extra heuristic matches never raise the ceiling. Evidence quantity is not a
probability, and a classifier cannot raise confidence by asserting it more
strongly.

Only medium- or high-confidence candidates are eligible to produce a specialized
operator diagnostic. Low-confidence candidates remain visible only in bounded,
redacted development explanations. If no candidate is eligible, the failure unit
uses the generic fallback.

### Candidate validation

Before ranking, the arbiter validates each candidate. It rejects the whole
candidate when any required field is missing, its failure unit or evidence
reference does not exist, its diagnostic identity is unknown, its confidence or
precedence is out of range, its required claims contradict normalized facts, or
it contains mutually contradictory required claims.

Optional individual facts, blame, or help proposals that lack their required
evidence are removed without strengthening the remaining candidate. If removing
one changes the candidate's diagnostic meaning, the whole candidate is rejected.
All rejection reasons are stable machine-readable reason codes and may be shown
in development mode.

Duplicate classifier keys disable every classifier with that key for the event;
registration or discovery order must not choose one. A classifier exception or
malformed return is isolated, recorded as a classifier failure, and treated as
no candidates from that classifier. Other classifiers and failure units continue.
If no eligible candidate remains for an affected unit, that unit falls back
generically.

### Deterministic selection algorithm

For each normalized event, the arbiter performs these steps:

1. Sort registered classifiers by stable classifier key. Reject duplicate keys.
2. Invoke each classifier on the same immutable input snapshot under the limits
   below. Canonicalize and validate its candidates.
3. Partition valid candidates by failure-unit key.
4. Within each unit, merge candidates only when they have the same diagnostic
   identity, do not disagree on any required fact, and both permit combination.
   Evidence is de-duplicated by source key; confidence is recomputed after merge.
5. Remove low-confidence candidates from operator eligibility, retaining their
   development explanations.
6. Rank eligible candidates by this tuple, in descending order:
   effective confidence; presence of direct evidence; independent corroborating
   count capped at two; independent contextual count capped at two; then
   registry-reviewed precedence.
7. If exactly one semantic candidate has the highest tuple, select it. If all
   highest candidates have the same diagnostic identity and can combine, merge
   and select them.
8. If highest candidates with different diagnostic identities have the same
   tuple, do not use classifier key, discovery order, or collection order as a
   semantic tie-breaker. Record a conflict and select the generic fallback for
   that failure unit.
9. Emit selected diagnostics for every independent failure unit in canonical
   failure-unit order. Use diagnostic identity, canonical location, and
   classifier key only as stable secondary ordering keys when units share the
   same normalized position.

Precedence is a signed integer in the inclusive range `-100` to `100`, defaults
to zero, and is part of the reviewed classifier registry. It expresses an
explicit product rule between otherwise comparable classifiers; it is not
evidence and cannot change confidence. Extension classifiers always default to
zero unless a core registry decision assigns another value.

This algorithm depends only on canonical input values and registry data. It must
not depend on wall-clock time, random values, identity hash codes, hash-map
iteration, filesystem or network state, default locale, time zone, or thread
scheduling.

### Independent failures and aggregation

Arbitration never discards one failure unit because another unit received a
higher-ranked diagnostic. Independent selected diagnostics may be displayed
separately or aggregated when they share a diagnostic identity and aggregation
is declared safe. Aggregation must:

- retain every item's stable unit key and evidence-backed facts in the completed
  diagnostic or its structured child items;
- preserve canonical item order;
- report the exact total and exact omitted count when the operator view folds a
  bounded list;
- retain all items accepted by the normalized model in the correlated structured
  or debug record; and
- never combine contradictory values into one apparent fact.

If independence cannot be proven, candidates share the root failure unit and
compete. Different exception classes or diagnostic codes alone do not prove
independence.

### Blame and remediation safety

Every operator-visible claim must cite evidence with the needed capability.
Confidence alone is never proof.

A diagnostic may name a responsible mod only when it is high confidence and has
corroborating or direct `ownership` evidence that binds the exact failing
artifact, resource, entrypoint, or component to that mod. A package prefix,
message mention, first external frame, adjacency in a causal chain, or presence
in the mod list is insufficient. Without the ownership binding, wording must
describe the verified failing component and omit responsibility.

Help proposals follow these rules:

- low confidence cannot produce specialized help;
- medium confidence may request inspection, validation, or collection of more
  evidence, but may not name a responsible mod or instruct a state change;
- high confidence may offer a specific reversible action only when separate
  `remedy` evidence proves its prerequisite and scope;
- deletion of world or user data, disabling security, broad permission changes,
  automatic configuration edits, and automatic downloads are never emitted as
  direct remediation; and
- restore, remove, replace, or migration advice must state backup, compatibility,
  or verification prerequisites and must not promise success.

Unsupported blame or help is removed before rendering. If it was essential to
the proposed diagnostic meaning, the candidate is rejected instead. These rules
apply equally to built-in and extension classifiers.

### Generic fallback

The generic fallback is a core result, not a low-priority classifier. It is
always available for every failure unit and is used when:

- no classifier matches;
- only low-confidence or malformed candidates remain;
- the highest eligible candidates tie on rank but disagree in meaning;
- processing limits prevent complete arbitration for that unit; or
- an internal arbitration invariant fails.

Its meaning is the existing `SF0001` unexpected-operation diagnostic documented
in [the style guide](../DIAGNOSTIC_STYLE.md#unknown-exception), subject to final
registry ownership in #5. It may state only the verified operation, a redacted
exception type, preservation/omission facts, and where correlated details can be
found. It must not infer a responsible mod, root cause, or corrective action.
Safe help is limited to inspecting the correlated trace or producing an explicit,
sanitized support bundle.

A classifier or arbiter failure must not hide the original event. If even the
generic diagnostic cannot be completed, the pipeline follows the project's
fail-open rule and emits the original event unchanged while separately reporting
Stackframe's failure.

### Development-mode explainability

Development mode adds a bounded arbitration explanation to the correlated
record. It contains, in stable order:

- classifier key, proposed diagnostic identity, and failure-unit key;
- asserted and effective confidence;
- evidence strengths, capabilities, and redacted source descriptions;
- computed rank tuple;
- selected, merged, suppressed, rejected, conflict, or limit-exceeded outcome;
  and
- a stable reason code for every non-selected candidate.

It never includes an unredacted exception message, absolute path, secret,
personal identifier, or raw classifier payload merely because development mode
is enabled. Operator output may report that a conflict occurred only through the
generic fallback; it must not present losing hypotheses as facts.

### Stability and limits

The first implementation must centralize and contract-test these per-event hard
limits:

| Resource | Limit |
| --- | ---: |
| Registered classifiers | 256 |
| Candidates from one classifier | 64 |
| Candidates considered across the event | 512 |
| Evidence references per candidate after de-duplication | 32 |
| Selected failure units before operator folding | 128 |
| Development explanation entries | 512 |

Limits are applied after canonical sorting so repeated runs truncate the same
items. Work budgets use deterministic counters, not elapsed-time races. A
classifier exceeding its per-classifier limit is malformed for that event; none
of its candidates are trusted. If the event-wide candidate budget is exceeded,
units whose arbitration may be incomplete use the generic fallback rather than a
partial specialized result.

Operator folding does not erase failures: it emits the exact omitted count and
keeps accepted items in the bounded correlated record. If an upstream normalized
model limit has already omitted input, arbitration preserves and surfaces its
omission marker. It never claims to have classified data it did not receive.

Classifier changes that alter evidence requirements, precedence, diagnostic
identity, combination behavior, or fallback behavior require fixture updates and
review as observable contract changes. Classifier keys and reason codes are
stable machine-facing identifiers. Wording may evolve without changing
arbitration semantics.

### Decision table

| Situation within one failure unit | Operator result | Development record |
| --- | --- | --- |
| One high candidate with direct typed evidence | Select it | Candidate and winning rank |
| High typed candidate versus medium message-pattern candidate | Select high candidate | Lower candidate suppressed |
| One medium candidate with valid corroborating evidence | Select it with medium-safety restrictions | Evidence ceiling and restrictions |
| Only heuristic or low candidates | Generic fallback | Low candidates and reason |
| Equal-rank candidates with different meanings | Generic fallback | Conflict and both candidates |
| Same identity, compatible facts, combination allowed | Merge once and recompute confidence | Merge members and de-duplicated evidence |
| Different proven failure units | Preserve all; aggregate only under the rules above | Selection per unit |
| Unsupported mod blame on an otherwise valid candidate | Remove blame, or reject if blame defines its meaning | Claim rejection reason |
| Malformed classifier plus a valid independent classifier | Isolate malformed result; select valid candidate | Classifier failure and valid selection |
| Candidate/event budget makes a unit incomplete | Generic fallback for that unit; report omission | Limit and affected units |

### Worked examples

#### Typed bind failure defeats a message resemblance

The normalized root contains a typed bind exception and validated endpoint data.
Classifier `network.bind` returns high confidence with direct identity and scope
evidence. Classifier `generic.message-address-use` returns medium confidence from
a message pattern plus causal context. The first rank component decides:
`network.bind` wins. It may identify the endpoint, but it may say another
listener owns it only if the adapter supplied a result that proves that fact.

#### A frame cannot blame a mod

A failure's first external frame belongs to package `example.mod`, and the
exception message mentions "example". A candidate naming Example Mod has only
heuristic/contextual evidence, so its ceiling is low and it is ineligible. If no
other candidate exists, `SF0001` is emitted without a responsible mod. Even a
medium diagnosis of the failing operation could not retain the blame claim
without an ownership binding.

#### Equal conflict falls back

Two built-in classifiers propose different diagnostics for the same validation
item. Each has medium confidence, one corroborating item, no direct evidence,
and precedence zero. Their rank tuples are equal. Lexical classifier order is not
used to pick a story; the unit receives `SF0001`, while development output lists
the conflict and redacted evidence for both candidates.

#### Independent validation failures survive

A datapack validator returns three structured item identifiers: one unknown
registry key and two malformed values. Classifiers select a registry diagnostic
for the first and a value diagnostic for each of the others. All three units are
retained. The two value diagnostics may be aggregated into one presentation only
if both items remain addressable and the output says there are two. The registry
failure cannot be hidden by that aggregation.

#### Malformed extension output is isolated

An extension candidate references evidence absent from normalized input. That
candidate is rejected with a stable reason. A valid built-in candidate for the
same unit still competes normally. If it wins, operator output does not mention
the broken extension; the bounded development record does.

## Alternatives considered

### First registered match wins

This is simple, but plugin discovery and collection order would change behavior.
It also lets weak classifiers mask stronger evidence, so it is rejected.

### Floating-point probability

Numeric scores imply calibration that the project cannot establish across
unrelated classifier families. Tiny arithmetic or weighting changes also create
unstable outcomes. Ordinal tiers with explicit evidence ceilings are auditable
and sufficient.

### Always select by classifier key

A lexical final tie-breaker would be deterministic but would turn naming into a
semantic safety decision. Conflicting equal-rank explanations instead fall back
without guessing.

### Emit every candidate

Showing contradictory diagnoses moves arbitration to the operator and can expose
unsupported blame. Only proven independent failure units are all retained;
competing hypotheses remain development-only.

## Consequences

### Positive

- Repeated classification of the same normalized input and registry snapshot has
  the same selected set and order.
- Weak or conflicting evidence cannot create unsupported blame or mutating help.
- Independent failures survive arbitration and bounded aggregation.
- Future classifier fixtures can assert exact winners, fallbacks, and reasons.

### Negative

- Candidate and evidence contracts carry more metadata than a boolean matcher.
- Conservative ties produce generic diagnostics until precedence or stronger
  evidence is deliberately added.
- Registry precedence and stable reason codes require compatibility review.

### Risks

- Adapters may overstate evidence capabilities. Contract fixtures must include
  false ownership and misleading-message cases.
- Limits may be too high for performance or too low for large validation sets.
  Benchmarks may change numeric limits through a superseding decision, but may
  not replace explicit omission with silent truncation.
- Development explanations could leak sensitive values if they bypass redaction.
  They must use the same redaction boundary as every other external output.

## Validation

This repository does not yet contain the Gradle scaffold or executable core
types, so this decision is validated as documentation and does not claim runtime
tests exist. The implementation following #7 and #6 must add:

- table-driven fixtures for every decision-table row;
- permutation tests proving classifier registration and candidate return order do
  not change results;
- repeatability tests across locale and time-zone settings;
- conflict, duplicate-key, exception, malformed-reference, and limit fixtures;
- aggregation fixtures proving every independent item or explicit omission count
  survives;
- blame/help policy tests using misleading frames and messages;
- redaction tests for development explanations; and
- property tests over malformed, cyclic, deeply nested, and oversized normalized
  failures.

Documentation review must verify consistency with
[diagnostic style](../DIAGNOSTIC_STYLE.md) and
[security and privacy](../SECURITY_AND_PRIVACY.md), including the fail-open and
no-unsupported-blame guarantees.

## Follow-up

- #6 should make the candidate evidence references and failure-unit identity
  representable without adopting renderer or platform types.
- #5 should register stable diagnostic identities, classifier keys, precedence,
  and machine-readable arbitration reason codes.
- #9 should provide the decision-table, permutation, aggregation, malformed, and
  hostile-input fixtures.
- Specialized classifier issues must declare evidence capabilities and conform to
  this arbiter rather than selecting diagnostics directly.
