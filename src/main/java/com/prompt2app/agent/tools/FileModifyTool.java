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
import java.nio.file.StandardOpenOption;

/**
 * 文件修改工具
 * 支持 AI 通过工具调用的方式修改文件内容
 *
 * <p>所有路径输入经过 {@link Sandbox#resolveForRead(String)} 三层防御（ADR-0004）。
 */
@Slf4j
@Component
public class FileModifyTool extends BaseTool {

    private final SandboxFactory sandboxFactory;
    private final ToolCallCounter toolCallCounter;

    public FileModifyTool(SandboxFactory sandboxFactory, ToolCallCounter toolCallCounter) {
        this.sandboxFactory = sandboxFactory;
        this.toolCallCounter = toolCallCounter;
    }

    @Tool("修改文件内容，用新内容替换指定的旧内容")
    public String modifyFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @P("要替换的旧内容")
            String oldContent,
            @P("替换后的新内容")
            String newContent,
            @ToolMemoryId Long appId
    ) {
        try {
            Sandbox sandbox = sandboxFactory.forVueApp(appId);
            Path path = sandbox.resolveForRead(relativeFilePath);
            toolCallCounter.recordCall(appId, relativeFilePath, ToolKind.EDIT);
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return "错误：文件不存在或不是文件 - " + relativeFilePath;
            }
            String originalContent = Files.readString(path);
            if (!originalContent.contains(oldContent)) {
                return "警告：文件中未找到要替换的内容，文件未修改 - " + relativeFilePath;
            }
            String modifiedContent = originalContent.replace(oldContent, newContent);
            if (originalContent.equals(modifiedContent)) {
                return "信息：替换后文件内容未发生变化 - " + relativeFilePath;
            }
            Files.writeString(path, modifiedContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功修改文件: {}", path.toAbsolutePath());
            return "文件修改成功: " + relativeFilePath;
        } catch (ToolSafetyException e) {
            log.warn("[Safety] modifyFile rejected: {}", e.getMessage());
            return "操作被拒绝（安全限制）: " + e.getReason() + " - " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "修改文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "modifyFile";
    }

    @Override
    public String getDisplayName() {
        return "修改文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String oldContent = arguments.getStr("oldContent");
        String newContent = arguments.getStr("newContent");
        // 显示对比内容
        return String.format("""
                [工具调用] %s %s
                
                替换前：
                ```
                %s
                ```
                
                替换后：
                ```
                %s
                ```
                """, getDisplayName(), relativeFilePath, oldContent, newContent);
    }
}
