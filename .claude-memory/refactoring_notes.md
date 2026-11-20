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
`DocsAreTrueTest`) fails when a heading's file no longer exists or no longer declares the class,
so a rename or deletion cannot leave an orphaned entry.

**Format.** One `## ` heading per class: the class name and its path, both in backticks. Under
it, one bullet per finding: `- YYYY-MM-DD -- finding. Why it matters. Suggested change.`
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
