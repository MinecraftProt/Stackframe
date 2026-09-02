# Worker handoff

Use this format when an issue changes owner, a session ends before completion, or
one pull request unlocks dependent work. Put the handoff in the issue or pull
request so it remains available to future workers.

After the handoff, every worker creates one concise draft card in the separate
[Stackframe Retrospective project](https://github.com/orgs/MinecraftProt/projects/2).
The card records process lessons; it does not repeat the handoff or PR summary.

## Handoff template

```markdown
### Handoff

**Issue:** #
**Workstream:**
**Branch / PR:**
**State:** ready for review | blocked | paused | dependency delivered

#### Outcome

What now works or what decision was reached.

#### Owned paths changed

- `path/`

#### Contracts added or changed

- Public type, method, schema, diagnostic code, configuration, or behavior.

#### Validation

- Command or scenario and result.

#### Remaining work

- Concrete unfinished item with acceptance condition.

#### Dependencies and consumers

- Blocking issue/PR:
- Issues now unblocked:
- Required base commit:

#### Risks and context

- Known limitation, rejected approach, sensitive data concern, or compatibility note.
```

## Required quality

A handoff is concise but self-contained. The next worker should not need access
to the previous chat transcript to understand:

- what was requested;
- what changed;
- what remains;
- which commit or pull request contains the dependency;
- how to validate it;
- where the important design and safety constraints are documented.

Do not paste secrets, private server data, or huge command logs. Link persistent
repository evidence and summarize only the relevant result.

## Retrospective entry

Use the template in the retrospective project's README. Include:

- the issue and PR;
- what worked;
- what slowed progress;
- what was surprising;
- one specific improvement for the next worker;
- an issue link for actionable follow-up, or `None`.

Write one entry per work cycle after validation and PR handoff, fill the
Workstream, Outcome, Issue or PR, Worker, and Date fields, then set the card to
Done before the worker stops. Reviewers or coordinators add another card only
when their work produced a distinct lesson.
