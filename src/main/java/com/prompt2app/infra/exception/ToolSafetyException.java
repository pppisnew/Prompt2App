package com.prompt2app.infra.exception;

import lombok.Getter;

/**
 * 工具安全异常 —— 所有 Tool Calling 三层防御产生的拒绝原因都用这个类型抛出。
 *
 * <p>由 {@link com.prompt2app.agent.tools.safety.PathValidator}、
 * {@link com.prompt2app.agent.tools.safety.Sandbox}、
 * {@link com.prompt2app.agent.tools.safety.ToolCallCounter} 三层防御类抛出。
 *
 * <p>设计为 RuntimeException，让 LangChain4j 的 tool framework 能直接捕获并把
 * 错误消息回传给 LLM，让 LLM 接收到 "PATH_TRAVERSAL: ..." 之类的反馈后调整策略。
 *
 * <p>详见 ADR-0004。
 */
@Getter
public class ToolSafetyException extends RuntimeException {

    /** 拒绝原因枚举。每条都对应一个明确的攻击模式或安全约束。 */
    public enum Reason {
        /** Layer 1：路径含 {@code ..} 段或解析后逃逸。 */
        PATH_TRAVERSAL,
        /** Layer 1：路径是绝对路径（{@code /...} 或 Windows {@code C:\...}）。 */
        PATH_ABSOLUTE,
        /** Layer 1：路径含 null byte / 控制字符 / 非法格式。 */
        PATH_INVALID,
        /** Layer 2：解析后的 canonical 路径超出工作目录（含软链接逃逸）。 */
        SYMLINK_ESCAPE,
        /** Layer 2：试图操作受保护的关键文件（{@code package.json} 等）。 */
        GUARDED_FILE,
        /** Layer 3：单会话工具调用次数超限，疑似死循环。 */
        CIRCUIT_BREAKER,
        /** Layer 3：同一文件被高频修改，疑似无意义循环。 */
        HIGH_FREQ_MOD
    }

    private final Reason reason;
    private final String relativePath;

    public ToolSafetyException(Reason reason, String relativePath, String detail) {
        super(reason.name() + ": " + detail + " (path=" + relativePath + ")");
        this.reason = reason;
        this.relativePath = relativePath;
    }

    public ToolSafetyException(Reason reason, String detail) {
        super(reason.name() + ": " + detail);
        this.reason = reason;
        this.relativePath = null;
    }
}
