# Step-by-step refactoring guide

Prepared against revision `1a034239` on 12 November 2026. Use this guide alongside the [historical plan and file inventory](/home/anugeshtu/IdeaProjects/autobuilder/docs/refactoring-plan.md).

This guide describes proposed code changes. The refactors and their tests have yet to be implemented. The suggested new classes below are concrete starting designs based on inspected code. Recheck their boundaries when implementing against a later revision.

## How to execute the guide

Work through one numbered operation at a time. Keep each extraction buildable before starting the next one. The checkboxes in the companion plan track completion.

For an extraction, use this sequence.

1. Read the complete source method and its direct callers.
2. Identify the instance fields it reads and writes, including state hidden behind collaborators.
3. Select the existing tests that exercise the public path.
4. Add a regression case only for a specific uncovered behavior the move could break.
5. Move the implementation, initially retaining the old entry point as a delegate.
6. Compile and run the focused tests.
7. Inspect persistence, events, exceptions, and caller ordering in the diff.
8. Remove the old body and any imports made obsolete by the move.
9. Record the result in the plan before advancing.

Retain public delegates where they provide a useful service boundary. Remove a delegate when all callers can use a clearer owner and compatibility permits it. Choose class extraction when it establishes a cohesive responsibility. Use a private method when the behavior belongs to the existing owner.

## Slice 1 — prepare a baseline that supports the first move

### Step 1. Locate the entry points

Open the source files linked in slice 1 of the plan. Begin with AutobuilderController and HomeController, then follow calls into AutobuilderService.

Write the call chains for manual evolution, scheduled evolution, start/continue, operator input, and rewind into the plan. Record where each chain enters exclusive execution and where it returns a result to its caller.

**Check before advancing.** The map accounts for both scheduled and manual execution, including the path that starts a project from the home context.

### Step 2. Trace one complete criteria lifecycle

Start at CycleContextCoordinator's call to getCriteriaPassStatus. Follow the returned map into CycleContextData, through evaluation, and to CycleExecutor's saveCriteriaPassStatus call.

Then trace the alternative writes in AutobuilderService and GoalPlannerService. Include the early completion path when all criteria appear banked.

Record these facts for each write.

- Which goal ID is used.
- Which tree or cycle the evidence describes.
- Whether the operation adds, removes, or sets an entry to false.
- Whether it saves only the graph, writes a JSON snapshot, or emits a tree event.
- Whether it can run again in the same cycle or after restart.

**Check before advancing.** Each mutation of the criteria bank has an identified owner and trigger.

### Step 3. Capture the actual persistence contract

Read saveGoalMetadata. At the inspected revision it saves through GoalGraphService and publishes a tree event. The enclosing path may separately call persist.

Write that distinction into the ownership table. Read graph and JSON persistence helpers before changing either. Preserve existing write ordering during the first extraction.

**Check before advancing.** The baseline records the graph save, event, and snapshot behavior for every path selected for slice 2.

### Step 4. Select and run the first regression set

Inspect GoalPlannerServiceTest, ClaimRefutationAttributionTest, GoalPlannerServiceResumeRegroundTest, and CycleContextCoordinatorTest. Locate assertions for criteria reading, invalidation, and hydration.

Run the relevant existing tests. Record the revision, command, counts from that invocation, and failures. Investigate an existing failure before using it as a refactoring baseline.

**Check before advancing.** A passing baseline exists for the first move, or a pre-existing failure has a specific explanation and a separate resolution.

### Step 5. Add only missing compatibility cases

Check for coverage of absent metadata, numeric-shaped string keys, non-Boolean values, a fully invalidated bank, and an unattributed refutation. Add missing cases to the relevant existing test class or a focused criteria-helper test as that helper is introduced.

Construct representative legacy metadata using the actual stored shape. Record the source revision for historical JSON fixtures.

**Slice result.** The criteria extraction can begin with an explicit behavior contract and runnable checks.

## Slice 2 — move criteria-bank interpretation out of GoalPlannerService

Primary source is [GoalPlannerService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/GoalPlannerService.java). Inspect [CriteriaBinding.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/goal/CriteriaBinding.java) as an existing example of a stateless reader.

