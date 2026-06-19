package com.prompt2app.eval;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Layer 2 · 渲染检查（{@link Scorer}）。
 *
 * <p><b>没有</b>引入 Playwright（ADR-0005 §代价）。轻量级实现：
 * <ul>
 *   <li>HTML 策略：抓 {@code <body>...</body>} 内文本（去注释、去空白），长度 ≥ 100</li>
 *   <li>MULTI_FILE 策略：merged output 中至少出现 2 次 {@code <body>} → 视为多文件已落地</li>
 *   <li>VUE_PROJECT 策略：merged output 至少包含 {@code package.json} + {@code main.} 或 {@code App.vue}</li>
 * </ul>
 *
 * <p>不通过 → veto = true（最终分 0）。通过 → 该维度满分 100。
 *
 * <p>详见 ADR-0005 §决策。Playwright 升级路径在 backlog。
 */
public class RenderScorer implements Scorer {

    /** HTML 策略下，body 内非空文本最少长度。 */
    private static final int MIN_BODY_TEXT_LEN = 100;

    /** {@code <body ...>...</body>} 大小写不敏感、跨行。 */
    private static final Pattern BODY_PATTERN = Pattern.compile(
            "<body\\b[^>]*>([\\s\\S]*?)</body>",
            Pattern.CASE_INSENSITIVE);

    /** HTML 注释，用于剔除。 */
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--[\\s\\S]*?-->");

    /** 标签和大段空白，用于剔除得到纯文本。 */
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    @Override
    public String name() {
        return "render";
    }

    @Override
    public ScoreContribution evaluate(EvalCase evalCase, AgentInvoker.InvocationResult invocation) {
        if (!invocation.isInvoked()) {
            return ScoreContribution.builder()
                    .dimension(name())
                    .score(0.0)
                    .veto(true)
                    .detail("not invoked")
                    .build();
        }
        String output = invocation.getMergedOutput();
        if (output == null || output.isEmpty()) {
            return veto("empty output");
        }

        String strategy = evalCase.getExpectedStrategy();
        if ("VUE_PROJECT".equals(strategy)) {
            return checkVueProject(output);
        }
        if ("MULTI_FILE".equals(strategy)) {
            return checkMultiFile(output);
        }
        // Default: HTML
        return checkHtml(output);
    }

    private ScoreContribution checkHtml(String output) {
        Matcher m = BODY_PATTERN.matcher(output);
        if (!m.find()) {
            return veto("no <body> tag found");
        }
        String body = m.group(1);
        String stripped = HTML_COMMENT.matcher(body).replaceAll("");
        stripped = HTML_TAG.matcher(stripped).replaceAll(" ");
        stripped = stripped.replaceAll("\\s+", " ").trim();
        if (stripped.length() < MIN_BODY_TEXT_LEN) {
            return veto(String.format(Locale.ROOT,
                    "body text only %d chars, < %d", stripped.length(), MIN_BODY_TEXT_LEN));
        }
        return ok(String.format(Locale.ROOT, "body text %d chars", stripped.length()));
    }

    private ScoreContribution checkMultiFile(String output) {
        // merged output 中应当出现多个 <body> 块（每个文件一个）
        int bodyCount = 0;
        Matcher m = BODY_PATTERN.matcher(output);
        while (m.find()) {
            bodyCount++;
        }
        if (bodyCount < 2) {
            return veto(String.format(Locale.ROOT, "only %d <body> tag(s) in MULTI_FILE output", bodyCount));
        }
        return ok(String.format(Locale.ROOT, "%d <body> tags found", bodyCount));
    }

    private ScoreContribution checkVueProject(String output) {
        boolean hasPkg = output.contains("package.json");
        boolean hasEntry = output.contains("App.vue") || output.contains("main.js") || output.contains("main.ts");
        if (!hasPkg) return veto("missing package.json");
        if (!hasEntry) return veto("missing App.vue / main.js / main.ts entry");
        return ok("Vue project structure: package.json + entry present");
    }

    private ScoreContribution ok(String detail) {
        return ScoreContribution.builder()
                .dimension(name())
                .score(100.0)
                .veto(false)
                .detail(detail)
                .build();
    }

    private ScoreContribution veto(String detail) {
        return ScoreContribution.builder()
                .dimension(name())
                .score(0.0)
                .veto(true)
                .detail(detail)
                .build();
    }
}
