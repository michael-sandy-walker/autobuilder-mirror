package com.walpro.autobuilder.core.stuck;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T4-EU, the ledger half of the alternation reading: the cycle of every counted sighting, and closure refusals whose
 * sightings trade places named as a pair. Written before the change, as its specification.
 */
class StuckLedgerAlternationSpecTest {

    private static final String MENU_GONE = "closing 'the screensaver' was refused: The application frame displays without a menu bar";
    private static final String MENU_USER = "closing 'the screensaver' was refused: The menu includes an option to specify a username.";
    private static final String PAUSE = "closing 'the screensaver' was refused: The animation can be paused via menu items.";

    private static StuckObservation refusal(String signature, String cycle) {
        return new StuckObservation(StuckObservation.Source.CLOSURE_REFUSAL, signature, "sg", cycle, List.of());
    }

    private static StuckObservation build(String signature, String cycle) {
        return new StuckObservation(StuckObservation.Source.BUILD, signature, "sg", cycle, List.of());
    }

    private static StuckLedger.Entry entry(StuckLedger l, String signature) {
        return l.entries().stream().filter(e -> e.signature().equals(signature)).findFirst().orElseThrow();
    }

    @Test
    void anEntryKeepsTheCycleOfEveryCountedSightingInOrder() {
        StuckLedger l = new StuckLedger();
        l.observe(refusal(MENU_GONE, "c1"));
        l.observe(refusal(MENU_GONE, "c2"));
        l.observe(refusal(MENU_GONE, "c5"));

        assertThat(entry(l, MENU_GONE).cycles()).containsExactly("c1", "c2", "c5");
    }

    @Test
    void aSightingTheLedgerDoesNotCountIsNotKept() {
        StuckLedger l = new StuckLedger();
        l.observe(refusal(MENU_GONE, "c1"));
        l.observe(refusal(MENU_GONE, "c1"));   // the same cycle again: not counted
        l.observe(refusal(MENU_GONE, "c2"));

        assertThat(entry(l, MENU_GONE).cycles()).containsExactly("c1", "c2");
        assertThat(entry(l, MENU_GONE).occurrences()).isEqualTo(2);
    }

    @Test
    void twoRefusalsThatKeepTradingPlacesAreNamedAsAPair() {
        StuckLedger l = new StuckLedger();
        l.observe(refusal(MENU_GONE, "c1"));
        l.observe(refusal(MENU_USER, "c2"));
        l.observe(refusal(MENU_GONE, "c3"));
        l.observe(refusal(MENU_USER, "c4"));
        l.observe(refusal(MENU_GONE, "c5"));
        l.observe(refusal(MENU_USER, "c6"));

        assertThat(l.alternatingPairs()).containsExactly(new StuckLedger.AlternatingPair(
                MENU_GONE, MENU_USER, List.of("c1", "c3", "c5"), List.of("c2", "c4", "c6")));
    }

    @Test
    void fourRunsAreEnough() {
        StuckLedger l = new StuckLedger();
        l.observe(refusal(MENU_USER, "c1"));
        l.observe(refusal(MENU_GONE, "c2"));
        l.observe(refusal(MENU_USER, "c3"));
        l.observe(refusal(MENU_GONE, "c4"));

        assertThat(l.alternatingPairs()).containsExactly(new StuckLedger.AlternatingPair(
                MENU_USER, MENU_GONE, List.of("c1", "c3"), List.of("c2", "c4")));
    }

    @Test
    void theOrderIsTheOrderTheLedgerCountedInEvenWithinOneCycle() {
        StuckLedger l = new StuckLedger();
        l.observe(refusal(MENU_GONE, "c1"));
        l.observe(refusal(MENU_USER, "c1"));
        l.observe(refusal(MENU_GONE, "c2"));
        l.observe(refusal(MENU_USER, "c2"));

        assertThat(l.alternatingPairs()).containsExactly(new StuckLedger.AlternatingPair(
                MENU_GONE, MENU_USER, List.of("c1", "c2"), List.of("c1", "c2")));
    }

    @Test
    void threeRunsAreNotAPair() {
        StuckLedger l = new StuckLedger();
        l.observe(refusal(MENU_GONE, "c1"));
        l.observe(refusal(MENU_USER, "c2"));
        l.observe(refusal(MENU_GONE, "c3"));

        assertThat(l.alternatingPairs()).isEmpty();
    }

    @Test
    void oneHandOverIsNotAPairHoweverLongEachSideRepeats() {
        StuckLedger l = new StuckLedger();
        for (String c : List.of("c1", "c2", "c3")) l.observe(refusal(MENU_GONE, c));
        for (String c : List.of("c4", "c5", "c6")) l.observe(refusal(MENU_USER, c));

        assertThat(l.alternatingPairs()).isEmpty();
    }

    @Test
    void aSignatureRepeatingAloneIsNotAPair() {
        StuckLedger l = new StuckLedger();
        for (String c : List.of("c1", "c2", "c3", "c4")) l.observe(refusal(MENU_GONE, c));

        assertThat(l.alternatingPairs()).isEmpty();
    }

    @Test
    void onlyClosureRefusalsFormPairs() {
        StuckLedger l = new StuckLedger();
        String compile = "the build fails: Quill has no getInk()";
        l.observe(refusal(MENU_GONE, "c1"));
        l.observe(build(compile, "c2"));
        l.observe(refusal(MENU_GONE, "c3"));
        l.observe(build(compile, "c4"));

        assertThat(l.alternatingPairs()).isEmpty();
    }

    @Test
    void everyPairAmongSeveralIsNamedOnceInTheOrderOfTheirFirstSightings() {
        StuckLedger l = new StuckLedger();
        String[] order = {MENU_GONE, MENU_USER, PAUSE, MENU_GONE, MENU_USER, PAUSE};
        for (int i = 0; i < order.length; i++) l.observe(refusal(order[i], "c" + (i + 1)));

        assertThat(l.alternatingPairs()).containsExactly(
                new StuckLedger.AlternatingPair(MENU_GONE, MENU_USER, List.of("c1", "c4"), List.of("c2", "c5")),
                new StuckLedger.AlternatingPair(MENU_GONE, PAUSE, List.of("c1", "c4"), List.of("c3", "c6")),
                new StuckLedger.AlternatingPair(MENU_USER, PAUSE, List.of("c2", "c5"), List.of("c3", "c6")));
    }

    @Test
    void noSightingsNoPairs() {
        assertThat(new StuckLedger().alternatingPairs()).isEmpty();
    }
}