### Step 1. Introduce a cohesive criteria-bank helper

A proposed destination is `src/main/java/com/walpro/autobuilder/goal/CriteriaBank.java`. Keep it stateless. Give it responsibility for the criteriaPassStatus representation and its transformations.

Start with operations shaped like the following. These signatures are a design sketch.

```java
static Map<Integer, Boolean> read(GoalEntity goal)
static Map<String, Boolean> encode(Map<Integer, Boolean> status)
```

Use the existing parsing and serialization loops. Preserve the fresh mutable map returned by read and the LinkedHashMap used by encoding.

**Check.** The helper has no graph, filesystem, event-publisher, or current-cycle dependency.

### Step 2. Replace the reader body with delegation

Change getCriteriaPassStatus to return the helper's read result. Keep its callers intact.

Run the criteria-read tests. Check that modifying the returned map does not mutate the goal's raw metadata until a save occurs.

**Check.** There is one implementation of key parsing and Boolean filtering.

### Step 3. Delegate encoding while retaining persistence

In saveCriteriaPassStatus, retain goal lookup, the absent-goal early return, metadata assignment, and saveGoalMetadata. Replace only the encoding loop with the helper call.

Keep the same behavior for unsupported inputs such as a null status map unless a separately identified fix changes that contract.

**Check.** One save still produces the existing graph save and tree event. Encoding alone has no observable side effects.

### Step 4. Move attributed bank transformation

Move the transformation in narrowBankDrop into CriteriaBank. Pass the supplied goal and refuted criterion texts explicitly.

Initially preserve its existing result convention. A null result means narrowing is unavailable. An empty map means narrowing succeeded and removed every banked entry. Document this distinction beside the operation.

Keep clearCriteriaBankAfterRefutation responsible for choosing whether to replace the map or remove the metadata key, and for logging and saving.

**Check.** Unknown criterion text still triggers full invalidation. Attributed refutation retains unrelated entries.

### Step 5. Give false-setting its own explicit operation

Extract the map transformation used by unbank. The operation should return a transformed bank while the service saves it.

Preserve one-based criterion positions. This path sets matching positions to false. Keep that behavior distinct from removing entries after an attributed refutation.

**Check.** The early completion path sees the invalidated criterion as unfulfilled.

### Step 6. Review additions after criteria filtering

Read addMetCriteria and setAcceptanceCriteria together. Keep criterion insertion and filtering under GoalPlannerService.

After the stored list is available, use its actual positions to bank the added criteria. Move only a reusable map transformation if it reduces duplication. Keep the existing persistence order visible in the service.

**Check.** A line filtered out by setAcceptanceCriteria receives no bank entry, and surviving lines receive their stored positions.

### Step 7. Recheck resume behavior

Run GoalPlannerServiceResumeRegroundTest. Inspect the ACTIVE-goal reset and terminal-goal handling. Confirm the extraction has not expanded which metadata is reset.

**Check.** Criteria banks, requests, and fail streaks retain their existing lifetime rules.

### Step 8. Finish the boundary

Search for direct parsing of criteriaPassStatus across production code. Redirect duplicate interpretation to the helper if semantics match. Keep service calls where the caller needs goal lookup or persistence.

Run the selected criteria, hydration, and completion tests. Review the diff for changed metadata keys, additional writes, or extra events.

**Slice result.** GoalPlannerService coordinates goals and persistence while CriteriaBank owns representation and transformations.

## Slice 3 — extract closure policy in small, testable steps

Primary source is GoalPlannerService. Reuse existing verification and goal-transition components listed in the plan.

### Step 1. Start with checkSubgoalClosure

This method has a smaller boundary than attemptRootClosure. Read every branch and make a test table for SUBSUMED, ACTIVE, COMPLETED, and GIVEN_UP goals.

Include missing criteria, all criteria banked, unfulfilled specification-bound criteria, and advisory authored criteria.

**Check.** The expected result includes the current reason text as well as pass/block status where that text is consumed.

### Step 2. Introduce a narrow closure-policy class

A proposed destination is `src/main/java/com/walpro/autobuilder/goal/GoalClosurePolicy.java`.

