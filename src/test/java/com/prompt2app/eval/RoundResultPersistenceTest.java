package com.prompt2app.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RoundResult} 序列化/反序列化契约测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>正常 JSON roundtrip：字段全部保留</li>
 *   <li>schemaVersion 不匹配时抛错（防脏数据混入）</li>
 * </ul>
 */
class RoundResultPersistenceTest {

    @Test
    void writeAndReadJson_roundtrip(@TempDir Path tmp) {
        RoundResult original = RoundResult.builder()
                .roundId(2)
                .generatedAt("2026-06-22T15:30:00")
                .schemaVersion(RoundResult.SCHEMA_VERSION)
                .invokerName("direct-service")
                .cases(List.of(
                        RoundResult.CaseRoundScore.builder()
                                .caseId("001-resume")
                                .strategy("HTML")
                                .finalScore(95.5)
                                .veto(false)
                                .vetoBy(null)
                                .invoked(true)
                                .perDim(Map.of("rubric", 95.5, "render", 100.0))
                                .build(),
                        RoundResult.CaseRoundScore.builder()
                                .caseId("002-vue-todo")
                                .strategy("VUE_PROJECT")
                                .finalScore(0.0)
                                .veto(true)
                                .vetoBy("render")
                                .invoked(true)
                                .perDim(Map.of("rubric", 80.0, "render", -1.0))
                                .build()
                ))
                .build();

        Path jsonPath = tmp.resolve("round-2.json");
        original.writeJson(jsonPath);
        assertTrue(jsonPath.toFile().exists());

        RoundResult restored = RoundResult.readJson(jsonPath);
        assertEquals(original.getRoundId(), restored.getRoundId());
        assertEquals(original.getGeneratedAt(), restored.getGeneratedAt());
        assertEquals(original.getSchemaVersion(), restored.getSchemaVersion());
        assertEquals(original.getInvokerName(), restored.getInvokerName());
        assertEquals(original.getCases().size(), restored.getCases().size());

        RoundResult.CaseRoundScore c0 = restored.getCases().get(0);
        assertEquals("001-resume", c0.getCaseId());
        assertEquals(95.5, c0.getFinalScore());
        assertFalse(c0.isVeto());

        RoundResult.CaseRoundScore c1 = restored.getCases().get(1);
        assertEquals("002-vue-todo", c1.getCaseId());
        assertEquals(0.0, c1.getFinalScore());
        assertTrue(c1.isVeto());
        assertEquals("render", c1.getVetoBy());
        assertEquals(-1.0, c1.getPerDim().get("render"));  // veto marker preserved
    }

    @Test
    void readJson_schemaVersionMismatch_throws(@TempDir Path tmp) throws Exception {
        Path p = tmp.resolve("bad.json");
        // Manually craft a JSON with wrong schemaVersion
        String wrongJson = """
                {
                  "roundId": 1,
                  "generatedAt": "now",
                  "schemaVersion": 999,
                  "invokerName": "x",
                  "cases": []
                }
                """;
        java.nio.file.Files.writeString(p, wrongJson);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> RoundResult.readJson(p));
        assertTrue(ex.getMessage().contains("schemaVersion"));
    }

    @Test
    void fromFinalScores_buildsCorrectShape() {
        EvalCase ec = new EvalCase();
        ec.setId("c1");
        ec.setTitle("t");
        ec.setExpectedStrategy("HTML");
        var rubricContrib = Scorer.ScoreContribution.builder()
                .dimension("rubric").score(80.0).veto(false).detail("ok").build();
        var renderContrib = Scorer.ScoreContribution.builder()
                .dimension("render").score(0.0).veto(true).detail("missing body").build();
        var fs = CompositeScorer.CaseFinalScore.builder()
                .caseId("c1").finalScore(0.0).veto(true)
                .contributions(List.of(rubricContrib, renderContrib))
                .summary("vetoed").build();

        RoundResult rr = RoundResult.fromFinalScores(1, "test", List.of(fs), List.of(ec));
        assertEquals(1, rr.getRoundId());
        assertEquals(1, rr.getCases().size());
        var crs = rr.getCases().get(0);
        assertEquals("c1", crs.getCaseId());
        assertEquals("HTML", crs.getStrategy());
        assertTrue(crs.isVeto());
        assertEquals("render", crs.getVetoBy());
        assertEquals(80.0, crs.getPerDim().get("rubric"));
        assertEquals(-1.0, crs.getPerDim().get("render"));  // veto encoded as -1
    }
}