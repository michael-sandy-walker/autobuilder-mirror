package com.walpro.autobuilder.analysis.heuristic;

import com.walpro.autobuilder.agent.evaluation.model.EvaluationOutcome;
import com.walpro.autobuilder.analysis.diff.ProjectDiffResult;
import com.walpro.autobuilder.analysis.diff.model.StructuralDiffNode;
import com.walpro.autobuilder.core.lifecycle.CycleContextData;
import com.walpro.autobuilder.model.GoalProgressState;
import com.walpro.autobuilder.model.MutationAcceptanceState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1-GS: an AUTHORED criterion (not stated by the specification) never keeps a subgoal from closing. The recorded
 * episode: criterion 1 of 8 ("Create AlternatingPair.java") unmet, 2-8 met, the change accepted -- the run held it
 * back ("STAGNATING, Merged: false") and replanned to satisfy criterion 1 against the ticket.
 */
class SubGoalFulfillmentAdvisoryCriteriaTest {

    private final SubGoalFulfillmentHeuristic heuristic = new SubGoalFulfillmentHeuristic();
    private static final List<String> EIGHT = List.of("c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8");

    private static CycleContextData accepted(GoalProgressState progress, double score) {
        ProjectDiffResult diff = new ProjectDiffResult(new StructuralDiffNode(null, null, List.of()),
                Map.of("StuckLedger.java", "changed"), Set.of());
        return CycleContextData.builder()
                .currentSubGoalId("goal-d8626bdc")
                .mutationState(MutationAcceptanceState.ACCEPTED)
                .goalProgress(progress)
                .projectDiff(diff)
                .evaluationOutcome(new EvaluationOutcome(score, null, null, null, null))
                .currentSubGoalCriteria(EIGHT)
                .build();
    }

    private static Map<Integer, Boolean> firstUnmetRestMet() {
        Map<Integer, Boolean> m = new HashMap<>();
        m.put(1, false);
        for (int i = 2; i <= 8; i++) m.put(i, true);
        return m;
    }

    @Test
    void theRecordedEpisode_criterionOneAuthoredAndUnmet_theSubgoalCloses() {
        var ctx = accepted(GoalProgressState.PROGRESSING, 0.79)
                .withCumulativeCriteriaStatus(firstUnmetRestMet())
                .withCurrentSubGoalAuthoredCriteria(Set.of(1));
        var result = heuristic.decide(ctx);
        assertThat(result.fulfilled()).isTrue();
        assertThat(result.rationale()).contains("score-based closure");
    }

    @Test
    void withoutTheStampTheSameUnmetCriterionStillVetoes_asBefore() {
        var ctx = accepted(GoalProgressState.PROGRESSING, 0.79).withCumulativeCriteriaStatus(firstUnmetRestMet());
        assertThat(heuristic.decide(ctx).fulfilled()).isFalse();
        assertThat(heuristic.decide(ctx).rationale()).contains("unmet acceptance criterion");
    }

    @Test
    void anUnmetSpecCriterionStillVetoesWhenAnotherIsAuthored() {
        Map<Integer, Boolean> m = firstUnmetRestMet();
        m.put(5, false);
        var ctx = accepted(GoalProgressState.PROGRESSING, 0.79)
                .withCumulativeCriteriaStatus(m)
                .withCurrentSubGoalAuthoredCriteria(Set.of(1));
        assertThat(heuristic.decide(ctx).fulfilled()).isFalse();
    }

    @Test
    void theInterpreterSayingFulfilledIsNotSuppressedByAnAuthoredCriterion() {
        var ctx = accepted(GoalProgressState.FULFILLED, 0.5)
                .withCumulativeCriteriaStatus(firstUnmetRestMet())
                .withCurrentSubGoalAuthoredCriteria(Set.of(1));
        assertThat(heuristic.decide(ctx).fulfilled()).isTrue();
    }

    @Test
    void withoutACumulativeMap_anAdvisoryCrossIsNotUnmet_aBindingOneIs() {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("[1] ✗ Create AlternatingPair.java -- nested, not a separate file");
        for (int i = 2; i <= 8; i++) lines.add("[" + i + "] ✓ c" + i);
        assertThat(SubGoalFulfillmentHeuristic.hasUnmetCriterion(lines, EIGHT, null, Set.of(1))).isFalse();
        assertThat(SubGoalFulfillmentHeuristic.hasUnmetCriterion(lines, EIGHT, null, Set.of())).isTrue();
    }
}
