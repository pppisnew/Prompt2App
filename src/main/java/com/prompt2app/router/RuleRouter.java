package com.prompt2app.router;

import com.prompt2app.app.model.enums.CodeGenTypeEnum;
import com.prompt2app.router.RoutingDecision.Layer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Layer 1 · 规则路由。
 *
 * <p>纯字符串规则，无 IO、无 LLM 调用、毫秒级。按优先级从高到低短路：
 * <ol>
 *   <li>VUE 强关键词（应用 / 看板 / 编辑器 / 表单 / 状态 ... → VUE_PROJECT）</li>
 *   <li>MULTI_FILE 关键词（多页 / 官网 / 多个页面 ... → MULTI_FILE）</li>
 *   <li>HTML 短小判定（&lt; 50 字 + 无上述关键词 → HTML）</li>
 *   <li>HTML 单页关键词（单页 / 简历 / 邀请函 ... → HTML）</li>
 *   <li>未命中 → {@link Optional#empty()} 让上层走 Layer 2</li>
 * </ol>
 *
 * <p>详见 ADR-0003 §实施细节 §Layer 1 规则集。
 */
@Component
public class RuleRouter {

    /** Vue 项目类强关键词。命中即返回 VUE_PROJECT。 */
    private static final List<String> VUE_KEYWORDS = List.of(
            "vue", "应用", "工具", "dashboard", "仪表盘", "看板", "kanban",
            "编辑器", "editor", "番茄钟", "pomodoro", "倒计时器", "计时器",
            "待办", "todo",
            "交互", "状态管理", "表单", "拖拽", "拖放", "持久化", "localstorage",
            "游戏", "quiz", "答题", "测验", "井字棋", "象棋", "计算器", "记账", "记账本",
            "聊天", "chat-app", "笔记本", "笔记 app", "playground"
            // 注：早期版本含 "倒计时" / "番茄" / "状态" — 实测在 case 007/009/015 上误判，
            //   改为 "倒计时器" / "番茄钟" / "状态管理" 提高特异性。详见 ADR-0003 复盘。
    );

    /** 多页静态站关键词。 */
    private static final List<String> MULTI_FILE_KEYWORDS = List.of(
            "多页", "多个页面", "多个 page", "数个页面",
            "官网", "网站", "站点", "微站",
            "导航跳转", "顶部导航", "sitemap",
            "静态站", "静态网站"
    );

    /** "N 页" / "N 个页面" 形式的多页指示，用正则匹配。 */
    private static final java.util.regex.Pattern MULTI_FILE_PAGE_COUNT =
            java.util.regex.Pattern.compile("\\b\\d+\\s*[个]?页(?:面)?\\b");

    /** HTML 单页关键词。命中且字数 < 200 → HTML。 */
    private static final List<String> HTML_SINGLE_PAGE_KEYWORDS = List.of(
            "单页", "一页", "卡片", "简历", "名片",
            "邀请函", "邀请卡", "海报",
            "404", "coming soon", "倒计时页", "错误页", "落地页"
            // 注：早期版本含 "简介" — 实测在 case 015（大学系简介）上误判 MultiFile→HTML，
            //   "简介" 在多页站点中也常见（公司简介 / 系简介），过于通用，移除。
    );

    /** 字数小于此阈值且无 VUE/MULTI_FILE 关键词时直接判 HTML。 */
    private static final int HTML_LENGTH_THRESHOLD = 50;

    /** HTML 单页关键词命中时允许的最大 prompt 字数（防止"做一个落地页有 5 个页面..."误判）。 */
    private static final int HTML_KEYWORD_LENGTH_LIMIT = 200;

    /**
     * 尝试规则路由。命中返回 {@link RoutingDecision}（layer = RULE_KEYWORD / RULE_LENGTH，confidence = 1.0），
     * 未命中返回 {@link Optional#empty()}。
     */
    public Optional<RoutingDecision> route(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            // 空 prompt 不交给 LLM 处理，直接 HTML
            return Optional.of(decision(CodeGenTypeEnum.HTML, Layer.RULE_LENGTH,
                    "empty prompt → HTML", userPrompt));
        }
        String lower = userPrompt.toLowerCase(Locale.ROOT);
        int len = userPrompt.length();

        // 1. MULTI_FILE 关键词（优先于 VUE 检查）
        //    经验：MULTI_FILE 信号词（官网/多页/导航）比 VUE 信号词（表单/编辑）更"具体"，
        //    场景：含"表单 + 多页"的 prompt 应是 MultiFile 站点而非 Vue 应用。
        String mfHit = firstMatch(lower, MULTI_FILE_KEYWORDS);
        if (mfHit != null) {
            return Optional.of(decision(CodeGenTypeEnum.MULTI_FILE, Layer.RULE_KEYWORD,
                    "matched MULTI_FILE keyword '" + mfHit + "'", userPrompt));
        }

        // 1b. "N 页" / "N 个页面" 数字指示符
        java.util.regex.Matcher m = MULTI_FILE_PAGE_COUNT.matcher(userPrompt);
        if (m.find()) {
            return Optional.of(decision(CodeGenTypeEnum.MULTI_FILE, Layer.RULE_KEYWORD,
                    "matched MULTI_FILE pattern '" + m.group() + "'", userPrompt));
        }

        // 2. VUE 强关键词
        String vueHit = firstMatch(lower, VUE_KEYWORDS);
        if (vueHit != null) {
            return Optional.of(decision(CodeGenTypeEnum.VUE_PROJECT, Layer.RULE_KEYWORD,
                    "matched VUE keyword '" + vueHit + "'", userPrompt));
        }

        // 3. HTML 短小判定
        if (len < HTML_LENGTH_THRESHOLD) {
            return Optional.of(decision(CodeGenTypeEnum.HTML, Layer.RULE_LENGTH,
                    "prompt length " + len + " < " + HTML_LENGTH_THRESHOLD + ", no complex keyword",
                    userPrompt));
        }

        // 4. HTML 单页关键词（短中等长度）
        String htmlHit = firstMatch(lower, HTML_SINGLE_PAGE_KEYWORDS);
        if (htmlHit != null && len < HTML_KEYWORD_LENGTH_LIMIT) {
            return Optional.of(decision(CodeGenTypeEnum.HTML, Layer.RULE_KEYWORD,
                    "matched HTML single-page keyword '" + htmlHit + "', length " + len + " ok",
                    userPrompt));
        }

        // 5. 未命中
        return Optional.empty();
    }

    /** 返回第一个出现在 haystack 中的关键词，无命中返回 null。 */
    private static String firstMatch(String haystackLower, List<String> needles) {
        for (String needle : needles) {
            if (haystackLower.contains(needle)) {
                return needle;
            }
        }
        return null;
    }

    private static RoutingDecision decision(CodeGenTypeEnum strategy, Layer layer,
                                             String reason, String userPrompt) {
        return RoutingDecision.builder()
                .strategy(strategy)
                .layer(layer)
                .reason(reason)
                .confidence(1.0)
                .durationMs(0L)        // RuleRouter 自身不算耗时；调用方填充总耗时
                .userPrompt(userPrompt)
                .build();
    }
}
