# Task Record · 2026-06-19 · Phase 8 配置统一化

> 单 session 完成 Phase 8 全部 DoD 项目。Charter v1.0 锁定后第一个增量。

## 0. 元信息

- **触发方**：用户审计指令（"新增统一的环境配置文件，用于集中管理项目中的所有可配置参数"）
- **分支**：`feature/ai-engineering-rebuild`
- **基线**：Phase 7 收尾时 95 测试 / 94 通过（1 pre-existing context-init 失败：`AiCodeGenTypeRoutingServiceTest` 需要 DB）
- **结果**：Phase 8 完成后 **95 / 94 完全持平**，无回归
- **Charter 影响**：v1.0 → v1.1（§4 增加"配置外部化"质量底线，§7 版本号变更）

## 1. 审计成果（5 处硬编码 + 3 处 SSOT 违反）

| # | 旧位置 | 类别 | 处置 |
| --- | --- | --- | --- |
| 1 | `AppConstant.CODE_OUTPUT_ROOT_DIR` | 路径硬编码（`System.getProperty("user.dir") + ...`） | 删除 → `prompt2app.storage.code-output-dir` |
| 2 | `AppConstant.CODE_DEPLOY_ROOT_DIR` | 路径硬编码 | 删除 → `prompt2app.storage.code-deploy-dir` |
| 3 | `AppConstant.CODE_DEPLOY_HOST` | host 硬编码（与 application.yml 重复） | 删除 → `prompt2app.storage.code-deploy-host` |
| 4 | `application.yml` 明文 `password: 123456` + 4× `<Your API Key>` + `your-secret-id` | 敏感值入库 | `${VAR:default}` 占位 + `.env` 注入 |
| 5 | `WebScreenshotUtils:61` `System.getProperty("user.dir") + "/tmp/screenshots/..."` | 路径硬编码 | 方法参数注入 `screenshotsRoot` |
| 6 | `CodeFileSaver:22`（`@Deprecated` 死代码） | SSOT 违反（与 AppConstant 重复） | **整文件删除**（无调用方） |
| 7 | `CodeFileSaverTemplate:23` `static final FILE_SAVE_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR` | SSOT 违反 | 改为构造器注入字段 |
| 8 | `AppServiceImpl:58` `@Value("${code.deploy-host:http://localhost}")` | 与 AppConstant + application.yml 三方重复 | 统一改为 `properties.getStorage().getCodeDeployHost()` |

## 2. 交付物清单

### 新增

- `docs/adr/0010-config-unification.md` — 4 备选方案对比 + 实施细节 + Charter 微补丁说明
- `src/main/java/com/prompt2app/infra/config/Prompt2AppProperties.java` — `@ConfigurationProperties(prefix="prompt2app")`，Storage + Tool 两个嵌套类，启动时打印生效配置（脱敏）
- `src/main/resources/application-dev.yml` — dev profile（gitignored）
- `src/main/resources/application-test.yml` — test profile（提交，CI 安全默认）
- `src/main/resources/application-prod.yml` — prod profile（gitignored，仅含开关，敏感值由 OS env 注入）
- `.env.example` — 项目根目录，含全部 ~50 个可配置项分组 + 注释 + 命名约定说明

### 修改