Move checkSubgoalClosure and its ClosureCheck result into this class after confirming its helper dependencies. Pass the goal and bank explicitly. Use CriteriaBinding for authored status only after verifying equivalence with isAuthoredCriterion.

Leave the GoalPlannerService method as a delegate first.

**Check.** The policy reads supplied values and produces a result. It does not promote goals, persist them, or create repair work.

### Step 3. Verify through the original public closure route

Run GoalPlannerServiceClosureTest and ObjectiveMetClosureTest. Add direct policy cases only where they protect a meaningful uncovered branch.

Compare the root status, remaining active goal, and emitted transitions through the service entry point.

**Check.** The extraction works through normal closure calls.

### Step 4. Extract the observed-leaf decision

Inspect whyNotSatisfied, sourceFileWhoseExistenceIsDemanded, outputHoldsFile, and endpointWhoseExistenceIsDemanded. Trace any shared parser callers before moving helpers.

A separate cohesive operation can accept criteria, specification text, BootObservation, and the supplied output map. Give it the existing endpoint and exercisability queries as dependencies when required.

Keep promoteSatisfiedLeaves responsible for selecting a snapshot of eligible leaves and applying promotions. A promotion changes pointers and ancestors, so preserve the snapshot-before-iteration behavior.

**Check.** Structural checks still work with an abstained observation. File existence alone still fails the current rule requiring an observed claim.

### Step 5. Name the stages inside root closure

Read attemptRootClosure from start to finish. Mark contiguous sections by the question they answer, their early exits, and their writes.

Extract one private method at a time while preserving statement order. Use existing result types where adequate. Do not force the entire method into a pure-policy class while it still creates work and changes goals.

**Check.** Each extracted stage has explicit inputs and a result that tells the caller whether processing continues.

### Step 6. Make the current-tree dependency visible

Retain the current-disk read used by root closure and the existing fingerprint invalidation. Trace outputNow and closureInputsFingerprint before passing their results into stages.

Keep the observed-leaf sweep's existing context input intact during its own extraction. Changing which tree a path observes is a behavior change that needs a specific regression case.

**Check.** Each path verifies the same tree version it verified before the move.

### Step 7. Keep applying transitions under one owner

Trace transitionGoal, graph writes, transition-log append, pointer updates, and tree events. Keep these effects in GoalPlannerService or its established transition collaborator until their ordering is covered.

If a decision result needs a reason or repair description, carry that data explicitly. GoalProgressApplicationResult describes applied promotion, so extend it only when its callers need additional applied-result information.

**Check.** A policy result cannot accidentally perform the same transition twice.

### Step 8. Replace temporary rationale state if the call chain permits it

Trace pendingFulfillmentResolution from the three-argument applyGoalProgress through promotion to completion. Add an explicit rationale parameter along that path while preserving public overloads.

After all consumers use the parameter, remove the field and the associated finally cleanup. Cover a failed transition and a later successful transition so a stale reason cannot leak.

**Check.** Existing persisted reasons remain identical.

### Step 9. Verify refusal and repair loops

Run closure, observed-leaf, transition, attribution, and fault-injection tests listed in the plan. Exercise a refused criterion followed by successful repair.

Check that bank invalidation occurs before the next early-completion decision and that equivalent repair work does not multiply.

**Slice result.** Read-only closure policy has an explicit home, root closure has readable stages, and goal transitions retain deliberate side-effect ordering.

## Slice 4 — make lifecycle sequencing readable

Primary source is [AutobuilderService.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/service/AutobuilderService.java).

### Step 1. Preserve the outer lock boundary

Read runAutobuilderCycle and runExclusively. The former checks context-failure backoff before acquiring the lock. The latter attempts the lock for two seconds and preserves interruption.

Keep those boundaries while extracting internal lifecycle stages.

**Check.** Scheduler backoff, reentrant calls, and operator requests retain their current behavior.

### Step 2. Document and retain queued rewind semantics

Follow requestOperatorRewind. It tries immediate execution, then replaces the queued request if the run is busy.

Add or select tests for immediate execution, busy queueing, and replacement by a newer request. Keep the atomic queue ownership in AutobuilderService.

