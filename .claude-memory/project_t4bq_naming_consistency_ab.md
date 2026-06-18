---
name: project-t4bq-naming-consistency-ab
description: "Result of the 2026-06-18 A/B for T4-BQ slice 2 (consumer-4 naming-consistency block): naming-churn target met emphatically (invented exception names 5→0, compile churn 7× lower), run-outcome benefit NOT demonstrated (neither arm fulfilled). n=1."
metadata:
  node_type: memory
  type: project
  originSessionId: 609aab02-3d49-46ec-874f-e42f8a4aa75f
---

**The A/B (2026-06-18, n=1 pair, test-authoring seed = the library goal WITH the "JUnit 5 tests for every endpoint" block).** Single jar (built 01:39 with the slice-2 code), `reset-on-start`, identical seed; the ONLY difference was `--autobuilder.evolution.naming-consistency.enabled=true|false`. Backups: `/tmp/abq_ab/{armON,armOFF}/` (autobuilder.log + behaviour.ndjson + output_src). Both runs honest terminals, zero false completions.

| metric | Arm ON (flag true) | Arm OFF (control) |
|---|---|---|
| block fires (`EXISTING PROJECT TYPES` in prompt) | 37 | 0 (control clean) |
| **invented exception names** (referenced as missing symbol, never declared on disk) | **0** | **5** (`BookNotFoundException`, `MemberNotFoundException`, `InvalidIsbnException`, `LoanValidationException`, `BookAlreadyExistsException`) |
| total `cannot find symbol` (all symbols) | 153 (4.0/cyc) | 1087 (13.1/cyc) — 7× total, 3.3× per-cycle |
| distinct missing `*Exception` classes | 2 (both actually declared → import/wiring, not invented) | 9 |
| synonym cluster for one concept | none in the declared set (2 generic: `ResourceNotFoundException` + `BusinessRuleViolationException`) | `MemberMaxLoansException`/`MaxActiveLoansExceededException`/`MemberLoanLimitExceededException` (3 names for "max active loans"); 5 competing not-found variants |
| cycles / merges | 38 / 5 | 83 / 14 |
| terminal | T4-I convergence stall (frozen at 6 completed-with-merge, ~26 min) | root GIVEN_UP (~62 min) |

**What is established (strong, this pair):** half-1 "does it fire" is CLOSED (37× ON, 0× control). The block's **design target — suppress the four-names-for-one-exception churn — is met emphatically**: invented exception names 5→0, exception compile-churn ~20× lower, total compile-churn 7× lower (3.3× even per-cycle, so not just a duration artifact). Arm OFF reproduced the 2026-06-17 pathology exactly (three synonyms for "max loans" incl. the literal `MaxActiveLoansExceededException`). First field proof that the inbound consumer (4) reduces the failure class it targets. Matches [[project-introspection-as-north-star]]'s information-axis claim (feed the local model the missing *information* and the thing the information is about improves) — same shape as T4-BS doubling verifier recall.

**What is NOT established (the honest ceiling):** a **run-outcome** benefit. Neither arm fulfilled the goal; Arm ON actually stalled SOONER (26 min / 6 completed via the convergence brake) than Arm OFF (62 min / 14 merges → GIVEN_UP). So cleaner naming did not carry the run to completion — consistent with the long-standing deferral read that the binding constraint on *completion* is last-mile/model capability, not missing naming information ([[feedback-activity-not-progress]]: a mechanism firing + improving its own metric ≠ moving the run outcome).

**Confounds (why this is n=1, not a verdict):** (1) the planner decompositions diverged — ON chose 2 generic exceptions, OFF chose per-rule exceptions; part of the churn gap is that ON's design inherently needs fewer exception types (though the block plausibly *caused* that convergence — its mechanism is "reuse the exact name"). (2) single stochastic pair; the runbook demands ≥3 pairs. (3) **yellow flag:** ON's earlier stall — does the strong "reuse these EXACT names" steer over-constrain the evolver into a too-small exception design? Speculative; both arms failed, just via different terminals. Do NOT read ON's earlier stall as either help or harm on outcome.

**Disposition (recommendation, pending user):** keep the flag ON — it is cheap and removes a large, real waste class (1087→153 compile errors is many salvaged cycles) with no clear outcome regression — but **do not claim a convergence benefit**. Re-validate with ≥2 more pairs before a firm verdict; watch whether ON's earlier-stall replicates (if it does, investigate over-constraint). The completion ceiling held, as predicted; consumer (4) is validated as *doing its job*, not as *the convergence lever*.
