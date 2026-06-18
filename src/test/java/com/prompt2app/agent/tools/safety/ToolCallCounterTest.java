package com.prompt2app.agent.tools.safety;

import com.prompt2app.agent.tools.safety.ToolCallCounter.ToolKind;
import com.prompt2app.infra.exception.ToolSafetyException;
import com.prompt2app.infra.exception.ToolSafetyException.Reason;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layer 3 · {@link ToolCallCounter} 单元测试。
 *
 * <p>覆盖：累计正常 / 总数超限 / 单文件超限 / 多 appId 隔离 / reset / 并发安全。
 */
class ToolCallCounterTest {

    @Test
    void records_calls_until_session_limit() {
        ToolCallCounter counter = ToolCallCounter.forTest(3, 100);
        Long appId = 1L;
        counter.recordCall(appId, "a.txt", ToolKind.WRITE);
        counter.recordCall(appId, "b.txt", ToolKind.WRITE);
        counter.recordCall(appId, "c.txt", ToolKind.WRITE);
        assertEquals(3, counter.totalOf(appId));
    }

    @Test
    void breaks_circuit_when_session_limit_exceeded() {
        ToolCallCounter counter = ToolCallCounter.forTest(2, 100);
        Long appId = 1L;
        counter.recordCall(appId, "a.txt", ToolKind.WRITE);
        counter.recordCall(appId, "b.txt", ToolKind.WRITE);
        ToolSafetyException ex = assertThrows(ToolSafetyException.class,
                () -> counter.recordCall(appId, "c.txt", ToolKind.WRITE));
        assertEquals(Reason.CIRCUIT_BREAKER, ex.getReason());
    }

    @Test
    void breaks_circuit_when_per_file_limit_exceeded() {
        ToolCallCounter counter = ToolCallCounter.forTest(100, 3);
        Long appId = 1L;
        for (int i = 0; i < 3; i++) {
            counter.recordCall(appId, "App.vue", ToolKind.EDIT);
        }
        ToolSafetyException ex = assertThrows(ToolSafetyException.class,
                () -> counter.recordCall(appId, "App.vue", ToolKind.EDIT));
        assertEquals(Reason.HIGH_FREQ_MOD, ex.getReason());
    }

    @Test
    void counters_are_isolated_per_app_id() {
        ToolCallCounter counter = ToolCallCounter.forTest(2, 2);
        // app 1 用满
        counter.recordCall(1L, "a.txt", ToolKind.WRITE);
        counter.recordCall(1L, "b.txt", ToolKind.WRITE);
        // app 2 应该是新的桶，不受影响
        counter.recordCall(2L, "c.txt", ToolKind.WRITE);
        counter.recordCall(2L, "d.txt", ToolKind.WRITE);

        assertEquals(2, counter.totalOf(1L));
        assertEquals(2, counter.totalOf(2L));
        // 各自再加一次都该爆
        assertThrows(ToolSafetyException.class,
                () -> counter.recordCall(1L, "e.txt", ToolKind.WRITE));
        assertThrows(ToolSafetyException.class,
                () -> counter.recordCall(2L, "f.txt", ToolKind.WRITE));
    }

    @Test
    void reset_clears_counters_for_app() {
        ToolCallCounter counter = ToolCallCounter.forTest(2, 2);
        counter.recordCall(1L, "a.txt", ToolKind.WRITE);
        counter.recordCall(1L, "b.txt", ToolKind.WRITE);
        assertEquals(2, counter.totalOf(1L));

        counter.reset(1L);
        assertEquals(0, counter.totalOf(1L));
        assertEquals(0, counter.perFileOf(1L, "a.txt"));

        // reset 后可以重新累计
        counter.recordCall(1L, "x.txt", ToolKind.WRITE);
        assertEquals(1, counter.totalOf(1L));
    }

    @Test
    void records_calls_with_null_path() {
        // 例如 ExitTool 没有路径参数
        ToolCallCounter counter = ToolCallCounter.forTest(5, 3);
        counter.recordCall(1L, null, ToolKind.OTHER);
        counter.recordCall(1L, null, ToolKind.OTHER);
        assertEquals(2, counter.totalOf(1L));
    }

    @Test
    void is_thread_safe_under_concurrent_calls() throws InterruptedException {
        ToolCallCounter counter = ToolCallCounter.forTest(1000, 1000);
        Long appId = 1L;
        int threads = 10;
        int callsPerThread = 50;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            int idx = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < callsPerThread; i++) {
                        counter.recordCall(appId, "thread-" + idx + ".txt", ToolKind.WRITE);
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS), "threads should finish in 5s");
        pool.shutdownNow();

        assertEquals(threads * callsPerThread, counter.totalOf(appId),
                "total count must equal exact number of recorded calls");
    }
}
