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
 * 文件读取工具
 * 支持 AI 通过工具调用的方式读取文件内容
 *
 * <p>所有路径输入经过 {@link Sandbox#resolveForRead(String)} 三层防御（ADR-0004）。
 */
@Slf4j
@Component
public class FileReadTool extends BaseTool {

    private final SandboxFactory sandboxFactory;
    private final ToolCallCounter toolCallCounter;

    public FileReadTool(SandboxFactory sandboxFactory, ToolCallCounter toolCallCounter) {
        this.sandboxFactory = sandboxFactory;
        this.toolCallCounter = toolCallCounter;
    }

    @Tool("读取指定路径的文件内容")
    public String readFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @ToolMemoryId Long appId
    ) {
        try {
            Sandbox sandbox = sandboxFactory.forVueApp(appId);
            Path path = sandbox.resolveForRead(relativeFilePath);
            toolCallCounter.recordCall(appId, relativeFilePath, ToolKind.READ);
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return "错误：文件不存在或不是文件 - " + relativeFilePath;
            }
            return Files.readString(path);
        } catch (ToolSafetyException e) {
            log.warn("[Safety] readFile rejected: {}", e.getMessage());
            return "操作被拒绝（安全限制）: " + e.getReason() + " - " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "读取文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "readFile";
    }

    @Override
    public String getDisplayName() {
        return "读取文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        return String.format("[工具调用] %s %s", getDisplayName(), relativeFilePath);
    }
} 