package com.prompt2app.agent.codegen.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.prompt2app.infra.exception.BusinessException;
import com.prompt2app.infra.exception.ErrorCode;
import com.prompt2app.app.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 抽象代码文件保存器 - 模板方法模式。
 *
 * <p>根目录通过构造器注入（ADR-0010 配置外部化），由 {@code CodeFileSaverExecutor} 从
 * {@code Prompt2AppProperties.storage.codeOutputDir} 取值后传入，禁止在此硬编码。
 *
 * @param <T> 代码结果对象类型
 */
public abstract class CodeFileSaverTemplate<T> {

    /** 文件保存的根目录（来自 Prompt2AppProperties.storage.codeOutputDir）。 */
    private final String fileSaveRootDir;

    protected CodeFileSaverTemplate(String fileSaveRootDir) {
        this.fileSaveRootDir = fileSaveRootDir;
    }

    /**
     * 模板方法：保存代码的标准流程
     *
     * @param result 代码结果对象
     * @param appId 应用 ID
     * @return 保存的目录
     */
    public final File saveCode(T result, Long appId) {
        // 1. 验证输入
        validateInput(result);
        // 2. 构建唯一目录
        String baseDirPath = buildUniqueDir(appId);
        // 3. 保存文件（具体实现交给子类）
        saveFiles(result, baseDirPath);
        // 4. 返回文件目录对象
        return new File(baseDirPath);
    }

    /**
     * 写入单个文件的工具方法
     *
     * @param dirPath  目录路径
     * @param filename 文件名
     * @param content  文件内容
     */
    public final void writeToFile(String dirPath, String filename, String content) {
        if (StrUtil.isNotBlank(content)) {
            String filePath = dirPath + File.separator + filename;
            FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
        }
    }

    /**
     * 验证输入参数（可由子类覆盖）
     *
     * @param result 代码结果对象
     */
    protected void validateInput(T result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码结果对象不能为空");
        }
    }

    /**
     * 构建文件的唯一路径：tmp/code_output/bizType_雪花 ID
     *
     * @param appId 应用 ID
     * @return 目录路径
     */
    protected String buildUniqueDir(Long appId) {
        if (appId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        }
        String codeType = getCodeType().getValue();
        String uniqueDirName = StrUtil.format("{}_{}", codeType, appId);
        String dirPath = fileSaveRootDir + File.separator + uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 保存文件（具体实现交给子类）
     *
     * @param result      代码结果对象
     * @param baseDirPath 基础目录路径
     */
    protected abstract void saveFiles(T result, String baseDirPath);

    /**
     * 获取代码生成类型
     *
     * @return 代码生成类型枚举
     */
    protected abstract CodeGenTypeEnum getCodeType();
}
