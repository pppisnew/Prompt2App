package com.prompt2app.agent.tools.safety;

import com.prompt2app.infra.exception.ToolSafetyException;
import com.prompt2app.infra.exception.ToolSafetyException.Reason;

/**
 * Layer 1 防御：纯字符串层面的路径合法性校验。
 *
 * <p>在 tool 入口最早期阶段调用，无 IO、无 Spring 上下文、纯函数。
 *
 * <p>合法相对路径必须满足：
 * <ul>
 *   <li>非 null、非空</li>
 *   <li>不以 {@code /} 或 {@code ~} 开头</li>
 *   <li>不匹配 Windows 绝对路径模式（{@code C:\...} / {@code \\?\...}）</li>
 *   <li>不含 null byte（{@code \0}）或其它控制字符</li>
 *   <li>任一路径段都不是 {@code ..}（{@code .} 允许）</li>
 * </ul>
 *
 * <p>不做的事：
 * <ul>
 *   <li>不解析符号链接（Layer 2 的 canonical path 检查负责）</li>
 *   <li>不校验文件是否存在</li>
 *   <li>不做关键文件黑名单（Layer 2 负责）</li>
 * </ul>
 *
 * <p>详见 ADR-0004。
 */
public final class PathValidator {

    /** Windows 绝对路径模式：盘符 + 反斜杠 / UNC 长路径。 */
    private static final java.util.regex.Pattern WINDOWS_ABS =
            java.util.regex.Pattern.compile("^([A-Za-z]:[\\\\/]|\\\\\\\\\\?\\\\).*");

    private PathValidator() {
    }

    /**
     * 校验一条工具传入的"相对路径"参数。失败时抛出 {@link ToolSafetyException}。
     *
     * @param relativePath 待校验路径
     * @throws ToolSafetyException 任一规则失败
     */
    public static void validateRelative(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            throw new ToolSafetyException(Reason.PATH_INVALID, relativePath, "path is null or empty");
        }
        // null byte / 控制字符（Tab/换行除外）
        for (int i = 0; i < relativePath.length(); i++) {
            char c = relativePath.charAt(i);
            if (c == '\0') {
                throw new ToolSafetyException(Reason.PATH_INVALID, relativePath, "contains null byte");
            }
            if (Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r') {
                throw new ToolSafetyException(Reason.PATH_INVALID, relativePath, "contains control char");
            }
        }
        // 用户家目录前缀
        if (relativePath.startsWith("~")) {
            throw new ToolSafetyException(Reason.PATH_ABSOLUTE, relativePath, "starts with ~ (home dir)");
        }
        // POSIX 绝对路径
        if (relativePath.startsWith("/")) {
            throw new ToolSafetyException(Reason.PATH_ABSOLUTE, relativePath, "starts with / (POSIX absolute)");
        }
        // Windows 绝对路径
        if (WINDOWS_ABS.matcher(relativePath).matches()) {
            throw new ToolSafetyException(Reason.PATH_ABSOLUTE, relativePath, "Windows absolute path");
        }
        // .. 段（同时支持 / 和 \ 分隔符）
        String normalized = relativePath.replace('\\', '/');
        for (String seg : normalized.split("/")) {
            if ("..".equals(seg)) {
                throw new ToolSafetyException(Reason.PATH_TRAVERSAL, relativePath, "contains '..' segment");
            }
        }
    }
}
