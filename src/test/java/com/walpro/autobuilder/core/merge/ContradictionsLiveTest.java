package com.walpro.autobuilder.core.merge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walpro.autobuilder.client.LLMClient;
import com.walpro.autobuilder.client.LlmCircuitBreaker;
import com.walpro.autobuilder.goal.purpose.InputEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * T4-EY step 2: the pair check replayed on a recorded landing, by the one code path ({@link ProjectInputService#pairsOf}
 * and {@link ProjectInputService#check}) with the run's model settings (local model, reasoning none; the screening
 * list question at "low"). Off unless {@code -Dt4ey.ledger=<meta/inputs.json>}; {@code -Dt4ey.spec=<seed.txt>} is the
 * receiving project's specification the landing compared against. Asserts nothing: the result is read against the
 * rule fixed on the ticket before the replay.
 */
@EnabledIfSystemProperty(named = "t4ey.ledger", matches = ".+")
class ContradictionsLiveTest {

    @Test
    void replaysThePairCheckOnARecordedLanding() throws Exception {
        LLMClient llm = new LLMClient(new LlmCircuitBreaker());
        ReflectionTestUtils.setField(llm, "apiUrl", System.getProperty("t4ey.url", "http://localhost:11434/v1/chat/completions"));
        ReflectionTestUtils.setField(llm, "apiKey", "local");
        ReflectionTestUtils.setField(llm, "model", System.getProperty("t4ey.model", "qwen3.8"));
        ReflectionTestUtils.setField(llm, "readTimeoutMs", 300_000);
        ReflectionTestUtils.setField(llm, "reasoningEffort", System.getProperty("t4ey.effort", "none"));
        ReflectionTestUtils.setField(llm, "readTimeoutEscalationFactor", 1.0);
        ReflectionTestUtils.setField(llm, "retryMaxAttempts", 1);
        ReflectionTestUtils.setField(llm, "responseReserveTokens", 4096);
        ReflectionTestUtils.setField(llm, "contextWindowTokens", 32768);
        Method init = LLMClient.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(llm);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        JsonNode ledger = mapper.readTree(Path.of(System.getProperty("t4ey.ledger")).toFile());
        List<InputEvent> events = new ArrayList<>();
        ledger.get("events").forEach(e -> events.add(mapper.convertValue(e, InputEvent.class)));
        String spec = Files.readString(Path.of(System.getProperty("t4ey.spec"))).strip();
        String inputId = events.stream().filter(e -> InputEvent.CONFLICT.equals(e.kind())).findFirst().orElseThrow().inputId();

        long t0 = System.currentTimeMillis();
        var pairs = ProjectInputService.pairsOf(events, id -> false, inputId);
        var result = ProjectInputService.check(inputId, pairs, new InputComparison(llm), spec, new HashMap<>());
        long seconds = (System.currentTimeMillis() - t0) / 1000;

        System.out.printf("T4-EY replay: %d pair(s), %d flagged, %d could not be checked, %ds%n",
                result.asked(), result.flagged().size(), result.unchecked().size(), seconds);
        for (var p : result.flagged()) {
            System.out.printf("T4-EY flagged %-11s %s  <->  %s%n    %s%n", p.verdict(), p.first().subject(),
                    p.second().subject(), p.reason());
        }
        for (var p : result.unchecked()) {
            System.out.printf("T4-EY unchecked %s  <->  %s%n", p.first().subject(), p.second().subject());
        }
    }
}