**Check.** A second request has the same precedence as before.

### Step 3. Extract a context-settlement calculation

Inside settleAfterRewind, identify the calculation that chooses restored versus current context and resets result/exhaustion fields.

If this clarifies the method, extract that calculation as a private helper returning CycleContextData. Keep resyncAfterRewind and assignments to service flags in the coordinating method.

Preserve the user pause from the current context and use the restored context as the data source when available.

**Check.** The next tick continues from restored work and does not replay the terminal result from the discarded path.

### Step 4. Keep rewind at the same cycle boundary

At the beginning of runAutobuilderCycleLocked, preserve the start-refused, awaiting-goal, and pending-import guard before the queued rewind check.

Keep the return after a successful queued rewind so the next tick begins from restored state.

**Check.** Extraction preserves the exact order of these guards, including cases where a queued request waits.

### Step 5. Extract breaker-resume handling

Read the LLM-breaker cooldown block and its resumedFromLlmBreakerPause flag. Extract it into a private operation only if the result clearly preserves the skip of stale terminal processing.

Keep systemResumed emission in the same transition.

**Check.** Breaker recovery leads to another attempt without duplicating pause or terminal handling.

### Step 6. Extract startup and continue stages

Read init, runFirstCycle, startNow, performStart, continueProject, and restoreOwnDecisions as one sequence.

Name coherent private stages for restoring persisted decisions and preparing context. Reuse StartModeService and CycleContextCoordinator. Preserve readiness timing and the point where the first cycle may run.

**Check.** Initializing the Spring bean cannot execute an extra cycle.

### Step 7. Extract post-cycle sections individually

Identify contiguous post-cycle blocks by their effects, including preservation, stagnation handling, terminal reporting, and rewind consideration.

Move one block into a private method, pass required context explicitly, and return whether it consumed the remainder of the tick where needed.

**Check.** A rewind or terminal decision skips the same remaining work as before.

### Step 8. Verify checkpoint and operator behavior

Keep Snapshot and runBrakes representation intact while moving methods. Run queued-rewind, rewind-wiring, preserve, and round-trip tests.

Exercise pause, continue, and the next tick through public entry points. Preserve the distinction between user pause and a recoverable breaker pause.

**Slice result.** AutobuilderService remains the lock and lifecycle owner, with short coordinating paths whose ordering can be inspected.

## Slice 5 — isolate execution from verification

Primary sources are [BootGate.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/build/BootGate.java) and [ProbeSession.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/core/build/ProbeSession.java).

### Step 1. Start from the shared HTTP sequence implementation

BootGate already has a shared static executeSequence used by gate execution and Served. Read that implementation and its helpers before creating another executor.

Inventory renumbered, resolveReferences, substituteReferences, and result construction. Identify helper callers outside the sequence path.

**Check.** The list distinguishes sequence mechanics from criterion interpretation and summarization.

### Step 2. Extract the shared sequence implementation

A proposed destination is `src/main/java/com/walpro/autobuilder/core/build/ProbeSequenceExecutor.java`.

Move the shared implementation and helpers used exclusively by it. Preserve its request-builder input, form-page map, prefix, and existing outcome representation. Retain delegating BootGate entry points where package tests or callers require them.

Keep ProbeSession as the owner of cookies and form state.

**Check.** Both gate verification and Served call the extracted implementation.

### Step 3. Preserve inserted requests and reference numbering

Follow the shift array, inserted form-page requests, and wentOut sequence. Keep reference renumbering and recorded execution order unchanged.

Run ProbeSessionTest and the relevant BootGate sequence tests. Add a case for an uncovered inserted-request/reference interaction if the extraction can affect it.

**Check.** A captured response value still refers to the intended response after extra requests are inserted.

### Step 4. Extract finite process execution

Read runToEnd and runArtifactToEnd. Keep artifact selection, command construction, and service setup explicit.

A proposed `core/build/ArtifactProcessRunner.java` can own the finite process operation once it receives a complete command, working directory, timeout, and required execution settings. Keep service lifetime in the caller's try-with-resources boundary initially.

