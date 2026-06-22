package com.prompt2app.eval;

import com.prompt2app.agent.codegen.AiCodeGeneratorFacade;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 真实 LLM 评测运行器（ADR-0013：多轮均分 + temperature=0 + 断点续跑）。
 *
 * <p>用法：
 * <pre>
 * mvn test -Dtest=RealEvalRunner -Dspring.profiles.active=dev
 * </pre>
 *
 * <p>需要 .env 中配置真实的 LLM API key + DB + Redis。
 * 25 case × 3 轮 = 75 次 LLM 调用，输出多轮均分 ± 标准差 baseline report。
 *
 * <p><b>temperature=0</b>：通过 {@link TestPropertySource} 强制覆盖
 * {@code langchain4j.open-ai.streaming-chat-model.temperature=0}，只在评测时生效，
 * 不污染生产路径。
 *
 * <p><b>断点续跑</b>：若 {@code eval/reports/runs/baseline-real.round-{N}.json} 已存在，
 * 跳过该轮的 LLM 调用直接 load。要强制重跑某轮，删除对应 JSON。
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "langchain4j.open-ai.streaming-chat-model.temperature=0",
        "langchain4j.open-ai.chat-model.temperature=0",
        "langchain4j.open-ai.reasoning-streaming-chat-model.temperature=0"
})
class RealEvalRunner {

    @Resource
    private AiCodeGeneratorFacade facade;

    @Resource
    private com.prompt2app.infra.config.Prompt2AppProperties properties;

    @Test
    void runRealEval() throws Exception {
        log.info("========== 真实 LLM 评测启动 (ADR-0013 多轮模式) ==========");

        Path casesDir = Paths.get("eval/cases");
        Path reportFile = Paths.get("eval/reports/baseline-real.md");

        // 构建真实 invoker（DirectServiceInvoker 支持 setRoundContext 切轮）
        DirectServiceInvoker invoker = new DirectServiceInvoker(facade, properties);

        // 三维评分先用 rubric + render（LLM-Judge 由 P1-1 task 启用，独立 task）
        List<Scorer> scorers = List.of(
                new RubricScorer(),
                new RenderScorer()
        );

        MultiRoundEvalRunner runner = new MultiRoundEvalRunner(
                invoker, scorers, properties.getEval());
        log.info("开始跑 {} 轮 × 25 case (resume={}) ...",
                properties.getEval().getRounds(), properties.getEval().isResumeOnRestart());
        var report = runner.runMultiRound(casesDir, reportFile);

        log.info("========== 评测完成: totalMean={} ± {} | 报告: {} ==========",
                report.getTotalMean(), report.getTotalStdev(),
                reportFile.toAbsolutePath());
    }
}
