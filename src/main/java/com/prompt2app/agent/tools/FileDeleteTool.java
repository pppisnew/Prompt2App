package com.prompt2app.agent.tools;

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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件删除工具
 * 支持 AI 通过工具调用的方式删除文件
 *
 * <p>所有路径输入经过 {@link Sandbox#resolveForDelete(String)} 三层防御（ADR-0004），
 * 包括关键文件（package.json 等）的完整相对路径黑名单（不再依赖文件名匹配）。
 */
@Slf4j
@Component
public class FileDeleteTool extends BaseTool {

    private final SandboxFactory sandboxFactory;
    private final ToolCallCounter toolCallCounter;

    public FileDeleteTool(SandboxFactory sandboxFactory, ToolCallCounter toolCallCounter) {
        this.sandboxFactory = sandboxFactory;
        this.toolCallCounter = toolCallCounter;
    }

    @Tool("删除指定路径的文件")
    public String deleteFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @ToolMemoryId Long appId
    ) {
        try {
            Sandbox sandbox = sandboxFactory.forVueApp(appId);
            // Layer 1 + 2 + 关键文件黑名单（一次性校验）
            Path path = sandbox.resolveForDelete(relativeFilePath);
            // Layer 3：调用次数熔断
            toolCallCounter.recordCall(appId, relativeFilePath, ToolKind.DELETE);
            if (!Files.exists(path)) {
                return "警告：文件不存在，无需删除 - " + relativeFilePath;
            }
            if (!Files.isRegularFile(path)) {
                return "错误：指定路径不是文件，无法删除 - " + relativeFilePath;
            }
            Files.delete(path);
            log.info("成功删除文件: {}", path.toAbsolutePath());
            return "文件删除成功: " + relativeFilePath;
        } catch (ToolSafetyException e) {
            log.warn("[Safety] deleteFile rejected: {}", e.getMessage());
            return "操作被拒绝（安全限制）: " + e.getReason() + " - " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "删除文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "deleteFile";
    }

    @Override
    public String getDisplayName() {
        return "删除文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        return String.format(" [工具调用] %s %s", getDisplayName(), relativeFilePath);
    }
}
