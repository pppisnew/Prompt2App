package com.prompt2app.metric;

import com.prompt2app.infra.common.BaseResponse;
import com.prompt2app.infra.common.ResultUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Metric 报表接口（Phase 6）。
 *
 * <p>不上 Grafana（Charter §3）—— 一个 REST endpoint 返回完整 JSON 报表。
 * 前端可以单页 fetch 渲染（Phase 7 README 中可贴截图）。
 */
@RestController
@RequestMapping("/metric")
public class MetricReportController {

    @Resource
    private MetricReportService reportService;

    /**
     * 一页报表汇总。
     *
     * @param hours 回看小时数（默认 24）
     */
    @GetMapping("/report")
    public BaseResponse<Map<String, Object>> report(@RequestParam(defaultValue = "24") int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(Math.max(1, hours));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sinceUtc", since.toString());
        result.put("overall", reportService.overallStats(since));
        result.put("byStrategy", reportService.byStrategy(since));
        result.put("byRouterLayer", reportService.byRouterLayer(since));
        result.put("recentFailures", reportService.recentFailures(10));
        return ResultUtils.success(result);
    }
}
