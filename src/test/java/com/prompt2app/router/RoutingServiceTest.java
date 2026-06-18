package com.prompt2app.router;

import com.prompt2app.app.model.enums.CodeGenTypeEnum;
import com.prompt2app.router.RoutingDecision.Layer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RoutingService} 编排单测。
 *
 * <p>使用 mock {@link AiCodeGenTypeRoutingServiceFactory} 避免实际 LLM 调用。
 * 真实 RuleRouter 直接注入（pure，无 IO）。
 */
class RoutingServiceTest {

    private RuleRouter ruleRouter;
    private AiCodeGenTypeRoutingServiceFactory llmFactory;
    private AiCodeGenTypeRoutingService llmService;
    private RoutingService routingService;

    @BeforeEach
    void setUp() {
        ruleRouter = new RuleRouter();
        llmFactory = mock(AiCodeGenTypeRoutingServiceFactory.class);
        llmService = mock(AiCodeGenTypeRoutingService.class);
        when(llmFactory.createAiCodeGenTypeRoutingService()).thenReturn(llmService);

        routingService = new RoutingService();
        ReflectionTestUtils.setField(routingService, "ruleRouter", ruleRouter);
        ReflectionTestUtils.setField(routingService, "llmFactory", llmFactory);
    }

    @Test
    void rule_hit_returns_directly_without_calling_llm() {
        RoutingDecision decision = routingService.route("做一个 Vue 看板应用");
        assertEquals(CodeGenTypeEnum.VUE_PROJECT, decision.getStrategy());
        assertEquals(Layer.RULE_KEYWORD, decision.getLayer());
        assertEquals(1.0, decision.getConfidence());
        // 不应触发 LLM
        org.mockito.Mockito.verify(llmFactory, org.mockito.Mockito.never())
                .createAiCodeGenTypeRoutingService();
    }

    @Test
    void rule_miss_falls_back_to_llm() {
        // 长歧义 prompt → Layer 1 miss → LLM 走 stub 返回 MULTI_FILE
        when(llmService.routeCodeGenType(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(CodeGenTypeEnum.MULTI_FILE);

        String ambiguous = "我们公司是一家做企业级数据治理的厂商，目标客户是金融、能源、政务行业。"
                + "希望你帮我们准备一份产品介绍材料，能展示我们的核心模块、典型客户案例、合作伙伴。"
                + "整体风格要专业稳重，体现技术实力。";
        RoutingDecision decision = routingService.route(ambiguous);
        assertEquals(CodeGenTypeEnum.MULTI_FILE, decision.getStrategy());
        assertEquals(Layer.LLM_FALLBACK, decision.getLayer());
        assertEquals(0.6, decision.getConfidence());
    }

    @Test
    void llm_exception_falls_back_to_html() {
        when(llmService.routeCodeGenType(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RuntimeException("simulated LLM down"));

        String ambiguous = "我们公司是一家做企业级数据治理的厂商，目标客户是金融、能源、政务行业。"
                + "希望你帮我们准备一份产品介绍材料，能展示我们的核心模块、典型客户案例、合作伙伴。"
                + "整体风格要专业稳重，体现技术实力。";
        RoutingDecision decision = routingService.route(ambiguous);
        assertEquals(CodeGenTypeEnum.HTML, decision.getStrategy());
        assertEquals(Layer.LLM_ERROR_FALLBACK, decision.getLayer());
        assertEquals(0.3, decision.getConfidence());
    }

    @Test
    void llm_returning_null_treated_as_error_fallback() {
        when(llmService.routeCodeGenType(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(null);

        String ambiguous = "我们公司是一家做企业级数据治理的厂商，目标客户是金融、能源、政务行业。"
                + "希望你帮我们准备一份产品介绍材料，能展示我们的核心模块、典型客户案例、合作伙伴。"
                + "整体风格要专业稳重，体现技术实力。";
        RoutingDecision decision = routingService.route(ambiguous);
        assertEquals(CodeGenTypeEnum.HTML, decision.getStrategy());
        assertEquals(Layer.LLM_ERROR_FALLBACK, decision.getLayer());
    }

    @Test
    void all_decisions_carry_duration_and_user_prompt() {
        String prompt = "Vue 看板应用";
        RoutingDecision decision = routingService.route(prompt);
        assertEquals(prompt, decision.getUserPrompt());
        assertNotNull(decision.getReason());
        // duration may be 0 for very fast paths but never negative
        org.junit.jupiter.api.Assertions.assertTrue(decision.getDurationMs() >= 0);
    }

    @Test
    void route_code_gen_type_convenience_returns_just_strategy() {
        CodeGenTypeEnum strategy = routingService.routeCodeGenType("做一个看板应用");
        assertEquals(CodeGenTypeEnum.VUE_PROJECT, strategy);
    }
}
