package com.prompt2app.infra.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.Setter;
import lombok.AccessLevel;
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
        /** AI 代码生成输出根目录（@Setter(NONE)：手写 setter 做空串/占位符兜底，避免 Lombok 覆盖） */
        @Setter(AccessLevel.NONE)
        private String codeOutputDir;
        /** 应用部署根目录（提供给静态资源访问） */
        @Setter(AccessLevel.NONE)
        private String codeDeployDir;
        /** 网页截图保存目录 */
        @Setter(AccessLevel.NONE)
        private String screenshotsDir;
        /** 部署 host（拼接静态访问 URL 的前缀，含端口 + context-path + /static） */
        private String codeDeployHost = "http://localhost:8123/api/static";

        /**
         * Setter 兜底：Spring Boot @ConfigurationProperties binding 会调它。
         * yml 给的空串 / null / 含未解析占位符 ${...} 时，回落到 System.getProperty("user.dir")。
         * 历史教训：Spring Boot 把空字符串视为"已设值"，字段默认值根本没机会生效；
         * 又 ${user.dir} 占位符不被解析会变字面量。两者都要在 setter 处理掉。
         */
        public void setCodeOutputDir(String codeOutputDir) {
            this.codeOutputDir = resolve(codeOutputDir, "tmp/code_output");
        }

        public void setCodeDeployDir(String codeDeployDir) {
            this.codeDeployDir = resolve(codeDeployDir, "tmp/code_deploy");
        }

        public void setScreenshotsDir(String screenshotsDir) {
            this.screenshotsDir = resolve(screenshotsDir, "tmp/screenshots");
        }

        private static String resolve(String configured, String defaultSub) {
            if (configured == null || configured.isBlank() || configured.contains("${")) {
                return System.getProperty("user.dir") + "/" + defaultSub;
            }
            return configured;
        }
    }

    @Data
    public static class Tool {
        /** 单 session 最大工具调用次数（ADR-0008） */
        private int maxPerSession = 50;
        /** 单文件最大工具调用次数（ADR-0008） */
        private int maxPerFile = 10;
    }
}
