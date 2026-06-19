package com.prompt2app.metric;

import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一页 SQL 报表服务（Phase 6 · ADR Charter §3「不上 Grafana」）。
 *
 * <p>用直接 SQL 查询代替 Grafana：成本 0 / 复杂度 0 / 数据足够。
 *
 * <p>报表覆盖：
 * <ol>
 *   <li>{@link #overallStats} —— 总成功率 / 平均耗时 / 平均成本</li>
 *   <li>{@link #byStrategy} —— 按策略分组（HTML/MULTI_FILE/VUE_PROJECT）</li>
 *   <li>{@link #byRouterLayer} —— 按路由层分组（规则命中率 vs LLM fallback 率）</li>
 *   <li>{@link #recentFailures} —— 最近失败 case Top N</li>
 * </ol>
 */
@Slf4j
@Service
public class MetricReportService {

    /** 总体统计。 */
    public OverallStats overallStats(LocalDateTime since) {
        String sql = "SELECT "
                + "COUNT(*) AS total, "
                + "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCount, "
                + "AVG(generationDurationMs) AS avgGenMs, "
                + "AVG(costUsd) AS avgCost, "
                + "AVG(routerDurationMs) AS avgRouterMs "
                + "FROM generation_metric "
                + "WHERE createTime >= ?";
        try {
            Row row = Db.selectOneBySql(sql, since);
            if (row == null || row.getLong("total") == null || row.getLong("total") == 0) {
                return OverallStats.empty();
            }
            long total = row.getLong("total");
            long success = row.getLong("successCount") == null ? 0 : row.getLong("successCount");
            return OverallStats.builder()
                    .total(total)
                    .successCount(success)
                    .successRate(total == 0 ? 0.0 : (double) success / total)
                    .avgGenerationMs(safeDouble(row.get("avgGenMs")))
                    .avgCostUsd(safeBigDecimal(row.get("avgCost")))
                    .avgRouterMs(safeDouble(row.get("avgRouterMs")))
                    .build();
        } catch (Exception e) {
            log.warn("[Report] overallStats failed: {}", e.getMessage());
            return OverallStats.empty();
        }
    }

    /** 按策略分组：HTML / MULTI_FILE / VUE_PROJECT 各自的成功率与耗时。 */
    public List<StrategyStats> byStrategy(LocalDateTime since) {
        String sql = "SELECT strategy, "
                + "COUNT(*) AS total, "
                + "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCount, "
                + "AVG(generationDurationMs) AS avgGenMs, "
                + "AVG(costUsd) AS avgCost "
                + "FROM generation_metric "
                + "WHERE createTime >= ? "
                + "GROUP BY strategy";
        try {
            List<Row> rows = Db.selectListBySql(sql, since);
            List<StrategyStats> result = new ArrayList<>(rows.size());
            for (Row row : rows) {
                long total = row.getLong("total");
                long success = row.getLong("successCount") == null ? 0 : row.getLong("successCount");
                result.add(StrategyStats.builder()
                        .strategy(row.getString("strategy"))
                        .total(total)
                        .successRate(total == 0 ? 0.0 : (double) success / total)
                        .avgGenerationMs(safeDouble(row.get("avgGenMs")))
                        .avgCostUsd(safeBigDecimal(row.get("avgCost")))
                        .build());
            }
            return result;
        } catch (Exception e) {
            log.warn("[Report] byStrategy failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 按路由层分组：规则命中率 vs LLM fallback。 */
    public List<RouterLayerStats> byRouterLayer(LocalDateTime since) {
        String sql = "SELECT routerLayer, "
                + "COUNT(*) AS total, "
                + "AVG(routerDurationMs) AS avgRouterMs, "
                + "AVG(routerConfidence) AS avgConf "
                + "FROM generation_metric "
                + "WHERE createTime >= ? AND routerLayer IS NOT NULL "
                + "GROUP BY routerLayer";
        try {
            List<Row> rows = Db.selectListBySql(sql, since);
            List<RouterLayerStats> result = new ArrayList<>(rows.size());
            for (Row row : rows) {
                result.add(RouterLayerStats.builder()
                        .layer(row.getString("routerLayer"))
                        .total(row.getLong("total"))
                        .avgRouterMs(safeDouble(row.get("avgRouterMs")))
                        .avgConfidence(safeBigDecimal(row.get("avgConf")))
                        .build());
            }
            return result;
        } catch (Exception e) {
            log.warn("[Report] byRouterLayer failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 最近失败的 case（Top N）。 */
    public List<Map<String, Object>> recentFailures(int limit) {
        String sql = "SELECT id, appId, strategy, routerLayer, errorMessage, createTime "
                + "FROM generation_metric "
                + "WHERE success = 0 AND errorMessage IS NOT NULL "
                + "ORDER BY createTime DESC "
                + "LIMIT ?";
        try {
            List<Row> rows = Db.selectListBySql(sql, limit);
            List<Map<String, Object>> out = new ArrayList<>(rows.size());
            for (Row row : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", row.get("id"));
                m.put("appId", row.get("appId"));
                m.put("strategy", row.get("strategy"));
                m.put("routerLayer", row.get("routerLayer"));
                m.put("errorMessage", row.get("errorMessage"));
                m.put("createTime", row.get("createTime"));
                out.add(m);
            }
            return out;
        } catch (Exception e) {
            log.warn("[Report] recentFailures failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static double safeDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static BigDecimal safeBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try {
            return new BigDecimal(o.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    @Value @Builder
    public static class OverallStats {
        long total;
        long successCount;
        double successRate;
        double avgGenerationMs;
        BigDecimal avgCostUsd;
        double avgRouterMs;

        public static OverallStats empty() {
            return OverallStats.builder()
                    .total(0).successCount(0).successRate(0.0)
                    .avgGenerationMs(0.0).avgCostUsd(BigDecimal.ZERO).avgRouterMs(0.0)
                    .build();
        }
    }

    @Value @Builder
    public static class StrategyStats {
        String strategy;
        long total;
        double successRate;
        double avgGenerationMs;
        BigDecimal avgCostUsd;
    }

    @Value @Builder
    public static class RouterLayerStats {
        String layer;
        long total;
        double avgRouterMs;
        BigDecimal avgConfidence;
    }
}
