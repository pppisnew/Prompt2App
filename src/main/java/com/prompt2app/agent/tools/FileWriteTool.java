package com.prompt2app.agent.tools;

import cn.hutool.core.io.FileUtil;
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
 * 文件写入工具
 * 支持 AI 通过工具调用的方式写入文件
 *
 * <p>所有路径输入经过 {@link Sandbox#resolveForWrite(String)} 三层防御（ADR-0004）。
 */
@Slf4j
@Component
public class FileWriteTool extends BaseTool {

    private final SandboxFactory sandboxFactory;
    private final ToolCallCounter toolCallCounter;

    public FileWriteTool(SandboxFactory sandboxFactory, ToolCallCounter toolCallCounter) {
        this.sandboxFactory = sandboxFactory;
        this.toolCallCounter = toolCallCounter;
    }

    @Tool("写入文件到指定路径")
    public String writeFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @P("要写入文件的内容")
            String content,
            @ToolMemoryId Long appId
    ) {
        try {
            Sandbox sandbox = sandboxFactory.forVueApp(appId);
            // Layer 1 + Layer 2：路径校验 + 工作目录绑定
            Path path = sandbox.resolveForWrite(relativeFilePath);
            // Layer 3：调用次数熔断
            toolCallCounter.recordCall(appId, relativeFilePath, ToolKind.WRITE);
            // 创建父目录（如果不存在）
            Path parentDir = path.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            Files.write(path, content.getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功写入文件: {}", path.toAbsolutePath());
            // 注意要返回相对路径，不能让 AI 把文件绝对路径返回给用户
            return "文件写入成功: " + relativeFilePath;
        } catch (ToolSafetyException e) {
            log.warn("[Safety] writeFile rejected: {}", e.getMessage());
            return "操作被拒绝（安全限制）: " + e.getReason() + " - " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "文件写入失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "writeFile";
    }

    @Override
    public String getDisplayName() {
        return "写入文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String suffix = FileUtil.getSuffix(relativeFilePath);
        String content = arguments.getStr("content");
        return String.format("""
                        [工具调用] %s %s
                        ```%s
                        %s
                        ```
                        """, getDisplayName(), relativeFilePath, suffix, content);
    }
}
