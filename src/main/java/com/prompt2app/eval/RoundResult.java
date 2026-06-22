package com.prompt2app.eval;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单轮评测结果（ADR-0013 多轮评测）。
 *
 * <p>双文件持久化（Task §4.3 选项 B）：
 * <ul>
 *   <li>{@code .json} —— 机器可读，断点续跑 + 聚合用，schemaVersion=1</li>
 *   <li>{@code .md}   —— 人读，单轮 markdown 报告（与最终报告同格式但只含本轮）</li>
 * </ul>
 *
 * <p>JSON 不含 {@code mergedOutput}（避免膨胀；mergedOutput 仅运行时用于 Scorer）。
 */
@Value
@Builder
@Jacksonized
@Slf4j
public class RoundResult {

    /** Schema version for forward-compat: 旧版无法加载就抛错而非静默跳过 */
    public static final int SCHEMA_VERSION = 1;

    /** 1-based round number */
    int roundId;
    /** ISO local datetime when this round finished */
    String generatedAt;
    /** Schema version (always SCHEMA_VERSION on write; checked on read) */
    int schemaVersion;
    /** Invoker name (for traceability) */
    String invokerName;
    /** Per-case scores */
    List<CaseRoundScore> cases;

    /** 单 case 单轮的最小评分快照（不含 mergedOutput） */
    @Value
    @Builder
    @Jacksonized
    public static class CaseRoundScore {
        String caseId;
        String strategy;
        /** Final composite score for this case in this round (0–100). */
        double finalScore;
        /** Whether any scorer vetoed. */
        boolean veto;
        /** Which dimension vetoed (or null if not vetoed). */
        String vetoBy;
        /** Was the agent invocation itself successful (no exception)? */
        boolean invoked;
        /**
         * Per-dimension raw scores (e.g. rubric → 80, render → 100, judge → 85).
         * Vetoed dimensions are recorded as -1 to distinguish from genuine 0.
         */
        Map<String, Double> perDim;
    }

    // ---------- factory ----------

    /** Build a RoundResult from a list of CompositeScorer.CaseFinalScore. */
    public static RoundResult fromFinalScores(int roundId, String invokerName,
                                              List<CompositeScorer.CaseFinalScore> finalScores,
                                              List<EvalCase> cases) {
        if (finalScores.size() != cases.size()) {
            throw new IllegalArgumentException("finalScores.size=" + finalScores.size()
                    + " must equal cases.size=" + cases.size());
        }
        Map<String, EvalCase> byId = new LinkedHashMap<>();
        for (EvalCase c : cases) byId.put(c.getId(), c);
        List<CaseRoundScore> caseScores = new ArrayList<>(finalScores.size());
        for (CompositeScorer.CaseFinalScore fs : finalScores) {
            EvalCase ec = byId.get(fs.getCaseId());
            String strategy = ec == null ? "?" : ec.getExpectedStrategy();
            Map<String, Double> perDim = new LinkedHashMap<>();
            String vetoBy = null;
            boolean invoked = true;  // invocation success is reflected by scorer outputs
            for (Scorer.ScoreContribution sc : fs.getContributions()) {
                perDim.put(sc.getDimension(), sc.isVeto() ? -1.0 : sc.getScore());
                if (sc.isVeto() && vetoBy == null) vetoBy = sc.getDimension();
            }
            caseScores.add(CaseRoundScore.builder()
                    .caseId(fs.getCaseId())
                    .strategy(strategy)
                    .finalScore(fs.getFinalScore())
                    .veto(fs.isVeto())
                    .vetoBy(vetoBy)
                    .invoked(invoked)
                    .perDim(perDim)
                    .build());
        }
        return RoundResult.builder()
                .roundId(roundId)
                .generatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .schemaVersion(SCHEMA_VERSION)
                .invokerName(invokerName)
                .cases(caseScores)
                .build();
    }

    // ---------- persistence ----------

    private static ObjectMapper mapper() {
        return new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public void writeJson(Path jsonPath) {
        try {
            if (jsonPath.getParent() != null) Files.createDirectories(jsonPath.getParent());
            mapper().writeValue(jsonPath.toFile(), this);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write round JSON: " + jsonPath, e);
        }
    }

    public static RoundResult readJson(Path jsonPath) {
        try {
            RoundResult r = mapper().readValue(jsonPath.toFile(), RoundResult.class);
            if (r.getSchemaVersion() != SCHEMA_VERSION) {
                throw new IllegalStateException("schemaVersion=" + r.getSchemaVersion()
                        + " differs from current " + SCHEMA_VERSION + " — please re-run round.");
            }
            return r;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read round JSON: " + jsonPath, e);
        }
    }
}
