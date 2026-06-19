# System Overview

> **状态**：Stub + Phase 8 增量章节
> **对应 Phase**：Phase 1（模块化单体）+ Phase 8（配置统一化）
> **相关 ADR**：ADR-0001 / ADR-0010

---

## 待写内容（占位）

- [ ] 整体架构图（前端 / 后端单体 / 数据层 / LLM 接入）
- [ ] 模块边界与依赖方向
- [ ] 用户请求的端到端数据流（含 SSE）
- [ ] 部署形态（单进程 + Vue 静态产物）

---

## 配置架构（ADR-0010 / Phase 8）

### 配置来源优先级（高 → 低）

```
JVM -D                              # 单次启动覆盖
  ↓
CLI --param                         # spring-boot run 参数
  ↓
OS 环境变量                         # 容器/CI 注入
  ↓
.env （spring-dotenv 4.0.0 加载）   # 本地开发便捷入口
  ↓
application-{profile}.yml           # 环境差异（dev/test/prod）
  ↓
application.yml                     # 基础默认（${VAR:default}）
  ↓
@ConfigurationProperties 字段默认值 # 最后兜底
```

### 关键组件

| 组件 | 位置 | 作用 |
| --- | --- | --- |
| `Prompt2AppProperties` | `infra/config/Prompt2AppProperties.java` | `@ConfigurationProperties(prefix="prompt2app")`，业务代码统一注入入口 |
| `application.yml` | `src/main/resources/` | 所有值用 `${VAR:default}` 占位，无明文密钥 |
| `application-{dev,test,prod}.yml` | `src/main/resources/` | profile 差异化覆盖（test 提交，dev/prod 入 `.gitignore`） |
| `.env.example` | 项目根目录 | 含全部可配置项与说明的模板（提交） |
| `.env` | 项目根目录 | 真实敏感值（**不提交**，自 `cp .env.example .env` 衍生） |
| `spring-dotenv` | `pom.xml` | 通过 SPI 在 `application.yml` 解析前注入 `.env` 到 Environment |

### 业务约束

- 业务代码**禁止**：`System.getProperty("user.dir") + ...`、写死 host/port/path/key、使用 `@Value` 拼接环境变量
- 业务代码**应当**：`@Resource Prompt2AppProperties properties; properties.getStorage().getXxxDir()`
- 新增配置项**必须**：① `Prompt2AppProperties` 字段；② `application.yml` 用 `${VAR:default}`；③ `.env.example` 加注释；④ 命名遵循 `PROMPT2APP_*` / `LLM_*` / `DB_*` / `REDIS_*` 前缀约定

### 已被外化的硬编码（Phase 8 清单）

| 旧位置 | 新位置 |
| --- | --- |
| `AppConstant.CODE_OUTPUT_ROOT_DIR` | `prompt2app.storage.code-output-dir` |
| `AppConstant.CODE_DEPLOY_ROOT_DIR` | `prompt2app.storage.code-deploy-dir` |
| `AppConstant.CODE_DEPLOY_HOST` | `prompt2app.storage.code-deploy-host` |
| `WebScreenshotUtils:61` 静态拼接 | `prompt2app.storage.screenshots-dir`（方法参数注入） |
| `CodeFileSaverTemplate` 静态字段 | 构造器注入 `fileSaveRootDir` |
| `AppServiceImpl @Value("${code.deploy-host}")` | `properties.getStorage().getCodeDeployHost()` |
| `application.yml` 明文 password / `<Your API Key>` | `.env`（开发） / OS env（生产） |

> 详细决策见 [ADR-0010](../adr/0010-config-unification.md)；新增/迁移流程见上文 §业务约束。
