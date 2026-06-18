package com.prompt2app.eval;

import lombok.Builder;
import lombok.Value;

/**
 * Abstraction over "given a case, run the agent and collect outputs".
 *
 * <p>Phase 0 ships only {@link Stub}. Real implementations land in Phase 1+
 * (e.g. a {@code DirectServiceInvoker} that calls
 * {@code com.prompt2app.ai.AiCodeGeneratorService} once the surrounding
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
