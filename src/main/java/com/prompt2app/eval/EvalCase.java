package com.prompt2app.eval;

import lombok.Data;

import java.util.List;

/**
 * Java mirror of {@code eval/schema/case.schema.yaml}.
 *
 * <p>Field-name conversion (snake_case YAML keys ↔ camelCase Java fields)
 * is handled in {@link EvalCaseLoader} via a custom {@code PropertyUtils},
 * so this POJO uses standard Java conventions.
 */
@Data
public class EvalCase {

    private String id;
    private String title;
    /** {@code easy | medium | hard} */
    private String difficulty;
    /** {@code HTML | MULTI_FILE | VUE_PROJECT} */
    private String expectedStrategy;
    private List<String> tags;
    private String prompt;
    private Rubric rubric;
    private Baseline baseline;
    private String notes;

    @Data
    public static class Rubric {
        private List<String> mustContain;
        private List<String> mustNotContain;
        private List<String> llmJudgeDimensions;
        /** Required for {@code MULTI_FILE} / {@code VUE_PROJECT}; null for HTML. */
        private Integer minFiles;
    }

    /**
     * Optional baseline metrics. Populated by the evaluator after a real run;
     * left null until then.
     */
    @Data
    public static class Baseline {
        private Boolean compileOk;
        private Boolean renderNonEmpty;
        private Double llmJudgeScore;
        private Long durationMs;
        private Integer tokenInput;
        private Integer tokenOutput;
        private Double costUsd;
        private String recordedAt;
        private String model;
    }
}
