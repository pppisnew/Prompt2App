package com.prompt2app.eval;

import lombok.Builder;
import lombok.Value;

/**
 * Abstraction over "given a case, run the agent and collect outputs".
 *
 * <p>Phase 0 ships only {@link Stub}. Real implementations land in Phase 1+
 * (e.g. a {@code DirectServiceInvoker} that calls
 * {@code com.prompt2app.agent.AiCodeGeneratorService} once the surrounding
 * codebase compiles cleanly after the LangChain4j patch removal).
 *
 * <p>Implementations <b>must not throw</b> — failures are encoded into the
 * {@link InvocationResult}.
 */
public interface AgentInvoker {

    /** Identifier shown in reports (e.g., {@code stub}, {@code direct-service}). */
    String name();

    /** Invoke the agent for one case. Always returns; never throws. */
    InvocationResult invoke(EvalCase evalCase);

    /**
     * Multi-round variant: invoke with a round identifier so the invoker can isolate artifacts
     * across rounds (e.g. write to {@code tmp/code_output/round-N/...}). ADR-0013.
     *
     * <p>Default delegates to {@link #invoke(EvalCase)} — existing single-round callers
     * (and the {@link Stub}) need no changes. Implementations that care about round isolation
     * (e.g. {@code DirectServiceInvoker}) override this to honour {@code roundId}.
     *
     * @param roundId 1-based round number, or {@code null} for single-round legacy behaviour
     */
    default InvocationResult invoke(EvalCase evalCase, Integer roundId) {
        return invoke(evalCase);
    }

    @Value
    @Builder
    class InvocationResult {
        /** Concatenated text artifact (HTML body / merged file contents / etc). */
        String mergedOutput;
        /** Number of distinct files produced (1 for HTML; >1 for MultiFile / Vue). */
        int fileCount;
        /** Wall clock duration in milliseconds. */
        long durationMs;
        /** {@code true} iff the invocation completed without exception. Stub returns {@code false}. */
        boolean invoked;
        /**
         * {@code true} iff the generated artifact was successfully built into a renderable
         * output (e.g. VUE_PROJECT produced {@code dist/index.html}). For strategies without
         * a build step (HTML / MULTI_FILE) this stays {@code false} and is ignored by the
         * Render scorer (those branches key off {@link #mergedOutput}).
         *
         * <p>Render scorer (ADR-0005) reads this for VUE_PROJECT to decide veto, instead of
         * text-matching "package.json" inside {@code mergedOutput} (which fails when dist
         * contents — compiled JS — are merged back).
         */
        boolean buildSuccess;
        /** Free-text note — stub uses this to mark "not yet implemented". */
        String note;
    }

    /**
     * Default no-op invoker. Marks every case as "not invoked" with empty output.
     *
     * <p>Used by Phase 0 smoke runs to verify the pipeline (load → score → report)
     * without burning LLM tokens.
     */
    class Stub implements AgentInvoker {
        @Override
        public String name() {
            return "stub";
        }

        @Override
        public InvocationResult invoke(EvalCase evalCase) {
            return InvocationResult.builder()
                    .mergedOutput("")
                    .fileCount(0)
                    .durationMs(0L)
                    .invoked(false)
                    .note("Stub invoker — real LLM call deferred to Phase 1+.")
                    .build();
        }
    }
}
