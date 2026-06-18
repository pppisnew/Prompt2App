package com.prompt2app.agent.tools.safety;

import com.prompt2app.infra.exception.ToolSafetyException;
import com.prompt2app.infra.exception.ToolSafetyException.Reason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Layer 3 防御：跨调用维度的行为熔断。
 *
 * <p>按 {@code appId} 隔离计数，进程内单例。
 *
 * <p>两类约束：
 * <ul>
 *   <li><b>会话总调用上限</b>：单 app 累计工具调用 ≤ {@code maxPerSession}（默认 50）</li>
 *   <li><b>同文件高频修改上限</b>：单 app 同一相对路径调用 ≤ {@code maxPerFile}（默认 10）</li>
 * </ul>
 *
 * <p>超限时抛 {@link ToolSafetyException}，LangChain4j 会把错误消息传给 LLM，
 * 让模型主动结束会话。
 *
 * <p>详见 ADR-0004。
 */
@Slf4j
@Component
public class ToolCallCounter {

    /** 工具调用类型。用于扩展性 / 日志区分。 */
    public enum ToolKind {
        READ, WRITE, EDIT, DELETE, LIST, OTHER
    }

    private final Map<Long, Counters> perApp = new ConcurrentHashMap<>();
    private final int maxPerSession;
    private final int maxPerFile;

    /** Spring DI 构造（生产路径）。 */
    public ToolCallCounter(
            @Value("${prompt2app.tool.max-per-session:50}") int maxPerSession,
            @Value("${prompt2app.tool.max-per-file:10}") int maxPerFile) {
        this.maxPerSession = maxPerSession;
        this.maxPerFile = maxPerFile;
    }

    /** 测试构造，可绕开 Spring。 */
    public static ToolCallCounter forTest(int maxPerSession, int maxPerFile) {
        return new ToolCallCounter(maxPerSession, maxPerFile);
    }

    /**
     * 记录一次工具调用。失败时抛 {@link ToolSafetyException}。
     *
     * @param appId         应用标识（来自 {@code @ToolMemoryId}）
     * @param relativePath  目标相对路径（用于按文件计数；可为 null 表示无文件参数）
     * @param kind          工具类型（仅用于日志）
     * @throws ToolSafetyException 总数 / 单文件超限
     */
    public void recordCall(Long appId, String relativePath, ToolKind kind) {
        if (appId == null) {
            // 未提供 appId 视为单进程共享桶（不应发生，但保守容忍）
            appId = -1L;
        }
        Counters c = perApp.computeIfAbsent(appId, k -> new Counters());
        int total = c.total.incrementAndGet();
        if (total > maxPerSession) {
            log.warn("[Layer3] CIRCUIT_BREAKER appId={} total={} kind={}", appId, total, kind);
            throw new ToolSafetyException(Reason.CIRCUIT_BREAKER, relativePath,
                    "session tool calls exceeded " + maxPerSession);
        }
        if (relativePath != null) {
            int perFile = c.perFile.computeIfAbsent(relativePath, k -> new AtomicInteger())
                    .incrementAndGet();
            if (perFile > maxPerFile) {
                log.warn("[Layer3] HIGH_FREQ_MOD appId={} path={} count={} kind={}",
                        appId, relativePath, perFile, kind);
                throw new ToolSafetyException(Reason.HIGH_FREQ_MOD, relativePath,
                        "file modified " + perFile + " times exceeds " + maxPerFile);
            }
        }
    }

    /** 重置某个 app 的计数（一般在会话结束 / 新会话开始时调用）。 */
    public void reset(Long appId) {
        if (appId != null) {
            perApp.remove(appId);
        }
    }

    /** 当前 appId 的总计数；为测试用。 */
    public int totalOf(Long appId) {
        Counters c = perApp.get(appId);
        return c == null ? 0 : c.total.get();
    }

    /** 当前 appId 单文件计数；为测试用。 */
    public int perFileOf(Long appId, String relativePath) {
        Counters c = perApp.get(appId);
        if (c == null) return 0;
        AtomicInteger v = c.perFile.get(relativePath);
        return v == null ? 0 : v.get();
    }

    private static final class Counters {
        final AtomicInteger total = new AtomicInteger();
        final Map<String, AtomicInteger> perFile = new ConcurrentHashMap<>();
    }
}
