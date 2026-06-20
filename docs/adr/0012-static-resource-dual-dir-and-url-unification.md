# ADR-0012 StaticResourceController 双目录 fallback + 前端 URL 策略统一

- **状态**：Accepted
- **日期**：2026-06-20
- **决策人**：项目作者 + AI 协作者
- **关联 Phase**：Phase 8（增量 · 启动事故链 #6–#8 收尾）
- **相关 ADR**：ADR-0010（配置统一化，触发了整条事故链）、ADR-0011（Redis ChatMemory 删除，同链上 #4–#5）
- **相关 Charter 条款**：§4「ADR 覆盖」、§4「配置外部化」、§5「文档先于代码」

---

## 1. 背景

### 事故链

Phase 8 配置外部化（ADR-0010）合入后，用户启动应用测试暴露了一连串事故。ADR-0011 已记录事故 #1–#5（数据库自动建库、Redis namespace 隔离、RedisChatMemoryStore 删除、依赖回归、`user.dir` 占位符泄露）。本 ADR 记录后续 #6–#8：

| # | 现象 | 根因 |
| --- | --- | --- |
| 6 | 部署后应用路径无法访问；截图报 `ERR_CONNECTION_REFUSED` | `codeDeployHost` 默认值 `http://localhost`（80 端口），但后端在 8123；nginx 80 端口是旧项目残留 |
| 7 | 生成后的网页预览 404 | StaticResourceController 只读 `codeDeployDir`，但预览的文件在 `codeOutputDir` |
| 8 | "查看作品"按钮 404 | 前端 `getDeployUrl` 用 `DEPLOY_DOMAIN`（http://localhost）拼 URL，走 80 端口 nginx |

### 共同根因

三个事故**同源**：静态资源访问路径策略不统一。

- **后端**：`StaticResourceController` 只从一个目录读文件，但预览和部署的文件在不同目录
- **前端**：`getDeployUrl` 和 `getStaticPreviewUrl` 用不同的 base URL 拼接，`getDeployUrl` 走了废弃的 `DEPLOY_DOMAIN`（nginx 80 端口残留）
- **配置**：`codeDeployHost` 默认值不含端口 + context-path + `/static`，导致拼出的 URL 打到 80 端口

---

## 2. 备选方案

### 方案 A：StaticResourceController 双目录 fallback（采纳）

`resolveFile()` 按 `codeDeployDir → codeOutputDir` 顺序查找文件。

- 工作量：30 min
- 代价：每次请求多一次文件存在性检查（`File.exists()`，纳秒级，可忽略）
- 评价：最小改动，预览和部署两条路径都能工作

### 方案 B：拆分两个 Controller（预览 + 部署各一个）

`PreviewController` 读 `codeOutputDir`，`DeployController` 读 `codeDeployDir`，路由分开。

- 工作量：1–2h
- 代价：两个 controller 重复逻辑；前端要区分两个 URL 前缀
- 评价：过度拆分；当前一个 controller + fallback 已够用

### 方案 C：部署时把文件复制到统一目录

部署时把 `codeOutputDir/{key}` 也复制到 `codeDeployDir/{key}`，controller 只读 `codeDeployDir`。

- 工作量：20 min
- 代价：预览也要走"复制"流程，增加 I/O；语义混乱（预览 ≠ 部署）
- 评价：把"预览"和"部署"混为一谈，破坏语义

### 方案 D：预览直接读 codeOutputDir，不走 Controller

前端预览用 `file://` 协议直接读本地文件。

- 工作量：15 min
- 代价：`file://` 在浏览器安全策略下无法被 iframe 加载（跨协议）；生产环境也不可行
- 评价：技术上不可行

---

## 3. 决策

### 3.1 后端：StaticResourceController 双目录 fallback

```java
private File resolveFile(String deployKey, String resourcePath) {
    // 先查部署目录（部署后的应用）
    File deployFile = new File(codeDeployDir + "/" + deployKey + resourcePath);
    if (deployFile.exists() && deployFile.isFile()) return deployFile;
    // 再查生成目录（未部署的预览）
    File outputFile = new File(codeOutputDir + "/" + deployKey + resourcePath);
    if (outputFile.exists() && outputFile.isFile()) return outputFile;
    return null;
}
```