- `pom.xml` — 添加 `me.paulschwarz:spring-dotenv:4.0.0`；Lombok 版本改为 BOM 管理；compiler-plugin 加 `<proc>full</proc>`（修复 maven-compiler 3.14.0 注解处理回归）
- `src/main/resources/application.yml` — 全部值改 `${VAR:default}` 占位；新增 `prompt2app.*` + `code.deploy-host` 块；移除明文密码与 API key
- `src/main/java/com/prompt2app/infra/constant/AppConstant.java` — 删除 3 个部署变量常量，仅保留 `GOOD_APP_PRIORITY` + `DEFAULT_APP_PRIORITY`（业务规则）
- `src/main/java/com/prompt2app/agent/codegen/saver/CodeFileSaverTemplate.java` — 改为构造器注入 `fileSaveRootDir`
- `src/main/java/com/prompt2app/agent/codegen/saver/HtmlCodeFileSaverTemplate.java` / `MultiFileCodeFileSaverTemplate.java` — 加构造器
- `src/main/java/com/prompt2app/agent/codegen/saver/CodeFileSaverExecutor.java` — 由 static 工具类升级为 `@Component`，从 `Prompt2AppProperties` 读 rootDir
- `src/main/java/com/prompt2app/agent/codegen/AiCodeGeneratorFacade.java` — 注入 `Prompt2AppProperties` + `CodeFileSaverExecutor`，2 处 vue project path 改用配置
- `src/main/java/com/prompt2app/agent/tools/safety/SandboxFactory.java` — 注入 `Prompt2AppProperties`
- `src/main/java/com/prompt2app/agent/workflow/node/CodeGeneratorNode.java` — 通过 `SpringContextUtil.getBean` 获取 properties
- `src/main/java/com/prompt2app/app/controller/AppController.java` — 注入 properties；保留 `AppConstant.GOOD_APP_PRIORITY`（业务规则）
- `src/main/java/com/prompt2app/app/controller/StaticResourceController.java` — 注入 properties；同时修复 `Resource` 二义性导入
- `src/main/java/com/prompt2app/app/service/impl/AppServiceImpl.java` — 删除 `@Value` 字段，3 处路径与 host 全用 properties
- `src/main/java/com/prompt2app/app/service/impl/ScreenshotServiceImpl.java` — 注入 properties，传递截图目录
- `src/main/java/com/prompt2app/infra/utils/WebScreenshotUtils.java` — `saveWebPageScreenshot(url)` → `saveWebPageScreenshot(url, screenshotsRoot)`
- `src/test/java/com/prompt2app/infra/utils/WebScreenshotUtilsTest.java` — 适配新签名
- `.gitignore` — 添加 `.env` / `.env.local` / `application-dev.yml` / `application-prod.yml`
- `PROJECT_CHARTER.md` — §4 增加"配置外部化"行；§7 v1.0 → v1.1 + 微补丁说明
- `docs/adr/README.md` — 索引 ADR-0010
- `docs/architecture/system-overview.md` — 增加 §配置架构（来源优先级 + 关键组件 + 业务约束 + 已外化清单）
- `docs/roadmap/milestones.md` — Phase 8 行 + 详情 + 状态记录 2 行
- `docs/roadmap/current-phase.md` — Phase 8 收尾

### 删除

- `src/main/java/com/prompt2app/agent/codegen/CodeFileSaver.java` — `@Deprecated` 死代码，0 调用方

## 3. 关键决策记录

### 选 spring-dotenv 4.0.0 而非纯 OS env

`.env.example` 双重职责：① 配置项目录文档；② 新人 30 秒上手钩子（`cp .env.example .env` 即可启动）。换取一个 ~10KB 依赖。

### 保留 `application-local.yml` 兼容

老开发者本地拷贝可继续用；新增 `application-dev.yml` 作为团队约定的 dev profile，二者并存不冲突。

### `CodeFileSaverExecutor` 改 Spring Bean

原本是静态工具类。Phase 8 升级为 `@Component`：在 `@PostConstruct` 中从注入的 `Prompt2AppProperties` 读 rootDir，构造好两个 saver 模板。这同时移除了模板类对 `AppConstant` 的依赖。

### `CodeFileSaver.java` 直接删除

虽然名义是"删硬编码"，但该类标 `@Deprecated`、零引用，是已被 `CodeFileSaverExecutor + CodeFileSaverTemplate + Html/MultiFile 子类` 替代的早期版本——硬编码只是促因。

### Charter v1.0 → v1.1 走 §6 修订流程

ADR-0010 本身就是触发条件。修订极小（§4 加一行 + §7 版本号），不构成大版本。

## 4. 工具链坑（额外修复）

**问题**：mvn clean compile 在 JDK 21 + Spring Boot 3.5.4 + maven-compiler-plugin 3.14.0 下 Lombok 注解处理静默失败（`@Slf4j`、`@Getter`、`@Data`、`@Builder` 全部不生成）。

**根因**：maven-compiler-plugin 3.14.0 默认 `proc` 行为变更，需要显式声明 `<proc>full</proc>` 才会运行注解处理器。

**修复**：在 `pom.xml` 的 `<plugin>` 配置中加 `<proc>full</proc>`。同时把 `<dependency>` 与 `<annotationProcessorPath>` 中的 Lombok 显式版本（1.18.36）去掉，让 Spring Boot BOM（1.18.38）统一管理。

**意义**：这是 Phase 7 之后才会触发的环境问题（先前未升级 maven 或 plugin）。修复后 mvn compile + test 都恢复正常。

## 5. 验证

```
mvn compile         → BUILD SUCCESS（171 source files）
mvn test (子集)     → Tests run: 95, Failures: 0, Errors: 1, Skipped: 0
                       唯一 Error 是 pre-existing 的 AiCodeGenTypeRoutingServiceTest（要 DB），
                       与 Phase 1～7 各阶段一致 → 95/94 基线无回归
```

## 6. 后续守门

- 任何新增配置项必须走 `Prompt2AppProperties` + `application.yml ${VAR:default}` + `.env.example`，不允许业务代码出现 `System.getProperty("user.dir")` 拼接
- 任何新增第三方密钥 / 模型名 / host 必须先入 `.env.example` 再写代码引用
- 每次 Code Review 检查："新增 `@Value`" + "新增字符串字面量路径/host"两个红线