Move output draining, waiting, termination, and interruption handling together. Preserve inherited JVM-option removal, stdin closure, output accumulation, and existing status mapping.

**Check.** Timeout, not-started, interrupted, and completed outcomes retain the same public representation.

### Step 5. Check cleanup at every return

List each return and exception path in the extracted runner. Verify who destroys the process, closes streams, waits for the reader, and closes services.

Inspect actual cleanup behavior before broadening it. If a resource leak is discovered, specify its fix and regression test as a behavior change within a separate unit.

**Check.** The extraction does not lose any existing cleanup action or change the timeout budget inadvertently.

### Step 6. Review long-running serving separately

Trace serveOrWhy, readiness waiting, Served.close, and launchAndObserve. Their lifetime differs from a process run to completion.

Reuse the finite-run helper only for truly shared mechanics. Keep a serving handle responsible for the live process until close.

**Check.** Returning a serving handle leaves the application available, while startup failure follows the existing release path.

### Step 7. Keep grounding and attribution in the verifier

Review probeCommandCriteria and probeBehaviouralCriteria. Call the execution components with prepared exercises and interpret their observations in the verifier.

Preserve the distinction between an unobserved result and a contradicted criterion, including stable failure summaries.

**Check.** Equivalent failures produce the same work-item identity and attributed criteria.

### Step 8. Verify all public consumers

Run the runtime tests named in the plan. Include closure and reverse-engineering adapters in caller review. Exercise a local process where needed to verify real termination and cleanup.

**Slice result.** Shared sequence mechanics and finite process execution have coherent owners, while BootGate coordinates artifact verification.

## Slice 6 — expose evaluation stages without changing precedence

Primary source is [EvaluationAgent.java](/home/anugeshtu/IdeaProjects/autobuilder/src/main/java/com/walpro/autobuilder/agent/evaluation/EvaluationAgent.java).

### Step 1. Write an exit table before moving code

Enumerate every return in execute and the helpers it invokes. For each, record context fields, file state, model/probe calls, requests, metadata writes, and graph events.

Include the empty-diff path that settles lastEvaluatedEvolutionFingerprint.

**Check.** Every exit has a known effect set.

### Step 2. Extract eligibility preparation

Keep null-diff and settled-empty-diff exits at the top. Extract the incomplete-criteria retry calculation where it forms a coherent stage.

Preserve the BUILD_FIX exception to replan emission and the order in which retries are incremented.

**Check.** An unchanged candidate with incomplete criteria can still receive the existing re-evaluation treatment.

### Step 3. Retain repeat refusal before expensive work

Keep standingRejectionFor ahead of the compile probe and evaluator. Extract its coordination only if needed for readability.

Run EvaluationAgentRepeatRefusalTest. Check the same-tree repeat and the changed-tree retry case.

**Check.** A refused repeat avoids the probe and model under the same conditions as before.

### Step 4. Extract probe preparation with all necessary outputs

The probe produces both execution facts and filesystem effects. If a helper returns updated context and a probe result, use a small explicit result record carrying those values.

Preserve filesOnDisk, rollbackPoint, lastProbeOutcome, and injector changes. A compile error whose files remain staged follows its existing downstream path.

**Check.** The helper's result is sufficient for every later rollback decision.

### Step 5. Extract outcome acquisition

Keep unchanged-fingerprint reuse, local probe rejection, fragmentation rejection, and fresh model evaluation explicit.

Preserve criteria-assessment clearing on the paths that currently clear it. Keep scoring and fingerprint data available to the same downstream stages.

**Check.** The model is called in exactly the intended branches.

### Step 6. Extract negotiation and overrides

Reuse HeuristicNegotiator. Read and preserve the actual sequence of critical findings, originating-gate checks, test-progress checks, and interpretation.

Run EvaluationAgentGateCheckTest and EvaluationAgentTestProgressTest. Preserve the cases where critical findings suppress gate checks and where a gate failure does not reverse an already accepted assessment.

**Check.** The same inputs produce the same final state and rationale in covered cases.

### Step 7. Extract rejection rollback

Move the rollback block into a private method that receives the current context and current probe result explicitly.

