package com.prompt2app.eval;

import cn.hutool.core.io.FileUtil;
import com.prompt2app.agent.codegen.AiCodeGeneratorFacade;
import com.prompt2app.app.model.enums.CodeGenTypeEnum;
import com.prompt2app.infra.config.Prompt2AppProperties;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 真实 LLM 调用的 AgentInvoker 实现。
 *
 * <p>通过 {@link AiCodeGeneratorFacade} 调用 LangChain4j AiServices，
 * 根据案例的 {@code expected_strategy} 分派到 HTML / MultiFile / Vue 三条路径。
 * 生成产物读回 mergedOutput 供 Scorer 评分。
 *
 * <p>HTML / MultiFile 用非流式 {@code generateAndSaveCode}（直接返回 File）；
 * Vue 用流式 {@code generateAndSaveCodeStream} + blockLast（Vue 只有流式接口）。
 */
@Slf4j
public class DirectServiceInvoker implements AgentInvoker {

    private final AiCodeGeneratorFacade facade;
    private final Prompt2AppProperties properties;
    private final AtomicLong appIdSeq = new AtomicLong(100000L);

    public DirectServiceInvoker(AiCodeGeneratorFacade facade, Prompt2AppProperties properties) {
        this.facade = facade;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "direct-service";
    }

    @Override
    public InvocationResult invoke(EvalCase evalCase) {
        long appId = appIdSeq.getAndIncrement();
        long start = System.currentTimeMillis();
        try {
            String strategy = evalCase.getExpectedStrategy();
            CodeGenTypeEnum genType = CodeGenTypeEnum.getEnumByValue(strategy);
            if (genType == null && strategy != null) {
                // YAML 用大写 HTML/MULTI_FILE/VUE_PROJECT，枚举 value 是小写 html/multi_file/vue_project
                genType = CodeGenTypeEnum.getEnumByValue(strategy.toLowerCase());
            }
            if (genType == null) {
                genType = CodeGenTypeEnum.HTML;
            }

            log.info("[Eval] case={} strategy={} prompt={}...", evalCase.getId(), genType,
                    evalCase.getPrompt().length() > 40 ? evalCase.getPrompt().substring(0, 40) : evalCase.getPrompt());

            File outputDir;
            if (genType == CodeGenTypeEnum.VUE_PROJECT) {
                // Vue 只有流式接口：blockLast 等待流完成，生成产物在 codeOutputDir/vue_project_{appId}/
                Flux<String> stream = facade.generateAndSaveCodeStream(evalCase.getPrompt(), genType, appId);
                stream.blockLast(Duration.ofMinutes(10));
                outputDir = new File(properties.getStorage().getCodeOutputDir(), "vue_project_" + appId);
            } else {
                // HTML / MultiFile 非流式：直接返回保存目录
                outputDir = facade.generateAndSaveCode(evalCase.getPrompt(), genType, appId);
            }

            // 读回生成的文件
            String mergedOutput = readMergedOutput(outputDir, genType);
            int fileCount = countFiles(outputDir);

            long duration = System.currentTimeMillis() - start;
            log.info("[Eval] case={} done, files={}, duration={}ms", evalCase.getId(), fileCount, duration);

            return InvocationResult.builder()
                    .mergedOutput(mergedOutput)
                    .fileCount(fileCount)
                    .durationMs(duration)
                    .invoked(true)
                    .note("direct-service: " + genType.getValue())
                    .build();

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[Eval] case={} failed: {}", evalCase.getId(), e.getMessage(), e);
            return InvocationResult.builder()
                    .mergedOutput("")
                    .fileCount(0)
                    .durationMs(duration)
                    .invoked(false)
                    .note("error: " + e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }

    /** 合并目录下所有文件内容为单个字符串，供 RubricScorer 检查 mustContain。 */
    private String readMergedOutput(File dir, CodeGenTypeEnum genType) {
        if (dir == null || !dir.exists()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        // Vue 项目：dist 目录
        if (genType == CodeGenTypeEnum.VUE_PROJECT) {
            File distDir = new File(dir, "dist");
            if (distDir.exists()) {
                readDirRecursive(distDir, sb);
            } else {
                readDirRecursive(dir, sb);
            }
        } else {
            readDirRecursive(dir, sb);
        }
        return sb.toString();
    }

    private void readDirRecursive(File dir, StringBuilder sb) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                readDirRecursive(f, sb);
            } else {
                try {
                    String content = FileUtil.readString(f, StandardCharsets.UTF_8);
                    sb.append(content).append("\n");
                } catch (Exception ignored) {
                    // 跳过无法读取的文件（二进制等）
                }
            }
        }
    }

    private int countFiles(File dir) {
        if (dir == null || !dir.exists()) return 0;
        File countDir = dir;
        File distDir = new File(dir, "dist");
        if (distDir.exists()) {
            countDir = distDir;
        }
        return countFilesRecursive(countDir);
    }

    private int countFilesRecursive(File dir) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) {
                count += countFilesRecursive(f);
            } else {
                count++;
            }
        }
        return count;
    }
}
