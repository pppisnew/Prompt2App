package com.prompt2app.agent.tools.safety;

import com.prompt2app.infra.exception.ToolSafetyException;
import com.prompt2app.infra.exception.ToolSafetyException.Reason;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Layer 2 防御：工作目录绑定 + canonical 路径越权检查 + 关键文件保护。
 *
 * <p>每个 appId 创建一个 {@code Sandbox} 实例，绑定到该 app 的工作目录。
 * tool 通过 {@link #resolveForWrite(String)} / {@link #resolveForRead(String)}
 * / {@link #resolveForDelete(String)} 取得最终 {@link Path}，路径越权或操作受保护文件
 * 会抛出 {@link ToolSafetyException}。
 *
 * <p>三个 resolve 方法的差异：
 * <ul>
 *   <li>{@code resolveForRead} - 文件必须存在；canonical 检查通过 toRealPath</li>
 *   <li>{@code resolveForWrite} - 文件可不存在；先 normalize + startsWith 校验，
 *       再用 toRealPath 校验已存在的祖先目录（避免软链接祖先逃逸）</li>
 *   <li>{@code resolveForDelete} - 同 read，但额外校验关键文件黑名单</li>
 * </ul>
 *
 * <p>详见 ADR-0004。
 */
public final class Sandbox {

    /** 受保护的相对路径白名单。删除请求会拒绝匹配项。 */
    public static final Set<String> DEFAULT_GUARDED_PATHS = Set.of(
            "package.json",
            "package-lock.json",
            "pnpm-lock.yaml",
            "yarn.lock",
            "vite.config.js",
            "vite.config.ts",
            "tsconfig.json",
            "index.html"
    );

    private final Path workDir;
    private final Set<String> guardedPaths;

    public Sandbox(Path workDir) {
        this(workDir, DEFAULT_GUARDED_PATHS);
    }

    public Sandbox(Path workDir, Set<String> guardedPaths) {
        if (workDir == null) {
            throw new IllegalArgumentException("workDir must not be null");
        }
        // workDir 自身要 normalize 但不强制存在 —— sandbox 创建时 app 目录可能尚未建立
        this.workDir = workDir.toAbsolutePath().normalize();
        this.guardedPaths = Set.copyOf(guardedPaths);
    }

    /** 解析路径用于 read：要求目标已存在，软链接 canonical 检查。 */
    public Path resolveForRead(String relativePath) {
        Path resolved = resolveBasic(relativePath);
        if (!Files.exists(resolved)) {
            // read 要求目标存在；不存在不是安全错误，但提早返回避免下游 IO
            return resolved;
        }
        verifyCanonical(resolved, relativePath);
        return resolved;
    }

    /** 解析路径用于 write：目标可不存在，但已存在的祖先目录要 canonical 校验。 */
    public Path resolveForWrite(String relativePath) {
        Path resolved = resolveBasic(relativePath);
        // 找到最近的已存在祖先做 toRealPath 校验（防止软链接祖先逃逸）
        Path ancestor = resolved;
        while (ancestor != null && !Files.exists(ancestor)) {
            ancestor = ancestor.getParent();
        }
        if (ancestor != null && !ancestor.equals(workDir)) {
            // 若最近存在的祖先就是 workDir 自身则跳过（已在 resolveBasic 里校验）
            verifyCanonical(ancestor, relativePath);
        }
        return resolved;
    }

    /** 解析路径用于 delete：read 校验 + 关键文件黑名单。 */
    public Path resolveForDelete(String relativePath) {
        // 先做 normalize 后的相对路径黑名单校验
        String normalized = relativePath.replace('\\', '/');
        if (guardedPaths.contains(normalized)) {
            throw new ToolSafetyException(Reason.GUARDED_FILE, relativePath,
                    "guarded file cannot be deleted");
        }
        // 再做基础越权校验
        return resolveForRead(relativePath);
    }

    /** 暴露 workDir 给业务（例如显示给用户）。永远是 absolute + normalized。 */
    public Path workDir() {
        return workDir;
    }

    // ---- internal ----

    /** Layer 1 + 基础 normalize/startsWith 校验。共享逻辑。 */
    private Path resolveBasic(String relativePath) {
        PathValidator.validateRelative(relativePath);
        Path resolved = workDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(workDir)) {
            // 这条理论上 Layer 1 已经拦截（因为 Layer 1 拒绝 ..），
            // 但 normalize 后再次 verify 是 defense-in-depth。
            throw new ToolSafetyException(Reason.PATH_TRAVERSAL, relativePath,
                    "resolved path escapes workDir after normalize");
        }
        return resolved;
    }

    /** 用 toRealPath 做 canonical 校验（解析软链接），失败抛 SYMLINK_ESCAPE。 */
    private void verifyCanonical(Path path, String relativePath) {
        try {
            Path real = path.toRealPath();
            Path realWorkDir = workDir.toRealPath();
            if (!real.startsWith(realWorkDir)) {
                throw new ToolSafetyException(Reason.SYMLINK_ESCAPE, relativePath,
                        "canonical path escapes workDir (symlink?)");
            }
        } catch (IOException e) {
            throw new ToolSafetyException(Reason.SYMLINK_ESCAPE, relativePath,
                    "canonical path resolution failed: " + e.getMessage());
        }
    }
}
