package com.prompt2app.agent.tools.safety;

import com.prompt2app.infra.config.Prompt2AppProperties;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 为给定 {@code appId} 构造 {@link Sandbox} 实例。
 *
 * <p>沙箱本身是无状态对象（每次调用现造），便于多线程使用。
 * 工厂模式避免在 Spring Bean 上挂 appId 状态。
 *
 * <p>详见 ADR-0004 §实施细节；代码输出根目录由 {@link Prompt2AppProperties} 提供（ADR-0010）。
 */
@Component
public class SandboxFactory {

    /** Vue 项目目录命名约定，与现有 FileWriteTool 等一致。 */
    private static final String VUE_PROJECT_PREFIX = "vue_project_";

    @Resource
    private Prompt2AppProperties properties;

    /** 为某个 Vue app 创建沙箱：workDir 为 {@code <codeOutputDir>/vue_project_{appId}/}。 */
    public Sandbox forVueApp(Long appId) {
        if (appId == null) {
            throw new IllegalArgumentException("appId must not be null");
        }
        Path workDir = Paths.get(properties.getStorage().getCodeOutputDir(),
                VUE_PROJECT_PREFIX + appId);
        return new Sandbox(workDir);
    }

    /** 为任意 workDir 创建沙箱（测试用 + 通用扩展）。 */
    public Sandbox forWorkDir(Path workDir) {
        return new Sandbox(workDir);
    }
}

