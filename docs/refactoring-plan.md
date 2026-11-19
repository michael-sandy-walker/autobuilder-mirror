# Refactoring plan grounded in repository history

Prepared 12 November 2026 against `1a034239`.

For the concrete edit-by-edit sequence, follow the [step-by-step refactoring guide](/home/anugeshtu/IdeaProjects/autobuilder/docs/refactoring-guide.md). This plan retains the historical evidence, file inventory, and completion checklists.

## Objective and evidence

Make the main execution paths understandable, give each decision and mutable state a clear owner, and preserve the behavior accumulated through field fixes.

The comparison uses ancestors of the current branch selected at May 1, August 1, and October 1. Counts cover tracked production Java files, including comments and blank lines. They describe growth rather than complexity by themselves.

| Snapshot | Files | Lines | GoalPlannerService lines |
| --- | ---: | ---: | ---: |
| May, `4da6e25d` | 307 | 28,841 | 1,863 |
| August, `f4822d95` | 420 | 58,159 | 3,968 |
| October, `4f83e207` | 510 | 83,907 | 5,460 |
| Current, `1a034239` | 580 | 103,584 | 6,033 |

The historical snapshots were inspected through Git. The current method inventory, selected bodies, caller searches, commit history, and existing test names were also inspected. This is a staged implementation plan. Exhaustive dependency tracing and coverage assessment are explicit entry work for each slice.

May provides a reference for the core goal and execution responsibilities. It already concentrates substantial logic in GoalPlannerService. August provides an intermediate reference after stronger closure and recovery checks. Current requirements include imported projects, continued runs, and rewind, which those earlier designs only partly supported.

## Historical responsibility map

| Area | Historical development | Current boundary to recover |
| --- | --- | --- |
| Goal state | May already combines criteria metadata, promotion, graph sync, planning, and root completion. August adds resume regrounding, scope repair, rehabilitation, and verification before pause. October adds observed-leaf promotion and goal-state restoration. November adds input-specific goals and observed criteria. | Goal transitions own tree mutation. Criterion interpretation and verification have explicit inputs and results. Persistence stays behind the existing graph/file paths. |
| Run lifecycle | May exposes initialization, cycle execution, reset, and signals. August includes exclusive operations and pause. October adds checkpoint capture/restore and start modes. November adds queued rewind and project inputs. | One owner of the run lock and lifecycle state, with explicit ordered coordination of existing services. |
| Build and runtime verification | BootGate is absent in May. July introduces completion-edge boot checks and behavioral exercises. By October it exposes observations and attributed failures. Current code also owns serving, CLI invocation, sessions, grounding, and cleanup. | Reuse existing execution/session components. Separate resource lifecycle from judging criteria while keeping attribution intact. |
| Evaluation | The public agent interface remains stable while internal execution expands to roughly 1,500 lines. | Explicit evaluation stages with a single final mutation decision and deliberate side effects. |
| Shared cycle state | Goal criteria are read into CycleContextData, altered during work, and written through multiple completion paths. Run-level decisions also have a checkpoint representation. | Document authoritative owners and snapshot lifetimes before regrouping fields or removing carriers. |

## Historical fixes that constrain the work

Read these diffs when implementing the corresponding slice.

| Commit | Requirement to preserve |
| --- | --- |
| `4c93e4b0` | Closure verifies the tree currently on disk. |
| `ef6df5c1` | Gate-refused criteria cannot remain banked through the early-exit path. |
| `10ed8cf7` | Criteria deliberately let go stay resolved through coverage review. |
| `fa83d2e5` | Continue restores the service's decision state. |
| `3da55e13` | Restored state comparisons handle a NaN score correctly. |
| `a258687f` | Operator rewind requested during a cycle executes at a cycle boundary. |
| `df2a907a` | Repeated equivalent probe failures have stable summaries for work-item deduplication. |
| `afa9f239` | Fluent value assertions count for the closure assertion gate. |
| `acef9e1f` | Kept reference answers reach later work, including relevant sibling goals. |

The current goal-service comments also document a resume failure where inherited requests suppressed evolution for 21 cycles. Preserve the distinction between continuing checkpoint state and regrounding active goals at the appropriate run boundary.

## Ordered implementation backlog

Each numbered slice should be independently reviewable and reversible. Complete its focused checks before proceeding. Proposed component boundaries must be checked against existing collaborators before introducing a class.

### 1. Record the behavioral baseline and ownership map

Trace the normal entry points through AutobuilderService, CycleExecutor, AdaptiveExecutionEngine, EvaluationAgent, and GoalPlannerService. Cover scheduled execution, manual evolve, continue, operator input, and queued rewind.

For each decision, record its callers, authoritative state, mutations, persistence writes, event emission, and failure behavior. Begin with criterion banking and goal completion, whose callers already span the lifecycle and evaluation layers.

Use the existing closure, resume, checkpoint, and rewind tests. Add characterization cases only where inspection reveals an unprotected behavior. Include string-keyed criteria metadata and representative older checkpoint/goal JSON.

**Done when** the first extraction has a complete caller and side-effect inventory, executable checks, and a recorded baseline revision. Expand this map as later slices become concrete.

**Rollback boundary** documentation and test additions only.

### 2. Extract criteria metadata interpretation

Start with GoalPlannerService.getCriteriaPassStatus and its serialization counterpart in saveCriteriaPassStatus. Preserve absent metadata, malformed keys, Boolean filtering, and integer-to-string key conversion.

Then inspect addMetCriteria, unbank, narrowBankDrop, and resume regrounding before deciding which policy belongs together. Keep the goal map and persistence writes under their existing owner during the first extraction. Avoid a second mutable criteria cache.

Known callers include CycleContextCoordinator, CycleExecutor, AutobuilderService, EvaluationAgent, and RunEndReportService. Keep existing service methods delegating while these callers migrate.

**Checks** criteria-related existing tests, GoalPlannerServiceResumeRegroundTest, and a focused compatibility case if legacy metadata shapes lack coverage.

**Done when** metadata conversion has one implementation, all callers retain their observed behavior, and the extraction gives the next slice a coherent boundary.

**Rollback boundary** delegates and conversion logic can return to GoalPlannerService without changing stored data.

### 3. Separate closure decisions from applying transitions

Trace autoCompleteRootIfSatisfied, verifyAndPromoteBeforePause, promoteSatisfiedLeaves, whyNotSatisfied, and both applyGoalProgress overloads. Enumerate refusal, repair, completion, pause, and already-resolved outcomes before changing the flow.

