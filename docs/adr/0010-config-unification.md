# ADR-0010：配置统一化（.env + Spring profiles + ConfigurationProperties）

- **状态**：Accepted
- **日期**：2026-06-19
- **决策者**：项目作者（指令）
- **相关 Phase**：Phase 8 · 配置统一化（v1.0 后增量）
- **相关 Issue / PR**：feature/ai-engineering-rebuild commit 待提交

---

## 背景（Context）

项目 v1.0 完成后，用户提出了"统一环境配置文件 + 杜绝硬编码"的明确需求。审计当前代码，发现配置散落在 4 个地方：

### 现状画像

```
1. AppConstant.java（接口）— 硬编码
   ├── CODE_OUTPUT_ROOT_DIR = user.dir + "/tmp/code_output"   ← 7 处 import 依赖
   ├── CODE_DEPLOY_ROOT_DIR = user.dir + "/tmp/code_deploy"   ← 多处 import
   └── CODE_DEPLOY_HOST     = "http://localhost"              ← 与 yml 冲突 ★

2. application.yml — 明文密钥
   ├── 数据库密码 123456                                       ← 入库即泄露
   ├── 4 处 <Your API Key> 占位（DeepSeek / Pexels / DashScope ...）
   └── COS your-secret-id / your-secret-key                    ← 模板化

3. 业务代码内拼接 user.dir
   ├── CodeFileSaver:22  user.dir + "/tmp/code_output"        ← 与 AppConstant 重复 ★
   └── WebScreenshotUtils:61  user.dir + "/tmp/screenshots/..." ← 又一处

4. 已部分外部化但不彻底
   └── AppServiceImpl:58  @Value("${code.deploy-host:...}")    ← 与 AppConstant.CODE_DEPLOY_HOST 重复 ★

★ 标记的是"同一概念被定义两次"——典型的 SSOT (Single Source of Truth) 违反
```

### 治理体系上的判断

`PROJECT_CHARTER.md §4 质量底线` 没有"配置外部化"硬约束，**这是 Charter v1.0 的盲点**。本 ADR 同时把这条原则补回 Charter（§4 增加一行）。

事实判断：

- **现有配置散落不直接违反任何 ADR**——Charter §3「明确不做」9 项均已落地
- **但违反 12-factor 应用的"配置应该是部署时的差异"原则**——明文密钥在 git history 永久存在
- **现状对作品集场景的影响**：低（自己用，能跑就行）；**对工程素养展示的影响**：高（招聘官第一眼看到 `password: 123456` 入库会扣分）
- **修复成本**：中（影响 8-10 个文件，但都是机械替换）

---

## 备选方案（Options）

### 方案 A：维持现状

- 优点：0 工作量
- 缺点：明文密钥进 git；招聘官看 README + 第一行 `application.yml` 即扣分；不符合 12-factor
- 长期成本：高（每次招聘都要解释；新人 onboarding 需要"我口头告诉你密钥"）

### 方案 B：纯环境变量 + Spring `${VAR:default}` 语法（无新依赖）

- 优点：Spring 原生支持，0 新依赖
- 缺点：
  - 开发者 onboarding 时要手工 `export FOO=...` 一堆变量
  - 无统一"配置清单"文件供查阅
  - IntelliJ run config 配 env vars 复杂
- 长期成本：中（每个新开发者都要被告知配哪些环境变量）

### 方案 C：`.env` 文件 + `spring-dotenv` 库（**本决策**）

- 添加 `me.paulschwarz:spring-dotenv` 依赖（轻量、Spring 原生集成）
- `.env` 在仓库根目录，被 `.gitignore` 排除
- `.env.example` 提交到 git，作为"配置清单 + 文档"
- `application.yml` 用 `${VAR:default}` 引用 `.env` 中的变量
- 三套 `application-{dev,test,prod}.yml` 实现 profile 隔离

优点：
- 业界标准（Node / Python / Ruby / Go 都是这套）
- 招聘官眼熟（"哦，正常的工程化项目"）
- `.env.example` 一份文件就是 onboarding 文档
- 与现有 `application-local.yml`（已在 .gitignore）思路一致，向上兼容

缺点：
- +1 依赖（`spring-dotenv` 单 JAR ~10 KB）
- 需要在 `pom.xml` 加 1 行 + 修 1 处 properties 块

### 方案 D：Spring Cloud Config Server / Consul / Vault

- 优点：生产级配置中心
- 缺点：需要单独服务 + 多机部署；与 Charter §1「保持复杂度可控」直接冲突
- 长期成本：极高（属于 Charter §3「明确不做」类）

---

## 决策（Decision）

**选择方案 C**：`.env` + `spring-dotenv` + Spring profiles 三件套。

