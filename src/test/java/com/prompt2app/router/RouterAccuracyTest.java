package com.prompt2app.router;

import com.prompt2app.app.model.enums.CodeGenTypeEnum;
import com.prompt2app.eval.EvalCase;
import com.prompt2app.eval.EvalCaseLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路由准确率回归测试：把评测集 25 case 当作"路由真相"，
 * 检验 Layer 1 RuleRouter 的命中率与正确率。
 *
 * <p>设计意图（详见 ADR-0003 §测试覆盖）：
 * <ul>
 *   <li>Layer 1 准确率（命中且与 expected_strategy 一致）≥ 60%</li>
 *   <li>Layer 1 命中率（命中即可，不要求正确）≥ 80%</li>
 *   <li>不要求 100% —— 若 Layer 1 在 case 上 100% 准确，说明规则过拟合</li>
 * </ul>
 *
 * <p>这个测试同时是 ADR-0003 的"实证文件"：跑一遍即可看到每个 case 在 Layer 1 上的表现，
 * 从而决定哪些 case 应该走 LLM fallback、哪些规则需要扩充。
 */
class RouterAccuracyTest {

    private static final Path CASES_DIR = Paths.get("eval/cases");
    /** Layer 1 准确率下限。低于此值视为规则退化或评测集变化。 */
    private static final double MIN_ACCURACY = 0.60;
    /** Layer 1 命中率下限。 */
    private static final double MIN_HIT_RATE = 0.80;

    private final EvalCaseLoader loader = new EvalCaseLoader();
    private final RuleRouter router = new RuleRouter();

    @Test
    void layer1_accuracy_against_eval_cases() {
        List<EvalCase> cases = loader.loadAll(CASES_DIR);
        assertNotNull(cases);
        assertTrue(cases.size() >= 25, "expected ≥25 cases for meaningful regression");

        int total = cases.size();
        int hit = 0;       // Layer 1 命中（无论对错）
        int correct = 0;   // 命中且与 expected_strategy 一致
        Map<String, Integer> missByExpected = new TreeMap<>();
        Map<String, Integer> mismatchByExpected = new TreeMap<>();

        StringBuilder report = new StringBuilder();
        report.append("\n=== Layer 1 (RuleRouter) accuracy on ")
              .append(total).append(" eval cases ===\n");

        for (EvalCase c : cases) {
            CodeGenTypeEnum expected = parseExpected(c.getExpectedStrategy());
            Optional<RoutingDecision> result = router.route(c.getPrompt());
            String status;
            if (result.isPresent()) {
                hit++;
                if (result.get().getStrategy() == expected) {
                    correct++;
                    status = "OK   ";
                } else {
                    mismatchByExpected.merge(c.getExpectedStrategy(), 1, Integer::sum);
                    status = "MISS↛"; // hit but wrong strategy
                }
            } else {
                missByExpected.merge(c.getExpectedStrategy(), 1, Integer::sum);
                status = "FALL "; // fallback to LLM
            }
            report.append(String.format(Locale.ROOT,
                    "  %s %s expected=%-12s actual=%s%n",
                    status, c.getId(), c.getExpectedStrategy(),
                    result.map(d -> d.getStrategy().name()).orElse("(LLM_FALLBACK)")));
        }

        double accuracy = (double) correct / total;
        double hitRate = (double) hit / total;
        report.append(String.format(Locale.ROOT,
                "%n  Hit rate: %d/%d = %.1f%%   Accuracy: %d/%d = %.1f%%%n",
                hit, total, hitRate * 100, correct, total, accuracy * 100));
        report.append("  Layer 1 misses by expected strategy: ").append(missByExpected).append("\n");
        report.append("  Layer 1 mismatches by expected strategy: ").append(mismatchByExpected).append("\n");

        // 打印到 stdout 供 review
        System.out.println(report);

        assertTrue(hitRate >= MIN_HIT_RATE,
                String.format(Locale.ROOT,
                        "Layer 1 hit rate %.1f%% below threshold %.0f%%",
                        hitRate * 100, MIN_HIT_RATE * 100));
        assertTrue(accuracy >= MIN_ACCURACY,
                String.format(Locale.ROOT,
                        "Layer 1 accuracy %.1f%% below threshold %.0f%%",
                        accuracy * 100, MIN_ACCURACY * 100));
    }

    private static CodeGenTypeEnum parseExpected(String s) {
        if (s == null) {
            throw new IllegalArgumentException("expected_strategy is null");
        }
        return CodeGenTypeEnum.valueOf(s);
    }
}
