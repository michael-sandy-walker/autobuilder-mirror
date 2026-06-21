---
name: project-completion-binding-constraint
description: "2026-06-22 investigation: the binding constraint on GOAL_FULFILLED is keeping a large interdependent (main+test) build compiling, NOT naming or vague 'model capability'. 0/7 test-requiring-seed runs fulfilled; build is red 60-83% of cycles, dominated by cross-file structural errors; completion edge never reached."
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
