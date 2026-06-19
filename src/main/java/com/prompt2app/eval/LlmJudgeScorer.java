package com.prompt2app.eval;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Layer 3 · LLM-as-Judge 主观评分（{@link Scorer}）。
 *
 * <p>调用 {@link LlmJudgeService}（LangChain4j AiService）给 0-100 分。
 * <b>非否决项</b>：哪怕 LLM 评 50 分，最终分仍允许其它维度通过；
 * 但极低分（< 30）会触发 detail 警告，便于人工 review。
 *
 * <p>{@link LlmJudgeService} 缺失或调用异常时，本评分器返回中位分（50）+ 详细错误，
 * 而不是 veto，避免 LLM 抽风导致 CI 错误红灯。
 *
 * <p>详见 ADR-0005 §实施细节 §LlmJudgeScorer + §LLM-Judge 提示词稳定性。
 */
@Slf4j
public class LlmJudgeScorer implements Scorer {

    /** LLM 抽风时的兜底分。 */
    private static final double FALLBACK_SCORE = 50.0;

    /** "score": 75 之类的 JSON 字段抓取（兜底用）。 */
    private static final Pattern SCORE_REGEX = Pattern.compile(
            "\"score\"\\s*:\\s*(\\d{1,3})", Pattern.CASE_INSENSITIVE);

    private final LlmJudgeService judge;

    /** 生产构造（{@code @Resource} 注入实际 LangChain4j AiService）。 */
    public LlmJudgeScorer(LlmJudgeService judge) {
        this.judge = judge;
    }

    @Override
    public String name() {
        return "llm-judge";
    }

    @Override
    public ScoreContribution evaluate(EvalCase evalCase, AgentInvoker.InvocationResult invocation) {
        if (!invocation.isInvoked()) {
            return ScoreContribution.builder()
                    .dimension(name())
                    .score(0.0)
                    .veto(false)
                    .detail("not invoked (stub mode)")
                    .build();
        }
        if (judge == null) {
            return ScoreContribution.builder()
                    .dimension(name())
                    .score(FALLBACK_SCORE)
                    .veto(false)
                    .detail("LlmJudgeService not wired (config absent)")
                    .build();
        }

        String hints = formatHints(evalCase);
        String response;
        try {
            response = judge.judge(evalCase.getPrompt(),
                    invocation.getMergedOutput() == null ? "" : invocation.getMergedOutput(),
                    hints);
        } catch (Exception e) {
            log.warn("[LlmJudge] failed for case {}: {}", evalCase.getId(), e.getMessage());
            return ScoreContribution.builder()
                    .dimension(name())
                    .score(FALLBACK_SCORE)
                    .veto(false)
                    .detail("LLM judge error: " + e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }

        Parsed parsed = parse(response);
        return ScoreContribution.builder()
                .dimension(name())
                .score(parsed.score)
                .veto(false)
                .detail(parsed.reasoning == null ? "(no reasoning)" : truncate(parsed.reasoning, 200))
                .build();
    }

    /** 把 case 的 llm_judge_dimensions 列表拼成 prompt-friendly 字符串。 */
    private static String formatHints(EvalCase c) {
        if (c.getRubric() == null) return "";
        List<String> dims = c.getRubric().getLlmJudgeDimensions();
        if (dims == null || dims.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("评分维度：");
        for (int i = 0; i < dims.size(); i++) {
            sb.append("\n  ").append(i + 1).append(". ").append(dims.get(i));
        }
        return sb.toString();
    }

    /**
     * 解析 LLM 返回。
     *
     * <p>优先按 JSON 解析（{@code {"score": 80, "reasoning": "..."}}）；
     * 解析失败则正则抓 score 字段；都拿不到 → fallback 分。
     */
    static Parsed parse(String response) {
        if (response == null || response.isBlank()) {
            return new Parsed(FALLBACK_SCORE, "empty response");
        }
        // 1) 尝试 JSON
        try {
            String trimmed = response.trim();
            // 去 ```json fences
            if (trimmed.startsWith("```")) {
                int firstNl = trimmed.indexOf('\n');
                if (firstNl > 0) trimmed = trimmed.substring(firstNl + 1);
                int lastFence = trimmed.lastIndexOf("```");
                if (lastFence > 0) trimmed = trimmed.substring(0, lastFence);
            }
            JSONObject obj = JSONUtil.parseObj(trimmed.trim());
            int s = obj.getInt("score", -1);
            String reasoning = obj.getStr("reasoning");
            if (s >= 0 && s <= 100) {
                return new Parsed(s, reasoning);
            }
        } catch (Exception ignore) {
            // 落到 regex
        }
        // 2) regex 兜底
        Matcher m = SCORE_REGEX.matcher(response);
        if (m.find()) {
            try {
                int s = Integer.parseInt(m.group(1));
                if (s >= 0 && s <= 100) {
                    return new Parsed(s, "(parsed via regex) " + truncate(response, 200));
                }
            } catch (NumberFormatException ignore) {
                // continue
            }
        }
        return new Parsed(FALLBACK_SCORE, "could not parse score from: " + truncate(response, 200));
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** 解析中间结果。包级可见，便于测试。 */
    static final class Parsed {
        final double score;
        final String reasoning;
        Parsed(double score, String reasoning) {
            this.score = score;
            this.reasoning = reasoning;
        }
    }
}
