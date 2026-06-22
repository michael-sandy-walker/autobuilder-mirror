package com.walpro.autobuilder.protocol.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import com.walpro.autobuilder.agent.evolution.model.EvolutionOutcome;
import com.walpro.autobuilder.client.LLMClient;
import com.walpro.autobuilder.client.LlmCircuitBreaker;
import com.walpro.autobuilder.core.lifecycle.CycleContextData;
import com.walpro.autobuilder.client.dto.ChatResponse;
import com.walpro.autobuilder.client.dto.Message;
import com.walpro.autobuilder.protocol.PromptTokenBudget;
import com.walpro.autobuilder.testsupport.PromptSalienceFixtures;
import com.walpro.autobuilder.testsupport.PromptSalienceFixtures.Case;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Prompt-salience adherence eval (REAL local model, opt-in). Directly measures the proximal question
 * the 75-minute autonomous loop measures slowly and noisily: <b>does capping the previous-response
 * echo make the local model honor the diagnostic block in a single call?</b>
 *
 * <p>Method (replay, not loop): for each {@link PromptSalienceFixtures} case — a type that exists on
 * disk under a canonical name, an "EXISTING PROJECT TYPES" diagnostic listing it, and a bloated echo
 * that refers to the same concept by a wrong synonym — render the genuine evolution prompt twice,
 * differing ONLY in {@code compactPreviousResponse} (off/on), make one model call each, and score
 * whether the response reuses the canonical name (adherence) rather than the synonym. The naming
 * diagnostic is ON in both conditions; the only variable is the echo cap. Paired, n = cases × trials.
 *
 * <p>This isolates the mechanism the loop cannot: it does not prove a full run fulfills more often
 * (distal), it proves whether the model attends to the diagnostic once the echo no longer drowns it
 * (proximal) — exactly the unknown the naming A/B left open (the block fired and was ignored).
 *
 * <p>Opt-in by contract: needs the local model up. Enable with {@code PROMPT_COMPACTION_EVAL=1};
 * endpoint via {@code LLM_API_URL}/{@code LLM_API_MODEL}/{@code OPENAI_API_KEY} (defaults mirror the
 * local Ollama config). Prints an adherence report; asserts only that the endpoint produced output
 * (a down endpoint fails loudly), never a hard score — the number is a measurement to track.
 */
@Tag("prompt-compaction-eval")
@EnabledIfEnvironmentVariable(named = "PROMPT_COMPACTION_EVAL", matches = "1")
class PromptCompactionAdherenceEvalTest {

    /** Trials per case per condition (temp 0.0, but local models are not perfectly deterministic). */
    private static final int TRIALS = 3;

    @Test
    void measureDiagnosticAdherenceWithAndWithoutEchoCompaction() {
        List<Case> cases = PromptSalienceFixtures.all();
        LLMClient client = realClient();

        Arm echoFull = new Arm("ECHO FULL (compaction OFF)");
        Arm echoCapped = new Arm("ECHO CAPPED (compaction ON)");

        StringBuilder detail = new StringBuilder();
        for (Case c : cases) {
            CycleContextData ctx = contextFor(c);
            String system = EvolutionProtocolContract.getSystemPrompt();
            // Naming diagnostic ON in both; ONLY the echo cap differs.
            String promptFull = EvolutionProtocolContract.getUserPrompt(
                    ctx, null, false, PromptTokenBudget.DEFAULT_PROMPT_HEADROOM_TOKENS, true, false);
            String promptCapped = EvolutionProtocolContract.getUserPrompt(
                    ctx, null, false, PromptTokenBudget.DEFAULT_PROMPT_HEADROOM_TOKENS, true, true);

            for (int t = 0; t < TRIALS; t++) {
                detail.append(scoreOne(client, c, "full", system, promptFull, echoFull));
                detail.append(scoreOne(client, c, "capped", system, promptCapped, echoCapped));
            }
        }

        System.out.println("\n========== prompt-compaction adherence (model="
                + env("LLM_API_MODEL", "qwen3-coder:30b") + ", " + TRIALS + " trials/case) ==========");
        System.out.println("cases=" + cases.size() + "  trials/case/arm=" + TRIALS);
        System.out.print(detail);
        System.out.println(echoFull.report());
        System.out.println(echoCapped.report());
        System.out.printf("ADHERENCE DELTA (capped - full) = %+.2f  (positive ⇒ capping the echo lifts diagnostic adherence)%n",
                echoCapped.adherenceRate() - echoFull.adherenceRate());

        // Measurement, not a gate: only fail if the endpoint produced nothing parseable at all.
        assertThat(echoFull.responses + echoCapped.responses)
                .as("the local model produced at least one response — is the endpoint up?")
                .isGreaterThan(0);
    }

