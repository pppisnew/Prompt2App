package com.prompt2app.infra.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Prompt2App 业务配置（ADR-0010 配置统一化）。
 *
 * <p>所有外部可变参数集中在此处，业务代码通过依赖注入读取，禁止再使用 {@code System.getProperty("user.dir")}
 * 拼接路径或在常量类中硬编码 host / 端口。配置来源优先级：
 * <pre>
 * JVM -D > CLI -- > OS env > .env (spring-dotenv) > application-{profile}.yml > application.yml > 本类默认值
 * </pre>
 *
 * @see <a href="../../../../../../../docs/adr/0010-config-unification.md">ADR-0010</a>
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "prompt2app")
public class Prompt2AppProperties {

    /**
     * 文件存储路径配置（代码生成、部署、截图等）。
     */
    private Storage storage = new Storage();

    /**
     * AI 工具调用安全配额配置（参见 ADR-0008）。
     */
    private Tool tool = new Tool();

    /**
     * 启动时打印关键配置（脱敏），便于排错确认环境是否正确加载。
     */
    @PostConstruct
    public void logEffectiveConfig() {
        log.info("[Prompt2AppProperties] storage.codeOutputDir={}", storage.getCodeOutputDir());
        log.info("[Prompt2AppProperties] storage.codeDeployDir={}", storage.getCodeDeployDir());
        log.info("[Prompt2AppProperties] storage.screenshotsDir={}", storage.getScreenshotsDir());
        log.info("[Prompt2AppProperties] storage.codeDeployHost={}", storage.getCodeDeployHost());
        log.info("[Prompt2AppProperties] tool.maxPerSession={}, tool.maxPerFile={}",
                tool.getMaxPerSession(), tool.getMaxPerFile());
    }

    @Data
    public static class Storage {
        /** AI 代码生成输出根目录 */
        private String codeOutputDir = System.getProperty("user.dir") + "/tmp/code_output";
        /** 应用部署根目录（提供给静态资源访问） */
        private String codeDeployDir = System.getProperty("user.dir") + "/tmp/code_deploy";
        /** 网页截图保存目录 */
        private String screenshotsDir = System.getProperty("user.dir") + "/tmp/screenshots";
        /** 部署 host（拼接静态访问 URL 的前缀） */
        private String codeDeployHost = "http://localhost";
    }

    @Data
    public static class Tool {
        /** 单 session 最大工具调用次数（ADR-0008） */
        private int maxPerSession = 50;
        /** 单文件最大工具调用次数（ADR-0008） */
        private int maxPerFile = 10;
    }
}
