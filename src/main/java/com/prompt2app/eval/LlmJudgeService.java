package com.prompt2app.eval;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * LLM-as-Judge AiService 接口（LangChain4j AiServices 框架自动织入实现）。
 *
 * <p>使用项目现有的 routingChatModel（小模型）作为 judge model，避免引入新依赖。
 * 在 {@code config/AiServiceConfig} 或类似处通过 {@code AiServices.builder} 织入。
 *
 * <p>详见 ADR-0005 §实施细节 §LlmJudgeScorer。
 */
public interface LlmJudgeService {

    /**
     * 根据 user prompt + 生成的产物 + rubric 提示，让 LLM 给一个 0-100 分的主观评价。
     *
     * @param userPrompt       原始用户需求
     * @param generatedOutput  AI 生成的产物（merged code text）
     * @param rubricHints      评分维度提示（来自 case rubric.llm_judge_dimensions）
     * @return JSON-friendly 文本：{@code {"score": 75, "reasoning": "..."}}
     */
    @SystemMessage(fromResource = "prompt/eval-judge-system-prompt.txt")
    @UserMessage("""
            ## 用户需求
            {{userPrompt}}

            ## AI 生成的产物
            {{generatedOutput}}

            ## 评分维度提示
            {{rubricHints}}

            请按系统消息中的评分锚点给出分数。
            """)
    String judge(@V("userPrompt") String userPrompt,
                 @V("generatedOutput") String generatedOutput,
                 @V("rubricHints") String rubricHints);
}