    /** One model call: scores canonical-reuse vs synonym-only and appends a detail line. */
    private static String scoreOne(LLMClient client, Case c, String armLabel, String system, String user, Arm arm) {
        String resp = call(client, system, user);
        if (resp == null || resp.isBlank()) {
            return "  " + c.name() + " [" + armLabel + "]: NO RESPONSE\n";
        }
        arm.responses++;
        boolean usesCanonical = resp.contains(c.canonicalType());
        boolean usesSynonymOnly = !usesCanonical && resp.contains(c.synonym());
        if (usesCanonical) {
            arm.adhered++;
            return "  " + c.name() + " [" + armLabel + "]: ✓ used canonical " + c.canonicalType() + "\n";
        }
        return "  " + c.name() + " [" + armLabel + "]: ✗ "
                + (usesSynonymOnly ? "minted synonym " + c.synonym() : "neither name present") + "\n";
    }

    private static CycleContextData contextFor(Case c) {
        EvolutionOutcome last = new EvolutionOutcome(
                List.of(), List.of(), "previous attempt", c.previousRawResponse(), false);
        return CycleContextData.builder()
                .cycleName("eval-" + c.name())
                .currentGoalDescription(c.subjectGoal())
                .currentSubGoalDescription(c.subGoal())
                .currentSubGoalCriteria(c.criteria())
                .previousOutput(c.previousOutput())
                .lastEvolutionOutcome(last)
                .build();
    }

    private static String call(LLMClient client, String system, String user) {
        try {
            List<Message> messages = new ArrayList<>();
            messages.add(new Message("system", system));
            messages.add(new Message("user", user));
            ChatResponse body = client.ask(messages).getBody();
            return body == null ? null : body.getFirstContent();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Adherence tally for one condition. */
    private static final class Arm {
        final String label;
        int responses = 0;
        int adhered = 0;
        Arm(String label) { this.label = label; }
        double adherenceRate() { return responses == 0 ? 0.0 : (double) adhered / responses; }
        String report() {
            return String.format("--- %s ---%n  responses=%d  adhered(canonical reused)=%d  adherence=%.2f%n",
                    label, responses, adhered, adherenceRate());
        }
    }

    private static LLMClient realClient() {
        LLMClient client = new LLMClient(new LlmCircuitBreaker());
        ReflectionTestUtils.setField(client, "apiUrl",
                env("LLM_API_URL", "http://localhost:11434/v1/chat/completions"));
        ReflectionTestUtils.setField(client, "apiKey", env("OPENAI_API_KEY", "local-only-no-key"));
        ReflectionTestUtils.setField(client, "model", env("LLM_API_MODEL", "qwen3-coder:30b"));
        ReflectionTestUtils.setField(client, "readTimeoutMs", 120_000);
        ReflectionTestUtils.setField(client, "retryMaxAttempts", 1);
        ReflectionTestUtils.setField(client, "retryDelayMs", 1_000L);
        ReflectionTestUtils.invokeMethod(client, "init");
        return client;
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v != null && !v.isBlank() ? v : fallback;
    }
}