### 实施细节

#### 1. 配置目录结构

```
项目根/
├── .env.example              ← 提交，配置清单 + 用途文档
├── .env                      ← 不提交（.gitignore），本地敏感值
│
├── src/main/resources/
│   ├── application.yml       ← 提交，base 配置，全部用 ${VAR:default}
│   ├── application-dev.yml   ← 不提交（.gitignore），开发覆盖
│   ├── application-test.yml  ← 提交，测试 profile（用 default 值）
│   ├── application-prod.yml  ← 不提交（.gitignore），生产覆盖
│   ├── application-local.yml ← 不提交（已存在），向后兼容
│   └── application-prod-sample.yml  ← 提交（已存在），生产示例
```

#### 2. Pom 依赖

```xml
<dependency>
    <groupId>me.paulschwarz</groupId>
    <artifactId>spring-dotenv</artifactId>
    <version>4.0.0</version>
</dependency>
```

`spring-dotenv` 通过 `EnvironmentPostProcessor` SPI 在 Spring 配置加载**之前**注入 `.env` 中的变量，让 `${VAR:default}` 能拿到值。

#### 3. application.yml 改造模式

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:prompt2app}
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:}        # 必须显式提供，无默认明文

langchain4j:
  open-ai:
    chat-model:
      api-key: ${DEEPSEEK_API_KEY:}
      base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
      model-name: ${DEEPSEEK_MODEL:deepseek-chat}

