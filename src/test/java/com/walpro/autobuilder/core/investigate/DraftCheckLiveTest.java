package com.walpro.autobuilder.core.investigate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.walpro.autobuilder.client.LLMClient;
import com.walpro.autobuilder.client.LlmCircuitBreaker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T4-EZ: a draft ticket checked by READING -- the loop's own investigator probes the run's logs and the project's
 * files and answers KEEP or DROP. Off unless {@code -Dt4ez.check=<corpus.json>} (written by
 * {@code scripts/t4ez_draft_tickets.py --prepare-reading}); run settings as the loop's (local model, temperature 0,
 * reasoning none). Asserts nothing: every answer and every probe go to {@code <corpus>.results.json} for scoring.
 */
@EnabledIfSystemProperty(named = "t4ez.check", matches = ".+")
class DraftCheckLiveTest {

    @Test
    void checksEachDraftByReadingTheRun() throws Exception {
        LLMClient llm = new LLMClient(new LlmCircuitBreaker());
        ReflectionTestUtils.setField(llm, "apiUrl", System.getProperty("t4ez.url", "http://localhost:11434/v1/chat/completions"));
        ReflectionTestUtils.setField(llm, "apiKey", "local");
        ReflectionTestUtils.setField(llm, "model", System.getProperty("t4ez.model", "qwen3.8"));
        ReflectionTestUtils.setField(llm, "readTimeoutMs", 300_000);
        ReflectionTestUtils.setField(llm, "reasoningEffort", System.getProperty("t4ez.effort", "none"));
        ReflectionTestUtils.setField(llm, "readTimeoutEscalationFactor", 1.0);
        ReflectionTestUtils.setField(llm, "retryMaxAttempts", 1);
        ReflectionTestUtils.setField(llm, "responseReserveTokens", 4096);
        ReflectionTestUtils.setField(llm, "contextWindowTokens", 32768);
        Method init = LLMClient.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(llm);

        Path corpusPath = Path.of(System.getProperty("t4ez.check"));
        int probes = Integer.getInteger("t4ez.probes", 12);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode corpus = mapper.readTree(corpusPath.toFile());
        ArrayNode out = mapper.createArrayNode();
        LogInvestigator investigator = new LogInvestigator(llm);
        for (JsonNode q : corpus) {
            Map<String, List<String>> logs = new LinkedHashMap<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = q.get("logs").fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                logs.put(e.getKey(), Files.readAllLines(Path.of(e.getValue().asText()), StandardCharsets.UTF_8));
            }
            Path tree = Path.of(q.get("tree").asText());
            long t0 = System.currentTimeMillis();
            LogInvestigator.Investigation inv = investigator.investigate(q.get("question").asText(), logs, tree, probes, null);
            String verdict = !inv.answered() ? "DROP (no answer)"
                    : inv.answer().stripLeading().toUpperCase().startsWith("KEEP") ? "KEEP" : "DROP";
            System.out.printf("T4-EZ check %-6s %-16s probes=%d  %ds%n", q.get("id").asText(), verdict,
                    inv.steps().size(), (System.currentTimeMillis() - t0) / 1000);
            var rec = out.addObject();
            rec.put("id", q.get("id").asText());
            rec.put("verdict", verdict);
            rec.put("answer", inv.answer());
            var steps = rec.putArray("steps");
            inv.steps().forEach(s -> steps.addObject().put("probe", s.probe()).put("result", s.result()));
            mapper.writerWithDefaultPrettyPrinter().writeValue(
                    corpusPath.resolveSibling(corpusPath.getFileName() + ".results.json").toFile(), out);
        }
    }
}
