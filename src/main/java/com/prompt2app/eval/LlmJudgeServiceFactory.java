package com.prompt2app.eval;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM-as-Judge 服务工厂（ADR-0005 第三维评分）。
 *
 * <p>用 {@code routingChatModelPrototype}（小模型，prototype scope）作为 Judge model，
 * 避免引入新依赖 + 省 token。Judge 维度不 veto（软评分），与 Rubric/Render 硬门控互补。
 *
 * <p>参照 {@code CodeQualityCheckServiceFactory} 模式。
 */
@Slf4j
@Configuration
public class LlmJudgeServiceFactory {

    @Resource(name = "routingChatModelPrototype")
    private ChatModel chatModel;

    /**
     * 织入 {@link LlmJudgeService}（LangChain4j AiServices 自动实现接口）。
     * System message 来自 {@code prompt/eval-judge-system-prompt.txt}。
     */
    @Bean
    public LlmJudgeService llmJudgeService() {
        log.info("[LlmJudge] 织入 LlmJudgeService (model=routingChatModelPrototype, 小模型省 token)");
        return AiServices.builder(LlmJudgeService.class)
                .chatModel(chatModel)
                .build();
    }
}