Reuse CriteriaVerificationService and existing result types where they express those outcomes. Extract one coherent decision path first. Keep tree mutation, graph writes, JSON snapshots, and events in their current ordered path until that path is independently covered.

Account for pendingFulfillmentResolution, which currently carries a rationale through nested calls and is cleared in finally. Any replacement must preserve the rationale through promotion and failure.

**Checks** GoalPlannerServiceClosureTest, GoalPlannerServiceClosureClaimTest, GoalPlannerServiceVerifyBeforePauseTest, GoalPlannerServiceOriginatingObservationTest, ObjectiveMetClosureTest, and ClosureRefutationRepairScenarioTest.

**Done when** the extracted decision can be understood from its explicit inputs and result, and its caller applies side effects once in the existing order.

**Rollback boundary** one closure path per change. Preserve persisted statuses and reason formats.

### 4. Make lifecycle coordination explicit

Start with requestOperatorRewind, performQueuedOperatorRewind, settleAfterRewind, and startNow/performStart. Reuse RewindService, StartModeService, and the existing context and persistence coordinators.

Retain a single owner of the ReentrantLock. Trace operator requests arriving during work, restore failures, startup readiness, pause, and final reporting. Then divide runAutobuilderCycleLocked into meaningful stages that expose the existing ordering.

Keep checkpoint ownership explicit. AutobuilderService currently captures runBrakes through Snapshot, including retry counters, pause flags, and terminal-report state. Moving these fields requires corresponding capture/restore compatibility.

**Checks** AutobuilderServiceQueuedRewindTest, AutobuilderServiceRewindWiringTest, AutobuilderServiceMidRunPreserveTest, RewindRoundTripPropertyTest, and lifecycle tests covering the touched stage.

**Done when** lifecycle operations have readable boundaries, lock ownership is singular, and restored runs make the same next decision in covered scenarios.

**Rollback boundary** preserve the runBrakes key and field representation while reorganizing behavior.

### 5. Separate runtime execution from criterion judgment

Inventory BootGate's callers before touching serveOrWhy, runToEnd, launchAndObserve, probeCommandCriteria, and executeSequence. Check reuse of ProbeSession and the existing adapters.

First isolate resource ownership, including process exit, output draining, timeout, temporary files, service containers, and session cookies. Next isolate CLI and HTTP exercise execution. Keep grounding and criterion attribution together wherever separating them would lose the explanation of a failure.

**Checks** BootGateTest, BootGateStartFailureTest, ProbeSessionTest, BehaviouralClosureVerifierTest, and ClosureClaimRefutalEndToEndTest. Confirm actual test coverage for cleanup on partial failure.

**Done when** execution paths share the appropriate lifecycle implementation and failures retain the same attribution and stable summary.

**Rollback boundary** preserve public observations and result types through delegation during extraction.

### 6. Clarify evaluation stages and decision ownership

Map EvaluationAgent.execute and its early returns, originating-gate checks, cumulative criteria updates, repeat refusals, heuristic negotiation, and persistence writes.

Reuse HeuristicNegotiator. Extract cohesive stages only after recording ordering and inputs. Keep gate refusal, heuristic acceptance, and root completion distinct where they answer different questions. Consolidate duplicate policy only after checking caller semantics.

**Checks** existing evaluation tests selected from the caller map, plus the closure scenarios affected by criteria banking. Cover early returns that persist or invalidate decision state.

**Done when** mutation acceptance has an identifiable final decision point and every early exit has explicit state and persistence effects.

**Rollback boundary** one stage per change, with existing agent entry points retained.

### 7. Reduce shared state and obsolete branches

Audit CycleContextData fields and run-level counters by producer, consumer, persistence, checkpoint bucket, and reset boundary. Include signals, requests, and participation windows.

Use the map to remove unused state or group state with its actual owner. Verify reflection and serialization users before deletion. Preserve reading older project data whenever a representation changes.

For suspected obsolete policy, trace the introducing fix, later replacement, configuration switches, and replay scenarios. Record why the remaining implementation covers the original failure.

**Checks** checkpoint and rewind coverage, existing serialization tests, and affected execution scenarios.

**Done when** each removed carrier or rule has a documented replacement or evidence of disuse, and persistence compatibility is verified.

**Rollback boundary** state migration is a separate change whenever stored representation must change.

## Checklist navigation

Follow the detailed slices in order. Check a task only after its stated work and verification are complete. All implementation tasks begin unchecked.

