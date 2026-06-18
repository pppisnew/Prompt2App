package com.prompt2app.agent.tools.safety;

import com.prompt2app.infra.exception.ToolSafetyException;
import com.prompt2app.infra.exception.ToolSafetyException.Reason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layer 2 · {@link Sandbox} 单元测试。
 *
 * <p>使用 JUnit 5 {@link TempDir} 创建真实临时目录，覆盖：
 * <ul>
 *   <li>正常 resolve / `..` resolve / 绝对路径 resolve</li>
 *   <li>canonical 越权（normalize 之后跳出 workDir）</li>
 *   <li>软链接逃逸（POSIX-only）</li>
 *   <li>关键文件保护（删除黑名单）</li>
 * </ul>
 */
class SandboxTest {

    @TempDir
    Path tempRoot;

    Path workDir;
    Sandbox sandbox;

    @BeforeEach
    void setUp() throws IOException {
        workDir = tempRoot.resolve("vue_project_1");
        Files.createDirectories(workDir);
        sandbox = new Sandbox(workDir);
    }

    @Test
    void resolves_valid_relative_path_inside_workdir() {
        Path resolved = sandbox.resolveForWrite("src/App.vue");
        assertTrue(resolved.startsWith(workDir.toAbsolutePath().normalize()),
                "resolved path should stay inside workDir");
    }

    @Test
    void rejects_dotdot_via_layer1() {
        ToolSafetyException ex = assertThrows(ToolSafetyException.class,
                () -> sandbox.resolveForWrite("../escape.txt"));
        assertEquals(Reason.PATH_TRAVERSAL, ex.getReason());
    }

    @Test
    void rejects_absolute_path_via_layer1() {
        ToolSafetyException ex = assertThrows(ToolSafetyException.class,
                () -> sandbox.resolveForWrite("/tmp/evil.sh"));
        assertEquals(Reason.PATH_ABSOLUTE, ex.getReason());
    }

    @Test
    void resolves_for_read_when_target_exists() throws IOException {
        Files.writeString(workDir.resolve("hello.txt"), "hi");
        assertDoesNotThrow(() -> sandbox.resolveForRead("hello.txt"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void rejects_symlink_escape_on_posix() throws IOException {
        // 在 workdir 内建一个软链接指向 workdir 外
        Path outsideTarget = tempRoot.resolve("outside-secret.txt");
        Files.writeString(outsideTarget, "secret");
        Path link = workDir.resolve("link-to-outside.txt");
        Files.createSymbolicLink(link, outsideTarget);

        // resolveForRead 应当用 toRealPath 解析软链接并发现越权
        ToolSafetyException ex = assertThrows(ToolSafetyException.class,
                () -> sandbox.resolveForRead("link-to-outside.txt"));
        assertEquals(Reason.SYMLINK_ESCAPE, ex.getReason());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void rejects_symlinked_ancestor_dir_for_write() throws IOException {
        // 把 workdir 下某子目录指向 workdir 外
        Path outsideDir = tempRoot.resolve("outside-dir");
        Files.createDirectory(outsideDir);
        Path linkDir = workDir.resolve("evil");
        Files.createSymbolicLink(linkDir, outsideDir);

        // resolveForWrite("evil/foo.txt") 的祖先 evil 是软链接 → 越权
        ToolSafetyException ex = assertThrows(ToolSafetyException.class,
                () -> sandbox.resolveForWrite("evil/foo.txt"));
        assertEquals(Reason.SYMLINK_ESCAPE, ex.getReason());
    }

    @Test
    void rejects_deletion_of_guarded_file() {
        // 即使文件不存在，黑名单校验也先于存在性检查
        ToolSafetyException ex = assertThrows(ToolSafetyException.class,
                () -> sandbox.resolveForDelete("package.json"));
        assertEquals(Reason.GUARDED_FILE, ex.getReason());
    }

    @Test
    void allows_writing_to_guarded_file() {
        // 写入 package.json 是允许的（AI 需要能创建/更新 package.json）
        // 仅删除被禁止
        assertDoesNotThrow(() -> sandbox.resolveForWrite("package.json"));
    }

    @Test
    void rejects_deletion_of_all_default_guarded_paths() {
        for (String guarded : Sandbox.DEFAULT_GUARDED_PATHS) {
            ToolSafetyException ex = assertThrows(ToolSafetyException.class,
                    () -> sandbox.resolveForDelete(guarded),
                    "should reject deletion of " + guarded);
            assertEquals(Reason.GUARDED_FILE, ex.getReason(),
                    "wrong reason for " + guarded);
        }
    }

    @Test
    void allows_deletion_of_normal_file() throws IOException {
        Files.writeString(workDir.resolve("temp.log"), "log content");
        assertDoesNotThrow(() -> sandbox.resolveForDelete("temp.log"));
    }

    @Test
    void resolves_nested_path_in_subdirectory() throws IOException {
        Path nested = workDir.resolve("src/components/HelloWorld.vue");
        Files.createDirectories(nested.getParent());
        Files.writeString(nested, "<template></template>");
        assertDoesNotThrow(() -> sandbox.resolveForRead("src/components/HelloWorld.vue"));
    }

    @Test
    void exposes_normalized_workdir() {
        Path exposed = sandbox.workDir();
        assertTrue(exposed.isAbsolute(), "workDir() must return absolute path");
        assertEquals(workDir.toAbsolutePath().normalize(), exposed);
    }
}