两条路径共用同一个 controller：
- **预览**：key = `html_{appId}`，文件在 `codeOutputDir/{key}/`
- **部署**：key = `{random6}`（如 `7b9Hln`），文件在 `codeDeployDir/{key}/`

### 3.2 前端：URL 策略统一为 `STATIC_BASE_URL`

```typescript
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8123/api'
export const STATIC_BASE_URL = `${API_BASE_URL}/static`

export const getDeployUrl = (deployKey: string) => {
  return `${STATIC_BASE_URL}/${deployKey}/`    // ← 从 DEPLOY_DOMAIN 改为 STATIC_BASE_URL
}

export const getStaticPreviewUrl = (codeGenType: string, appId: string) => {
  const baseUrl = `${STATIC_BASE_URL}/${codeGenType}_${appId}/`
  if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) return `${baseUrl}dist/index.html`
  return baseUrl
}
```

`getDeployUrl` 和 `getStaticPreviewUrl` 现在同源，都走后端 `StaticResourceController`。`DEPLOY_DOMAIN` 保留但不再被使用（向后兼容）。

### 3.3 配置：`codeDeployHost` 默认值含端口 + context-path + `/static`

```yaml
code:
  deploy-host: ${CODE_DEPLOY_HOST:http://localhost:8123/api/static}
```

```java
private String codeDeployHost = "http://localhost:8123/api/static";
```

截图服务访问此 URL 拿部署后的页面，不再走 80 端口 nginx。

---

## 4. 代价

| 失去什么 | 是否影响当前业务 |
| --- | --- |
| `DEPLOY_DOMAIN` 前端环境变量废弃（不再被 `getDeployUrl` 使用） | 否；保留定义避免破坏其他引用 |
| `StaticResourceController` 每次请求多一次 `File.exists()` 检查 | 否（纳秒级） |
| nginx 80 端口配置彻底无用 | 否；用户可自行关掉 |

---

## 5. 复盘指标

- 预览功能：生成后 1 秒内 iframe 能正常加载页面
- 部署功能：部署成功后"查看作品"能打开正确 URL
- 截图功能：部署后 30 秒内日志显示"截图上传成功"

---

## 6. 触发回头看的条件

| 条件 | 对应动作 |
| --- | --- |
| 预览和部署的目录结构分化（如部署加 CDN、预览加临时目录） | 重新评估双目录 fallback 是否仍然合适 |
| 引入 nginx 反向代理服务静态资源 | `getDeployUrl` 可能要改回 `DEPLOY_DOMAIN`，需新 ADR |
| `codeOutputDir` 和 `codeDeployDir` 合并为一个目录 | `resolveFile` 简化为单目录查找 |

---

## 7. 流程教训（治理层面）

本 ADR 是**事后补写**的——事故 #6–#8 的代码修复在 commit `267723b`（StaticResourceController 双目录）、`11b6955`（前端 URL 统一）中已经完成，但当时跳过了 ADR 流程，违反 Charter §4「ADR 覆盖」和 §5「文档先于代码」。

**根因**：AI 在事故修复链中陷入"救火模式"，每出一个报错就立刻改代码推 commit，没有在架构决策点停下来写 ADR。

**防再犯**：当一次修复涉及**多个文件的路径策略 / URL 策略变更**时，必须先暂停代码修改，写 ADR 再继续。commit message 不能替代 ADR。

---

## 8. 参考

- 事故 #6 commit：`32065b3`（codeDeployHost 默认值修复）
- 事故 #7 commit：`267723b`（StaticResourceController 双目录 fallback）
- 事故 #8 commit：`11b6955`（前端 getDeployUrl URL 策略统一）
- ADR-0010（配置统一化，触发整条事故链）
- ADR-0011（RedisChatMemoryStore 删除，同链 #4–#5）
