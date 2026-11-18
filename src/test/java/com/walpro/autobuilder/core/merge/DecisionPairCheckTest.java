package com.walpro.autobuilder.core.merge;

import com.walpro.autobuilder.goal.purpose.InputEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T4-EY step 2: two decisions are checked against each other before an import is kept. Recorded case: the gradient
 * landing decided "Remove the menu bar entirely" and "paused and resumed via menu items" at once, and nothing said so.
 */
class DecisionPairCheckTest {

    private static InputEvent own(String id, String input, String subject, String receiving, String other, String resolution) {
        return new InputEvent(id, InputEvent.CONFLICT, input, subject, receiving, other, null, null, List.of(),
                InputEvent.DECIDED, InputEvent.FOR_OWN, "operator", Instant.EPOCH, resolution, true);
    }

    private final InputEvent menu = own("m", "in", "Control menu availability", "No menu bar is present",
            "A menu bar with Start, Pause", "Remove the menu bar entirely");
    private final InputEvent pause = own("p", "in", "Pause functionality", "Animation cannot be paused",
            "Paused via menu items", "The animation can be paused and resumed via menu items.");
    private final InputEvent speed = own("s", "in", "Animation update frequency", "Every 33 ms", "Every 100 ms",
            "Use a 33-millisecond update interval");

    @Test
    void decisionsPairUpWhenOneIsTheImports_andOpenFileAndWithdrawnOnesAreLeftOut() {
        InputEvent earlier = own("e", "kept-before", "Colours", "a gradient", "black", "a gradient");
        InputEvent elsewhere = own("x", "kept-other", "Size", "800x600", "full screen", "800x600");
        InputEvent open = new InputEvent("o", InputEvent.CONFLICT, "in", "Open one", "r", "o", null, null, List.of(),
                InputEvent.OPEN, null, null, Instant.EPOCH);
        InputEvent file = new InputEvent("f", InputEvent.CONFLICT, "in", "src/A.java", "r", "o", null, "meta/held/A.java",
                List.of(), InputEvent.DECIDED, InputEvent.FOR_INPUT, "operator", Instant.EPOCH);
        InputEvent gone = own("g", "withdrawn", "Gone", "r", "o", "o");
        InputEvent intent = new InputEvent("c", InputEvent.CORRECTION, "in", "Greeting", "none", "a greeting", null, null,
                List.of(), InputEvent.DECIDED, InputEvent.FOR_INPUT, "intent", Instant.EPOCH);

        var pairs = ProjectInputService.pairsOf(List.of(earlier, elsewhere, menu, open, file, gone, intent, pause),
                "withdrawn"::equals, "in");

        assertThat(pairs).extracting(p -> p.first().id() + p.second().id())
                .as("the import's decisions with each other and with the project's; never two that are both the project's")
                .containsExactlyInAnyOrder("em", "ec", "ep", "xm", "xc", "xp", "mc", "mp", "cp");
    }

    @Test
    void theScreeningNarrowsWhatIsAsked_andOnlyAContradictionOrATensionIsFlagged() {
        InputComparison comparison = mock(InputComparison.class);
        when(comparison.screen(eq("spec"), anyList())).thenReturn(List.<int[]>of(new int[]{1, 2}));
        when(comparison.together(eq("spec"), anyString(), anyString()))
                .thenReturn(new InputComparison.PairVerdict(InputComparison.Together.CONTRADICTS, "pausing needs the menu"));

        var result = ProjectInputService.check("in", ProjectInputService.pairsOf(List.of(menu, pause, speed), id -> false, "in"),
                comparison, "spec", new HashMap<>());

        assertThat(result.asked()).isEqualTo(3);
        assertThat(result.flagged()).singleElement().satisfies(p -> {
            assertThat(p.first().id() + p.second().id()).isEqualTo("mp");
            assertThat(p.verdict()).isEqualTo("CONTRADICTS");
            assertThat(p.reason()).isEqualTo("pausing needs the menu");
        });
        verify(comparison, times(1)).together(anyString(), anyString(), anyString());
    }

