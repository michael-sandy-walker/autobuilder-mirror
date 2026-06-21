---
name: project-t4bq-naming-consistency-ab
description: "T4-BQ slice-2 naming-consistency block A/B, 3 pairs (2026-06-18 + 2026-06-21): block fires reliably but NO robust effect on its own metric (pair3 reversed) and ZERO run-outcome benefit (0/6 fulfilled). STOOD DOWN — flag back to false, code dormant. Lever is outbound (T4-AW), not inbound prompt."
metadata:
  node_type: memory
  type: project
  originSessionId: 609aab02-3d49-46ec-874f-e42f8a4aa75f
---

**Verdict (3 pairs, n=6 runs): the inbound naming-consistency block is truthful but unproven — STOOD DOWN 2026-06-21 (flag → false, tested code kept dormant).** Same disposition slice 1 (`synthesize-members`) got 2026-06-16. The pair-1-only read below was an overclaim corrected by pairs 2–3.

**Setup.** Test-authoring seed (library goal WITH "JUnit 5 tests for every endpoint"). Single jar, `reset-on-start`, identical seed; the ONLY difference per run was `--autobuilder.evolution.naming-consistency.enabled=true|false`. Backups: `/tmp/t4bq-naming-block-ab/pair{1,2,3}/{block-enabled,baseline}/`. Pairs 2–3 order counterbalanced. All 6 runs honest terminals, zero false completions.

| pair | config | terminal | cyc | merg | cfs | cfs/cyc | invented exc | fulfilled |
|---|---|---|---|---|---|---|---|---|
| 1 | block-enabled | paused (T4-I stall) | 38 | 5 | 153 | 4.0 | 0 | no |
| 1 | baseline | GIVEN_UP | 83 | 14 | 1087 | 13.1 | 5 | no |
| 2 | block-enabled | paused | 68 | 11 | 204 | 3.0 | 0 | no |
| 2 | baseline | (cut at 70min) | 66 | 10 | 307 | 4.7 | 0 | no |
| 3 | block-enabled | paused | 41 | 11 | 324 | 7.9 | **7** | no |
| 3 | baseline | paused | 28 | 4 | 178 | 6.4 | 4 | no |

("invented exc" = `*Exception` names referenced as a missing symbol that were never declared on disk. "cfs" = total `cannot find symbol`.)

**What 3 pairs establish:**
1. **Fires reliably** — 37/64/39× when enabled, 0× in every baseline. Half-1 closed.
2. **Zero run-outcome benefit** — 0/6 fulfillments; the completion ceiling held in EVERY run, both conditions. The block is not the convergence lever (as the deferral always predicted — the binding constraint is last-mile/local-model capability).
3. **No robust effect even on its own design metric.** Pair 1 looked emphatic (invented 0 vs 5; churn 4.0 vs 13.1/cyc) but pair 2 was marginal and **pair 3 REVERSED it** (enabled invented 7 vs baseline 4; churn 7.9 vs 6.4/cyc). Pair-1's win was substantially decomposition luck (its planner happened to pick 2 generic exceptions). The pair-2 baseline is the clean counter-example: it declared 7 exceptions and invented 0 — perfectly coherent naming WITHOUT the block.
4. **The mechanism's ceiling is exposed directly.** In pair 3 the enabled run minted synonyms of names the block was actively listing: declared `MemberHasTooManyActiveLoansException` but invented `TooManyActiveLoansException`; declared `BookAlreadyReturnedException` but invented `ReturnAlreadyException`. **The local model ignores the prompt directive even when the canonical name is in front of it.** So a stronger/longer inbound prompt cannot fix this — it is the wrong layer.

**Why no inbound "fix" was attempted (despite the brief).** The failure is not a bug in the block; it is that surfacing information does not force a local model to use it under load. Building a bigger inbound block would be exactly the reflexive, evidence-free steering [[feedback-activity-not-progress]] forbids. The real lever for synonym-minting is OUTBOUND, deterministic enforcement — detect a new name that is a synonym of an existing on-disk type and rewrite/resteer it — which is **T4-AW's `SymbolReviewer`**, a separate ticket, not a prompt tweak. Or a stronger LOCAL model ([[project-local-first-constraint]]).

**Re-entry condition:** build the outbound T4-AW reviewer (deterministic synonym catch) OR a better local model; only then is re-enabling the inbound block (as the cheap, cooperating half) worth retesting. Until then it stays off.

**Methodology note:** pair-1-alone produced a confident "design target met emphatically" claim that 2 more pairs falsified. Single nondeterministic pairs are not evidence ([[feedback-verify-population-before-claim]]); the runbook's ≥3-pair minimum earned its keep here. Also [[feedback-no-invented-labels]] (don't name runs "arm ON/OFF" — use block-enabled / baseline).
