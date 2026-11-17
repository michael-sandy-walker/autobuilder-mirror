package com.walpro.autobuilder.core.stuck;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StuckLedgerAlternatingPairsTest {

    private static StuckObservation obs(StuckObservation.Source source, String sig, String cycle) {
        return new StuckObservation(source, sig, null, cycle, List.of());
    }

    @Test
    void fourRunsABABAreNamedAsOnePair() {
        StuckLedger ledger = new StuckLedger();
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigA", "c1"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigB", "c2"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigA", "c3"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigB", "c4"));

        List<StuckLedger.AlternatingPair> pairs = ledger.alternatingPairs();
        assertEquals(1, pairs.size());
        StuckLedger.AlternatingPair p = pairs.get(0);
        assertEquals("sigA", p.first());
        assertEquals("sigB", p.second());
        assertEquals(List.of("c1", "c3"), p.firstCycles());
        assertEquals(List.of("c2", "c4"), p.secondCycles());
    }

    @Test
    void signatureRepeatingAloneIsNotAPair() {
        StuckLedger ledger = new StuckLedger();
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigA", "c1"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigA", "c2"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigA", "c3"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigA", "c4"));

        assertTrue(ledger.alternatingPairs().isEmpty());
    }

    @Test
    void twoSignaturesOverlappingOnlyOnceIsNotAPair() {
        StuckLedger ledger = new StuckLedger();
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigA", "c1"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigA", "c2"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigB", "c3"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigB", "c4"));

        assertTrue(ledger.alternatingPairs().isEmpty());
    }

    @Test
    void twoSignaturesOverlappingOnceABAIsNotAPair() {
        StuckLedger ledger = new StuckLedger();
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigA", "c1"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigB", "c2"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigA", "c3"));

        assertTrue(ledger.alternatingPairs().isEmpty());
    }

    @Test
    void pairInvolvingNonClosureRefusalSourceIsNotAPair() {
        StuckLedger ledger = new StuckLedger();
        ledger.observe(obs(StuckObservation.Source.BUILD, "sigA", "c1"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigB", "c2"));
        ledger.observe(obs(StuckObservation.Source.BUILD, "sigA", "c3"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigB", "c4"));

        assertTrue(ledger.alternatingPairs().isEmpty());
    }

    @Test
    void eachQualifyingPairIsNamedExactlyOnce() {
        StuckLedger ledger = new StuckLedger();
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigA", "c1"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigB", "c2"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigA", "c3"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigB", "c4"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigA", "c5"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigB", "c6"));

        List<StuckLedger.AlternatingPair> pairs = ledger.alternatingPairs();
        assertEquals(1, pairs.size());
        assertEquals("sigA", pairs.get(0).first());
        assertEquals("sigB", pairs.get(0).second());
    }

    @Test
    void pairsAreOrderedByFirstSightingOfFirstThenSecond() {
        StuckLedger ledger = new StuckLedger();
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigX", "c1"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigY", "c2"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigX", "c3"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigY", "c4"));

        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigP", "c5"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigQ", "c6"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigP", "c7"));
        ledger.observe(obs(StuckObservation.Source.CLOSURE_REFUSAL, "sigQ", "c8"));

        List<StuckLedger.AlternatingPair> pairs = ledger.alternatingPairs();
        assertEquals(2, pairs.size());
        assertEquals("sigX", pairs.get(0).first());
        assertEquals("sigY", pairs.get(0).second());
        assertEquals("sigP", pairs.get(1).first());
        assertEquals("sigQ", pairs.get(1).second());
    }
}