    @Test
    void aPairThatHoldsTogetherIsNotFlagged_andOneThatCouldNotBeAskedIsReportedAsSuch() {
        InputComparison comparison = mock(InputComparison.class);
        when(comparison.screen(anyString(), anyList())).thenReturn(List.<int[]>of(new int[]{1, 2}, new int[]{1, 3}));
        when(comparison.together(anyString(), eq(ProjectInputService.describe(menu)), eq(ProjectInputService.describe(pause))))
                .thenReturn(new InputComparison.PairVerdict(InputComparison.Together.TOGETHER, "a popup menu"));
        when(comparison.together(anyString(), eq(ProjectInputService.describe(menu)), eq(ProjectInputService.describe(speed))))
                .thenReturn(new InputComparison.PairVerdict(InputComparison.Together.UNKNOWN, ""));

        var result = ProjectInputService.check("in", ProjectInputService.pairsOf(List.of(menu, pause, speed), id -> false, "in"),
                comparison, "spec", new HashMap<>());

        assertThat(result.flagged()).isEmpty();
        assertThat(result.unchecked()).singleElement().satisfies(p -> assertThat(p.second().id()).isEqualTo("s"));
    }

    /** A screening that could not be read narrows nothing: every pair is asked -- slow, not blind. */
    @Test
    void aFailedScreeningAsksEveryPair() {
        InputComparison comparison = mock(InputComparison.class);
        when(comparison.screen(anyString(), anyList())).thenReturn(null);
        when(comparison.together(anyString(), anyString(), anyString()))
                .thenReturn(new InputComparison.PairVerdict(InputComparison.Together.TOGETHER, ""));

        ProjectInputService.check("in", ProjectInputService.pairsOf(List.of(menu, pause, speed), id -> false, "in"),
                comparison, "spec", new HashMap<>());

        verify(comparison, times(3)).together(anyString(), anyString(), anyString());
    }

    /** After an Edit, only pairs with the changed decision are asked again: an unchanged pair's verdict is kept. */
    @Test
    void anUnchangedPairIsNotAskedAgain_butAnAnswerThatCouldNotBeReadIsNotKept() {
        InputComparison comparison = mock(InputComparison.class);
        when(comparison.screen(anyString(), anyList())).thenReturn(List.<int[]>of(new int[]{1, 2}));
        when(comparison.together(anyString(), anyString(), anyString()))
                .thenReturn(new InputComparison.PairVerdict(InputComparison.Together.CONTRADICTS, "r"));
        Map<String, InputComparison.PairVerdict> cache = new HashMap<>();
        var pairs = ProjectInputService.pairsOf(List.of(menu, pause), id -> false, "in");

        ProjectInputService.check("in", pairs, comparison, "spec", cache);
        ProjectInputService.check("in", pairs, comparison, "spec", cache);
        verify(comparison, times(1)).together(anyString(), anyString(), anyString());

        InputComparison silent = mock(InputComparison.class);
        when(silent.screen(anyString(), anyList())).thenReturn(List.<int[]>of(new int[]{1, 2}));
        when(silent.together(anyString(), anyString(), anyString()))
                .thenReturn(new InputComparison.PairVerdict(InputComparison.Together.UNKNOWN, ""));
        Map<String, InputComparison.PairVerdict> none = new HashMap<>();
        ProjectInputService.check("in", pairs, silent, "spec", none);
        assertThat(none).isEmpty();
    }

    @Test
    void noPairsAskNothing() {
        InputComparison comparison = mock(InputComparison.class);
        var result = ProjectInputService.check("in", List.of(), comparison, "spec", new HashMap<>());
        assertThat(result.asked()).isZero();
        verify(comparison, never()).screen(anyString(), anyList());
    }

    /** What the model is shown: what each project had, how it was decided, and what now holds. */
    @Test
    void aDecisionIsDescribedWithWhatLiesUnderIt() {
        assertThat(ProjectInputService.describe(pause))
                .contains("\"Pause functionality\"", "receiving project had: Animation cannot be paused",
                        "brought in had: Paused via menu items", "in the operator's words: The animation can be paused",
                        "Now required: The animation can be paused and resumed via menu items.");
        InputEvent claimOut = new InputEvent("k", InputEvent.CONFLICT, "in", "A claim", null, null, "Knot is final", null,
                List.of(), InputEvent.DECIDED, InputEvent.FOR_RECEIVING, "operator", Instant.EPOCH);
        assertThat(ProjectInputService.describe(claimOut)).contains("carried this requirement: Knot is final", "left out");
    }
}