Preserve the condition requiring staged files from this probe. Preserve only eligible injector-created infrastructure, excluding injector edits to candidate-owned paths.

Clear lastRollbackPoint after the rollback in the same place as before.

**Check.** A stale rollback point cannot undo previously accepted files.

### Step 8. Keep same-cycle persistence visible

Retain persistMutationStateToMeta before later same-cycle consumers need accepted variants. Keep graphFacade.recordEvaluation and action registration in their existing relation to interpretation and rollback.

Avoid combining these into a generic persistence helper that hides ordering.

**Check.** The files on disk, mutation metadata, and recorded evaluation agree after acceptance and rejection.

### Step 9. Run through the agent entry point

Run the tests selected from the exit table and the evaluation-specific list in the plan. Review every new helper's dependencies.

Keep private stages where they depend on the agent's shared context. Introduce another component only for a cohesive responsibility with a smaller dependency set.

**Slice result.** The agent reads as an ordered evaluation process with explicit early exits and mutation effects.

## Slice 7 — reduce state only after ownership is established

Primary sources are CycleContextData, AutobuilderService, GoalEntity, and the checkpoint machinery linked in the plan.

### Step 1. Inventory one state family at a time

Begin with criteria state, evaluation retry state, and run pause/terminal state affected by earlier slices.

Record the field, writers, readers, serialized key, lifecycle, and checkpoint bucket. Include generated withers/builders, reflection, tests, and graph queries.

**Check.** A missing textual getter reference is not sufficient evidence for removal.

### Step 2. Trace how persisted state is loaded

Read CycleContextData deserialization and AutobuilderService.restoreState. Check absent fields, default values, maps, and NaN score behavior.

Run existing round-trip tests before changing the representation.

**Check.** The current compatibility rules are recorded.

### Step 3. Remove one proven-unused carrier

Select a field with no live consumer after the completed extractions. Remove its producer assignments and accessors/builders together.

Search again for its Java name and serialized key. Inspect fixtures and older supported data to determine whether readers tolerate the old key.

**Check.** The application compiles and historical input still deserializes under the supported contract.

### Step 4. Group state only when ownership improves

For a group that belongs to one existing owner, determine whether moving it changes the serialized shape or checkpoint registration.

Prefer preserving the existing persisted shape during the first ownership change. If nesting changes JSON, plan the compatibility reader before changing writes.

**Check.** There is one authoritative copy and no unsynchronized mirror retained for convenience.

### Step 5. Preserve checkpoint registration

Run StateHolderRegistrationContractTest after moving mutable singleton state. Set the applicable StateHolder bucket and reason according to how the state is restored.

Run DecisionBearingHolderRoundTripTest and RewindRoundTripPropertyTest for captured decisions.

**Check.** Moving a field cannot silently remove it from rewind.

### Step 6. Remove one obsolete rule at a time

Choose a branch only after tracing its original bug and surviving replacement. Identify the test that demonstrates the replacement handles the original case.

Remove the branch and unused configuration or state in one coherent change. Run the associated replay tests.

**Check.** The deletion has evidence beyond the branch's age or apparent complexity.

### Step 7. Complete compatibility verification

Load supported older goal and checkpoint fixtures, continue a run, and rewind through normal services. Verify the next decision as well as successful deserialization.

If a migration writes a representation the previous version cannot read, document the preserved snapshot used to roll back.

**Slice result.** Each remaining state field has a known owner and lifetime, and removed mechanisms have documented evidence.

## Final walkthrough

1. Run the full suite on the resulting revision.
2. Count reports from that invocation and resolve failures or record genuinely external blockers.
3. Exercise the affected normal entry points using selected cases from the plan.
4. Compare criteria outcomes, terminal reasons, durable goal state, and restored next actions with the baseline.
5. Review the central methods for readable ordering and repeated policy left behind.
6. Update the original checklist with completed steps and verification evidence.
7. Record the final changed files and any remaining behavior changes, such as T4-EU, with their own acceptance criteria.

Completion means the implementation follows clearer ownership boundaries and preserves the verified contracts. The detailed guide itself remains a proposed sequence until those changes are executed.
