package com.prompt2app.agent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.prompt2app.agent.tools.safety.Sandbox;
import com.prompt2app.agent.tools.safety.SandboxFactory;
import com.prompt2app.agent.tools.safety.ToolCallCounter;
import com.prompt2app.agent.tools.safety.ToolCallCounter.ToolKind;
import com.prompt2app.infra.exception.ToolSafetyException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * 文件目录读取工具
 * 使用 Hutool 简化文件操作
 *
 * <p>所有路径输入经过 {@link Sandbox#resolveForRead(String)} 三层防御（ADR-0004）。
 * 空路径会被替换为 {@code "."}（即工作目录本身）。
 */
@Slf4j
@Component
public class FileDirReadTool extends BaseTool {

    /**
     * 需要忽略的文件和目录
     */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules", ".git", "dist", "build", ".DS_Store",
            ".env", "target", ".mvn", ".idea", ".vscode", "coverage"
    );

    /**
     * 需要忽略的文件扩展名
     */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log", ".tmp", ".cache", ".lock"
    );

    private final SandboxFactory sandboxFactory;
    private final ToolCallCounter toolCallCounter;

    public FileDirReadTool(SandboxFactory sandboxFactory, ToolCallCounter toolCallCounter) {
        this.sandboxFactory = sandboxFactory;
        this.toolCallCounter = toolCallCounter;
    }

    @Tool("读取目录结构，获取指定目录下的所有文件和子目录信息")
    public String readDir(
            @P("目录的相对路径，为空则读取整个项目结构")
            String relativeDirPath,
            @ToolMemoryId Long appId
    ) {
        try {
            Sandbox sandbox = sandboxFactory.forVueApp(appId);
            // 空路径 → "." 表示工作目录本身（PathValidator 接受单 . 段）
            String safePath = StrUtil.isEmpty(relativeDirPath) ? "." : relativeDirPath;
            Path path = sandbox.resolveForRead(safePath);
            toolCallCounter.recordCall(appId, safePath, ToolKind.LIST);
            File targetDir = path.toFile();
            if (!targetDir.exists() || !targetDir.isDirectory()) {
                return "错误：目录不存在或不是目录 - " + relativeDirPath;
            }
            StringBuilder structure = new StringBuilder();
            structure.append("项目目录结构:\n");
            List<File> allFiles = FileUtil.loopFiles(targetDir, file -> !shouldIgnore(file.getName()));
            allFiles.stream()
                    .sorted((f1, f2) -> {
                        int depth1 = getRelativeDepth(targetDir, f1);
                        int depth2 = getRelativeDepth(targetDir, f2);
                        if (depth1 != depth2) {
                            return Integer.compare(depth1, depth2);
                        }
                        return f1.getPath().compareTo(f2.getPath());
                    })
                    .forEach(file -> {
                        int depth = getRelativeDepth(targetDir, file);
                        String indent = "  ".repeat(depth);
                        structure.append(indent).append(file.getName());
                    });
            return structure.toString();
        } catch (ToolSafetyException e) {
            log.warn("[Safety] readDir rejected: {}", e.getMessage());
            return "操作被拒绝（安全限制）: " + e.getReason() + " - " + relativeDirPath;
        } catch (Exception e) {
            String errorMessage = "读取目录结构失败: " + relativeDirPath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    /**
     * 计算文件相对于根目录的深度
     */
    private int getRelativeDepth(File root, File file) {
        Path rootPath = root.toPath();
        Path filePath = file.toPath();
        return rootPath.relativize(filePath).getNameCount() - 1;
    }

    /**
     * 判断是否应该忽略该文件或目录
     */
    private boolean shouldIgnore(String fileName) {
        // 检查是否在忽略名称列表中
        if (IGNORED_NAMES.contains(fileName)) {
            return true;
        }

        // 检查文件扩展名
        return IGNORED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    @Override
    public String getToolName() {
        return "readDir";
    }

    @Override
    public String getDisplayName() {
        return "读取目录";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeDirPath = arguments.getStr("relativeDirPath");
        if (StrUtil.isEmpty(relativeDirPath)) {
            relativeDirPath = "根目录";
        }
        return String.format("[工具调用] %s %s", getDisplayName(), relativeDirPath);
    }
} 