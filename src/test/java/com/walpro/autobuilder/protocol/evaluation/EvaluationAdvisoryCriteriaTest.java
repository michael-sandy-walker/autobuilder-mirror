package com.walpro.autobuilder.protocol.evaluation;

import com.walpro.autobuilder.goal.CriteriaBinding;
import com.walpro.autobuilder.goal.GoalEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1-GS: a criterion the specification did not state (stamped AUTHORED) reaches the evaluator as ADVISORY. Replayed
 * on the recorded episode: subgoal d8626bdc of the stuck-alternation run 2026-11-17_21-23-21, its eight criteria and
 * its stamp, as the run's GoalStack holds them.
 */
class EvaluationAdvisoryCriteriaTest {

    private static final List<String> RECORDED = List.of(
            "Create AlternatingPair.java",
            "StuckLedger compiles with the new public method and nested record",
            "alternatingPairs() returns an empty list when no two CLOSURE_REFUSAL signatures form at least four runs",
            "For an A B A B merged sequence of two CLOSURE_REFUSAL signatures, alternatingPairs() returns exactly one AlternatingPair with first = the signature counted first, second = the other, and firstCycles/secondCycles equal to each signature's Entry.cycles() in counted order",
            "For A A A A (alone), A A B B, A B A, or any pair where either signature is not CLOSURE_REFUSAL, alternatingPairs() does not include that pair",
            "Pairs are ordered by first sighting of first, then of second, and each qualifying pair appears exactly once",
            "All existing tests under src/test still pass unchanged",
            "No existing public method signature or behaviour of StuckLedger is changed");

    private static GoalEntity recordedSubgoal() {
        GoalEntity g = new GoalEntity();
        g.setId("goal-d8626bdc-6f6f-40ed-b130-435bcdfd2d8b");
        g.getMetadata().put("acceptanceCriteria", RECORDED);
        g.getMetadata().put("criteriaBinding", Map.of("1", "AUTHORED", "2", "SPEC", "3", "SPEC", "4", "SPEC",
                "5", "SPEC", "6", "SPEC", "7", "SPEC", "8", "SPEC"));
        return g;
    }

    @Test
    void theRecordedStampReadsAsCriterionOneAuthored() {
        assertThat(CriteriaBinding.authoredIndices(recordedSubgoal(), RECORDED.size())).containsExactly(1);
        assertThat(CriteriaBinding.authoredIndices(new GoalEntity(), RECORDED.size())).isEmpty();
    }

    @Test
    void theRecordedEpisode_criterionOneReachesTheEvaluatorAsAdvisory_theOthersAsBefore() {
        Set<Integer> advisory = CriteriaBinding.authoredIndices(recordedSubgoal(), RECORDED.size());

        String check = EvaluationProtocolContract.buildCriteriaCheckFormat(RECORDED, advisory);
        assertThat(check).startsWith("CriteriaCheck: (8 criteria");
        assertThat(check).contains("[1] ✓ or ✗ (advisory) Create AlternatingPair.java");
        for (int i = 2; i <= 8; i++) {
            assertThat(check).contains("[" + i + "] ✓ or ✗ " + RECORDED.get(i - 1));
        }
        assertThat(check.lines().filter(l -> l.startsWith("[")).count()).as("same count and order: the parse is unchanged")
                .isEqualTo(8);
        assertThat(check.split("\\(advisory\\)", -1)).hasSize(2);

        String block = EvaluationProtocolContract.criteriaBlock(RECORDED, advisory);
        assertThat(block).contains("* (advisory) Create AlternatingPair.java");
        assertThat(block).contains("* StuckLedger compiles with the new public method and nested record");
        assertThat(block).contains("never a reason to reject the change");
    }

    @Test
    void withoutAStampNothingChanges() {
        String check = EvaluationProtocolContract.buildCriteriaCheckFormat(RECORDED, Set.of());
        String block = EvaluationProtocolContract.criteriaBlock(RECORDED, Set.of());
        assertThat(check).doesNotContain("advisory");
        assertThat(block).doesNotContain("advisory");
        assertThat(block.lines()).hasSize(8).allMatch(l -> l.startsWith("* "));
    }

    @Test
    void noCriteriaKeepsItsOwnWording() {
        assertThat(EvaluationProtocolContract.criteriaBlock(List.of(), Set.of(1)))
                .startsWith("No acceptance criteria were defined");
        assertThat(EvaluationProtocolContract.buildCriteriaCheckFormat(null, Set.of())).contains("Omit CriteriaCheck");
    }
}
