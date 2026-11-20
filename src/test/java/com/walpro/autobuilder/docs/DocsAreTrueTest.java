package com.walpro.autobuilder.docs;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T5-BS: the documentation names only what exists. Runs {@code scripts/doc_check.py} -- every class, configuration
 * key, script and endpoint that docs/technical.md and docs/functional.md name -- so a rename that orphans the
 * documentation fails the build instead of rotting it, as README.md and CLAUDE.md's "scheduled every 60 s" did.
 */
class DocsAreTrueTest {

    @Test
    void theDocumentsNameOnlyWhatExists() throws Exception {
        runCheck("scripts/doc_check.py");
    }

    /** The refactoring notes name only classes that exist, so a rename or deletion cannot orphan an entry. */
    @Test
    void theRefactoringNotesNameOnlyClassesThatExist() throws Exception {
        runCheck("scripts/refactoring_notes_check.py");
    }

    private static void runCheck(String path) throws Exception {
        Path script = Path.of(path);
        Assumptions.assumeTrue(Files.exists(script), "run from the repository root");
        Process p;
        try {
            p = new ProcessBuilder("python3", script.toString()).redirectErrorStream(true).start();
        } catch (java.io.IOException noPython) {
            Assumptions.abort("python3 is not available");
            return;
        }
        boolean done = p.waitFor(60, TimeUnit.SECONDS);
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(done).as(path + " finished").isTrue();
        assertThat(p.exitValue()).as(out).isZero();
    }
}
