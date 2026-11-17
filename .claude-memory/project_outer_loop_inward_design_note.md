---
name: project-outer-loop-inward-design-note
description: "Design note for T4-EZ (2026-11-16, condensed 2026-11-17): Autobuilder runs its own development cycle -- read the roadmap, pick or create a ticket, implement, verify, record against the ID, propose the next. Unit of similarity = a CASE (situation -> action -> result); finding / fact-checking / fault-or-design judged by different authorities; the ladder (T6-T) is the implementation rung. Read before any cross-run, self-diagnosis, ticket-proposal or self-application work."
metadata:
  type: project
---

# Moving the outer loop inward

**Status (2026-11-17):** design note for the umbrella T4-EZ. Condensed on 2026-11-17 at the user's word; the first
version (three directions as planned on 2026-11-16) is in git at `c9b44af8`, the measurement record in
[[project-t4ez-measurements]].

## The goal

The user (2026-11-16): *"Atm we're still busy a lot with the external loop between you and me, while in the long term,
Autobuilder should take over that part more and more. This can only be done with an introspection capability regarding
the question 'have I addressed or solved any similar problems already in other similar runs?'. Also, Autobuilder
should get more into the direction of building itself in the mid-future."* Similar problems include OTHER PROJECTS;
the knowledge is **run knowledge + memory knowledge** (`.claude-memory`).

Sharpened by the user on 2026-11-17: Autobuilder runs the **whole development cycle** itself -- read the roadmap, pick
or create a ticket, implement it, verify it, record the outcome against the ticket ID, propose the next (*"build on
its own based on the roadmap and even create and progress on tickets by itself"*). On filing, every open ticket had
been found by the outer loop and none by the loop. The north-star destination is [[project-introspection-as-north-star]].

## The cycle and what each step needs

```
  roadmap --> pick / create a ticket --> implement on a copy --> verify --> record against the ID --> propose next
                     ^                                                                                     |
                     +------------------------- the user decides (authority widens with competence) -------+
```

**Propose (find what to change).** From a finished run: the roadmap as it stands plus a structural digest of the run
(no model: run-end report, stuck ledger, diagnosis requests, refusals, rejection reasons, pauses / rewinds, input
decisions, narrator lines). Measured on 7 tickets we filed from 3 runs + 2 clean controls: the loop's model FINDS
(5 of 7, 3 with the right cause) without reasoning, with false alarms. Three different authorities are needed:
- **finding** -- the model over the digest, no reasoning (it finds; reasoning makes it timid);
- **facts** -- reading: the loop's own `LogInvestigator` probing the logs and files (it re-found T1-GM's cause by
  opening the test the gate misread);
- **fault or intended design** -- the project's RECORDED DECISIONS (closed tickets and the rules they fixed). A model's
  opinion follows the prompt's wording either way (measured twice). This is where memory knowledge is indispensable.

**Implement and verify.** The single-ticket rung exists: **T6-T, the ticket ladder** (`scripts/ladder_case.py`: the code
before a past commit, its message as the ticket, its tests as the judge; withheld-test variants where the loop writes
its own). Bands 1-3 measured (`ladder-rows`, 6 classes, fulfilled 2 of 3 twice; `ladder-grind` best 48/51; band 4 never
cut; the whole tree, 586 files, never attempted). New beyond it: a FORWARD ticket (an open one, cut from HEAD, judged by
its DoD and tests the loop writes, the existing suite green), a CHAIN (each ticket on the loop's own previous result),
how different the rebuilt code is, and the loop choosing the order. The rebuild of past tickets is the baseline /
control experiment, not the goal.

**Record and coordinate.** The Observatory (`observatory/`, [[reference-observatory]]) already shows roadmap,
tickets, live events, decisions and test reports, read-only. Missing: a **proposal registry** (drafts with evidence,
beside the open tickets and the run; the user accepts, edits or drops) and the **ticket -> run link** (only ladder runs
have it, via the commit title). The Observatory shows and records; it never decides and holds no planning engine.

## Problem memory -- what carries across projects

The unit of similarity is a **CASE: situation -> action -> result** (the user, 2026-11-17): a decision point where a
response or verdict came back with a dilemma, what the loop did next, what followed. Measured over 597 archived runs:
situations recur across projects and the action matters, but "what worked for this situation elsewhere" chose no
better than one situation-blind rule -- **after a rejection or refusal, step back (move on, replan) instead of retrying
the same files** (observational; a replay of frozen situations would prove it causal). Matching by signature text, by
cause, by fix form, by combined lenses or by the model's reading of the reason all came out the same. Real dilemmas
(requirements pulling apart) are too rare to count: they belong to diagnosis, and the transferable knowledge for them is
memory knowledge -- "this was the framework, not the work; a closed ticket fixed it; a recurrence is a regression".

**Rules for any knowledge the loop imports** (unchanged): it arrives as measurements with provenance, never as fact
([[project-ticket-premises-expire]]); read-only and advisory under T2-ER (`@HistoryRead`); a ticket id reaches a prompt
only if the prompt defines it ([[feedback-no-internal-ids-in-model-facing-text]]); no target names in framework
strings; imported knowledge is judged for applicability before it binds (T2-FN was a brought project's history doing
harm).

## What already exists (do not rebuild)

- **T4-CT:** problem memory across runs of the SAME task (`StuckPriors`, keyed by the seed's SHA-256): the question that
  ended a signature is asked first next time. Kept as it is (a shape key was measured and not worth it).
- **The stuck layer** (T4-DY, T4-DZ, T4-EU): sources, authorities structure -> observation -> model last,
  `EvidenceReader` / `LogInvestigator`, the diagnosis request and the pause ([[project-t4dy-meta-layer-design-note]]).
- **The ladder** (T6-T) and its toy cases; **the run archive** (599 runs, behaviour logs, main logs with every model
  response, ledgers, final trees); `meta/run-end-report.json` per run.
- **The outer loop's record:** `roadmap.md` (Observed, cause, fix, DoD and how it was verified per ticket), git history
  (643 ticket commits, 534 with tests).

## Guardrails

The framework that runs and the referee are never the copy; the measure is not one the copy can change; the base case
is the suite green. A change to Autobuilder itself lands only through the user ([[feedback-operator-reviews-ports]]) --
a STARTING rule, to widen step by step with demonstrated competence, not the end state.

## Open questions for the user

- **Memory knowledge, how read:** the checker needs the closed tickets and their decisions. Read from the checkout as a
  snapshot, or exported into a product-side record the loop owns ([[feedback-process-apart-from-product]])? Which
  parts beyond closed tickets (the feedback lessons)?
- **Where drafts appear:** the proposed answer is the Observatory's proposal registry; a file in `meta/` beneath it.
- **The first forward ticket:** module-sized (proposed) before the whole tree.

## Related

[[project-introspection-as-north-star]] (the destination), [[project-living-project-vision]], [[project-cross-run-knowledge-base]],
[[project-t4dy-meta-layer-design-note]], [[project-pattern-recognition-design-note]] (a pattern is worth recognizing
only if it predicts something), [[project-t4ez-measurements]] (the record), [[reference-observatory]].
