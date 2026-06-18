package com.prompt2app.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link EvalCaseLoader} can parse all 25 v0 cases and that
 * the strategy distribution matches the Phase 0 DoD (HTML 7 / MultiFile 8 / Vue 10).
 *
 * <p>Pure unit test — no Spring context, no LLM, no filesystem writes.
 */
class EvalCaseLoaderTest {

    private static final Path CASES_DIR = Paths.get("eval/cases");

    private final EvalCaseLoader loader = new EvalCaseLoader();

    @Test
    void loads_all_25_cases_with_dod_distribution() {
        List<EvalCase> cases = loader.loadAll(CASES_DIR);
        assertEquals(25, cases.size(), "should have exactly 25 cases at v0");

        Map<String, Long> byStrategy = new TreeMap<>();
        for (EvalCase c : cases) {
            byStrategy.merge(c.getExpectedStrategy(), 1L, Long::sum);
        }
        assertEquals(7L,  byStrategy.get("HTML"),        "HTML should be 7");
        assertEquals(8L,  byStrategy.get("MULTI_FILE"),  "MULTI_FILE should be 8");
        assertEquals(10L, byStrategy.get("VUE_PROJECT"), "VUE_PROJECT should be 10");
    }

    @Test
    void each_case_has_required_rubric_fields() {
        List<EvalCase> cases = loader.loadAll(CASES_DIR);
        for (EvalCase c : cases) {
            assertNotNull(c.getId(),               () -> "id missing");
            assertNotNull(c.getTitle(),            () -> "title missing in " + c.getId());
            assertNotNull(c.getDifficulty(),       () -> "difficulty missing in " + c.getId());
            assertNotNull(c.getExpectedStrategy(), () -> "expectedStrategy missing in " + c.getId());
            assertNotNull(c.getPrompt(),           () -> "prompt missing in " + c.getId());
            assertNotNull(c.getRubric(),           () -> "rubric missing in " + c.getId());
            assertNotNull(c.getRubric().getMustContain(),
                    () -> "rubric.mustContain missing in " + c.getId());
            assertNotNull(c.getRubric().getMustNotContain(),
                    () -> "rubric.mustNotContain missing in " + c.getId());
            assertFalse(c.getRubric().getMustContain().isEmpty(),
                    () -> "rubric.mustContain empty in " + c.getId());
            assertTrue(c.getDifficulty().matches("easy|medium|hard"),
                    () -> "difficulty enum invalid in " + c.getId() + ": " + c.getDifficulty());
        }
    }

    @Test
    void min_files_present_for_non_html_cases() {
        List<EvalCase> cases = loader.loadAll(CASES_DIR);
        for (EvalCase c : cases) {
            if (!"HTML".equals(c.getExpectedStrategy())) {
                assertNotNull(c.getRubric().getMinFiles(),
                        () -> "minFiles required for " + c.getExpectedStrategy() + " case " + c.getId());
                assertTrue(c.getRubric().getMinFiles() >= 1,
                        () -> "minFiles must be >= 1 for case " + c.getId());
            }
        }
    }
}
