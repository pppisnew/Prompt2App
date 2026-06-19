package com.prompt2app.eval;

import com.prompt2app.eval.Scorer.ScoreContribution;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CompositeScorer} 单测。覆盖否决合分 / LLM-Judge 主分 / 全 OK 取均值。
 */
class CompositeScorerTest {

    @Test
    void any_veto_yields_zero_final_score() {
        CompositeScorer composite = new CompositeScorer(List.of(
                fixed("rubric", 80, false),
                fixed("render", 0, true),       // veto
                fixed("llm-judge", 75, false)
        ));
        EvalCase c = anyCase();
        AgentInvoker.InvocationResult inv = invoked();
        CompositeScorer.CaseFinalScore r = composite.score(c, inv);
        assertEquals(0.0, r.getFinalScore());
        assertTrue(r.isVeto());
        assertTrue(r.getSummary().contains("VETO by render"));
    }

    @Test
    void all_ok_with_judge_returns_judge_score() {
        CompositeScorer composite = new CompositeScorer(List.of(
                fixed("rubric", 90, false),
                fixed("render", 100, false),
                fixed("llm-judge", 78, false)
        ));
        CompositeScorer.CaseFinalScore r = composite.score(anyCase(), invoked());
        assertEquals(78.0, r.getFinalScore());     // takes judge score, not avg
        assertFalse(r.isVeto());
    }

    @Test
    void all_ok_without_judge_returns_average() {
        CompositeScorer composite = new CompositeScorer(List.of(
                fixed("rubric", 90, false),
                fixed("render", 80, false)
                // no judge dimension
        ));
        CompositeScorer.CaseFinalScore r = composite.score(anyCase(), invoked());
        assertEquals(85.0, r.getFinalScore(), 0.0001);
        assertFalse(r.isVeto());
    }

    @Test
    void contributions_preserved_in_result() {
        CompositeScorer composite = new CompositeScorer(List.of(
                fixed("rubric", 90, false),
                fixed("render", 100, false)
        ));
        CompositeScorer.CaseFinalScore r = composite.score(anyCase(), invoked());
        assertEquals(2, r.getContributions().size());
        assertEquals("rubric", r.getContributions().get(0).getDimension());
        assertEquals("render", r.getContributions().get(1).getDimension());
    }

    @Test
    void empty_scorer_list_throws() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new CompositeScorer(List.of()));
    }

    @Test
    void real_three_dim_combination_works_with_judge_and_real_rubric() {
        // 综合一个真实场景：rubric 通过 + render 通过 + judge 给 65
        CompositeScorer composite = new CompositeScorer(Arrays.asList(
                new RubricScorer(),
                new RenderScorer(),
                fixed("llm-judge", 65, false)
        ));
        EvalCase c = htmlCaseWithRubric();
        String html = "<html><body><h1>张三</h1><p>Java 后端工程师，3 年经验，会 Spring Boot、MySQL、Redis。"
                + "邮箱 zhangsan@example.com 电话 13800138000。"
                + "viewport content text just to push the body length over 100 chars threshold here.</p></body></html>";
        AgentInvoker.InvocationResult inv = AgentInvoker.InvocationResult.builder()
                .mergedOutput(html).fileCount(1).durationMs(100L).invoked(true).note(null).build();
        CompositeScorer.CaseFinalScore r = composite.score(c, inv);
        assertFalse(r.isVeto(), r.getSummary());
        assertEquals(65.0, r.getFinalScore());
    }

    // ---- helpers ----

    private static Scorer fixed(String dim, double score, boolean veto) {
        return new Scorer() {
            @Override public String name() { return dim; }
            @Override public ScoreContribution evaluate(EvalCase c, AgentInvoker.InvocationResult i) {
                return ScoreContribution.builder()
                        .dimension(dim).score(score).veto(veto).detail("fixed").build();
            }
        };
    }

    private static EvalCase anyCase() {
        EvalCase c = new EvalCase();
        c.setId("test");
        c.setExpectedStrategy("HTML");
        c.setRubric(new EvalCase.Rubric());
        return c;
    }

    private static EvalCase htmlCaseWithRubric() {
        EvalCase c = anyCase();
        EvalCase.Rubric r = new EvalCase.Rubric();
        r.setMustContain(List.of("张三", "Java"));
        r.setMustNotContain(List.of("lorem ipsum"));
        c.setRubric(r);
        return c;
    }

    private static AgentInvoker.InvocationResult invoked() {
        return AgentInvoker.InvocationResult.builder()
                .mergedOutput("").fileCount(0).durationMs(0L).invoked(true).note(null).build();
    }
}
