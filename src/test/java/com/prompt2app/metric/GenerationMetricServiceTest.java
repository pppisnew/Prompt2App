package com.prompt2app.metric;

import com.prompt2app.app.model.enums.CodeGenTypeEnum;
import com.prompt2app.metric.entity.GenerationMetric;
import com.prompt2app.metric.mapper.GenerationMetricMapper;
import com.prompt2app.router.RoutingDecision;
import com.prompt2app.router.RoutingDecision.Layer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link GenerationMetricService} 单测：mock mapper，无 DB。
 *
 * <p>覆盖：路由记录字段映射 / outcome 字段映射 / 异常容错（DB 失败不阻塞业务）。
 */
class GenerationMetricServiceTest {

    private GenerationMetricMapper mapper;
    private GenerationMetricService service;

    @BeforeEach
    void setUp() {
        mapper = mock(GenerationMetricMapper.class);
        service = new GenerationMetricService();
        ReflectionTestUtils.setField(service, "mapper", mapper);
    }

    @Test
    void records_routing_with_full_field_mapping() {
        // mock mapper.insert 给 entity 设置 id（模拟 KeyType.Auto）
        doAnswer(invocation -> {
            GenerationMetric m = invocation.getArgument(0);
            m.setId(42L);
            return 1;
        }).when(mapper).insert(any(GenerationMetric.class));

        RoutingDecision decision = RoutingDecision.builder()
                .strategy(CodeGenTypeEnum.VUE_PROJECT)
                .layer(Layer.RULE_KEYWORD)
                .reason("matched VUE keyword '看板'")
                .confidence(1.0)
                .durationMs(3L)
                .userPrompt("做一个看板应用")
                .build();

        Long id = service.recordRouting(100L, 200L, decision);

        assertEquals(42L, id);
        ArgumentCaptor<GenerationMetric> captor = ArgumentCaptor.forClass(GenerationMetric.class);
        verify(mapper, times(1)).insert(captor.capture());
        GenerationMetric saved = captor.getValue();
        assertEquals(100L, saved.getAppId());
        assertEquals(200L, saved.getUserId());
        assertEquals("VUE_PROJECT", saved.getStrategy());
        assertEquals("RULE_KEYWORD", saved.getRouterLayer());
        assertTrue(saved.getRouterReason().contains("看板"));
        assertEquals(BigDecimal.valueOf(1.0), saved.getRouterConfidence());
        assertEquals(3, saved.getRouterDurationMs());
        assertEquals(false, saved.getSuccess());  // 默认 false，待 completeOutcome
    }

    @Test
    void records_routing_truncates_long_reason() {
        doAnswer(invocation -> {
            invocation.<GenerationMetric>getArgument(0).setId(1L);
            return 1;
        }).when(mapper).insert(any(GenerationMetric.class));

        // 构造长度超过 256 的 reason
        StringBuilder longReason = new StringBuilder();
        for (int i = 0; i < 30; i++) longReason.append("verbose_reason_segment_");
        RoutingDecision decision = RoutingDecision.builder()
                .strategy(CodeGenTypeEnum.HTML)
                .layer(Layer.RULE_LENGTH)
                .reason(longReason.toString())
                .confidence(1.0)
                .durationMs(1L)
                .userPrompt("p")
                .build();

        service.recordRouting(1L, 1L, decision);
        ArgumentCaptor<GenerationMetric> captor = ArgumentCaptor.forClass(GenerationMetric.class);
        verify(mapper).insert(captor.capture());
        assertTrue(captor.getValue().getRouterReason().length() <= 256);
    }

    @Test
    void records_routing_returns_null_on_db_exception() {
        doThrow(new RuntimeException("simulated DB failure"))
                .when(mapper).insert(any(GenerationMetric.class));

        RoutingDecision decision = RoutingDecision.builder()
                .strategy(CodeGenTypeEnum.HTML).layer(Layer.RULE_LENGTH).reason("r")
                .confidence(1.0).durationMs(1L).userPrompt("p").build();

        Long id = service.recordRouting(1L, 1L, decision);
        assertNull(id, "DB failure should yield null id, not throw");
    }

    @Test
    void completes_outcome_with_full_field_mapping() {
        GenerationMetricService.Outcome outcome = GenerationMetricService.Outcome.builder()
                .generationDurationMs(4500)
                .tokenInput(120)
                .tokenOutput(800)
                .costUsd(BigDecimal.valueOf(0.0123))
                .toolCallCount(5)
                .success(true)
                .errorMessage(null)
                .build();

        service.completeOutcome(42L, outcome);

        ArgumentCaptor<GenerationMetric> captor = ArgumentCaptor.forClass(GenerationMetric.class);
        verify(mapper, times(1)).update(captor.capture());
        GenerationMetric updated = captor.getValue();
        assertEquals(42L, updated.getId());
        assertEquals(4500, updated.getGenerationDurationMs());
        assertEquals(120, updated.getTokenInput());
        assertEquals(800, updated.getTokenOutput());
        assertEquals(0, BigDecimal.valueOf(0.0123).compareTo(updated.getCostUsd()));
        assertEquals(5, updated.getToolCallCount());
        assertEquals(true, updated.getSuccess());
        assertNull(updated.getErrorMessage());
    }

    @Test
    void completes_outcome_silently_skips_when_id_is_null() {
        // recordRouting 失败时 metricId 为 null —— completeOutcome 应安静跳过
        GenerationMetricService.Outcome outcome = GenerationMetricService.Outcome.builder()
                .success(true).build();

        service.completeOutcome(null, outcome);

        verify(mapper, times(0)).update(any(GenerationMetric.class));
    }

    @Test
    void completes_outcome_truncates_long_error_message() {
        StringBuilder longErr = new StringBuilder();
        for (int i = 0; i < 100; i++) longErr.append("error_message_part_");

        GenerationMetricService.Outcome outcome = GenerationMetricService.Outcome.builder()
                .success(false).errorMessage(longErr.toString()).build();

        service.completeOutcome(1L, outcome);

        ArgumentCaptor<GenerationMetric> captor = ArgumentCaptor.forClass(GenerationMetric.class);
        verify(mapper).update(captor.capture());
        assertTrue(captor.getValue().getErrorMessage().length() <= 512);
    }

    @Test
    void completes_outcome_swallows_db_exception() {
        doThrow(new RuntimeException("update failed"))
                .when(mapper).update(any(GenerationMetric.class));

        GenerationMetricService.Outcome outcome = GenerationMetricService.Outcome.builder()
                .success(true).build();

        // 不抛 —— 异常容错
        service.completeOutcome(1L, outcome);
        verify(mapper, times(1)).update(any(GenerationMetric.class));
    }

    @Test
    void records_routing_handles_null_strategy_and_layer_gracefully() {
        doAnswer(invocation -> {
            invocation.<GenerationMetric>getArgument(0).setId(1L);
            return 1;
        }).when(mapper).insert(any(GenerationMetric.class));

        RoutingDecision decision = RoutingDecision.builder()
                .strategy(null)  // 极端情况
                .layer(null)
                .reason(null)
                .confidence(0.0)
                .durationMs(0L)
                .userPrompt(null)
                .build();

        Long id = service.recordRouting(1L, 1L, decision);
        assertNotNull(id);
        ArgumentCaptor<GenerationMetric> captor = ArgumentCaptor.forClass(GenerationMetric.class);
        verify(mapper).insert(captor.capture());
        GenerationMetric saved = captor.getValue();
        assertNull(saved.getStrategy());
        assertNull(saved.getRouterLayer());
        assertNull(saved.getRouterReason());
    }
}
