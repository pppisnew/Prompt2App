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

    /**
     * 每个 round 的 appId 起始基数。Round 1 → 1_100_000; Round 2 → 2_100_000; ...
     * 用 appId 高位编码 round 实现产物天然隔离（{@code vue_project_2100000}），避免
     * 修改所有 {@code codeOutputDir} 调用方（saver / facade / sandbox / workflow 等）。
     * ADR-0013 多轮评测的"产物隔离"在这一层实现。
     */
    private static final long ROUND_APP_ID_STEP = 1_000_000L;
    private static final long ROUND_APP_ID_OFFSET = 100_000L;

    private final AiCodeGeneratorFacade facade;
    private final Prompt2AppProperties properties;
    private final AtomicLong appIdSeq = new AtomicLong(ROUND_APP_ID_OFFSET);
    /** 当前 round（仅 log 标识用，appId 已编码此信息）。null = legacy 单轮模式。 */
    private volatile Integer currentRoundId = null;

    public DirectServiceInvoker(AiCodeGeneratorFacade facade, Prompt2AppProperties properties) {
        this.facade = facade;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "direct-service";
    }

    /**
     * 切到指定 round：重置 appId 序列到 {@code round * 1_000_000 + 100_000}，
     * 后续生成产物天然落在 {@code <strategy>_{round}xxxxxx} 目录，跨轮不冲突。
     *
     * <p>由 {@code MultiRoundEvalRunner} 在每轮开始前调用。Stub 模式 / 旧单轮 runner
     * 不调用此方法时，行为与 ADR-0013 前完全一致（appId 从 100_000 起递增）。
     */
    public void setRoundContext(int roundId) {
        if (roundId < 1) {
            throw new IllegalArgumentException("roundId must be >= 1, got " + roundId);
        }
        this.currentRoundId = roundId;
        this.appIdSeq.set(roundId * ROUND_APP_ID_STEP + ROUND_APP_ID_OFFSET);
        log.info("[Eval] Switched to round {} (appId base = {})", roundId, appIdSeq.get());
    }

    @Override
    public InvocationResult invoke(EvalCase evalCase, Integer roundId) {
        // 若 caller 还没显式 setRoundContext 但传了 roundId，自动切——容忍 caller 顺序疏忽
        if (roundId != null && (currentRoundId == null || !currentRoundId.equals(roundId))) {
            setRoundContext(roundId);
        }
        return invoke(evalCase);
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
            // VUE_PROJECT：build 是否产出可预览产物（dist/index.html）。RenderScorer 据此判定 veto，
            // 替代旧的 mergedOutput 文本字面量匹配（编译后 JS 不含 "package.json" 字面量会误判）。
            // 路径 Y：不依赖 buildProject 的返回值（它在 facade 内部被丢弃），直接看磁盘产物。
            boolean buildSuccess = genType != CodeGenTypeEnum.VUE_PROJECT
                    || new File(outputDir, "dist/index.html").exists();

            long duration = System.currentTimeMillis() - start;
            log.info("[Eval] case={} done, files={}, buildSuccess={}, duration={}ms",
                    evalCase.getId(), fileCount, buildSuccess, duration);

            return InvocationResult.builder()
                    .mergedOutput(mergedOutput)
                    .fileCount(fileCount)
                    .durationMs(duration)
                    .invoked(true)
                    .buildSuccess(buildSuccess)
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

    /**
     * 合并目录下所有文件内容为单个字符串，供 RubricScorer 检查 mustContain。
     *
     * <p>VUE_PROJECT 分支（方案 D，2026-06-22 task）：读源码 + 文件路径清单，不读 dist。
     * 原因：dist 是 minified JS，源码符号（ref / localStorage）和文件名字面量（package.json）都会丢失，
     * 导致 RubricScorer must_contain 全部 veto。改为读源码 + 文件清单后，Rubric 能命中源码符号，
     * 文件清单让 "package.json" 等文件名字面量也可命中。跳过 node_modules / dist / .git。
     *
     * <p>HTML / MULTI_FILE 分支：行为不变，仍走 {@link #readDirRecursive}（无 build 产物，读全部文件）。
     */
    private String readMergedOutput(File dir, CodeGenTypeEnum genType) {
        if (dir == null || !dir.exists()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (genType == CodeGenTypeEnum.VUE_PROJECT) {
            // 方案 D：源码 + 文件清单，不读 dist
            sb.append("=== project file tree ===\n");
            appendFileTree(dir, dir, sb);
            sb.append("\n=== source contents ===\n");
            appendSourceContents(dir, dir, sb);
        } else {
            readDirRecursive(dir, sb);
        }
        return sb.toString();
    }

    /** VUE mergedOutput 读取时跳过的目录/文件名（无评分价值或体积过大）。 */
    private boolean isExcludedFromMergedOutput(String name) {
        return "node_modules".equals(name) || "dist".equals(name) || ".git".equals(name);
    }

    /**
     * 列文件相对路径清单（跳过 node_modules / dist / .git），让文件名字面量可被 Rubric 命中。
     * 格式：{@code === file: package.json ===}（每行一个）。
     */
    private void appendFileTree(File root, File dir, StringBuilder sb) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (isExcludedFromMergedOutput(f.getName())) continue;
            if (f.isDirectory()) {
                appendFileTree(root, f, sb);
            } else {
                String rel = root.toPath().relativize(f.toPath()).toString();
                sb.append("=== file: ").append(rel).append(" ===\n");
            }
        }
    }

    /** 读源码文件内容（跳过 node_modules / dist / .git），每文件前加 {@code --- 相对路径 ---} 分隔。 */
    private void appendSourceContents(File root, File dir, StringBuilder sb) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (isExcludedFromMergedOutput(f.getName())) continue;
            if (f.isDirectory()) {
                appendSourceContents(root, f, sb);
            } else {
                String rel = root.toPath().relativize(f.toPath()).toString();
                sb.append("--- ").append(rel).append(" ---\n");
                try {
                    sb.append(FileUtil.readString(f, StandardCharsets.UTF_8)).append("\n");
                } catch (Exception ignored) {
                    sb.append("(binary or unreadable)\n");
                }
            }
        }
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