| Slice | Checklist | Main responsibility |
| --- | --- | --- |
| 1 | [Baseline and ownership](#slice-1-execution--establish-what-must-survive) | Establish behavior and call paths |
| 2 | [Criteria metadata](#slice-2-execution--criteria-metadata-and-bank-operations) | Interpret and update banked criteria |
| 3 | [Closure and transitions](#slice-3-execution--closure-decisions-and-goal-transitions) | Separate decisions and applied changes |
| 4 | [Run lifecycle](#slice-4-execution--lifecycle-control-requests-and-rewind) | Clarify startup, scheduling, and rewind |
| 5 | [Runtime verification](#slice-5-execution--runtime-verification-and-resources) | Assign resource and exercise ownership |
| 6 | [Evaluation](#slice-6-execution--evaluation-ordering-and-mutation-effects) | Preserve stage ordering and mutation effects |
| 7 | [Shared state](#slice-7-execution--state-ownership-and-removal) | Remove obsolete state and policy |

File tables identify the implementation focus, collaborating files to inspect, and existing test files. Edit collaborators when the reviewed call-path analysis requires it. Extraction destinations are chosen during the corresponding task after checking existing abstractions. File links point to this checkout.

## Detailed execution plan per slice

The steps below expand the backlog into reviewable implementation units. Class names introduced during extraction remain a design choice until existing collaborators have been checked. Method names below refer to the baseline revision.

The default dependency order is 1 → 2 → 3 → 4 → 5 → 6 → 7. Slice 5 can follow slice 1 independently when its resource-lifecycle inventory is complete. Slice 6 needs the criteria and verification contracts from slices 2, 3, and 5. Slice 7 uses the ownership established by all preceding work. Implementing this plan sequentially reduces overlapping edits to the same central classes.

### Slice 1 execution — establish what must survive

**Files for this slice**

| Role | Files |
| --- | --- |
| Implementation focus | [AutobuilderController.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/controller/AutobuilderController.java)<br>[HomeController.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/home/HomeController.java)<br>[AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java)<br>[CycleExecutor.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleExecutor.java)<br>[CycleContextCoordinator.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleContextCoordinator.java)<br>[AdaptiveExecutionEngine.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/engine/AdaptiveExecutionEngine.java)<br>[EvaluationAgent.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgent.java)<br>[GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java) |
| Callers and collaborators to inspect | [CycleContextData.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleContextData.java)<br>[GoalEntity.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalEntity.java)<br>[RunEndReportService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/persistence/RunEndReportService.java) |
| Existing tests to inspect and select | [AutobuilderServiceTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/service/AutobuilderServiceTest.java)<br>[CycleContextCoordinatorTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/lifecycle/CycleContextCoordinatorTest.java)<br>[CycleExecutorTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/lifecycle/CycleExecutorTest.java)<br>[EvaluationAgentTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgentTest.java)<br>[GoalPlannerServiceTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/goal/GoalPlannerServiceTest.java) |

**Entry evidence.** Use the four historical revisions above and record the actual working revision before implementation. Preserve any concurrent user changes when collecting a baseline.

**Files to trace.** Start with controller/AutobuilderController, home/HomeController, core/service/AutobuilderService, core/lifecycle/CycleExecutor, CycleContextCoordinator, core/engine/AdaptiveExecutionEngine, agent/evaluation/EvaluationAgent, and goal/GoalPlannerService under src/main/java/com/walpro/autobuilder.

**Implementation units**

- [ ] **1A — map entry points.** Follow manual and scheduled cycle requests to their shared implementation. Trace startup with and without a project, continue, operator input, pause, and rewind. Record the lock acquired, the point at which context is hydrated, and the method that changes the next active goal. Files involved — [AutobuilderController.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/controller/AutobuilderController.java), [HomeController.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/home/HomeController.java), [AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java), [CycleExecutor.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleExecutor.java), [AdaptiveExecutionEngine.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/engine/AdaptiveExecutionEngine.java).
- [ ] **1B — map decision ownership.** For each criteria-bank write and goal transition, record the caller, inputs, tree version, state mutation, graph/file persistence, and emitted events. Include early exits in CycleExecutor and EvaluationAgent. A caller that appears read-only may repair pointers or refresh graph state, so read its body. Files involved — [GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java), [CycleContextCoordinator.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleContextCoordinator.java), [CycleExecutor.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleExecutor.java), [EvaluationAgent.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgent.java).
- [ ] **1C — reconstruct relevant historical changes.** Compare the same path across May, August, October, and current code. Read the introducing fix and its regression test. Record whether the responsibility was added, moved, replaced, or reverted. Use the historical-fix table as the initial list. Files involved — [GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java), [AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java), [BootGate.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/build/BootGate.java), [EvaluationAgent.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgent.java).
- [ ] **1D — establish focused executable baselines.** Read the existing tests before selecting them. Match each invariant to a test method and mark gaps explicitly. Add a case only for a material gap such as old metadata decoding or repeated application after a failure. Use the slice’s test files and record the result in this plan.
- [ ] **1E — record the handoff.** Add the resulting ownership and coverage tables to this plan. Record the tested revision, command, result, and any unavailable environment dependency. Use the slice’s test files and record the result in this plan.

**Required inventory fields**

| Field | What to record |
| --- | --- |
| Decision | The observable question answered, such as whether a criterion is banked |
| Inputs | Goal identity, criterion position, current tree, context snapshot |
| Owner | Object that authoritatively changes the state |
| Readers and writers | Direct calls plus JSON, graph, reflection, and generated accessors |
| Side effects | Disk, graph, logs, events, requests, counters |
| Failure and retry | State left after interruption and effect of calling again |
| Evidence | Introducing commit and current protecting test |

**Acceptance.** Slice 2's full call path is mapped. Each behavior it will touch has a protecting test or a concrete missing-case task. Wider inventories can be completed immediately before their corresponding slice.

**Review and rollback.** Review this unit as investigation and characterization work. Tests must describe externally relevant behavior. Reverting this unit requires only removal of its new documentation or test changes.

**Slice 1 completion checklist**

- [ ] **1V1 — Verify coverage.** Match each behavior matrix entry to an existing test method and add only the identified missing regression cases.
- [ ] **1V2 — Run the selected checks.** Record the exact command, revision, result, and any unverified environment-dependent case.
- [ ] **1V3 — Review compatibility and effects.** Confirm the slice's persistence, event, retry, and rollback requirements through its normal entry points.
- [ ] **1V4 — Review the diff.** Confirm the ownership map is supported by callers and tests, and that the first extraction has a bounded scope.
- [ ] **1V5 — Record completion.** Update this plan with the changed files, evidence, remaining questions, and next ready slice.

### Slice 2 execution — criteria metadata and bank operations

**Files for this slice**

| Role | Files |
| --- | --- |
| Implementation focus | [GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java)<br>[GoalEntity.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalEntity.java) |
| Callers and collaborators to inspect | [CycleContextCoordinator.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleContextCoordinator.java)<br>[CycleExecutor.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleExecutor.java)<br>[AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java)<br>[EvaluationAgent.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgent.java)<br>[RunEndReportService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/persistence/RunEndReportService.java) |
| Existing tests to inspect and select | [ClaimRefutationAttributionTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/goal/ClaimRefutationAttributionTest.java)<br>[CycleContextCoordinatorTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/lifecycle/CycleContextCoordinatorTest.java)<br>[CycleExecutorGoalLoopTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/lifecycle/CycleExecutorGoalLoopTest.java)<br>[GoalPlannerServiceResumeRegroundTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/goal/GoalPlannerServiceResumeRegroundTest.java)<br>[GoalPlannerServiceTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/goal/GoalPlannerServiceTest.java) |

**Desired ownership.** GoalPlannerService continues to locate goals and coordinate persistence. A cohesive goal-package implementation interprets criteria metadata and performs the relevant map transformations. The extraction operates on supplied data and returns values to the existing owner.

**Implementation units**

- [ ] **2A — extract conversion.** Move the shared interpretation behind getCriteriaPassStatus and the string-key serialization used by saveCriteriaPassStatus. Preserve the fresh-map behavior of reads. Keep all existing public callers working through delegates. Files involved — [GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java), [GoalEntity.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalEntity.java).
- [ ] **2B — extract attributed invalidation.** Trace clearCriteriaBankAfterRefutation and narrowBankDrop. Preserve the distinction between a valid empty narrowed result and an unattributable refutation. Unknown criterion text triggers the existing full-bank removal. Files involved — [GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java).
- [ ] **2C — trace the other bank operations.** Review unbank, addMetCriteria, setAcceptanceCriteria, and regroundActiveSubgoalsForResume together. unbank writes false at matching positions, whereas attributed bank removal removes positions. Preserve each operation's existing meaning. Newly added criteria must use their positions in the stored, filtered list. Files involved — [GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java).
- [ ] **2D — reconnect persistence.** Keep saveGoalMetadata and persist at their existing call sites during extraction. Confirm the order and number of writes and events, including paths that update both criteria text and banked status. Files involved — [GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java).
- [ ] **2E — review consumers.** Inspect CycleContextCoordinator hydration, CycleExecutor completion and persistence, AutobuilderService verification, EvaluationAgent retries, and RunEndReportService reporting. Migrate callers only where the new boundary reduces their knowledge of metadata. Files involved — [CycleContextCoordinator.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleContextCoordinator.java), [CycleExecutor.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleExecutor.java), [AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java), [EvaluationAgent.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgent.java), [RunEndReportService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/persistence/RunEndReportService.java).

**Compatibility cases**

| Input or event | Behavior to verify |
| --- | --- |
| Missing goal, metadata, or criteria bank | Existing empty/no-op behavior for the corresponding operation |
| String or integer-shaped keys | Same numeric-key interpretation |
| Malformed key or non-Boolean value | Existing filtering |
| Refutation names known criteria | Other entries survive |
| Refutation cannot be attributed | Whole bank is removed |
| All known bank entries refuted | Valid empty narrowing remains distinguishable |
| Criteria filtered or reordered when stored | Added criteria use their actual one-based stored positions |
| Resume regrounding | Active goals and terminal goals retain their existing distinct treatment |

**Tests.** Start with GoalPlannerServiceTest, ClaimRefutationAttributionTest, GoalPlannerServiceResumeRegroundTest, CycleContextCoordinatorTest, and CycleExecutorGoalLoopTest. Verify which cases they cover before extending them.

**Acceptance.** Conversion is implemented once. Existing goal-service callers remain compatible. Bank removal, false entries, and absent metadata preserve their distinct behavior. The extraction owns useful criteria operations rather than merely forwarding every call.

**Review and rollback.** Review 2A before the policy-bearing 2B–2C changes. Stored metadata uses the existing representation, so reversal consists of restoring the implementation behind the delegates.

**Slice 2 completion checklist**

- [ ] **2V1 — Verify coverage.** Match each behavior matrix entry to an existing test method and add only the identified missing regression cases.
- [ ] **2V2 — Run the selected checks.** Record the exact command, revision, result, and any unverified environment-dependent case.
- [ ] **2V3 — Review compatibility and effects.** Confirm the slice's persistence, event, retry, and rollback requirements through its normal entry points.
- [ ] **2V4 — Review the diff.** Confirm the new ownership boundary, remove accidental duplication, and check that the documented rollback remains feasible.
- [ ] **2V5 — Record completion.** Update this plan with the changed files, evidence, remaining questions, and next ready slice.

### Slice 3 execution — closure decisions and goal transitions

**Files for this slice**

| Role | Files |
| --- | --- |
| Implementation focus | [GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java)<br>[GoalProgressApplicationResult.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalProgressApplicationResult.java) |
| Callers and collaborators to inspect | [CriteriaVerificationService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/CriteriaVerificationService.java)<br>[ObjectiveMetClosure.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/ObjectiveMetClosure.java)<br>[GoalTransition.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalTransition.java)<br>[GoalTransitionLog.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalTransitionLog.java)<br>[CycleExecutor.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleExecutor.java)<br>[AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java) |
| Existing tests to inspect and select | [ClaimRefutationAttributionTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/goal/ClaimRefutationAttributionTest.java)<br>[ClosureRefutationRepairScenarioTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/service/ClosureRefutationRepairScenarioTest.java)<br>[CompletionEdgeFaultInjectionTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/goal/CompletionEdgeFaultInjectionTest.java)<br>[GoalPlannerServiceClosureClaimTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/goal/GoalPlannerServiceClosureClaimTest.java)<br>[GoalPlannerServiceClosureTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/goal/GoalPlannerServiceClosureTest.java)<br>[GoalPlannerServiceOriginatingObservationTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/goal/GoalPlannerServiceOriginatingObservationTest.java)<br>[GoalPlannerServiceStatusEventTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/goal/GoalPlannerServiceStatusEventTest.java)<br>[GoalPlannerServiceTreeChangeEventTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/goal/GoalPlannerServiceTreeChangeEventTest.java)<br>[GoalPlannerServiceVerifyBeforePauseTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/goal/GoalPlannerServiceVerifyBeforePauseTest.java)<br>[GoalTransitionApiContractTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/goal/GoalTransitionApiContractTest.java)<br>[ObjectiveMetClosureTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/goal/ObjectiveMetClosureTest.java)<br>[ObservedLeafPromotionTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/goal/ObservedLeafPromotionTest.java) |

**Desired ownership.** Verification reports what it observed. Closure policy determines the permitted next action. The goal-transition path applies the action and maintains persistence and events. Existing result types should carry the information when sufficient.

GoalProgressApplicationResult currently reports goal identity, whether promotion occurred, old/new subgoal pointers, and root completion. It does not by itself represent every closure refusal or repair outcome. Inspect callers before extending a shared type or adding a narrow internal result.

**Implementation units**

- [ ] **3A — enumerate closure routes.** Trace autoCompleteRootIfSatisfied → attemptRootClosure, verifyAndPromoteBeforePause, promoteSatisfiedLeaves → whyNotSatisfied, checkSubgoalClosure, and both applyGoalProgress overloads. Record the actual ordering of gates, cache/fingerprint checks, blocker reopening, and writes. Files involved — [GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java), [CycleExecutor.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleExecutor.java), [AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java).
- [ ] **3B — extract the smallest leaf decision.** Begin with a cohesive leaf-check path after identifying any hidden side effects. Supply the goal, relevant criteria, and current observation explicitly. Retain the surrounding transition code until the decision's contract is covered. Files involved — [GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java), [CriteriaVerificationService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/CriteriaVerificationService.java).
- [ ] **3C — extract root decision stages.** Work through one stage at a time. Preserve the current tree read through outputNow and the lifetime of closureInputsFingerprint. Record which evidence can be reused and what invalidates it. Files involved — [GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java), [ObjectiveMetClosure.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/ObjectiveMetClosure.java).
- [ ] **3D — make action application explicit.** Keep status changes, current-goal pointers, bank invalidation, repair creation, persistence, and events in an ordered method. Reuse transitionGoal and existing graph/transition-log machinery. Trace exception behavior between these effects before changing their placement. Files involved — [GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java), [GoalTransition.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalTransition.java), [GoalTransitionLog.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalTransitionLog.java), [GoalProgressApplicationResult.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalProgressApplicationResult.java).
- [ ] **3E — remove temporary rationale coupling where feasible.** Pass the fulfillment rationale through the affected call chain when that makes ownership clearer. Cover the current finally cleanup of pendingFulfillmentResolution, including a failed or nested transition. Files involved — [GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java).
- [ ] **3F — verify repeat application.** Exercise repeated refusal against the same tree, refusal followed by repair, already-completed goals, and resumed state. Ensure a repeat creates the same intended amount of repair work and emits the expected transitions. Use the slice’s test files and record the result in this plan.

**Behavior matrix.** Include a criterion verified on current disk state, stale bank invalidation, a protected test that prevents closure, a deliberately let-go criterion, a given-up blocker whose test suite later improves, and a completed leaf whose parent remains open. Record the current expected outcome from tests before extraction.

**Tests.** Use the tests named in the backlog plus ObservedLeafPromotionTest, GoalTransitionApiContractTest, GoalPlannerServiceStatusEventTest, GoalPlannerServiceTreeChangeEventTest, CompletionEdgeFaultInjectionTest, and ClaimRefutationAttributionTest as relevant to each substep.

**Acceptance.** A reviewer can follow verification → decision → applied transition for each migrated route. Existing event order and durable outcomes are preserved. Any discovered behavioral defect is recorded separately with an explicit expected change.

**Review and rollback.** Land leaf and root changes independently. Avoid changing persistent status names or transition-log meaning within these extraction units. If a schema change proves necessary, specify its compatibility and reversal before proceeding.

**Slice 3 completion checklist**

- [ ] **3V1 — Verify coverage.** Match each behavior matrix entry to an existing test method and add only the identified missing regression cases.
- [ ] **3V2 — Run the selected checks.** Record the exact command, revision, result, and any unverified environment-dependent case.
- [ ] **3V3 — Review compatibility and effects.** Confirm the slice's persistence, event, retry, and rollback requirements through its normal entry points.
- [ ] **3V4 — Review the diff.** Confirm the new ownership boundary, remove accidental duplication, and check that the documented rollback remains feasible.
- [ ] **3V5 — Record completion.** Update this plan with the changed files, evidence, remaining questions, and next ready slice.

### Slice 4 execution — lifecycle, control requests, and rewind

**Files for this slice**

| Role | Files |
| --- | --- |
| Implementation focus | [AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java) |
| Callers and collaborators to inspect | [AutobuilderController.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/controller/AutobuilderController.java)<br>[HomeController.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/home/HomeController.java)<br>[StartModeService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/init/StartModeService.java)<br>[RewindService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/checkpoint/RewindService.java)<br>[CheckpointService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/checkpoint/CheckpointService.java)<br>[CycleContextCoordinator.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleContextCoordinator.java)<br>[CycleExecutor.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleExecutor.java)<br>[CheckpointableState.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/checkpoint/CheckpointableState.java) |
| Existing tests to inspect and select | [AutobuilderServiceMidRunPreserveTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/service/AutobuilderServiceMidRunPreserveTest.java)<br>[AutobuilderServiceQueuedRewindTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/service/AutobuilderServiceQueuedRewindTest.java)<br>[AutobuilderServiceRewindWiringTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/service/AutobuilderServiceRewindWiringTest.java)<br>[AutobuilderServiceTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/service/AutobuilderServiceTest.java)<br>[DecisionBearingHolderRoundTripTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/checkpoint/DecisionBearingHolderRoundTripTest.java)<br>[RewindRoundTripPropertyTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/checkpoint/RewindRoundTripPropertyTest.java)<br>[RewindServiceTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/checkpoint/RewindServiceTest.java) |

**Desired ownership.** AutobuilderService remains the run coordinator and lock owner during extraction. Existing services perform their established work. Each lifecycle operation has an explicit order and a clearly defined handoff back to the scheduler.

**Implementation units**

- [ ] **4A — document the state transitions.** Build a table for held, running, paused, terminal, continuing, and queued-rewind situations using actual flags and return values. Include LLM-breaker cooldown because the current code deliberately avoids reprocessing stale terminal state after that pause. Files involved — [AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java), [AutobuilderController.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/controller/AutobuilderController.java), [HomeController.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/home/HomeController.java).
- [ ] **4B — isolate rewind coordination.** Follow requestOperatorRewind, rewindAsOperator, performQueuedOperatorRewind, and settleAfterRewind. Preserve immediate execution when the lock is available, queueing while busy, and replacement of an earlier queued request by the newest request. Files involved — [AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java), [RewindService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/checkpoint/RewindService.java).
- [ ] **4C — preserve settlement.** After restoring holders, choose the restored context when present and the documented current-context fallback otherwise. Clear obsolete exhaustion/terminal markers. Retain an operator's current user pause and its already-emitted episode flags. Files involved — [AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java), [CycleContextData.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleContextData.java).
- [ ] **4D — isolate start and continue.** Trace init, runFirstCycle, startNow, performStart, continueProject, and restoreOwnDecisions. Preserve application-readiness timing, single first-cycle execution, and the order in which context and decision state become usable. Files involved — [AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java), [StartModeService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/init/StartModeService.java), [CycleContextCoordinator.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleContextCoordinator.java).
- [ ] **4E — name cycle stages.** Extract coherent blocks from runAutobuilderCycleLocked while keeping statement order and lock lifetime. Make terminal handling, context preparation, cycle execution, and post-cycle handling visible. Preserve short-circuit returns after a rewind. Files involved — [AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java), [CycleExecutor.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleExecutor.java).
- [ ] **4F — check operator inputs.** Trace bringInput, takeStatedInput, cancelInput, keepInput, and conflict decisions. Verify whether each operation runs at a cycle boundary or under exclusive control and how it resets progress state. Files involved — [AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java), [AutobuilderController.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/controller/AutobuilderController.java).

**Concurrency and restore cases.** Verify idle rewind, busy rewind, two queued requests, refused queued rewind, user pause plus rewind, restore with absent carried context, and the next scheduler tick after each successful restore. Preserve existing exception behavior unless an explicit fix is approved within the implementation scope.

**Checkpoint contract.** Keep runBrakes and Snapshot fields compatible. Compare NaN and collection values correctly. A moved mutable field must retain a defined StateHolder bucket and capture/restore treatment.

**Tests.** Use AutobuilderServiceQueuedRewindTest, AutobuilderServiceRewindWiringTest, AutobuilderServiceMidRunPreserveTest, AutobuilderServiceTest, RewindServiceTest, RewindRoundTripPropertyTest, and DecisionBearingHolderRoundTripTest. Add timing-controlled tests only for uncovered races, using latches or barriers rather than fragile sleeps.

**Acceptance.** The next action after start, pause, continue, and rewind is covered. One object owns lock acquisition. Terminal events and preserved artifacts are produced with the existing frequency.

**Review and rollback.** Separate rewind, startup, and cycle-stage edits. Preserve checkpoint representation throughout these units. Any runtime field relocation must be reviewed with its restore path.

**Slice 4 completion checklist**

- [ ] **4V1 — Verify coverage.** Match each behavior matrix entry to an existing test method and add only the identified missing regression cases.
- [ ] **4V2 — Run the selected checks.** Record the exact command, revision, result, and any unverified environment-dependent case.
- [ ] **4V3 — Review compatibility and effects.** Confirm the slice's persistence, event, retry, and rollback requirements through its normal entry points.
- [ ] **4V4 — Review the diff.** Confirm the new ownership boundary, remove accidental duplication, and check that the documented rollback remains feasible.
- [ ] **4V5 — Record completion.** Update this plan with the changed files, evidence, remaining questions, and next ready slice.

### Slice 5 execution — runtime verification and resources

**Files for this slice**

| Role | Files |
| --- | --- |
| Implementation focus | [BootGate.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/build/BootGate.java)<br>[ProbeSession.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/build/ProbeSession.java) |
| Callers and collaborators to inspect | [ServiceContainers.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/services/ServiceContainers.java)<br>[BehaviouralClosureVerifier.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/build/BehaviouralClosureVerifier.java)<br>[EntryAdapter.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/coldread/entry/EntryAdapter.java)<br>[ProgramAdapter.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/coldread/entry/ProgramAdapter.java)<br>[LibraryStyleAdapter.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/coldread/entry/LibraryStyleAdapter.java)<br>[CriteriaVerificationService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/CriteriaVerificationService.java)<br>[EvaluationAgent.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgent.java) |
| Existing tests to inspect and select | [BehaviouralClosureVerifierTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/build/BehaviouralClosureVerifierTest.java)<br>[BootGateStartFailureTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/build/BootGateStartFailureTest.java)<br>[BootGateTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/build/BootGateTest.java)<br>[ClosureClaimRefutalEndToEndTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/build/ClosureClaimRefutalEndToEndTest.java)<br>[ProbeSessionTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/build/ProbeSessionTest.java) |

**Desired ownership.** Resource handling owns process and service lifetimes. Exercise execution owns CLI invocation or an HTTP sequence. Criterion interpretation owns attribution and verification outcomes. Existing ProbeSession handles client-carried HTTP state and should be reused where its contract applies.

**Implementation units**

- [ ] **5A — inventory public consumers.** Trace BootGate from closure, evaluation's originating-gate checks, and reverse-engineering adapters. Record each caller's artifact selection, timeout, service setup, observation type, and cleanup responsibility. Files involved — [BootGate.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/build/BootGate.java), [CriteriaVerificationService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/CriteriaVerificationService.java), [EvaluationAgent.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgent.java), [EntryAdapter.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/coldread/entry/EntryAdapter.java), [ProgramAdapter.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/coldread/entry/ProgramAdapter.java), [LibraryStyleAdapter.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/coldread/entry/LibraryStyleAdapter.java).
- [ ] **5B — map acquisition and release.** For serveOrWhy, runToEnd, and launchAndObserve, list every allocated process, stream drainer, temporary directory, and ServiceContainers resource. Record which layer closes it after success, startup failure, exception, timeout, and interruption. Files involved — [BootGate.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/build/BootGate.java), [ServiceContainers.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/services/ServiceContainers.java).
- [ ] **5C — consolidate a lifecycle path.** Select the duplicated lifecycle block with matching semantics and extract it with explicit ownership. Preserve timeout accounting and bounded output capture. Keep process-result interpretation in its current caller initially. Files involved — [BootGate.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/build/BootGate.java), [ServiceContainers.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/services/ServiceContainers.java).
- [ ] **5D — isolate HTTP sequence execution.** Preserve one client session per intended sequence, cookie additions/removals, hidden form fields, form-page refresh, redirects as observations, and captured values used by later requests. Maintain declared route spelling. Files involved — [BootGate.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/build/BootGate.java), [ProbeSession.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/build/ProbeSession.java).
- [ ] **5E — isolate command execution.** Keep command argument grounding, fixture files, working directories, exit status, and output available for attribution. Preserve the distinction between a failed observation and a criterion contradicted by an observed result. Files involved — [BootGate.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/build/BootGate.java).
- [ ] **5F — reconnect interpretation.** Retain BootObservation, ClaimRefutation, and existing public results where compatible. Check that stable failure summarization still allows equivalent failures to deduplicate. Files involved — [BootGate.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/build/BootGate.java), [BehaviouralClosureVerifier.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/build/BehaviouralClosureVerifier.java), [CriteriaVerificationService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/CriteriaVerificationService.java).

**Failure matrix.** Cover services started but application startup failed, readiness timeout, command timeout, interrupted execution, response missing a captured value, invalid exercise input, and successful cleanup after a complete sequence. Inspect existing tests first to determine which paths need additional coverage.

**Tests.** Use BootGateTest, BootGateStartFailureTest, ProbeSessionTest, BehaviouralClosureVerifierTest, and ClosureClaimRefutalEndToEndTest. A process-lifecycle extraction may need a small real local process test if mocks cannot prove cleanup.

**Acceptance.** Every acquired resource has an owner and a verified release path. Sequence state and criterion attribution remain correct. Existing public entry points exercise the new implementation.

**Review and rollback.** Separate lifecycle, HTTP, and CLI extractions. Keep the public BootGate façade until callers have a clear reason to migrate. Reversal restores delegation without altering stored exercises.

**Slice 5 completion checklist**

- [ ] **5V1 — Verify coverage.** Match each behavior matrix entry to an existing test method and add only the identified missing regression cases.
- [ ] **5V2 — Run the selected checks.** Record the exact command, revision, result, and any unverified environment-dependent case.
- [ ] **5V3 — Review compatibility and effects.** Confirm the slice's persistence, event, retry, and rollback requirements through its normal entry points.
- [ ] **5V4 — Review the diff.** Confirm the new ownership boundary, remove accidental duplication, and check that the documented rollback remains feasible.
- [ ] **5V5 — Record completion.** Update this plan with the changed files, evidence, remaining questions, and next ready slice.

### Slice 6 execution — evaluation ordering and mutation effects

**Files for this slice**

| Role | Files |
| --- | --- |
| Implementation focus | [EvaluationAgent.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgent.java) |
| Callers and collaborators to inspect | [HeuristicNegotiator.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/analysis/heuristic/HeuristicNegotiator.java)<br>[EvaluationInterpreter.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/EvaluationInterpreter.java)<br>[CompileProbeService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/build/CompileProbeService.java)<br>[MergeRollbackService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/merge/MergeRollbackService.java)<br>[GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java)<br>[CycleExecutor.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleExecutor.java) |
| Existing tests to inspect and select | [EvaluationAgentBlockerExtractionOutcomeTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgentBlockerExtractionOutcomeTest.java)<br>[EvaluationAgentGateCheckTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgentGateCheckTest.java)<br>[EvaluationAgentInjectorPreservationTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgentInjectorPreservationTest.java)<br>[EvaluationAgentRejectionRelayTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgentRejectionRelayTest.java)<br>[EvaluationAgentRepeatRefusalTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgentRepeatRefusalTest.java)<br>[EvaluationAgentTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgentTest.java)<br>[EvaluationAgentTestProgressTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgentTestProgressTest.java) |

**Desired ownership.** EvaluationAgent coordinates explicit stages. HeuristicNegotiator retains heuristic policy. The final interpreted outcome controls rollback, mutation metadata, and evaluation recording in their established order.

**Implementation units**

- [ ] **6A — enumerate every exit.** Build a table with exit condition, resulting context, model/probe calls, disk state, criteria changes, requests, metadata writes, and graph events. Include null diff, empty diff, repeat refusal, compile failure, and normal evaluation. Files involved — [EvaluationAgent.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgent.java).
- [ ] **6B — extract evaluation eligibility and reuse decisions.** Preserve the empty-diff fingerprint settlement, unchanged-fingerprint reuse, and re-evaluation of incomplete criteria. Preserve the BUILD_FIX exception to repeat-cap replanning. Files involved — [EvaluationAgent.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgent.java), [GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java).
- [ ] **6C — isolate probe preparation.** Keep repeat refusal ahead of the compile probe. Preserve filesOnDisk and rollback-point handling. A compile error with files retained follows a different path from a compile error already rolled back. Fold injector changes into the context before downstream consumers read the diff. Files involved — [EvaluationAgent.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgent.java), [CompileProbeService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/build/CompileProbeService.java).
- [ ] **6D — isolate outcome processing.** Preserve the sequence of LLM evaluation, heuristic negotiation, originating-gate checks, test-progress decisions, and interpretation. Read existing tests to establish precedence. For example, critical heuristic findings can prevent the originating gate from being consulted, and an accepted assessment remains accepted when that gate still fails. Files involved — [EvaluationAgent.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgent.java), [HeuristicNegotiator.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/analysis/heuristic/HeuristicNegotiator.java), [EvaluationInterpreter.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/EvaluationInterpreter.java).
- [ ] **6E — isolate effects after interpretation.** Preserve critical-streak updates, rejection relay, fingerprint updates, rollback, mutation metadata, and graph/action recording. Confirm which operations use the negotiated outcome and which use the interpreted result. Files involved — [EvaluationAgent.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgent.java), [MergeRollbackService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/merge/MergeRollbackService.java).
- [ ] **6F — review persistence timing.** Mutation state is currently persisted within evaluation so same-cycle variant selection can see accepted work. Preserve that timing. Confirm every earlier return retains its intended reporting and persistence behavior. Files involved — [EvaluationAgent.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgent.java), [CycleExecutor.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleExecutor.java).

**Rollback cases.** A rejected staged candidate restores the relevant files. Infrastructure added alongside the candidate by the injector may survive under the existing rule. Injector edits to a candidate's own staged file belong to the rejected candidate. An old rollback point must never undo a previously accepted mutation.

**Tests.** Use EvaluationAgentTest, EvaluationAgentRepeatRefusalTest, EvaluationAgentGateCheckTest, EvaluationAgentTestProgressTest, EvaluationAgentInjectorPreservationTest, EvaluationAgentRejectionRelayTest, and EvaluationAgentBlockerExtractionOutcomeTest. Select further cases from the exit table.

**Acceptance.** Each early return has a documented effect set. Disk and recorded mutation state agree in the tested paths. Stage extraction preserves model-call and probe-call conditions, including the skips that prevent waste or livelock.

**Review and rollback.** Review eligibility, probe handling, outcome processing, and final effects as separate units. Preserve existing context fields and persisted evaluation formats during those extractions.

**Slice 6 completion checklist**

- [ ] **6V1 — Verify coverage.** Match each behavior matrix entry to an existing test method and add only the identified missing regression cases.
- [ ] **6V2 — Run the selected checks.** Record the exact command, revision, result, and any unverified environment-dependent case.
- [ ] **6V3 — Review compatibility and effects.** Confirm the slice's persistence, event, retry, and rollback requirements through its normal entry points.
- [ ] **6V4 — Review the diff.** Confirm the new ownership boundary, remove accidental duplication, and check that the documented rollback remains feasible.
- [ ] **6V5 — Record completion.** Update this plan with the changed files, evidence, remaining questions, and next ready slice.

### Slice 7 execution — state ownership and removal

**Files for this slice**

| Role | Files |
| --- | --- |
| Implementation focus | [CycleContextData.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleContextData.java)<br>[AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java)<br>[GoalEntity.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalEntity.java) |
| Callers and collaborators to inspect | [CheckpointableState.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/checkpoint/CheckpointableState.java)<br>[StateHolder.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/checkpoint/StateHolder.java)<br>[CheckpointService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/checkpoint/CheckpointService.java)<br>[RewindService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/checkpoint/RewindService.java)<br>[GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java)<br>[CycleContextCoordinator.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleContextCoordinator.java)<br>[CycleExecutor.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleExecutor.java) |
| Existing tests to inspect and select | [CycleContextDataRoundTripTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/lifecycle/CycleContextDataRoundTripTest.java)<br>[CycleContextDataTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/lifecycle/CycleContextDataTest.java)<br>[CycleExecutorSignalLifetimeWiringTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/lifecycle/CycleExecutorSignalLifetimeWiringTest.java)<br>[DecisionBearingHolderRoundTripTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/checkpoint/DecisionBearingHolderRoundTripTest.java)<br>[RewindRoundTripPropertyTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/checkpoint/RewindRoundTripPropertyTest.java)<br>[StateHolderRegistrationContractTest.java](/home/anugeshtu/IdeaProjects/autobuilder/src/test/java/com/walpro/autobuilder/core/checkpoint/StateHolderRegistrationContractTest.java) |

**Desired ownership.** Each retained field has an authoritative writer, known readers, a lifetime, and a documented restore policy. Any new grouping follows those ownership boundaries.

**Implementation units**

- [ ] **7A — complete the state inventory.** Start with CycleContextData, AutobuilderService.Snapshot, goal metadata, and mutable singleton fields touched by earlier slices. Search constructors, builders, Lombok-generated access patterns, Jackson mappings, graph keys, and fixtures. Files involved — [CycleContextData.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleContextData.java), [AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java), [GoalEntity.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalEntity.java), [StateHolder.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/checkpoint/StateHolder.java).
- [ ] **7B — distinguish carriers and authority.** Identify persisted facts, cycle snapshots, derived values, caches, configuration, and transient handles. Record when each is valid and what invalidates it. Files involved — [CycleContextData.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleContextData.java), [CycleContextCoordinator.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleContextCoordinator.java), [CheckpointService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/checkpoint/CheckpointService.java).
- [ ] **7C — remove proven dead state.** Require a complete producer/consumer search and inspection of serialization/reflection users. Remove the field, writes, reads, configuration, and obsolete tests as one bounded change where applicable. Files involved — [CycleContextData.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleContextData.java), [AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java), [GoalEntity.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalEntity.java).
- [ ] **7D — group coherent surviving state.** Move a group only when it gives an existing owner a clearer responsibility. Keep builder and JSON compatibility explicit. If the old representation must change, add a dedicated migration unit with old-data fixtures. Files involved — [CycleContextData.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleContextData.java), [AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java), [CheckpointableState.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/checkpoint/CheckpointableState.java).
- [ ] **7E — retire obsolete policy.** For each branch, read the introducing failure, replacement mechanism, switches, and replay tests. State how the surviving behavior handles that failure. Preserve active cases and remove only the replaced branch. Files involved — [GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java), [AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java), [EvaluationAgent.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgent.java).
- [ ] **7F — verify restoration and completion.** Check checkpoint registration, round trips, and restart behavior through normal entry points. Recount decision sites and state owners and document remaining complexity that has an identified purpose. Files involved — [CheckpointService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/checkpoint/CheckpointService.java), [RewindService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/checkpoint/RewindService.java), [CycleExecutor.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/lifecycle/CycleExecutor.java).

**Tests.** Use StateHolderRegistrationContractTest, DecisionBearingHolderRoundTripTest, CycleContextDataRoundTripTest, CycleContextDataTest, CycleExecutorSignalLifetimeWiringTest, and RewindRoundTripPropertyTest, plus scenarios linked to each removed rule.

**Acceptance.** Every removal has evidence. Old supported project data still loads. Moved decision state has a valid checkpoint bucket. Completion is measured by simpler ownership and fewer overlapping decisions.

**Review and rollback.** Delete one coherent mechanism per unit. Keep serialization migrations independently reviewable. A migration must state whether the previous version can read newly written data and, if it cannot, how a preserved snapshot restores compatibility.

**Slice 7 completion checklist**

- [ ] **7V1 — Verify coverage.** Match each behavior matrix entry to an existing test method and add only the identified missing regression cases.
- [ ] **7V2 — Run the selected checks.** Record the exact command, revision, result, and any unverified environment-dependent case.
- [ ] **7V3 — Review compatibility and effects.** Confirm the slice's persistence, event, retry, and rollback requirements through its normal entry points.
- [ ] **7V4 — Review the diff.** Confirm the new ownership boundary, remove accidental duplication, and check that the documented rollback remains feasible.
- [ ] **7V5 — Record completion.** Update this plan with the changed files, evidence, remaining questions, and next ready slice.

## Working checklist for each implementation unit

Before editing, record the revision, specific methods, affected callers, relevant historical fix, and observable behavior to preserve. Select existing tests and identify any precise coverage gap.

After editing, record the changed responsibility, focused test results, compatibility checks, and remaining uncertainty. Review the diff for unrelated changes and verify normal entry points use the extracted implementation.

Use commands such as the following, choosing the classes named by the unit.

```bash
mvn test -Dtest=GoalPlannerServiceTest,ClaimRefutationAttributionTest,GoalPlannerServiceResumeRegroundTest
mvn test -Dtest=AutobuilderServiceQueuedRewindTest,AutobuilderServiceRewindWiringTest,RewindRoundTripPropertyTest
mvn test -Dtest=BootGateTest,BootGateStartFailureTest,ProbeSessionTest
mvn test -Dtest=EvaluationAgentRepeatRefusalTest,EvaluationAgentGateCheckTest,EvaluationAgentInjectorPreservationTest
```

These commands are proposed implementation checks. Their availability and environment requirements must be checked against the tests selected for the actual change. The documentation expansion itself does not establish a new passing baseline.


## Verification and completion

Run focused tests during each slice using Maven's test-class selection. Run the full suite at meaningful integration boundaries and before calling the refactoring complete. Count reports produced by that invocation because the current reports directory contains stale files.

Use existing local replay tests before field runs. Field checks should exercise the changed boundary, such as closure after a refused criterion, continued execution after restoring decisions, or capability reuse with a kept reference. Fresh scratch targets must have distinct run identities. Record the code revision, configuration, terminal outcome, and correctness checks. Model variation means exact cycle counts are observations rather than deterministic assertions.

Track progress through responsibilities removed from coordinating classes, duplicated decision sites eliminated, state ownership clarified, and preserved behavior. File and line counts are supporting measurements.

T4-EU introduces new recovery behavior and requires its own acceptance criteria. Its implementation may use the resulting boundaries, and should be reviewed as an explicit behavior change.


## Final integration todo list

- [ ] Finish all seven slice completion checklists and resolve their recorded verification gaps.
- [ ] Run the full test suite on the final implementation revision and count reports written by that invocation.
- [ ] Exercise the affected normal entry points and selected field cases from the verification section, recording configuration and correctness outcomes.
- [ ] Check supported older goal and checkpoint data through load, continue, and rewind.
- [ ] Review responsibilities, decision sites, and state ownership against the initial baseline.
- [ ] Update the functional and technical documentation where the final implementation requires it.
- [ ] Record the final changed-file list, test evidence, remaining limitations, and any separately scoped behavior changes.

## First implementation unit

Complete slice 1 for the criteria metadata path, then implement the bounded extraction in slice 2. This exercises the refactoring method on legacy-data handling with known callers before entering the more coupled closure and lifecycle paths.

The plan is ready for staged implementation. Production code has not been refactored as part of this document, and tests have not been rerun for this documentation-only change.
