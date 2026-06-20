package com.prompt2app.eval;

import com.prompt2app.agent.codegen.AiCodeGeneratorFacade;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 真实 LLM 评测运行器。
 *
 * <p>用法：
 * <pre>
 * mvn test -Dtest=RealEvalRunner -Dspring.profiles.active=dev
 * </pre>
 *
 * <p>需要 .env 中配置真实的 LLM API key + DB + Redis。
 * 25 case 全部调真实 LLM 生成，输出三维评分 baseline report。
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("dev")
class RealEvalRunner {

    @Resource
    private AiCodeGeneratorFacade facade;

    @Resource
    private com.prompt2app.infra.config.Prompt2AppProperties properties;

    @Test
    void runRealEval() throws Exception {
        log.info("========== 真实 LLM 评测启动 ==========");

        Path casesDir = Paths.get("eval/cases");
        Path reportFile = Paths.get("eval/reports/baseline-real.md");
        Path prevReport = Paths.get("eval/reports/baseline.md");

        // 构建真实 invoker
        DirectServiceInvoker invoker = new DirectServiceInvoker(facade, properties);
        EvalRunner runner = new EvalRunner(invoker);

        // 三维评分：rubric（确定性）+ render（确定性）+ llm-judge（LLM）
        // 注：LlmJudgeScorer 需要额外 LLM 调用，此处先用 rubric + render 两维
        List<Scorer> scorers = List.of(
                new RubricScorer(),
                new RenderScorer()
        );

        log.info("开始跑 25 case（真实 LLM 生成 + 双维度评分）...");
        runner.runFull(casesDir, reportFile, prevReport, scorers);

        log.info("========== 评测完成，报告已写入: {} ==========", reportFile.toAbsolutePath());
    }
}
