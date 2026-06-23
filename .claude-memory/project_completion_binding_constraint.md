---
name: project-completion-binding-constraint
description: "2026-06-22 investigation: the binding constraint on GOAL_FULFILLED is keeping a large interdependent (main+test) build compiling, NOT naming or vague 'model capability'. build red 60-83% of cycles, dominated by cross-file structural errors. REFINED same day by the test-compile-coupling A/B: the '0/7 never fulfills' claim was superseded — a run fulfilled by writing ZERO tests (test gate passes vacuously). The test requirement is neither reliably achievable nor enforced. See update block at end."
metadata:
  node_type: memory
  type: project
  originSessionId: 609aab02-3d49-46ec-874f-e42f8a4aa75f
---

**Question:** what actually prevents runs from reaching GOAL_FULFILLED? Investigated 2026-06-22 over the 6 backed-up T4-BQ A/B runs (`/tmp/t4bq-naming-block-ab/pair{1,2,3}/{block-enabled,baseline}/`) + the 4356-file log corpus. Belief→verification, not assertion.

**Headline finding (evidence-backed):** the binding constraint is **keeping a large, tightly-coupled (main + test) build compiling long enough to reach the completion edge.** It is NOT naming/synonyms, and "local-model last-mile capability" is too vague — the specific failure is **global cross-file structural coherence across a big working set**.

**The discriminator — the test-authoring requirement:**
- Corpus cross-tab: **0 of 7** runs whose seed required working tests ("provide JUnit 5 tests for every endpoint / MockMvc tests for each controller") ever fulfilled. Fulfillment is common WITHOUT that requirement (40+ distinct non-test-seed runs fulfilled).
- The recent `2026-06-17_19-03` run FULFILLED on the SAME framework with a non-test seed (and zero tests → test gate abstained). So the framework still completes; adding the test requirement is what stops it.

**The mechanism, quantified (aggregate over the 6 test-seed runs):**
- Build is COMPILE_ERROR most of the time — OK-ratio 0.17–0.37 per run.
- Dominant errors: `cannot find symbol` 2253, `package com.example.* does not exist` 443 (referencing project packages not yet created / wrong place), `constructor/method cannot be applied` 343 (wrong arity/signature). Smaller but test-specific: missing framework/test deps `package javax.validation|org.mockito|org.hamcrest|org.springframework.test ... does not exist` 140 (pom lacks test deps; javax-vs-jakarta).
- Error locus: **src/main 3732 vs src/test 721** (~5:1) — main code breaks more than the tests do; requiring tests roughly doubles the interdependent working set and knocks main around too.
- Downstream: planner backlog saturates at its 18-active cap (`PLANNING_THROTTLED` / "backlog cap reached" 364 events) because while COMPILE_ERROR the planner may only append build-fix subgoals — it fills with unresolved build-fixes and throttles.
- **Completion edge never reached: 0/6 rootCOMPLETED; package/test gates effectively never ran.** Runs halt (T4-I convergence brake / GIVEN_UP) far short of the edge.

**Interpretation:** this is "divide-and-conquer WITHOUT a linkage layer" — each cycle is a local edit that is locally plausible but doesn't resolve against the rest of the tree (wrong package, missing member, wrong constructor arity, missing dep). With test-compile coupling the tree is ~2× larger and more cross-referential, so coherence collapses faster than the loop repairs it. It IS a model limitation, but a *specific, addressable* one (global symbol map + build-config), not "the model isn't smart enough." Matches [[project-introspection-as-north-star]] (information axis: route the global symbol/dependency map to each local step).

