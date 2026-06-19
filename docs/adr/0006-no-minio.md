# ADR-0006：当前阶段不引入 MinIO/OSS 对象存储

- **状态**：Accepted
- **日期**：2026-06-19
- **决策者**：项目作者
- **相关 Phase**：Phase 7 · 收尾
- **相关 Issue / PR**：feature/ai-engineering-rebuild commit 待提交

---

## 背景（Context）

项目当前的代码生成产物落地路径：

```
AiCodeGeneratorFacade.generate(...)
        │
        ▼
tmp/code_output/{strategy}_{appId}/  ← 本地磁盘
        │
        ▼
StaticResourceController serve     ← Java 进程内做静态文件分发
```

事实判断：

- **本地磁盘 + Java serve** 是一个反模式，但**对作品集场景影响为零**：
  - 单进程 / 单机 / 单用户（自己） / 无 SLA 压力
  - 生成产物是"展示用"快照，丢了重新生成即可
- **替换方案的成本极高**：
  - 引入 MinIO → 至少 +1 个 Docker container + 1 套 SDK 依赖 + AccessKey 管理
  - 引入阿里云 OSS / AWS S3 → 信用卡绑定 + 区域选择 + 跨域 CORS
  - 都需要一个新 ADR 论证"为什么"
- **业界标准答案**："Java serve 静态文件是反模式，生产应当 CDN + 对象存储"——但**作品集不是生产**

PROJECT_CHARTER §3「明确不做」第 3 项早就把这个写在墙上：

> ❌ **MinIO / OSS 对象存储**（单机本地够用，预留接口）

本 ADR 是把 Charter 约束**正式 ADR 化**——治理体系要求"明确不做"的事必须有可被追溯的 ADR。

---

## 备选方案（Options）

### 方案 A：维持现状（**本决策**）

- 本地磁盘 + Java `StaticResourceController`
- 优点：0 依赖、0 配置、0 故障面
- 缺点：单点故障 / 无法水平扩展 / 服务器挂掉用户作品全丢
- 长期成本：低（**对作品集场景**）；高（**对生产 SaaS**）

### 方案 B：引入 MinIO（自托管对象存储）

- Docker compose 起 MinIO + 客户端 SDK
- 优点：S3 兼容、本地可跑、未来上云无缝
- 缺点：
  - +1 容器 + 启动复杂度
  - 增加新人 onboarding 摩擦（要先起 MinIO 才能跑项目）
  - 与 Charter §1「保持复杂度可控」目标冲突
- 长期成本：中

### 方案 C：阿里云 OSS / AWS S3（云端对象存储）

- 优点：业界标准
- 缺点：
  - 信用卡 + AccessKey + 跨域 + 计费监控
  - 简历项目反而被招聘官质疑"为什么生产化"
  - 与 Charter §1 矛盾
- 长期成本：高

### 方案 D：CDN + 静态站点（GitHub Pages / Cloudflare Pages）

- 优点：免费 + 现代 + 招聘官眼熟
- 缺点：
  - 需要每次生成都 push 到一个 git 仓库 + 等 CI 部署
  - 与 SSE 实时生成场景割裂
- 长期成本：高（异步部署 vs 实时反馈）

---

## 决策（Decision）

**选择方案 A**：维持本地磁盘 + `StaticResourceController`，**保留** `infra/manager/CosManager.java`（教学版的腾讯云 COS 适配器）作为"未来扩展锚点"。

### 关键约束

满足以下任一条件触发本 ADR 复盘：

- 项目从单人作品集转为对外 SaaS（用户量 > 100 DAU）
- 出现真实跨实例数据共享需求（多机部署）
- 招聘场景明确要求展示"对象存储经验" → 此时再写 ADR-0006b 升级方案
- 单机磁盘空间或 IO 成为瓶颈

### 不做的事

- **不删 `CosManager.java`**：保留为"我评估过 OSS 接入但选择延后"的实证
- **不删 `StaticResourceController.java`**：当前业务依赖
- **不引入抽象 `StorageProvider` 接口**：YAGNI，现在没第二个实现
- **不动 application.yml 的 cos 配置块**：留作"想用时配上 AccessKey 就行"

---

## 代价（Consequences）

### 正面

- 项目启动复杂度保持最低（一个 Spring Boot 进程 + MySQL + Redis）
- 招聘官评价时不会因"为什么搞 MinIO"被分散注意力，可以专注 Router / Agent / Eval / Metric 四个核心爆点
- 简历可以**反向卖点**："我评估过 MinIO/OSS，但作品集场景下选择了最简方案，并写了 ADR-0006 论证"

### 负面

- **多机部署不可行**：但本就不需要
- **服务器挂掉作品全丢**：但本就只是展示用
- **被招聘官追问"如果用户量上来怎么办？"**：直接打开 ADR-0006 §复盘指标念出来

### 中性

- 与 ADR-0001（模块化单体）一脉相承——都是"作品集场景下的最小可行架构"决策

---

## 复盘指标（Revisit Triggers）

满足下列任一条件时重新评估本决策：

- 项目目标从"作品集"切到"对外服务"
- DAU > 100 或本地存储 > 50GB
- 招聘场景明确要求展示对象存储 / CDN 工程经验
- 出现实际跨实例需要数据共享的场景

---

## 关于"为什么作品集 ≠ 生产"

> 简历项目最大的失败模式：**用生产级技术栈做作品集，结果各项都浅尝辄止，反而显得不专业**。

业界普遍误解："越多技术栈 = 越能力强"。实际上：

- 用 5 项技术做出 10 分东西 < 用 3 项做出 9 分东西
- 招聘官看到"我用了 MinIO + Redis + Kafka + ELK + Grafana + ..." 的反应是"哦，就是堆砌技术栈"
- 招聘官看到"我评估过 X 个方案、写了 ADR、最终选了 Y 因为 Z" 的反应是"这人有架构思考"

本 ADR 是 Charter §3「明确不做」原则的具体落地之一。

---

## 参考资料（References）

- [`PROJECT_CHARTER.md`](../../PROJECT_CHARTER.md) §3 「明确不做」第 3 项
- [`PROJECT_CHARTER.md`](../../PROJECT_CHARTER.md) §1 「保持复杂度可控」
- 业界类似实践：[Patrick McKenzie - "MicroSaaS shouldn't have AWS"](https://www.kalzumeus.com/) 风格
- 同一脉络的 ADR：[ADR-0001](./0001-modular-monolith.md)、[ADR-0007](./0007-no-langgraph4j-workflow.md)