prompt2app:
  storage:
    code-output-dir: ${PROMPT2APP_CODE_OUTPUT_DIR:${user.dir}/tmp/code_output}
    code-deploy-dir: ${PROMPT2APP_CODE_DEPLOY_DIR:${user.dir}/tmp/code_deploy}
    screenshots-dir: ${PROMPT2APP_SCREENSHOTS_DIR:${user.dir}/tmp/screenshots}
    code-deploy-host: ${PROMPT2APP_CODE_DEPLOY_HOST:http://localhost}
  tool:
    max-per-session: ${TOOL_MAX_PER_SESSION:50}
    max-per-file: ${TOOL_MAX_PER_FILE:10}
```

#### 4. ConfigurationProperties 类

```java
@ConfigurationProperties(prefix = "prompt2app")
@Data
public class Prompt2AppProperties {
    private final Storage storage = new Storage();
    private final Tool tool = new Tool();

    @Data
    public static class Storage {
        private String codeOutputDir;
        private String codeDeployDir;
        private String screenshotsDir;
        private String codeDeployHost;
    }
    @Data
    public static class Tool {
        private int maxPerSession = 50;
        private int maxPerFile = 10;
    }
}
```

业务代码注入：
```java
@Resource
private Prompt2AppProperties props;

Path workDir = Paths.get(props.getStorage().getCodeOutputDir(), "vue_project_" + appId);
```

#### 5. AppConstant 拆分

| 常量 | 命运 |
| --- | --- |
| `GOOD_APP_PRIORITY = 99` | **保留**（业务规则常量，非配置） |
| `DEFAULT_APP_PRIORITY = 0` | **保留** |
| `CODE_OUTPUT_ROOT_DIR` | **删除**（迁到 `Prompt2AppProperties.storage.codeOutputDir`） |
| `CODE_DEPLOY_ROOT_DIR` | **删除**（迁到 props） |
| `CODE_DEPLOY_HOST` | **删除**（与 yml 重复，单一来源 = props） |

#### 6. .env.example 内容

```bash
# ===========================================
# Prompt2App 环境配置（.env.example）
# 复制为 .env 后填入真实值。.env 不会被 git 追踪。
# ===========================================

# ---- 环境标识 ----
SPRING_PROFILES_ACTIVE=dev          # dev / test / prod / local

# ---- 数据库 ----
DB_HOST=localhost
DB_PORT=3306
DB_NAME=prompt2app
DB_USER=root
DB_PASSWORD=                        # 必填

# ---- Redis ----
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# ---- LLM API Keys ----
DEEPSEEK_API_KEY=                   # https://platform.deepseek.com
DASHSCOPE_API_KEY=                  # https://dashscope.console.aliyun.com
PEXELS_API_KEY=                     # https://www.pexels.com/api

# ---- 应用存储路径（默认 ${user.dir}/tmp/...）----
PROMPT2APP_CODE_OUTPUT_DIR=
PROMPT2APP_CODE_DEPLOY_DIR=
PROMPT2APP_SCREENSHOTS_DIR=
PROMPT2APP_CODE_DEPLOY_HOST=http://localhost

# ---- 服务端口 ----
SERVER_PORT=8123

# ---- 日志级别 ----
LOG_LEVEL_ROOT=INFO
LOG_LEVEL_APP=DEBUG

# ---- Tool 安全阈值（Phase 3）----
TOOL_MAX_PER_SESSION=50
TOOL_MAX_PER_FILE=10

# ---- 腾讯云 COS（保留扩展位，ADR-0006 决定不强制启用）----
COS_HOST=
COS_SECRET_ID=
COS_SECRET_KEY=
COS_REGION=ap-shanghai
COS_BUCKET=
```

#### 7. .gitignore 增量

```
# 环境配置（敏感值）
.env
.env.local
application-dev.yml
application-prod.yml
# application-local.yml 已存在
```

#### 8. 配置优先级链（spring-dotenv 介入后）

```
（高） JVM 系统属性 -Dxxx
       命令行参数 --xxx
       OS 环境变量
       .env 文件   ← spring-dotenv 注入位置
       application-{profile}.yml   profile=local/dev/test/prod
       application.yml             基础默认值（带 :default）
（低） 代码默认值（@Value 后的 :default）
```

`.env` 比 `application.yml` 优先级高，比真 OS 环境变量低——意味着：
- 部署时通过真环境变量覆盖（容器最佳实践）
- 本地开发用 `.env`（一份文件管理所有变量）
- 测试环境用 default 值（无需配置）

---

## 代价（Consequences）

### 正面

- **彻底消除**："为什么 `password: 123456` 在 git？" 这种基础题被消灭
- **配置 SSOT**：每个值只有一个权威来源，不再有 `CODE_DEPLOY_HOST` 重复
- **Onboarding**：新开发者只需 `cp .env.example .env && vi .env`
- **生产部署**：直接用 OS 环境变量覆盖，符合容器/K8s 习惯
- **简历**：可量化加分项—— "应用 12-factor 配置外部化标准 + spring-dotenv + ConfigurationProperties + Spring profiles 四件套"

### 负面

- **+1 依赖**：`spring-dotenv` 4.0.0（10 KB JAR，单一职责，0 传递依赖）
- **8-10 个业务文件改 import / @Value 注入**：纯机械重构，但有出错风险
- **一些测试需要补 default 值**：现在大部分测试用 mock，不依赖 ENV
- **`application-local.yml` 旧用户**：保留向后兼容，不清理

### 中性

- 与 Charter §3「明确不做」9 项**完全不冲突**——本 ADR 是工程素养加强，不是新增能力
- 未来若上 K8s ConfigMap / Vault / Spring Cloud Config，`.env` 可平滑切换为 ConfigMap
- 可能需要更新 `Charter §4 质量底线`增加一行"配置外部化"——本 ADR 同步处理

---

## 复盘指标（Revisit Triggers）

满足下列任一条件时重新评估：

- 项目从单进程升级为多实例 → 考虑 Spring Cloud Config / ConfigMap
- 配置项数 > 50 → 拆分多个 `.env.{module}` 或引入配置中心
- 出现 `.env` 误提交事故 → 加 pre-commit hook
- `application-{dev,prod}.yml` 内容超过 100 行 → 重新拆分

---

## Charter v1.1 微补丁（同步触发）

本 ADR 触发 `PROJECT_CHARTER.md §4 质量底线`增加一行：

```diff
| 维度 | 底线 |
| --- | --- |
| 评测回归 | 任何影响 AI 行为的改动，必须跑评测集；分数下降 > 5% 不允许合入 |
| ADR 覆盖 | 任何新增/修改/废弃的架构选择，必须有 ADR；架构变更不允许"裸 commit" |
| Task Record | 任何持续 > 30 分钟的工作，必须留 Task Record |
| Phase 边界 | 不允许跨 Phase 开发；当前 Phase 见 docs/roadmap/current-phase.md |
| CI 红线 | Tool 安全测试（Phase 3 起）必须 100% 通过 |
+ | **配置外部化** | **任何新增可变值（密钥/路径/端口/阈值）必须经 `Prompt2AppProperties` 或 `application.yml` 暴露；禁止业务代码内硬编码** |
```

Charter §7 版本字段同步从 v1.0 → v1.1。

---

## 参考资料

- [`PROJECT_CHARTER.md`](../../PROJECT_CHARTER.md)（被本 ADR 微补丁修订）
- [12-factor.net §3 Config](https://12factor.net/config) ← 业界标准
- [spring-dotenv GitHub](https://github.com/paulschwarz/spring-dotenv) ← 选定依赖
- 现有相关文件：`src/main/java/com/prompt2app/infra/constant/AppConstant.java`、`src/main/resources/application.yml`、`.gitignore`
- 与本 ADR 思路一致的：[ADR-0001](./0001-modular-monolith.md)（SSOT 原则）、[ADR-0006](./0006-no-minio.md)（不引入复杂基建）
