# Refactoring notes -- per class, picked up when the class is next changed

**What this is.** Findings from code reviews that are worth doing but were not made tickets: dead
code, misplaced responsibilities, stale names, switches whose reason expired. Grouped by class.

**What it is NOT: a queue.** Work comes from the roadmap only ([[feedback-roadmap-is-the-queue]]).
An entry is picked up in one of two ways:
- the user promotes it to a ticket; or
- a ticket is about to change that class anyway -- then the session reads the class's entries
  first and folds in what fits that ticket's scope (the usual way these get done: cleanup filed
  as its own low-priority ticket rarely reaches the top, see [[project-delete-debt-pattern]]).

**Entries expire.** When an entry is done or obsolete it is DELETED, and the commit names the
ticket that did it. `scripts/refactoring_notes_check.py` (run with the suite by
`DocsAreTrueTest`) fails when a named class's file no longer exists or no longer declares the
class (a class heading or a concern's `Classes:` line), so a rename or deletion cannot leave an
orphaned entry.

**Format.** Two kinds of section. A finding local to one class: a `## ` heading with the class
name and its path, both in backticks. A finding that spans classes: `## Concern: <name>`, with a
`Classes:` line naming every class it touches as `` `Name` -- `path` `` pairs separated by `;` --
so a search for a class name finds both kinds. Under either, one bullet per finding:
`- YYYY-MM-DD -- finding. Why it matters. Suggested change.`
State facts as found on that date; re-check them before acting (premises expire,
[[project-ticket-premises-expire]]).

---

## `EvolutionLLMAdapter` -- `src/main/java/com/walpro/autobuilder/agent/evolution/EvolutionLLMAdapter.java`

Context: reviewed 2026-11-19 alongside T5-BZ, whose step 1 rewrites this class's prompt assembly
(the ten appended blocks, the divergence path); these findings were deliberately left out of it.

- 2026-11-19 -- `withTestRunNow` runs the project's tests (`gate.runOnTree(...)`) while building
  the prompt, and on a declared suite with unknown tree state can run them a second time. The
  behaviour is right (T6-T needs the tree's test state before the model writes); the place is
  not: the most expensive side effect of the cycle hides in the method that talks to the model.
  Suggested: a preparation step in `EvolutionAgent` before `evolve` (in T5-BZ terms, before the
  components run). Changes when tests run -- needs a short field run.
- 2026-11-19 -- `setSubgoalSourceBlock` is a `@Value` setter that flips a STATIC flag in
  `SubgoalSourcePromptBlock` (JVM-global, tests included). T5-BT (2026-10-04) kept this switch
  only because "a script varies it"; no script, shell file, config or test references
  `autobuilder.evolution.subgoal-source-block` any more. Suggested: delete the switch and the
  static flag, block always on (T5-BT's own rule).
- 2026-11-19 -- `evolve(...)` declares `throws IOException`, but none of the methods it calls
  declares a checked `IOException`. Removing it removes the try/catch in `EvolutionAgent` and
  in `evolveDivergent` (the compiler confirms).
- 2026-11-19 -- eleven optional beans, each field-injected as "optional in narrow tests" and
  null-checked at its use. Most are prompt blocks; T5-BZ's ordered component list removes the
  pattern for those.
- 2026-11-19 -- `evolveGraph` is public but called only by this class; a one-line forward to
  `graphFacade.recordEvolution`. Suggested: inline.
- 2026-11-19 -- `registerEvolution("StaticEvolutionAgent", ...)`: no such class exists; only
  `EvolutionLLMAdapterTest` asserts the string. It is written into graph records, so a rename
  changes the identifier records carry -- check no graph query filters on it first. Open: the
  user's call whether to rename it.
- 2026-11-19 -- `editsFirstBlock` derives a class name by stripping `".java"` from the path --
  an ecosystem assumption in framework code ([[feedback-no-ecosystem-jargon-in-framework-code]]).
  Suggested: the file's base name without extension.

## `EvolutionAgent` -- `src/main/java/com/walpro/autobuilder/agent/evolution/EvolutionAgent.java`

Context: reviewed 2026-11-20 after T5-BZ's divergence measurement; the divergence switch itself
(`useDivergent`, `isRegressing`, T2-AO's consumption check) is T5-BZ step 1's, not listed here.

- 2026-11-20 -- `computeProjectDiff` treats a cycle as bootstrap when `cycleName` is null or starts
  with `"bootstrap"`. The loop names every cycle `"cycle-" + millis` (`CycleContextCoordinator`,
  `CheckpointService`); no `bootstrap*` name appears in any target log, only
  `EvolutionAgentDiffPathTest` builds one. A control-flow decision on a name prefix, one branch of
  it reachable only from a test. Suggested: decide from the state (no accepted cycle and no
  mutation path yet), delete the name test; also make the method private once the test reads it
  through `execute`.
- 2026-11-20 -- when `applyRegularMutations` or the diff throws, the catch logs and returns the
  context AFTER `unmergedEvolutionWork(true)` and the evaluation window were set, but without
  `withLastEvolutionOutcome` -- evaluation is made eligible for a mutation that was not written.
  Never happened: 0 "Failed to apply mutations" lines in the target logs and 400 backup logs.
  Suggested: set the flags only after the write succeeded.
- 2026-11-20 -- `ensureAttemptId` always sets `lastAttemptId` to `cycleName` (a UUID only when the
  name is blank, which the loop never produces). Seven readers (graph registrars,
  `GraphQueryService`, `EvaluationAgent`) read a copy of the cycle name. Suggested: read
  `cycleName` and delete the field -- check the graph's `attemptId` properties keep their meaning.
- 2026-11-20 -- `applyCompileDiagnosticSteering` returns its `context` unchanged; it only emits a
  behaviour event. The steering block is also rendered twice per cycle (here to decide the event,
  again inside the prompt). Suggested: a void method now; with T5-BZ's components, the component's
  own render result carries the event and the second render goes.
- 2026-11-20 -- stale names: log messages and one request source say "EvolutionExecutionAgent";
  `// TODO: should withMerged(!changed) be added here?` at the end of `runEvolution` asks a
  question nothing since has answered (merge state is the merge agent's). Suggested: rename to the
  class's name, delete the TODO.

## Concern: recombination / divergence leftovers

Classes: `EvolutionOutcome` -- `src/main/java/com/walpro/autobuilder/agent/evolution/model/EvolutionOutcome.java`; `EvolutionActionRegistrar` -- `src/main/java/com/walpro/autobuilder/graph/evolution/EvolutionActionRegistrar.java`; `CycleContextData` -- `src/main/java/com/walpro/autobuilder/core/lifecycle/CycleContextData.java`; `ContextLineageTracker` -- `src/main/java/com/walpro/autobuilder/core/lifecycle/ContextLineageTracker.java`; `SmsCycleIngestor` -- `src/main/java/com/walpro/autobuilder/memory/sms/SmsCycleIngestor.java`; `EvolutionLLMAdapter` -- `src/main/java/com/walpro/autobuilder/agent/evolution/EvolutionLLMAdapter.java`; `EvolutionAgent` -- `src/main/java/com/walpro/autobuilder/agent/evolution/EvolutionAgent.java`; `EvolutionProtocolContract` -- `src/main/java/com/walpro/autobuilder/protocol/evolution/EvolutionProtocolContract.java`

Context: moved here 2026-11-21 when T5-BZ was deferred ("not the best idea to change so much within
the core without having proven a clear benefit"). These change no behaviour; the divergence
mechanism itself (switch, ten request sites, texts) stays as it is -- see T5-BZ's measurements.

- 2026-11-21 -- `EvolutionOutcome.recombined` / `withRecombined` is read only to label the graph
  action `RECOMBINATION` instead of `MUTATION_APPLIED` in `EvolutionActionRegistrar`; no query,
  script or observatory page reads that label back. `recombined` is the same boolean as the
  contract's `divergent`. Suggested: delete the field and the label (check the graph's existing
  `RECOMBINATION` actions are not queried anywhere first).
- 2026-11-21 -- `CycleContextData.lastRecombinationFingerprint` is never set anywhere: carried
  forward by `ContextLineageTracker` and ingested always-null by `SmsCycleIngestor`.
  `recombinationSourceCycleName` is written by `ContextLineageTracker` and read only by itself, to
  carry it forward. Suggested: delete both.
- 2026-11-21 -- `EvolutionLLMAdapter.evolveDivergent` only calls `evolve(ctx, true)` and converts
  `IOException`; `EvolutionAgent` can call `evolve(ctx, divergent)` directly (with the
  `throws IOException` entry under `EvolutionLLMAdapter`).
- 2026-11-21 -- `EvolutionProtocolContract.getUserPrompt` has five overloads; production calls only
  the longest, four test files call the others. Suggested: one signature, tests pass defaults.
- 2026-11-21 -- the contract's "Score Plateau Detected" paragraph never renders: `SCORE_PLATEAU` was
  raised 174 times in the target logs (September-November) and the paragraph appeared 0 times (280
  times in the April-July backups). The signal is consumed by `RuleBasedDeliberationProposer`
  (merge boost, divergence request, replan) before an evolution prompt is built. Suggested: delete
  the paragraph -- or, if the plateau should reach the evolution model, route it as a concrete fact
  (the replays found the generic paragraphs of this kind do not help).
