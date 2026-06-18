package com.prompt2app.eval;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for {@link RubricScorer} — no I/O, deterministic.
 */
class RubricScorerTest {

    private final RubricScorer scorer = new RubricScorer();

    @Test
    void uninvoked_result_yields_zero_score_and_invoked_false() {
        EvalCase c = simpleCase();
        AgentInvoker.InvocationResult notInvoked = AgentInvoker.InvocationResult.builder()
                .mergedOutput("").fileCount(0).durationMs(0L).invoked(false).note("stub").build();

        RubricScorer.CaseScore s = scorer.score(c, notInvoked);
        assertFalse(s.isInvoked());
        assertEquals(0.0, s.getDeterministicScore(), 0.0001);
        assertEquals(0, s.getMustContainHits());
        assertEquals(2, s.getMustContainTotal());
    }

    @Test
    void all_must_contain_hits_no_anti_pattern_yields_full_score() {
        EvalCase c = simpleCase();
        AgentInvoker.InvocationResult inv = invocation("张三 java spring boot");
        RubricScorer.CaseScore s = scorer.score(c, inv);
        assertTrue(s.isInvoked());
        assertEquals(2, s.getMustContainHits());
        assertEquals(0, s.getMustNotContainHits());
        assertEquals(100.0, s.getDeterministicScore(), 0.0001);
    }

    @Test
    void anti_pattern_match_subtracts_from_score() {
        EvalCase c = simpleCase();
        AgentInvoker.InvocationResult inv = invocation("张三 Java some lorem ipsum text");
        RubricScorer.CaseScore s = scorer.score(c, inv);
        assertEquals(2, s.getMustContainHits());
        assertEquals(1, s.getMustNotContainHits());
        // containRatio=1.0, notContainPenalty=1/2=0.5 → raw=0.5 → 50.0
        assertEquals(50.0, s.getDeterministicScore(), 0.0001);
    }

    @Test
    void min_files_violation_applies_penalty() {
        EvalCase c = simpleCase();
        c.getRubric().setMinFiles(3);
        AgentInvoker.InvocationResult inv = AgentInvoker.InvocationResult.builder()
                .mergedOutput("张三 Java")
                .fileCount(1)              // < minFiles=3
                .durationMs(100L).invoked(true).note(null).build();
        RubricScorer.CaseScore s = scorer.score(c, inv);
        assertFalse(s.isMinFilesOk());
        // containRatio=1.0, notContainPenalty=0, minFilesPenalty=0.3 → raw=0.7 → 70.0
        assertEquals(70.0, s.getDeterministicScore(), 0.0001);
    }

    @Test
    void case_insensitive_matching() {
        EvalCase c = simpleCase();
        AgentInvoker.InvocationResult inv = invocation("ZHANGSAN JAVA");
        RubricScorer.CaseScore s = scorer.score(c, inv);
        // "张三" won't match "ZHANGSAN" (different chars), but "Java" → "JAVA" should via lowercase
        assertEquals(1, s.getMustContainHits());
    }

    private EvalCase simpleCase() {
        EvalCase c = new EvalCase();
        c.setId("test");
        c.setExpectedStrategy("HTML");
        EvalCase.Rubric r = new EvalCase.Rubric();
        r.setMustContain(Arrays.asList("张三", "Java"));
        r.setMustNotContain(Arrays.asList("lorem ipsum", "TODO"));
        c.setRubric(r);
        return c;
    }

    private AgentInvoker.InvocationResult invocation(String output) {
        return AgentInvoker.InvocationResult.builder()
                .mergedOutput(output).fileCount(1).durationMs(100L)
                .invoked(true).note(null).build();
    }
}