**Candidate levers — HYPOTHESES to test, NOT asserted fixes** ([[feedback-no-defensive-recast]]: don't name "the lever" off one investigation):
1. Full T4-BQ symbol-model closure (usage-derived member synthesis + dependency-ordered creation + missing-type closure) directly targets the dominant error class (`cannot find symbol` / ctor-mismatch / project-package-missing). Slice-1 member synthesis alone was inert; the closure/ordering may be the part that matters. Untested at scale.
2. Build-config management (ensure test deps in the pom; resolve javax-vs-jakarta) — addresses the 140 dep-missing class, test-specific. **User constraint (2026-06-22): do NOT add fixed javax→jakarta mappings; let the system discover the right coordinate dynamically (project pom → classpath → Maven Central), with a fixed mapping only as a last-resort fallback** ([[feedback-no-hardcoded-ecosystem-data]]). Existing debt to fix in that direction: `WellKnownPackageRegistry` (lines ~94-95) already hardcodes `javax.validation→jakarta.validation` + `javax.persistence→jakarta.persistence` (semi-dynamic on the SB version, but the pairs are frozen) — a frozen-worldview table, the exact pattern the rule warns about. Untested whether dynamic discovery is even the bottleneck vs the structural class (1) which is far larger (583 dep/pkg-missing combined vs 2253 cannot-find-symbol + 343 ctor).
3. Process question, not a build: did `mvn test-compile` probe (T4-BN-2) + the test-run gate (T4-AN-2) raise the bar past current capability for a SINGLE autonomous loop? Maybe test authoring should be a scoped phase after main reaches COMPILE_OK, not coupled every cycle. Untested.

**What is NOT established:** which (if any) lever moves the outcome; whether main-code incoherence would persist even without the test requirement at higher complexity. Next verification if pursued: an A/B on the test-compile coupling (probe = `mvn compile` vs `mvn test-compile`) on the test seed, measuring whether main reaches a stable COMPILE_OK and the edge is reached.

**Corrects:** the roadmap's frequent "remaining variance is local-model last-mile capability" shorthand — true but unspecific; the measurable constraint is build-greenness of a large interdependent tree, gated hardest by the test-authoring workload.

---

## UPDATE 2026-06-22 — test-compile-coupling A/B: inconclusive on its question, but corrects the headline

Ran the proposed A/B (probe `mvn test-compile` [coupled] vs `mvn compile` [decoupled], test-authoring seed held constant, 2 counterbalanced pairs, single jar, reset-on-start verified firing — `SystemResetConfig` purges goal graph + wipes output/mutations/snapshots/meta each run, so all 4 were clean starts). Backups `/tmp/test-compile-coupling-ab/`.

| run | config | terminal | cyc | OK-ratio | test files | note |
|---|---|---|---|---|---|---|
| pairA | coupled | paused | 36 | 0.48 | wrote tests | thrashed |
| pairA | decoupled | paused | 20 | 0.86 | few (8 files, barely restructured) | high ratio partly = built little |
| pairB | decoupled | GIVEN_UP | 4 | 0.00 | test file in src/main | collapsed fast |
| pairB | coupled | **GOAL_FULFILLED** | 4 | 0.83 | **0** | real main code, no tests |

**Inconclusive on the coupling question.** The dominant variance is **whether the model engages with tests at all** (stochastic per run), which swamps the probe-coupling variable: same config + same seed produced both a 36-cycle thrash (pairA/coupled, wrote tests) and a 4-cycle fulfillment (pairB/coupled, skipped tests). Run-to-run variance 4–36 cycles. More runs of this design would measure the confound, not the question.

**The real finding (well-evidenced) — the test requirement is neither achievable nor enforced.** The system faces a fork: (a) attempt tests → cross-file coherence collapses, build stays red, never reaches the edge; or (b) skip tests → `mvn test` gate returns COMPILE_OK *vacuously* (no tests to run), criteria verify against real main code, root COMPLETED. pairB/coupled took (b): 0 test files, genuine main artifact (ISBN `\d{13}` `@Pattern`, max-3-loans `>=3` both present), fulfilled in 4 cycles. **The seed's explicit "provide JUnit/MockMvc tests" requirement is silently unenforced when the model writes none.** Not a hollow false-completion (main is real) — an *unenforced-requirement* gap.

**Corrects the earlier claim** "0/7 → test authoring is the wall": completion CAN occur on the test seed, by skipping tests. So the constraint isn't "can't ever finish with tests"; it's that working tests are a fork the system neither reliably clears nor is forced to clear.

**Candidate next steps — HYPOTHESES, not asserted ([[feedback-no-defensive-recast]]):** (1) the test gate should not pass vacuously when the seed requires tests and none exist (enforce test *presence*, not just `mvn test` exit code) — addresses fork (b); but enforcing it would just push runs onto fork (a) where they currently collapse, so (1) alone likely converts false-passes into honest non-completions, not into fulfillments. (2) A clean coupling A/B would need to control test-engagement (e.g., a seed phase that mandates tests only after main COMPILE_OK), which is itself the "scope test-authoring as a later phase" idea. (3) Whether decoupling helps is still genuinely unknown. Not building anything without a steer.

**SHIPPED 2026-06-22 — step (1), the unenforced-test-requirement guard (T4-AN-2b, suite 1639/1639).** `TestRunGate.verifyTestsPass(cycleId, testsRequired)` overload: when `testsRequired` and no test sources exist under `<base>/output/**/src/test/**/*.java`, it returns a completion blocker instead of the vacuous `mvn test` pass — decided on disk, before the suite even runs. Requirement read from the goal spec via static `specRequiresTests(spec)` = generic `\btests?\b` (NO framework names — [[feedback-no-ecosystem-jargon-in-framework-code]]); wired at the `GoalPlannerService` closure edge from `root.getDescription()`. Fail-safe: scan error → treat as present (never false-block a clean completion). 4 gate tests + 1 closure-stub fix. Validated against this session's artifacts: pairB/coupled (0 test files) now BLOCKS; pairA/coupled (6 test files) proceeds to the suite. **As predicted, this is a correctness/honesty fix, not a capability gain** — it converts the false "fulfilled with 0 tests" into an honest non-completion that demands the tests; it does NOT make test-required runs more likely to finish (that is still the fork-(a) coherence wall). Steps (2)/(3) remain open and unbuilt.

---

## UPDATE 2026-06-22 (later) — prompt-salience lever found + shipped (n=1, A/B pending)

User hypothesis: the diagnostic info isn't absent, it's DROWNED — too much prompt, so the dedicated signal loses weight. Measured on a real evolution prompt (naming-experiment backup): total ~4,770 tok / 510 lines, and **one block — the verbatim echo of the previous cycle's raw response — was ~3,056 tok / 64%**, vs the naming diagnostic at ~123 tok / 2.6% (drowned 25:1). The echo is largely redundant (current state is already in Structure Summary + Diff; repetition is caught by dedicated signals). Notable supporting differential: feeding structural facts to the local model HELPED in the focused completion-verifier prompt (T4-BS, recall 0.25→0.50) but was IGNORED in the bloated evolution prompt (naming block) — same intervention, opposite outcome, consistent with salience/dilution (confound: judge-vs-generate task difference).

SHIPPED (`97fe5cc`): cap the previous-raw-response echo to the leading 40 lines + elision note; importance-ranked (only that block capped, all diagnostics/structure stay full); flag `autobuilder.evolution.prompt-compaction.enabled` (code false / yml true). 3 tests, suite 1642.

**n=1 field run (75-min deadline-cut, test seed, NOT fulfilled):** compaction fired 41×; merge rate 0.36 + OK-ratio 0.39 sit at the TOP of the prior no-compaction range (0.13–0.27 / 0.17–0.37) but WITHIN its spread; 0 invented exceptions; still merging at deadline. **Encouraging, NOT proof — classic n=1 trap ([[feedback-verify-population-before-claim]]).** Validation = controlled on/off A/B, same seed, counterbalanced ≥2 pairs (the rigor the naming A/B established; pair-1-alone overclaimed). Until then: shipped behind a flag, benefit unclaimed. This is the most promising lever found because, unlike per-signal fixes, it would lift EVERY diagnostic block's weight at once.

---

## UPDATE 2026-06-22 (eval) — prompt-compaction lifts diagnostic adherence 0.08→0.50 (proximal, controlled)

Replaced the slow/noisy 75-min loop A/B with a direct single-call adherence eval (the drastic reframe: measure the mechanism, not the emergent run). New `PromptCompactionAdherenceEvalTest` (opt-in `PROMPT_COMPACTION_EVAL=1`, mirrors the T5-AF layer-2 pattern) + `PromptSalienceFixtures`: each case = a type that exists on disk under a CANONICAL name, the naming diagnostic listing it, and a bloated previous-response echo that refers to the same concept by a WRONG synonym. Render the real evolution prompt twice differing ONLY in `compactPreviousResponse`, one model call each, score whether the response reuses the canonical name. 4 cases × 3 trials × 2 conditions = 24 calls, ~4 min, paired.

**Result (qwen3-coder:30b, same model the loop uses):** echo-full adherence **1/12 = 0.08**; echo-capped **6/12 = 0.50**; **delta +0.42**. Same model/prompts/diagnostic — only the echo cap differs. This is the proximal proof the loop couldn't give: the model was ignoring the diagnostic because the 64% echo drowned it, NOT because it can't follow it. Confirms the user's dilution hypothesis.

**Not uniform — the honest detail:** already-returned 0/3→3/3 and loan-limit 1/3→3/3 (compaction flips them); but book-not-found 0/3→0/3 and member-store 0/3→0/3 were UNMOVED — the model minted the synonym (`NoSuchBookException`, `MemberRepository`) both ways. Those are strong training-prior names; the base model's naming prior overrides in-context facts, a different failure than dilution that compaction cannot fix (→ outbound deterministic rename, T4-AW direction).

**Scope of the claim (do NOT overclaim — [[feedback-no-defensive-recast]], [[feedback-activity-not-progress]]):** PROXIMAL only — compaction makes the model attend to a diagnostic in a single call (~half the synonym-minting removed). It does NOT prove a full run fulfills more often (distal); and it does not fix prior-driven naming. It earns compaction staying on (flag true) and predicts diagnostics land more often. Method win: this eval is re-runnable in minutes as model/prompt change, vs a 5-hour loop batch — the right level to test per-prompt mechanisms.

---

## UPDATE 2026-06-23 — compaction DISTAL A/B: no measurable run-outcome benefit (proximal-but-inert)

Controlled compaction ON vs OFF, test seed, 2 counterbalanced pairs, cycle-cap 40 + 1s delay (cheaper batch). Backups `/tmp/compaction-distal-ab/`.

| pair | cond | OK-ratio | merges | files | tests | invented | edge | fulfilled |
|---|---|---|---|---|---|---|---|---|
| 1 | off | 0.47 | 11 | 10 | 0 | 0 | 0 | no |
| 1 | on  | 0.57 | 7  | 26 | 6 | 0 | 0 | no |
| 2 | on  | 0.35 | 13 | 10 | 2 | 0 | 0 | no |
| 2 | off | 0.39 | 12 | 19 | 6 | 0 | 0 | no |

Averages: OK-ratio on 0.46 / off 0.43 (+0.03, inside the on-condition's own 0.35–0.57 spread); merges on 10 / off 11.5; files on 18 / off 14.5; tests on 4 / off 3. **0/4 reached the completion edge, 0/4 fulfilled, invented=0 in ALL (not discriminating at capped scale).**

**Verdict: compaction is proximally real but DISTALLY INERT.** The single-call eval proved it lifts diagnostic adherence 0.08→0.50; this full-run comparison shows that does not move the run outcome — every metric is inside the noise. By [[feedback-activity-not-progress]] (validate the outcome moved, not activation) compaction is NOT a completion lever. It is a cheap, proximally-validated, distally-neutral prompt cleanup — keep-on is defensible as cost reduction, but claim only the proximal win.

**The real redirect:** 4 more capped runs, 0 reached the edge — runs stall PARTIAL (10–26 files, planner not driving full spec coverage; same T1-EI/coverage stall as the 2026-06-22 user run). The binding constraint on completion is UPSTREAM of prompt content — drive-to-completion / coverage, not salience. That is the next thing to investigate (NOT yet asserted as THE lever — corpus shows the coverage/backlog-churn family is common but hard amputation is only ~3%; needs its own grounded diagnosis before any build).

---

## UPDATE 2026-06-23 (later) — T4-BN-2 gate-vs-steer A/B CONTRADICTS the freeze diagnosis

Built the steer-not-gate fix (78155d9) on the diagnosis "the per-cycle test-compile GATE freezes main evolution → caused the 06-17→06-23 cliff." A counterbalanced gate-vs-steer A/B (2 pairs, cycle-cap 40, test seed; backups /tmp/t4bn2-validation/) does NOT support it:

| pair | cond | terminal | cyc | merges | files | tests | edge | probeERR | testPathErrs |
|---|---|---|---|---|---|---|---|---|---|
| 1 | legacy-gate | GIVEN_UP@8 | 8 | 2 | 3 | 0 | 0 | 1 | **0** |
| 1 | fix-steer | cap40 | 40 | 8 | 22 | 6 | 2 | 17 | 108 |
| 2 | fix-steer | paused@24 | 24 | 7 | 25 | 6 | 0 | 16 | 271 |
| 2 | legacy-gate | cap40 | 40 | 4 | 23 | 6 | 0 | 24 | **337** |

- **The dramatic pair1 contrast was an artifact:** pair1/legacy-gate collapsed at 8 cyc with 0 test-path errors — gave up early for reasons unrelated to test-compile; NOT a "freeze signature" (I wrongly called it one).
- **Gate-mode does NOT freeze:** pair2/legacy-gate hit 337 test-path errors (the exact freeze condition) yet ran to cap and built 23 files + 6 tests. Gate-mode keeps merging main-only mutations while test-bearing ones are rejected.
- **No outcome separation:** both conditions ~22-25 files + 6 tests; neither reached the edge reliably; neither fulfilled. The fix fires (steered 3-12×) but shows NO demonstrated run-outcome benefit.

**Correction:** the freeze mechanism is real IN CODE (a test-only break does reject the mutation) but is NOT load-bearing on the outcome, and removing it does not move fulfillment. The 06-17 cliff is most parsimoniously "test-requiring goals are simply harder" (must also author working tests + deps), NOT the test-compile gate. This is the SAME failure mode as the naming/compaction work: a mechanistically-real change that is distally inert ([[feedback-activity-not-progress]], [[feedback-no-defensive-recast]], [[feedback-verify-diff-is-substantive]]). The pair-1 excitement was caught and retracted before it was reported as a win.

**Disposition:** keep the steer fix as a principled correctness change (a main-green mutation shouldn't be discarded over a test that may be pre-existing or ahead-of-main; flag-gated, harmless) BUT drop the claim it fixes the cliff. T4-BN-3 (focused test-repair) inherits a weakened rationale — its one independently-justified piece is the deterministic "ensure test deps in pom" step, which targets the dominant REAL error (missing Mock/Test/ExtendWith deps). Get a steer from the user before building it.
