package com.prompt2app.eval;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RenderScorer} 单测。覆盖三种策略 + 几种否决场景。
 */
class RenderScorerTest {

    private final RenderScorer scorer = new RenderScorer();

    @Test
    void uninvoked_yields_veto() {
        EvalCase c = htmlCase();
        AgentInvoker.InvocationResult inv = stub();
        Scorer.ScoreContribution r = scorer.evaluate(c, inv);
        assertTrue(r.isVeto());
        assertEquals("render", r.getDimension());
    }

    @Test
    void html_with_full_body_text_passes() {
        EvalCase c = htmlCase();
        String html = "<!DOCTYPE html><html><head><title>X</title></head>"
                + "<body><h1>张三的简历</h1>"
                + "<p>Java 后端工程师，3 年经验，会 Spring Boot、MySQL、Redis。"
                + "联系方式 zhangsan@example.com，电话 13800138000。</p>"
                + "<p>这一段内容足够长，超过 100 字符，应该通过 RenderScorer 的非空检查。</p>"
                + "</body></html>";
        Scorer.ScoreContribution r = scorer.evaluate(c, invocation(html, 1));
        assertFalse(r.isVeto(), r.getDetail());
        assertEquals(100.0, r.getScore());
    }

    @Test
    void html_with_empty_body_vetoes() {
        EvalCase c = htmlCase();
        String html = "<html><body></body></html>";
        Scorer.ScoreContribution r = scorer.evaluate(c, invocation(html, 1));
        assertTrue(r.isVeto());
        assertTrue(r.getDetail().contains("body text"));
    }

    @Test
    void html_with_only_comments_vetoes() {
        EvalCase c = htmlCase();
        String html = "<html><body><!-- TODO: implement --></body></html>";
        Scorer.ScoreContribution r = scorer.evaluate(c, invocation(html, 1));
        assertTrue(r.isVeto(), "comment-only body should be vetoed");
    }

    @Test
    void html_without_body_tag_vetoes() {
        EvalCase c = htmlCase();
        Scorer.ScoreContribution r = scorer.evaluate(c, invocation("just plain text no html", 1));
        assertTrue(r.isVeto());
        assertTrue(r.getDetail().contains("no <body>"));
    }

    @Test
    void multifile_with_two_bodies_passes() {
        EvalCase c = multiFileCase();
        String merged = "<!-- index.html --><html><body>Home page content here.</body></html>"
                + "\n<!-- about.html --><html><body>About page content here.</body></html>";
        Scorer.ScoreContribution r = scorer.evaluate(c, invocation(merged, 2));
        assertFalse(r.isVeto());
    }

    @Test
    void multifile_with_one_body_vetoes() {
        EvalCase c = multiFileCase();
        String merged = "<html><body>only one</body></html>";
        Scorer.ScoreContribution r = scorer.evaluate(c, invocation(merged, 1));
        assertTrue(r.isVeto());
    }

    @Test
    void vue_with_package_and_app_passes() {
        EvalCase c = vueCase();
        String merged = "// package.json: { \"name\": \"app\" }\n// App.vue: <template><div>x</div></template>";
        Scorer.ScoreContribution r = scorer.evaluate(c, invocation(merged, 4));
        assertFalse(r.isVeto());
    }

    @Test
    void vue_without_package_vetoes() {
        EvalCase c = vueCase();
        String merged = "// just App.vue";
        Scorer.ScoreContribution r = scorer.evaluate(c, invocation(merged, 1));
        assertTrue(r.isVeto());
        assertTrue(r.getDetail().contains("package.json"));
    }

    private static EvalCase htmlCase() {
        EvalCase c = new EvalCase();
        c.setId("test-html");
        c.setExpectedStrategy("HTML");
        c.setRubric(new EvalCase.Rubric());
        return c;
    }

    private static EvalCase multiFileCase() {
        EvalCase c = htmlCase();
        c.setExpectedStrategy("MULTI_FILE");
        return c;
    }

    private static EvalCase vueCase() {
        EvalCase c = htmlCase();
        c.setExpectedStrategy("VUE_PROJECT");
        return c;
    }

    private static AgentInvoker.InvocationResult stub() {
        return AgentInvoker.InvocationResult.builder()
                .mergedOutput("").fileCount(0).durationMs(0L).invoked(false).note("stub").build();
    }

    private static AgentInvoker.InvocationResult invocation(String output, int fileCount) {
        return AgentInvoker.InvocationResult.builder()
                .mergedOutput(output).fileCount(fileCount).durationMs(100L)
                .invoked(true).note(null).build();
    }
}
