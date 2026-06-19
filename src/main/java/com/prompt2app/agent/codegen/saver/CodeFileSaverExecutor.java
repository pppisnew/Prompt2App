package com.prompt2app.agent.codegen.saver;

import com.prompt2app.agent.model.HtmlCodeResult;
import com.prompt2app.agent.model.MultiFileCodeResult;
import com.prompt2app.app.model.enums.CodeGenTypeEnum;
import com.prompt2app.infra.config.Prompt2AppProperties;
import com.prompt2app.infra.exception.BusinessException;
import com.prompt2app.infra.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 代码文件保存执行器：根据代码生成类型分派到具体的 Saver。
 *
 * <p>Phase 8 配置外部化（ADR-0010）：本类升级为 Spring Bean，从 {@link Prompt2AppProperties}
 * 读取代码输出根目录，构造 Saver 模板时一次性注入；不再读取静态常量。
 */
@Component
public class CodeFileSaverExecutor {

    @Resource
    private Prompt2AppProperties properties;

    private HtmlCodeFileSaverTemplate htmlCodeFileSaver;
    private MultiFileCodeFileSaverTemplate multiFileCodeFileSaver;

    @PostConstruct
    public void init() {
        String rootDir = properties.getStorage().getCodeOutputDir();
        this.htmlCodeFileSaver = new HtmlCodeFileSaverTemplate(rootDir);
        this.multiFileCodeFileSaver = new MultiFileCodeFileSaverTemplate(rootDir);
    }

    /**
     * 执行代码保存。
     *
     * @param codeResult  代码结果对象
     * @param codeGenType 代码生成类型
     * @param appId       应用 ID
     * @return 保存的目录
     */
    public File executeSaver(Object codeResult, CodeGenTypeEnum codeGenType, Long appId) {
        return switch (codeGenType) {
            case HTML -> htmlCodeFileSaver.saveCode((HtmlCodeResult) codeResult, appId);
            case MULTI_FILE -> multiFileCodeFileSaver.saveCode((MultiFileCodeResult) codeResult, appId);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType);
        };
    }
}
