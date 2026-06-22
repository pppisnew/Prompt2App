package com.prompt2app.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RoundAggregator} 单元测试。
 *
 * <p>覆盖：全 100、全 0、混合分数、1 轮（stdev=0）、极端波动。
 * 精度断言：±0.01（保留 2 位小数的容忍）。
 */
class RoundAggregatorTest {

    private final RoundAggregator agg = new RoundAggregator();

    // Helper: build a minimal round result
    private RoundResult round(int roundId, double... caseScores) {
        RoundResult.RoundResultBuilder rb = RoundResult.builder()
                .roundId(roundId)
                .generatedAt("now")
                .schemaVersion(RoundResult.SCHEMA_VERSION)
                .invokerName("test");
        List<RoundResult.CaseRoundScore> cases = new java.util.ArrayList<>();
        String[] strategies = {"HTML", "MULTI_FILE", "VUE_PROJECT"};
        for (int i = 0; i < caseScores.length; i++) {
            double score = caseScores[i];
            String caseId = String.format("case-%03d", i + 1);
            String strategy = strategies[i % strategies.length];
            cases.add(RoundResult.CaseRoundScore.builder()
                    .caseId(caseId)
                    .strategy(strategy)
                    .finalScore(score)
                    .veto(score == 0)
                    .vetoBy(score == 0 ? "rubric" : null)
                    .invoked(true)
                    .perDim(Map.of("rubric", score, "render", score < 50 ? 0.0 : 100.0))
                    .build());
        }
        return rb.cases(cases).build();
    }

    @Test
    void allPerfectRounds() {
        // 3 rounds, 3 cases each, all 100
        List<RoundResult> rounds = List.of(round(1, 100, 100, 100),
                round(2, 100, 100, 100),
                round(3, 100, 100, 100));
        RoundAggregator.RoundedReport report = agg.aggregate(rounds);

        assertEquals(3, report.getNumRounds());
        assertEquals(100.0, report.getTotalMean(), 0.01);
        assertEquals(0.0, report.getTotalStdev(), 0.01);
        assertEquals(0, report.getVetoCount());
        // All cases: mean=100, stdev=0
        for (var cs : report.getCases()) {
            assertEquals(100.0, cs.getMean(), 0.01);
            assertEquals(0.0, cs.getStdev(), 0.01);
        }
    }

    @Test
    void allZeroRounds() {
        List<RoundResult> rounds = List.of(round(1, 0, 0, 0),
                round(2, 0, 0, 0),
                round(3, 0, 0, 0));
        var report = agg.aggregate(rounds);

        assertEquals(0.0, report.getTotalMean(), 0.01);
        assertEquals(0.0, report.getTotalStdev(), 0.01);
        assertEquals(3, report.getVetoCount());
    }

    @Test
    void mixedScores() {
        // 3 rounds × 3 cases: case-001: (100, 50, 100), case-002: (0, 100, 50), case-003: (80, 90, 70)
        List<RoundResult> rounds = List.of(
                round(1, 100, 0, 80),
                round(2, 50, 100, 90),
                round(3, 100, 50, 70));
        var report = agg.aggregate(rounds);
        assertEquals(3, report.getNumRounds());

        // Find case-001
        var c1 = report.getCases().stream().filter(c -> c.getCaseId().equals("case-001")).findFirst().orElseThrow();
        assertEquals(83.33, c1.getMean(), 0.01);  // (100+50+100)/3
        assertEquals(23.57, c1.getStdev(), 0.01); // stdev of 100,50,100

        var c2 = report.getCases().stream().filter(c -> c.getCaseId().equals("case-002")).findFirst().orElseThrow();
        assertEquals(50.0, c2.getMean(), 0.01);   // (0+100+50)/3
        assertEquals(40.82, c2.getStdev(), 0.01); // stdev of 0,100,50

        var c3 = report.getCases().stream().filter(c -> c.getCaseId().equals("case-003")).findFirst().orElseThrow();
        assertEquals(80.0, c3.getMean(), 0.01);   // (80+90+70)/3
        assertEquals(8.16, c3.getStdev(), 0.01);  // stdev of 80,90,70

        // Round totals: R1=(100+0+80)/3=60, R2=(50+100+90)/3=80, R3=(100+50+70)/3=73.33
        assertEquals(60.0, report.getRoundTotalScores()[0], 0.01);
        assertEquals(80.0, report.getRoundTotalScores()[1], 0.01);
        assertEquals(73.33, report.getRoundTotalScores()[2], 0.01);
        assertEquals(71.11, report.getTotalMean(), 0.01);  // (60+80+73.33)/3
        assertEquals(8.31, report.getTotalStdev(), 0.01);  // stdev of 60,80,73.33  √
    }

    @Test
    void singleRoundProducesZeroStdev() {
        List<RoundResult> rounds = List.of(round(1, 75, 80, 85));
        var report = agg.aggregate(rounds);

        assertEquals(1, report.getNumRounds());
        assertEquals(0.0, report.getTotalStdev(), 0.01);
        for (var cs : report.getCases()) {
            assertEquals(0.0, cs.getStdev(), 0.01);
        }
    }

    @Test
    void stdevHelperPureZero() {
        assertEquals(0.0, RoundAggregator.stdev(new double[]{100, 100, 100}, 100), 0.01);
        assertEquals(0.0, RoundAggregator.stdev(new double[]{0, 0, 0}, 0), 0.01);
    }

    @Test
    void stdevHelperExtreme() {
        // values: 0, 100; mean=50; stdev=50
        double s = RoundAggregator.stdev(new double[]{0, 100}, 50);
        assertEquals(50.0, s, 0.01);
    }
}