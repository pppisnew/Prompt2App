package com.prompt2app.eval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LlmJudgeScorer} 解析逻辑单测（不调真 LLM）。
 *
 * <p>真 LLM 评分稳定性需要"live mode"——通过 EVAL_LIVE=1 本地手动跑，不进 CI。
 */
class LlmJudgeScorerParseTest {

    @Test
    void parse_pure_json_returns_score() {
        LlmJudgeScorer.Parsed p = LlmJudgeScorer.parse("{\"score\": 75, \"reasoning\": \"good layout\"}");
        assertEquals(75.0, p.score);
        assertEquals("good layout", p.reasoning);
    }

    @Test
    void parse_json_with_code_fence() {
        LlmJudgeScorer.Parsed p = LlmJudgeScorer.parse(
                "```json\n{\"score\": 82, \"reasoning\": \"clear\"}\n```");
        assertEquals(82.0, p.score);
    }

    @Test
    void parse_json_with_extra_whitespace() {
        LlmJudgeScorer.Parsed p = LlmJudgeScorer.parse("\n\n  {\"score\": 60, \"reasoning\": \"x\"}  \n");
        assertEquals(60.0, p.score);
    }

    @Test
    void parse_falls_back_to_regex_when_json_malformed() {
        LlmJudgeScorer.Parsed p = LlmJudgeScorer.parse("Score: 88 reasoning blah \"score\": 88");
        assertEquals(88.0, p.score);
        assertNotNull(p.reasoning);
    }

    @Test
    void parse_empty_returns_fallback() {
        LlmJudgeScorer.Parsed p = LlmJudgeScorer.parse("");
        assertEquals(50.0, p.score);
        assertEquals("empty response", p.reasoning);
    }

    @Test
    void parse_unparseable_returns_fallback_with_truncated_response() {
        LlmJudgeScorer.Parsed p = LlmJudgeScorer.parse("just plain prose, no score");
        assertEquals(50.0, p.score);
        assertTrue(p.reasoning.contains("could not parse"));
    }

    @Test
    void parse_out_of_range_score_falls_back() {
        // 200 不在 0-100 范围内 → fallback
        LlmJudgeScorer.Parsed p = LlmJudgeScorer.parse("{\"score\": 200, \"reasoning\": \"x\"}");
        assertEquals(50.0, p.score);
    }

    @Test
    void scorer_returns_fallback_when_judge_service_is_null() {
        LlmJudgeScorer scorer = new LlmJudgeScorer(null);
        EvalCase c = new EvalCase();
        c.setId("test");
        AgentInvoker.InvocationResult inv = AgentInvoker.InvocationResult.builder()
                .mergedOutput("output").fileCount(1).durationMs(100L).invoked(true).note(null).build();
        Scorer.ScoreContribution result = scorer.evaluate(c, inv);
        assertEquals(50.0, result.getScore());
        assertEquals("llm-judge", result.getDimension());
        assertEquals(false, result.isVeto());  // judge is non-vetoing
    }

    @Test
    void scorer_returns_zero_for_uninvoked() {
        LlmJudgeScorer scorer = new LlmJudgeScorer(null);
        EvalCase c = new EvalCase();
        c.setId("test");
        AgentInvoker.InvocationResult inv = AgentInvoker.InvocationResult.builder()
                .mergedOutput("").fileCount(0).durationMs(0L).invoked(false).note("stub").build();
        Scorer.ScoreContribution result = scorer.evaluate(c, inv);
        assertEquals(0.0, result.getScore());
        assertEquals(false, result.isVeto());  // still non-vetoing in stub mode
    }

    @Test
    void scorer_handles_judge_exception_gracefully() {
        LlmJudgeService throwing = (userPrompt, output, hints) -> {
            throw new RuntimeException("LLM down");
        };
        LlmJudgeScorer scorer = new LlmJudgeScorer(throwing);
        EvalCase c = new EvalCase();
        c.setId("test");
        AgentInvoker.InvocationResult inv = AgentInvoker.InvocationResult.builder()
                .mergedOutput("output").fileCount(1).durationMs(100L).invoked(true).note(null).build();
        Scorer.ScoreContribution result = scorer.evaluate(c, inv);
        assertEquals(50.0, result.getScore());
        assertEquals(false, result.isVeto());  // exception doesn't veto
        assertTrue(result.getDetail().contains("LLM judge error"));
    }
}
