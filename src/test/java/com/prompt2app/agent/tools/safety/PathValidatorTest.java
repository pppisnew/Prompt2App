package com.prompt2app.agent.tools.safety;

import com.prompt2app.infra.exception.ToolSafetyException;
import com.prompt2app.infra.exception.ToolSafetyException.Reason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Layer 1 · {@link PathValidator} 单元测试。
 *
 * <p>每条 case 对应 ADR-0004 §实施细节中的一条 schema 校验规则。
 * 共 8 条，纯字符串校验，零 IO。
 */
class PathValidatorTest {

    @Test
    void rejects_null_or_empty() {
        ToolSafetyException ex1 = assertThrows(ToolSafetyException.class,
                () -> PathValidator.validateRelative(null));
        assertEquals(Reason.PATH_INVALID, ex1.getReason());

        ToolSafetyException ex2 = assertThrows(ToolSafetyException.class,
                () -> PathValidator.validateRelative(""));
        assertEquals(Reason.PATH_INVALID, ex2.getReason());
    }

    @Test
    void rejects_posix_absolute_path() {
        ToolSafetyException ex = assertThrows(ToolSafetyException.class,
                () -> PathValidator.validateRelative("/etc/passwd"));
        assertEquals(Reason.PATH_ABSOLUTE, ex.getReason());
    }

    @Test
    void rejects_home_dir_prefix() {
        ToolSafetyException ex = assertThrows(ToolSafetyException.class,
                () -> PathValidator.validateRelative("~/secret"));
        assertEquals(Reason.PATH_ABSOLUTE, ex.getReason());
    }

    @Test
    void rejects_windows_absolute_path() {
        ToolSafetyException ex1 = assertThrows(ToolSafetyException.class,
                () -> PathValidator.validateRelative("C:\\Windows\\system32"));
        assertEquals(Reason.PATH_ABSOLUTE, ex1.getReason());

        ToolSafetyException ex2 = assertThrows(ToolSafetyException.class,
                () -> PathValidator.validateRelative("\\\\?\\C:\\Windows"));
        assertEquals(Reason.PATH_ABSOLUTE, ex2.getReason());
    }

    @Test
    void rejects_dotdot_segment() {
        ToolSafetyException ex1 = assertThrows(ToolSafetyException.class,
                () -> PathValidator.validateRelative("../../etc/passwd"));
        assertEquals(Reason.PATH_TRAVERSAL, ex1.getReason());

        ToolSafetyException ex2 = assertThrows(ToolSafetyException.class,
                () -> PathValidator.validateRelative("src/../../escape"));
        assertEquals(Reason.PATH_TRAVERSAL, ex2.getReason());
    }

    @Test
    void rejects_dotdot_with_backslash_separator() {
        ToolSafetyException ex = assertThrows(ToolSafetyException.class,
                () -> PathValidator.validateRelative("src\\..\\escape"));
        assertEquals(Reason.PATH_TRAVERSAL, ex.getReason());
    }

    @Test
    void rejects_null_byte_and_control_chars() {
        ToolSafetyException ex1 = assertThrows(ToolSafetyException.class,
                () -> PathValidator.validateRelative("foo\0bar"));
        assertEquals(Reason.PATH_INVALID, ex1.getReason());

        ToolSafetyException ex2 = assertThrows(ToolSafetyException.class,
                () -> PathValidator.validateRelative("foo\u0007bar"));
        assertEquals(Reason.PATH_INVALID, ex2.getReason());
    }

    @Test
    void accepts_valid_relative_paths() {
        assertDoesNotThrow(() -> PathValidator.validateRelative("src/App.vue"));
        assertDoesNotThrow(() -> PathValidator.validateRelative("package.json"));
        assertDoesNotThrow(() -> PathValidator.validateRelative("a/b/c/d.txt"));
        // 单个 . 段（current dir）不算 ..，应通过
        assertDoesNotThrow(() -> PathValidator.validateRelative("./foo/bar"));
        // tab/换行虽然控制字符但实际罕见用例放过 —— Layer 1 只防主要风险
        assertDoesNotThrow(() -> PathValidator.validateRelative("foo\tbar"));
    }
}
