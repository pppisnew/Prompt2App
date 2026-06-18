package com.prompt2app.router;

import com.prompt2app.app.model.enums.CodeGenTypeEnum;
import com.prompt2app.router.RoutingDecision.Layer;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layer 1 · {@link RuleRouter} 单测。覆盖每条规则 + 边界 + 未命中。
 */
class RuleRouterTest {

    private final RuleRouter router = new RuleRouter();

    @Test
    void empty_prompt_routes_to_html() {
        assertEquals(CodeGenTypeEnum.HTML,
                router.route("").orElseThrow().getStrategy());
        assertEquals(CodeGenTypeEnum.HTML,
                router.route("   ").orElseThrow().getStrategy());
        assertEquals(CodeGenTypeEnum.HTML,
                router.route(null).orElseThrow().getStrategy());
    }

    @Test
    void vue_keyword_routes_to_vue_project() {
        Optional<RoutingDecision> hit = router.route(
                "做一个待办事项 Vue 应用，含状态管理和 localStorage 持久化");
        assertTrue(hit.isPresent());
        assertEquals(CodeGenTypeEnum.VUE_PROJECT, hit.get().getStrategy());
        assertEquals(Layer.RULE_KEYWORD, hit.get().getLayer());
        assertEquals(1.0, hit.get().getConfidence());
        assertTrue(hit.get().getReason().contains("VUE"));
    }

    @Test
    void multifile_keyword_routes_to_multi_file() {
        Optional<RoutingDecision> hit = router.route(
                "给法式餐馆做 4 个页面的官网，含菜单、主厨、预订、关于");
        assertTrue(hit.isPresent());
        assertEquals(CodeGenTypeEnum.MULTI_FILE, hit.get().getStrategy());
        assertEquals(Layer.RULE_KEYWORD, hit.get().getLayer());
    }

    @Test
    void short_prompt_without_complex_keyword_routes_to_html() {
        Optional<RoutingDecision> hit = router.route("做一个 hello world 页面");
        assertTrue(hit.isPresent());
        assertEquals(CodeGenTypeEnum.HTML, hit.get().getStrategy());
        assertEquals(Layer.RULE_LENGTH, hit.get().getLayer());
        assertTrue(hit.get().getReason().contains("length"));
    }

    @Test
    void html_keyword_with_short_length_routes_to_html() {
        // 含 "简历" 关键词 + 字数 80 左右 → HTML 单页
        Optional<RoutingDecision> hit = router.route(
                "做一个个人简历网页，姓名张三，Java 后端工程师，3 年经验，会 Spring Boot 和 MySQL");
        assertTrue(hit.isPresent());
        assertEquals(CodeGenTypeEnum.HTML, hit.get().getStrategy());
        assertEquals(Layer.RULE_KEYWORD, hit.get().getLayer());
        assertTrue(hit.get().getReason().contains("HTML single-page"));
    }

    @Test
    void long_ambiguous_prompt_misses_rule() {
        // 中长 prompt 不含明确 VUE/MULTI_FILE/HTML 关键词 → Layer 1 miss
        String prompt = "我们公司是一家做企业级数据治理的厂商，目标客户是金融、能源、政务行业。"
                + "希望你帮我们准备一份产品介绍材料，能展示我们的核心模块、典型客户案例、合作伙伴。"
                + "整体风格要专业稳重，体现技术实力。";
        Optional<RoutingDecision> hit = router.route(prompt);
        assertFalse(hit.isPresent(),
                "ambiguous long prompt without explicit keywords should miss rules");
    }

    @Test
    void multifile_priority_over_vue_when_both_match() {
        // 含 "应用" + "多页面"，MULTI_FILE 优先（信号词更具体）
        // 业务场景：含"表单 + 多页"的 prompt 应是多页静态站，而非 SPA
        Optional<RoutingDecision> hit = router.route(
                "做一个多页面的 saas 官网，含登录表单和应用入口");
        assertTrue(hit.isPresent());
        assertEquals(CodeGenTypeEnum.MULTI_FILE, hit.get().getStrategy(),
                "多页面 / 官网 keyword should take priority over 应用 / 表单 (MultiFile beats Vue)");
    }

    @Test
    void case_insensitive_keyword_matching() {
        // 大写 "VUE" / "Dashboard" 应仍命中（小写匹配）
        Optional<RoutingDecision> hit = router.route("Build me a Dashboard for sales");
        assertTrue(hit.isPresent());
        assertEquals(CodeGenTypeEnum.VUE_PROJECT, hit.get().getStrategy());
    }

    @Test
    void rule_routing_yields_zero_self_duration() {
        // RuleRouter 自身不算耗时，调用方填充。这里仅验证字段存在。
        Optional<RoutingDecision> hit = router.route("做一个简介页面");
        assertTrue(hit.isPresent());
        assertEquals(0L, hit.get().getDurationMs());
        assertEquals(1.0, hit.get().getConfidence());
    }

    @Test
    void user_prompt_carried_through_decision() {
        String prompt = "Vue 看板应用";
        Optional<RoutingDecision> hit = router.route(prompt);
        assertTrue(hit.isPresent());
        assertEquals(prompt, hit.get().getUserPrompt());
    }
}
