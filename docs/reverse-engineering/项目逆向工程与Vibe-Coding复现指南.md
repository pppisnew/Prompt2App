# 《项目逆向工程与 Vibe Coding 复现指南》

> 分析对象：**yu-ai-code-mother（AI 零代码应用生成平台）**
> 作者：程序员鱼皮（教学项目，本项目代码仅供学习）
> 本文档目标：把该项目拆解为「产品逻辑 + 技术架构 + 实现模式 + AI 复刻指南」，让你能借助 AI（Cursor / Claude Code / Windsurf / Copilot）快速做出同类型产品。

---

## 📌 TL;DR（2 分钟读完）

**项目是什么**：开源版 v0/bolt.new——用户一句话描述需求，AI 流式生成可运行的网页应用，支持点选元素 + 对话精准修改、一键部署、源码下载。

**技术栈**：Spring Boot 3 + LangChain4j + Vue 3 + Reactor SSE + MySQL + Redis + Dubbo（微服务版）。线上主能力是 **Phase 3 单步 Agent**（HTML/多文件/Vue 三策略），单体实验线另有 LangGraph4j 多节点工作流（Phase 6，未接入主线）。

**规模**：~2.4 万行（后端 Java 18,700 + 前端 4,900）；3 张表；7 套 prompt；7 个 AI 工具；~265 个 Java 文件；测试覆盖率极低（15 文件全在单体）。

**5 大核心壁垒**（值得深研）：① Prompt 体系 ② AI 路由 ③ Tool Calling Agent ④ 可视化编辑（独有）⑤ 部署链路。

**单次生成成本量级**（按 DeepSeek 价）：HTML ~$0.001 / Vue Agent ~$0.04 / 工作流 ~$0.06——Vue 是 HTML 的 40 倍，这是 AI 路由策略的根本经济动因。

**复刻 4 个级别**：L1 HTML 生成器 3 天 / L2 Vue Agent 1 周 / L3 + 可视化+部署 2 周 / L4 完整平台 1 个月+。**强烈建议从 L1 起步**，附录 J 列出了 5 大失败路线（最常见：一上来就上 LangGraph + 微服务，3 个月没产品）。

**最该避免的两个坑**：① 抄 `dev/langchain4j/` 源码覆盖包（技术债，新版已原生支持）② 不建评测集就改 prompt（盲改）。

**对你的价值取决于身份**：复刻者 → 看 Part 3（9.8/10 实战指南）；架构学习者 → 看 Part 2（ADR + 设计模式 + 演进）；创业者 → 看 Part 4（运维 + 成本 + 竞品 + 边界）。

---

## 目录

> 文档按 **5 个 Part** 组织，避免附录无限堆叠。每个 Part 有明确读者目标。

### Part 1 · 项目分析（理解这个项目是什么）
- [第一部分：项目概览](#第一部分项目概览)
- [第二部分：需求分析](#第二部分需求分析)
- [第三部分：系统架构分析](#第三部分系统架构分析)
- [第四部分：代码结构分析](#第四部分代码结构分析)
- [第五部分：业务模型抽象](#第五部分业务模型抽象)

### Part 2 · 架构与工程深度（为什么这么设计）
- [第十部分：架构决策记录 ADR](#第十部分架构决策记录-adr)
- [第十一部分：Prompt 体系拆解](#第十一部分prompt-体系拆解)
- [第十二部分：数据模型与时序分析](#第十二部分数据模型与时序分析)
- [第十三部分：性能与扩展性分析](#第十三部分性能与扩展性分析)
- [附录 B：软件设计模式索引](#附录-b软件设计模式索引)
- [附录 C：项目演进路线图](#附录-c项目演进路线图)

### Part 3 · Vibe Coding 复刻（怎么重做一个）
- [第六部分：Vibe Coding 复现指南](#第六部分vibe-coding-复现指南)
- [第七部分：项目复刻 Prompt](#第七部分项目复刻-prompt)
- [第八部分：迁移与创新](#第八部分迁移与创新)
- [第九部分：AI 生成难度评估](#第九部分ai-生成难度评估)
- [第十四部分：复刻踩坑指南](#第十四部分复刻踩坑指南)
- [第十五部分：复刻优先级矩阵](#第十五部分复刻优先级矩阵)
- [附录 J：复刻失败路线 vs 正确路线](#附录-j复刻失败路线-vs-正确路线)

### Part 4 · 工程实践与商业化（上生产 / 做生意）
- [附录 D：可观测性与运维](#附录-d可观测性与运维)
- [附录 G：测试体系分析](#附录-g测试体系分析)
- [附录 H：成本模型拆解](#附录-h成本模型拆解)
- [附录 E：项目能力边界](#附录-e项目能力边界)
- [附录 I：竞品对比](#附录-i竞品对比)

### Part 5 · 参考索引（查阅用）
- [附录 A：源码导航地图](#附录-a源码导航地图)
- [附录 F：代码规模评估](#附录-f代码规模评估)
- [附录 K：核心数据速查卡](#附录-k核心数据速查卡)
- [文档维护指南](#文档维护指南)

---

## 阅读路径（按你的目标选读）

> 这份文档同时服务「理解项目 / 快速复刻 / 学架构 / 查源码 / 创业参考」5 类读者。不必通读，按目标选路径。

| 你的目标 | 推荐阅读 | 预计耗时 |
| --- | --- | --- |
| 🎯 **快速理解这个项目** | 第一～五部分（Part 1） | 30 分钟 |
| 🚀 **用 Cursor/Windsurf 快速复刻** | 第六～九部分 + 第十四～十五部分 + 附录 J（Part 3 核心） | 1 小时 |
| 🏛 **学架构设计与设计模式** | 第十～十三部分 + 附录 B、C（Part 2） | 1 小时 |
| 🔍 **对照源码研究实现** | 附录 A（源码导航）+ 附录 F（规模）+ 第四部分 | 按需查阅 |
| 💰 **创业做 AI SaaS** | 附录 D（运维）+ H（成本）+ I（竞品）+ E（边界） | 40 分钟 |
| 🧪 **做 AI Agent 工程化** | 附录 G（测试）+ C（演进）+ 第十三（性能） | 50 分钟 |
| ⚡ **只想看核心闭环怎么跑** | 第十二部分时序图 + 附录 C 链路对照表 | 15 分钟 |

> 💡 **首次阅读建议**：先读第一部分（含业务闭环 + 核心壁垒）→ 再按你的目标选路径。文档已超 2400 行，通读性价比低。

---

# 第一部分：项目概览

## 项目定位

| 维度 | 说明 |
| --- | --- |
| **解决什么问题** | 让不会写代码（或想省时间）的人，**用一句自然语言描述需求，AI 自动生成一个完整、可运行、可部署的网页应用**，并支持可视化编辑与一键发布。 |
| **目标用户** | ① 非程序员（产品/运营/个人创业者）想快速做个网站；② 程序员想用 AI 起一个前端项目骨架；③ 学习 AI 全栈开发的开发者（教学属性）。 |
| **核心价值** | "零代码 + AI + 流式输出 + 可视化二次编辑 + 一键部署 + 源码下载" 的一站式应用工厂；对标 v0 / bolt.new / Lovable 等 AI 应用生成器。 |

一句话定位：**「输入需求 → AI 生成网站 → 可视化改 → 一键部署分享」的 AI 应用生成平台。**

## 核心业务闭环 ⭐

> 后面所有架构设计都围绕这个闭环展开。这是理解整个项目的"主线"。

```mermaid
graph LR
    U["用户需求"] --> R["Prompt 路由<br/>选生成策略"]
    R --> G["AI 代码生成<br/>流式输出"]
    G --> S["文件落盘<br/>tmp/code_output"]
    S --> P["实时预览<br/>iframe"]
    P --> E{"满意?"}
    E -->|否,改| V["可视化编辑<br/>点选+对话精准改"]
    V --> G
    E -->|是| D["构建部署<br/>npm build + 短链"]
    D --> SH["分享传播<br/>URL + 截图封面"]
```

**闭环八步**：用户需求 → Prompt 路由 → 代码生成 → 文件落盘 → 实时预览 → AI 修改 → 构建部署 → 分享传播。其中**代码生成 → 预览 → 修改**是可循环的核心三角，决定了产品的"爽感"。

## 核心壁垒（为什么别人不容易做过来）

> 读完功能列表你会知道"项目有什么"，但不知道"真正该研究哪部分"。这 5 个壁垒是项目的护城河，也是复刻时该重点投入的地方——CRUD 谁都能写，壁垒不是。

| 壁垒 | 说明 | 复刻投入权重 |
| --- | --- | --- |
| **① Prompt 体系** | 7 套系统 prompt（路由/HTML/多文件/Vue/质检/图片）决定生成质量，是真正的"业务逻辑"，需针对失败 case 反复迭代 | ⭐⭐⭐ 最高 |
| **② AI 路由机制** | 按需求复杂度自动选 HTML/多文件/Vue，兼顾成本与体验（Vue 成本是 HTML 的 40 倍） | ⭐⭐ |
| **③ Tool Calling Agent** | AI 用文件工具从零搭工程（而非吐一坨代码），是"生成器"到"Agent"的质变，含安全沙箱设计 | ⭐⭐⭐ 最高 |
| **④ 可视化编辑** | iframe + postMessage + 选择器生成，把"无结构对话"变"有锚点指令"，v0/bolt 都没做 | ⭐⭐⭐ 最高 |
| **⑤ 部署链路** | build → 拷贝 → 短链 → 截图 → COS 闭环，让作品能立刻分享传播 | ⭐⭐ |

> 反之，用户管理 CRUD / 后台表格 / 列表分页这些**不是壁垒**——AI 能 100% 生成，别在这上面花时间。壁垒 = AI 生成难度评估里的 🟡🟢 之外的部分。

## 复刻范围定义（先选你要做到哪一级）

> "我要复刻这个项目"是个模糊目标。先对齐你要做到哪一级，否则容易想一步到位却半年没产出。

| 级别 | 能力 | 工作量（AI 辅助） | 适合 |
| --- | --- | --- | --- |
| **Level 1** | HTML 生成器（输入→单文件 HTML→预览） | ~3 天 | 验证想法 / 学习闭环 |
| **Level 2** | + Vue Agent（工具调用生成工程） | ~1 周 | 个人项目 / Demo |
| **Level 3** | + 可视化编辑 + 一键部署 | ~2 周 | 可演示的产品 |
| **Level 4** | + 用户体系 + 后台 + 监控 + 多策略 | ~1 个月+ | 上线运营 |

> 本文第六部分"7 天路线"对应 Level 2-3；附录 J 明确反对"一开始就做 Level 4"。**建议从 Level 1 起步，每级验证再升级。**

## 项目功能地图

```mermaid
graph TD
    ROOT["AI 零代码应用生成平台"]

    ROOT --> A1["🤖 智能代码生成"]
    ROOT --> A2["🖌️ 可视化编辑"]
    ROOT --> A3["🚀 一键部署分享"]
    ROOT --> A4["🛠️ 企业级后台"]
    ROOT --> A5["👤 用户体系"]
    ROOT --> A6["🏗 平台基础设施"]

    A1 --> A1a["AI 智能路由<br/>自动选 HTML/多文件/Vue"]
    A1 --> A1b["流式输出 SSE<br/>实时看到 AI 思考"]
    A1 --> A1c["多生成策略<br/>单文件/多文件/工程化"]
    A1 --> A1d["AI 工具调用<br/>写/改/删/读文件"]

    A2 --> A2a["iframe 实时预览"]
    A2 --> A2b["元素点选 + 选择器生成"]
    A2 --> A2c["选中元素 + 自然语言 → AI 精准改"]

    A3 --> A3a["静态文件托管<br/>deployKey 6 位短链"]
    A3 --> A3b["Vue 工程自动 npm 构建"]
    A3 --> A3c["Selenium 自动截图当封面"]
    A3 --> A3d["源码 ZIP 打包下载"]

    A4 --> A4a["用户管理 CRUD"]
    A4 --> A4b["应用管理 + 精选上下架"]
    A4 --> A4c["对话记录审计"]

    A5 --> A5a["注册 / 登录 / 登出"]
    A5 --> A5b["Spring Session + Redis 会话"]
    A5 --> A5c["权限控制 user/admin"]
    A5 --> A5d["额度体系(预留)<br/>次数/Token/套餐"]

    A6 --> A6a["Redis 缓存/会话/限流"]
    A6 --> A6b["Prometheus 指标采集"]
    A6 --> A6c["Grafana 仪表盘"]
    A6 --> A6d["腾讯云 COS 对象存储"]
```

> 📌 **分类说明**：监控(Prometheus/Grafana)、Redis、COS 属于**平台基础设施**(A6)，不是后台业务功能(A4)——它们支撑全局而非属于某个业务模块。用户体系的"额度体系"当前未实现但已预留(A5d)，是商业化演进的必然方向（见附录 H 成本模型）。

---

# 第二部分：需求分析

## 核心需求（必须实现）

1. **需求输入**：用户用一段话（≤1000 字）描述想做的网站。
2. **AI 代码生成**：后端调用大模型，**流式**返回代码，前端实时渲染。
3. **代码生成策略路由**：根据需求复杂度自动选「单文件 HTML / 多文件 / Vue 工程」。
4. **实时预览**：生成的代码能立刻在浏览器里看到效果（iframe）。
5. **可视化编辑**：点选页面元素 + 对话修改，AI 精准定位修改。
6. **应用管理**：我的应用列表 / 精选应用广场。
7. **部署分享**：一键部署到静态服务器，得到可访问 URL。
8. **代码下载**：把生成源码打包成 ZIP 下载。
9. **用户系统**：注册/登录、会话保持、权限。
10. **后台管理**：用户/应用/对话的增删改查、精选管理。

## 非核心需求（可选增强）

- **AI 智能体记忆**：多轮对话上下文（Redis + MySQL 双层记忆）。
- **限流防刷**：每用户每分钟 5 次对话（Redisson 限流器）。
- **Prompt 安全护栏**：拦截越狱/注入类输入。
- **应用封面自动截图**：Selenium 无头浏览器截图上传 COS。
- **多级缓存**：Caffeine 本地缓存 + Redis（精选列表缓存）。
- **监控告警**：Actuator + Micrometer + Prometheus + Grafana。
- **微服务化**：拆 user / app / screenshot 多服务 + Dubbo RPC + Nacos 注册。
- **图片素材库**（prompt 文件 `image-collection-*` + 4 个图片工具：单体已接入工作流，微服务主线未接入；见附录 C 链路对照表）。

## 用户使用流程

```mermaid
flowchart TD
    Start([用户访问首页]) --> Login{已登录?}
    Login -- 否 --> Reg[注册 / 登录页]
    Reg --> Home[首页]
    Login -- 是 --> Home

    Home --> Input["输入需求 / 点快捷模板"]
    Input --> Create["POST /app/add<br/>(后端 AI 路由选 codeGenType)"]
    Create --> Chat["进入 /app/chat/:id 对话页"]

    Chat --> AutoSend{是创建者 & 无历史?}
    AutoSend -- 是 --> First["自动发送 initPrompt"]
    AutoSend -- 否 --> Wait[等待用户输入]

    First --> SSE["GET /app/chat/gen/code (SSE)"]
    Wait --> SSE

    SSE --> Stream["流式接收: AI 逐字输出<br/>Vue 模式还实时看到工具调用"]
    Stream --> Save["后端落库 chat_history<br/>+ 代码写盘 tmp/code_output"]
    Save --> Preview["iframe 自动刷新预览"]

    Preview --> Edit{要改吗?}
    Edit -- 是,可视化 --> Pick["点选元素→生成选择器"]
    Pick --> Merge["把『选择器+标签+内容』拼进 prompt"]
    Merge --> SSE
    Edit -- 是,纯对话 --> Type["输入修改需求"]
    Type --> SSE

    Edit -- 否,满意 --> Deploy["点部署 → npm build → 拷 dist → 截图"]
    Deploy --> Share["得到 deployKey 短链<br/>可复制/打开"]
    Deploy --> Download["也可下载 ZIP 源码"]

    Share --> End([完成])
    Download --> End
```

---

# 第三部分：系统架构分析

## 整体架构图

```mermaid
graph TD
    subgraph FE["🖥️ 前端 (Vue3 + Vite)"]
        Browser["浏览器<br/>EventSource(SSE) + iframe"]
    end

    subgraph GW["🌐 网关层 (无独立网关, Nginx 反代)"]
        Nginx["Nginx 反向代理<br/>/api → 8125"]
    end

    subgraph MS["⚙️ 微服务 (Spring Boot 3 + Spring Cloud Alibaba)"]
        AppSvc["yu-ai-code-app<br/>:8125 核心业务<br/>代码生成/部署/下载"]
        UserSvc["yu-ai-code-user<br/>:8124 用户/鉴权"]
        ShotSvc["yu-ai-code-screenshot<br/>网页截图"]
    end

    subgraph AI["🤖 AI 层 (LangChain4j + 改造版)"]
        Route["AI 路由服务<br/>选生成策略"]
        Gen["AI 代码生成服务<br/>HTML/多文件/Vue"]
        Tools["AI 工具集<br/>写/改/删/读/退出"]
        Guard["护栏<br/>输入安全/重试"]
        Stream["流式输出引擎<br/>TokenStream→Flux→SSE"]
    end

    subgraph Data["💾 数据层"]
        MySQL[("MySQL<br/>user/app/chat_history")]
        Redis[("Redis<br/>Session/记忆/缓存/限流")]
        LocalDisk["本地磁盘<br/>tmp/code_output<br/>tmp/code_deploy"]
    end

    subgraph Third["🔗 第三方"]
        LLM["DeepSeek / 阿里 Qwen<br/>OpenAI 兼容协议"]
        COS["腾讯云 COS<br/>图片存储"]
        Nacos["Nacos<br/>注册+配置"]
        Selenium["Selenium<br/>无头 Chrome"]
        Prom["Prometheus + Grafana<br/>监控"]
    end

    Browser <--> Nginx <--> AppSvc
    AppSvc -.Dubbo RPC.-> UserSvc
    AppSvc -.Dubbo RPC.-> ShotSvc
    AppSvc --> AI
    AppSvc --> MySQL
    AppSvc <--> Redis
    AppSvc --> LocalDisk
    ShotSvc --> Selenium
    ShotSvc --> COS
    AI --> LLM
    MS -.注册.-> Nacos
    AppSvc -.指标.-> Prom
```

## 技术栈分析

| 模块 | 技术 | 作用 | 替代方案 |
| --- | --- | --- | --- |
| 后端语言/框架 | **Java 21 + Spring Boot 3.5** | 业务主体，虚拟线程支持并发 | Node/NestJS、Go、Python/FastAPI |
| 微服务 | **Spring Cloud Alibaba + Dubbo3 (tri协议)** | 服务发现(RPC)：app↔user↔screenshot | gRPC、OpenFeign、单体 |
| 注册/配置中心 | **Nacos** | 服务注册 + 配置 | Eureka、Consul、Zookeeper |
| AI 编排框架 | **LangChain4j 1.1** | AI Service / 工具调用 / 记忆 / 护栏 | Spring AI、LangChain4j、原生 HTTP |
| AI 增强 | **LangGraph4j** | 复杂 AI 工作流（依赖已引入，部分能力） | 自研 Agent 循环 |
| LLM | **DeepSeek (deepseek-chat / reasoner)** + **阿里 Qwen-Turbo** | 主生成 + 推理 + 路由 | OpenAI GPT、Claude、通义、Kimi |
| ORM | **MyBatis-Flex 1.11** | 轻量 ORM + 代码生成 | MyBatis-Plus、JPA |
| 数据库 | **MySQL 8** | 业务持久化 | PostgreSQL |
| 缓存/会话 | **Redis + Redisson** | Session、对话记忆、限流、热点缓存 | Caffeine 单机、Memcached |
| 本地缓存 | **Caffeine** | AI 服务实例缓存（appId+type 维度） | — |
| 对象存储 | **腾讯云 COS** | 截图/封面图 | 阿里 OSS、七牛、MinIO |
| 截图 | **Selenium 4 + WebDriverManager** | 无头 Chrome 网页截图 | Playwright、Puppeteer |
| Web/响应式 | **Spring WebFlux (Reactor Flux)** | SSE 流式响应 | SseEmitter、WebSocket |
| 限流 | **Redisson RateLimiter + AOP 注解** | 用户级/IP/API 级限流 | Sentinel、Bucket4j |
| API 文档 | **Knife4j + SpringDoc** | OpenAPI 文档 | Swagger UI |
| 监控 | **Actuator + Micrometer + Prometheus + Grafana** | 系统与 AI 调用监控 | SkyWalking、Datadog |
| 前端框架 | **Vue 3.5 + TypeScript** | 单页应用 | React |
| 前端路由/状态 | **Vue Router 4 + Pinia 3** | 路由 + 全局状态 | — |
| UI 库 | **Ant Design Vue 4** | 组件库 | Element Plus、Naive UI |
| 构建 | **Vite 7** | 前端打包 | Webpack |
| Markdown | **markdown-it + highlight.js** | 渲染 AI 流式 Markdown | marked |
| API 类型同步 | **@umijs/openapi** | 后端 Swagger → 前端 TS 自动生成 | — |

### 选型理由 / 优势 / 劣势

- **为什么 LangChain4j**：Java 生态里最成熟的 LLM 编排框架，原生支持 `AiService`、`@Tool`、`ChatMemory`、`Guardrail`、流式；与 Spring Boot 无缝集成。
- **为什么 Dubbo + Nacos**：教学目的是演示企业级微服务；app 调用 user 查用户信息、调用 screenshot 截图，跨进程解耦。
- **为什么 Reactor Flux 做 SSE**：相比 `SseEmitter`，`Flux<ServerSentEvent>` 与 LangChain4j 的 `TokenStream/Flux<String>` 拼接更自然，背压更好。
- **为什么改造 `dev.langchain4j` 源码**：上游版本**流式模式下不暴露「工具调用过程」事件**，前端无法实时显示「AI 正在写文件」；项目把若干类覆盖到 classpath 前面，强行补齐 `onPartialToolExecutionRequest` 等回调。**这是全项目最"硬核"也最脆弱的一处**——升级 LangChain4j 版本时必须重新对齐。
- **劣势**：① 微服务+多模型+改造源码，对新手认知负担大；② 本地磁盘做代码托管和部署，**水平扩展困难**（多实例需共享存储）；③ DeepSeek 弱于 Claude/GPT 在复杂前端工程生成上的稳定性。

---

# 第四部分：代码结构分析

## 目录结构

```text
yu-ai-code-mother/
├── pom.xml                         # 根聚合（旧单体残留，依赖全在这里）
├── README.md
├── grafana/                        # Grafana 仪表盘配置 JSON
│
├── yu-ai-code-mother-frontend/     # 🖥️ Vue3 前端
│   ├── src/
│   │   ├── api/                    # OpenAPI 自动生成的请求函数 + typings.d.ts
│   │   ├── components/             # AppCard / AppDetailModal / DeploySuccessModal
│   │   │                           # GlobalHeader/Footer / MarkdownRenderer / UserInfo
│   │   ├── pages/
│   │   │   ├── HomePage.vue        # 首页(落地+创作入口+精选/我的画廊)
│   │   │   ├── app/AppChatPage.vue # ⭐核心: 对话+预览+可视化编辑+部署+下载
│   │   │   ├── app/AppEditPage.vue # 应用元信息编辑(非可视化编辑)
│   │   │   ├── admin/              # 用户/应用/对话 后台管理
│   │   │   └── user/               # 登录/注册
│   │   ├── stores/loginUser.ts     # Pinia: 登录态
│   │   ├── utils/
│   │   │   ├── visualEditor.ts     # ⭐可视化编辑(iframe 注入选区)
│   │   │   └── codeGenTypes.ts     # 镜像后端 CodeGenTypeEnum
│   │   ├── router/index.ts
│   │   ├── access.ts               # 全局路由守卫
│   │   ├── request.ts              # axios 实例 + 拦截器
│   │   ├── layouts/BasicLayout.vue
│   │   └── config/env.ts           # 部署/静态/API 地址拼接
│   ├── openapi2ts.config.ts        # Swagger → TS 代码生成配置
│   └── vite.config.ts              # dev 代理 /api → 8123
│
└── yu-ai-code-mother-microservice/ # ⚙️ 后端微服务聚合
    ├── pom.xml                     # 微服务父 POM (Spring Cloud Alibaba + Dubbo BOM)
    │
    ├── yu-ai-code-common/          # 通用: 响应体/异常/常量/注解/COS/工具
    ├── yu-ai-code-model/           # 实体/枚举/DTO/VO (跨服务共享)
    ├── yu-ai-code-client/          # Dubbo 内部服务接口 + Inner* 实现
    │
    ├── yu-ai-code-user/            # 👤 用户服务 (:8124) 登录/注册/会话
    │
    ├── yu-ai-code-app/             # 🧠 核心应用服务 (:8125) ★
    │   └── src/main/java/com/yupi/yuaicodemother/
    │       ├── controller/         # AppController(SSE) / ChatHistory / StaticResource
    │       ├── service/            # AppService(路由+部署+截图) / ChatHistory / ProjectDownload
    │       ├── core/               # ⭐代码生成门面 + 解析器 + 保存器 + 流处理器 + VueBuilder
    │       │   ├── AiCodeGeneratorFacade.java
    │       │   ├── parser/         # HTML/多文件 代码块解析 (策略)
    │       │   ├── saver/          # 文件落盘 (模板方法)
    │       │   ├── handler/        # SimpleText/JsonMessage StreamHandler
    │       │   └── builder/        # VueProjectBuilder (npm install+build)
    │       ├── ai/                 # AI 路由/生成 服务 + 工厂
    │       ├── ratelimiter/        # @RateLimit 注解 + AOP + Redisson
    │       ├── config/             # Redis 缓存/Redisson
    │       └── aop/AuthInterceptor
    │
    ├── yu-ai-code-ai/              # 🤖 AI 能力模块 ★
    │   └── src/main/
    │       ├── java/com/yupi/yuaicodemother/
    │       │   ├── ai/             # AiCodeGeneratorService(5方法) + Routing + Factory
    │       │   │                   # + guardrail(输入安全/重试) + model(StreamMessage)
    │       │   └── ai/tools/       # ★AI 工具: 写/改/删/读文件/读目录/退出 + ToolManager
    │       ├── java/dev/langchain4j/  # ⚠️覆盖上游源码,补流式工具调用事件
    │       └── resources/prompt/   # 7 套系统 Prompt(路由/HTML/多文件/Vue/质量检查/图片集)
    │
    └── yu-ai-code-screenshot/      # 📸 截图服务 (Selenium 无头 Chrome → COS)
```

## 核心模块

### 模块 1：`AiCodeGeneratorFacade`（代码生成门面） — 可复用度 **高**

- **职责**：统一入口，按 `CodeGenTypeEnum` 分发到不同 AI 服务，统一返回 `Flux<String>`。
- **输入**：`userMessage`、`CodeGenTypeEnum`、`appId`。
- **输出**：`Flux<String>`（HTML/多文件是纯文本；Vue 是 JSON `StreamMessage`）。
- **核心逻辑**：
  - HTML / MULTI_FILE：调 `generateXxxCodeStream` → `processCodeStream`（`doOnNext` 收集 → `doOnComplete` 解析+保存）。
  - VUE_PROJECT：调 `generateVueProjectCodeStream` → `processTokenStream`（把 `TokenStream` 的 4 个回调包成 `Flux.create`，`onCompleteResponse` 时跑 `npm build`）。

### 模块 2：`AiCodeGeneratorServiceFactory`（AI 服务工厂 + 缓存） — 可复用度 **高**

- **职责**：为每个 `appId+type` 构建一个独立的 `AiCodeGeneratorService`（LangChain4j `AiServices`）。
- **核心逻辑**：
  - **Caffeine 缓存**（max 1000，写后 30min）避免重复构建。
  - **预热记忆**：构建时从 DB `chat_history` 读最近 20 条灌进 `MessageWindowChatMemory`（Redis 存储）。
  - **原型作用域模型**：`streamingChatModelPrototype` / `reasoningStreamingChatModelPrototype` 每次请求 `getBean` 新实例，解决并发 SSE 流共享可变状态问题。
  - **策略分叉**：VUE 用推理模型 + 注册全部工具 + `maxSequentialToolsInvocations(20)` + 幻觉工具名策略；HTML/多文件用普通流式模型、无工具、仅输入护栏。

### 模块 3：AI 工具集（`ai/tools/*`） — 可复用度 **高**

- **职责**：让 Vue 工程模式下 AI 通过 **函数调用** 直接写文件，而非吐一大段代码。
- **7 个工具**：`writeFile` / `modifyFile` / `deleteFile`（带重要文件保护）/ `readFile` / `readDir`（过滤 node_modules）/ `exit`（让 AI 主动跳出工具循环）。
- **设计**：抽象 `BaseTool`（`getToolName/getDisplayName/generateToolExecutedResult`），`ToolManager` 统一注册并通过 `getAllTools()` 注入 AI。
- **隔离**：工具路径用 `@ToolMemoryId appId` 隔离到 `vue_project_<appId>/`，互不干扰。

### 模块 4：流处理器 `StreamHandlerExecutor` + `JsonMessageStreamHandler` — 可复用度 **中**

- **职责**：把 AI 流式产物转换成「可显示 + 可落库」的格式。
- **核心逻辑**：
  - HTML/多文件 → `SimpleTextStreamHandler`（纯透传 + 完成时落库）。
  - Vue → `JsonMessageStreamHandler`：解析 `StreamMessage` JSON，对 `TOOL_REQUEST` 按 toolId 去重只发一次「选择工具」提示，对 `TOOL_EXECUTED` 调用工具生成漂亮结果（带代码块），并拼到历史字符串落库。

### 模块 5：`CodeParser` + `CodeFileSaver`（解析+落盘） — 可复用度 **高**

- **职责**：把 AI 吐的 Markdown 代码块解析成结构化结果再存盘。
- **设计模式**：**策略（Executor 选 Parser）+ 模板方法（SaverTemplate 定义骨架：validate→buildUniqueDir→saveFiles）**。
- **路径**：稳定目录 `tmp/code_output/<type>_<appId>/`（用 appId 而非随机 ID，方便覆盖式更新）。

### 模块 6：前端 `AppChatPage.vue` + `visualEditor.ts` — 可复用度 **中**

- **职责**：SSE 接收 + Markdown 实时渲染 + iframe 预览 + 可视化点选编辑。
- **亮点**：`EventSource` 手写 SSE；`postMessage` 跨 iframe 桥接编辑器；编辑时把「选择器+标签+内容」拼进 prompt 实现 AI 精准修改。

### 模块 7：`dev/langchain4j/` 覆盖包 — 可复用度 **低**（技术债）

- **职责**：覆盖 jar 包同名类，补齐 `StreamingChatModel` 在流式模式下的工具调用事件。
- **风险**：升级 LangChain4j 时必须手工 diff，否则丢失上游修复。

---

# 第五部分：业务模型抽象

## 核心业务对象

```text
User (用户)
├── 属性: id, userAccount, userPassword, userName, userAvatar, userProfile, userRole(user/admin), createTime
├── 行为: 注册/登录/登出/改资料
└── 关系: 1 ──< App (创建) ; 1 ──< ChatHistory (发送)

App (应用/作品)  ★ 核心
├── 属性: id, appName, cover, initPrompt, codeGenType(html/multi_file/vue_project),
│         deployKey, deployedTime, priority(0普通/99精选), userId, editTime, createTime
├── 行为: 创建(AI路由定类型)/对话改/部署/下载/删除/上下架
└── 关系: N ──> 1 User ; 1 ──< ChatHistory ; 1 ──> 1 静态部署目录

ChatHistory (对话记录)
├── 属性: id, message, messageType(user/ai), appId, userId, createTime
├── 行为: 追加/分页查询/按 appId 删除(级联)
└── 关系: N ──> 1 App ; N ──> 1 User

(隐式) DeployBundle (部署产物)
├── 属性: deployKey(6位), 目录路径, 访问URL
├── 行为: 由 App 触发构建+拷贝+截图
└── 关系: 1 ──> 1 App
```

## 业务规则

- **代码生成类型在创建时即定**：`AppServiceImpl.createApp` 调 AI 路由服务一次性决定 `codeGenType`，**之后不可改**（影响整个对话策略）。
- **权限控制**：
  - 普通用户只能操作自己的 App（对话/部署/下载/删除/改名）。
  - 管理员可管理所有用户/App/对话，可设精选（priority 99）、改封面。
  - `/admin/*` 路由前端守卫 + 后端 `@AuthCheck(ADMIN_ROLE)` 双重校验。
- **对话权限**：只有 App 创建者能与其对话；访客只能只读查看（`view=1`）。
- **限流**：`@RateLimit(USER, 5次/60秒)` 防单用户刷 AI。
- **状态流转（App 部署）**：
  ```mermaid
  stateDiagram-v2
      [*] --> 已创建: createApp (AI 路由)
      已创建 --> 已生成: 首次对话 AI 写盘
      已生成 --> 已生成: 继续对话修改
      已生成 --> 已部署: deployApp (build+copy+screenshot)
      已部署 --> 已部署: 重新部署(覆盖)
      已部署 --> 已删除: deleteApp (级联删 history)
      已生成 --> 已删除: deleteApp
  ```
- **生命周期**：创建（定类型）→ 生成（写盘）→ 多轮改 → 部署（构建+拷贝+截图）→ 删除（级联）。
- **记忆一致性**：MySQL `chat_history` 是**事实源**，每次构建 AI 服务都从 DB 重灌 Redis 记忆；Redis 仅作运行时缓存。

---

# 第六部分：Vibe Coding 复现指南

> 假设你**从零**做一个同类型 AI 应用生成平台，用 AI 编程工具加速。

## MVP 版本（最少要做什么）

**砍到极致的 MVP（~3 天可跑）**：

1. 用户登录（先**砍掉注册**，硬编码 1 个账号或直接无登录）。
2. 一个输入框 + 提交 → 创建 App（**砍掉 AI 路由，写死 HTML 单文件模式**）。
3. 后端调一次 LLM 流式生成单文件 HTML，**SSE 推回前端**。
4. 前端把流拼成 markdown 渲染 + **iframe 预览**。
5. 把生成的 HTML 存到本地目录，前端 iframe 指过去即可。

**这一版只验证一件事**：「输入需求 → AI 出 HTML → 能预览」。这是产品的灵魂。

## 开发优先级

- **P0（核心闭环）**：输入需求 → AI 流式生成单文件 HTML → SSE 推送 → Markdown 渲染 → iframe 实时预览 → 落盘。  
  → *没有这个，产品不成立。*
- **P0**：应用 CRUD + 我的应用列表 + 对话历史落库（多轮改的前提）。
- **P1**：AI 路由选策略 + 多文件/Vue 工程模式 + AI 工具调用写文件。
- **P1**：可视化编辑（iframe 点选 + 选择器拼进 prompt）。
- **P1**：一键部署（静态托管 + 短链）+ 源码 ZIP 下载。
- **P2**：用户体系（注册/Session/权限）、后台管理、精选广场。
- **P2**：限流、Prompt 护栏、Selenium 截图封面、COS 上传。
- **P3**：微服务化（Dubbo/Nacos）、监控、多级缓存、改造 LangChain4j 源码。

## 7 天开发路线

| 天 | 目标 | 产物 |
| --- | --- | --- |
| **Day 1** | 环境 + 骨架 | 前端 Vue3+Vite+AntdVue 空壳；后端 Spring Boot 空壳；MySQL 建 `user/app/chat_history` 三表；接通一个 LLM（DeepSeek）能返回 hello |
| **Day 2** | MVP 核心 | 输入需求 → 后端 LangChain4j 流式生成 HTML → SSE 推前端 → markdown-it 渲染 → iframe 预览 → 落盘。**这一天结束产品已成立** |
| **Day 3** | 应用管理 | App CRUD、我的应用列表、对话历史落库、多轮对话记忆（先内存/Redis） |
| **Day 4** | 多策略 + AI 工具 | 实现 AI 路由；加多文件模式；Vue 工程模式 + `writeFile/readFile/modifyFile` 工具 + npm build 预览 |
| **Day 5** | 可视化编辑 | iframe 注入脚本 → 点选元素 → 生成 CSS 选择器 → 拼进 prompt → AI 精准改 |
| **Day 6** | 部署 + 下载 | 静态资源 Controller 托管 `tmp/code_deploy/<deployKey>`；部署流程（build→copy→短链）；ZIP 打包下载；可选 Selenium 截图当封面 |
| **Day 7** | 用户/后台/优化 | 注册登录 + Session(Redis) + 权限；admin 后台（用户/应用/对话）；限流；Prompt 安全护栏；监控接入；联调修 bug |

> 第 8 天起再做微服务拆分、多级缓存、AI 智能体增强（LangGraph4j 工作流、RAG、MCP 等）。

## AI 协作策略

| 模块 | 让 AI 生成的程度 | 人工审查重点 |
| --- | --- | --- |
| **数据库 DDL（建表）** | 🟢 AI 100% 生成 | 字段类型/索引/逻辑删除字段 |
| **Entity/DTO/VO/枚举** | 🟢 AI 100% 生成 | 字段与表一致、序列化注解 |
| **CRUD Controller/Service** | 🟢 AI 100% 生成 | 权限校验、分页参数限制、异常处理 |
| **前端页面（列表/表单/后台表格）** | 🟢 AI 100% 生成 | 交互细节、loading/空态 |
| **API 请求层** | 🟢 用 OpenAPI 自动生成 | — |
| **Markdown 流式渲染组件** | 🟡 AI 生成后调 | 流式拼接性能、代码高亮 |
| **SSE 前端消费（EventSource）** | 🟡 AI 生成后调 | 断连重试、done/error 事件区分 |
| **SSE 后端（Flux<ServerSentEvent>）** | 🟡 AI 生成后调 | 背压、超时、异常事件 |
| **LangChain4j AiService + 系统 Prompt** | 🟡 AI 生成后调 | Prompt 工程需反复打磨（决定生成质量） |
| **AI 工具（@Tool 文件操作）** | 🟡 AI 生成后调 | **路径越权/重要文件保护/并发写** |
| **可视化编辑器（postMessage + 注入脚本）** | 🟡 AI 生成后调 | 选择器准确性、编辑态样式泄漏 |
| **整体架构 / 微服务拆分** | 🔴 必须人工设计 | 服务边界、数据一致性、扩展性 |
| **LangChain4j 源码覆盖** | 🔴 必须人工设计 | 升级兼容、最小化覆盖面 |
| **部署架构 / Nginx / COS / 监控** | 🔴 必须人工设计 | 安全、成本、可观测性 |
| **Prompt 安全护栏规则** | 🔴 必须人工设计 | 攻击面、误杀率 |

**黄金法则**：让 AI 写「重复的、有模板的、CRUD 的」；人工盯「架构、安全、Prompt 质量、AI 行为可控性」。

---

# 第七部分：项目复刻 Prompt

> 把下面这段整体喂给 Cursor / Claude Code / Windsurf，可让 AI 直接开始生成第一版项目骨架。

```markdown
# 任务：开发一个 AI 零代码应用生成平台（参考 yu-ai-code-mother）

你是一名资深全栈工程师。请从零搭建一个全栈项目，使用以下技术栈与规范。

## 一、技术栈
- 后端：Java 21 + Spring Boot 3.5 + Spring WebFlux(Reactor) + MyBatis-Flex + MySQL 8 + Redis + Redisson
- AI：LangChain4j 1.1（OpenAI 兼容协议，默认接 DeepSeek）；支持流式；支持 @Tool 工具调用；支持 ChatMemory(Redis 存储)
- 前端：Vue 3.5 + TypeScript + Vite 7 + Vue Router 4 + Pinia 3 + Ant Design Vue 4 + axios
- Markdown：markdown-it + highlight.js（支持流式增量渲染）
- 其它：Knife4j(OpenAPI)、Caffeine、Hutool、Lombok
- 部署：Nginx 反代；本地磁盘托管静态站点；Selenium 无头浏览器截图（可选）

## 二、功能需求
1. 用户：注册/登录/登出（Spring Session + Redis，cookie 30 天）；角色 user/admin。
2. 应用(App)：用户输入一段需求(initPrompt, ≤1000 字)创建应用；后端调 AI 路由服务，按需求复杂度选定 codeGenType ∈ {html, multi_file, vue_project}。
3. 代码生成(核心)：
   - GET /app/chat/gen/code?appId=&message= ，produces=text/event-stream，用 Flux<ServerSentEvent<String>>。
   - html/multi_file：LangChain4j 流式 Flux<String>，前端实时 markdown 渲染；流结束后解析 ```html/css/javascript 代码块并落盘到 tmp/code_output/<type>_<appId>/。
   - vue_project：用 AI 工具(writeFile/modifyFile/deleteFile/readFile/readDir/exit)直接写文件到 tmp/code_output/vue_project_<appId>/；流式输出 JSON 消息 {type: ai_response|tool_request|tool_executed, ...}；流结束时跑 npm install && npm run build 生成 dist。
   - 每条用户消息先落库 chat_history(user)，AI 回复完成时落库 chat_history(ai)。
   - 限流：每用户每分钟 5 次（@RateLimit 注解 + AOP + Redisson）。
4. 预览：前端 iframe 指向 GET /api/static/<type>_<appId>/[/dist/index.html]（StaticResourceController 托管）。
5. 可视化编辑：iframe 加载后注入脚本，捕获 mouseover/click，点选元素生成 CSS 选择器，postMessage 回父窗口；发送消息时把「选中元素信息（页面路径/标签/选择器/当前内容）」拼进 prompt，让 AI 精准修改。
6. 部署：POST /app/deploy {appId} → vue 项目先 npm build → 把产物拷到 tmp/code_deploy/<6位deployKey>/ → 返回 http://host/<deployKey>/；可选异步 Selenium 截图当 cover。
7. 下载：GET /app/download/{appId} → 把生成目录打 ZIP 流式下载（仅创建者）。
8. 后台：admin 可分页管理 user/app/chat_history；可设精选(priority=99)、改封面。

## 三、页面需求（前端路由）
- / HomePage：落地页 + 需求输入框 + 4 个快捷模板按钮 + "我的作品"和"精选案例"两个卡片画廊（a-pagination）。
- /user/login /user/register：账号密码登录注册。
- /app/chat/:id AppChatPage ★：左侧 AI 对话（流式 markdown、历史加载更多、当前选中元素提示），右侧 iframe 预览（编辑模式开关、新窗口打开）；顶栏按钮：应用详情/下载代码/部署。是创建者且无历史时自动发送 initPrompt。
- /app/edit/:id AppEditPage：编辑应用名称；管理员可改封面/优先级。
- /admin/userManage /admin/appManage /admin/chatManage：a-table 后台管理。
- 全局：BasicLayout(Header+Content+Footer)，access.ts 路由守卫，/admin/* 需 admin。

## 四、数据库设计（MySQL，逻辑删除 isDelete，雪花 ID）
- user(id BIGINT PK, userAccount VARCHAR uniq, userPassword VARCHAR, userName, userAvatar, userProfile, userRole ENUM('user','admin'), editTime, createTime, updateTime, isDelete TINYINT)
- app(id BIGINT PK, appName VARCHAR(64), cover VARCHAR(512), initPrompt TEXT, codeGenType VARCHAR(32), deployKey VARCHAR(16), deployedTime DATETIME, priority INT DEFAULT 0, userId BIGINT, editTime, createTime, updateTime, isDelete TINYINT; INDEX(userId), INDEX(priority))
- chat_history(id BIGINT PK, message TEXT, messageType ENUM('user','ai'), appId BIGINT, userId BIGINT, createTime, updateTime, isDelete TINYINT; INDEX(appId), INDEX(userId))

## 五、接口设计（REST，统一返回 BaseResponse<T>{code,data,message}; code=0 成功; 40100 未登录）
- 用户：POST /user/register /user/login /user/logout，GET /user/get/login（当前用户），POST /user/list/page/vo(admin)，POST /user/delete(admin)
- 应用：POST /app/add，POST /app/update，POST /app/delete，GET /app/get/vo，POST /app/my/list/page/vo，POST /app/good/list/page/vo(@Cacheable)，POST /app/deploy，GET /app/download/{appId}，GET /app/chat/gen/code(SSE)
- 对话历史：GET /chatHistory/app/{appId}?pageSize&lastCreateTime（游标分页），POST /chatHistory/admin/list/page/vo
- 静态资源：GET /static/{deployKey}/**，GET /static/<type>_<appId>/[/dist/index.html]
- 管理：/app/admin/{delete,update,list/page/vo,get/vo} 均 @AuthCheck(ADMIN_ROLE)
- 监控：Actuator + micrometer-prometheus 暴露 /actuator/prometheus

## 六、AI 服务设计（LangChain4j）
- AiCodeGenTypeRoutingService：结构化输出选 codeGenType（无记忆/无工具/非流式，模型用便宜的小模型）。
- AiCodeGeneratorService（@SystemMessage 从资源文件加载 prompt）：
  - generateHtmlCodeStream(String) → Flux<String>
  - generateMultiFileCodeStream(String) → Flux<String>
  - generateVueProjectCodeStream(@MemoryId long appId, @UserMessage String) → TokenStream（注册所有 @Tool，maxSequentialToolsInvocations=20，hallucinatedToolNameStrategy 返回错误结果让模型自纠，inputGuardrails=输入安全护栏）
- 工厂用 Caffeine 按 appId+type 缓存 Service；构建时从 DB 读最近 20 条历史灌进 MessageWindowChatMemory(RedisChatMemoryStore)；模型用原型作用域每次取新实例。
- 护栏：输入侧拦截长度>1000、空、含「忽略之前指令/越狱/jailbreak」等；输出侧护栏（可选）注意会缓冲流式，影响体验。

## 七、AI 工具设计（@Tool，参数用 @P，记忆用 @ToolMemoryId appId）
- writeFile(relativeFilePath, content, appId)：写到 CODE_OUTPUT_ROOT/vue_project_<appId>/ 下，自动建父目录，返回相对路径（禁止返回绝对路径）。
- modifyFile(relativeFilePath, oldContent, newContent, appId)：字符串替换，找不到报错。
- deleteFile(relativeFilePath, appId)：拒绝删除 package.json/vite.config.js/index.html/main.js/App.vue/tsconfig/.gitignore 等关键文件。
- readFile(relativeFilePath, appId)、readDir(relativeDirPath, appId)（忽略 node_modules/dist/.git）。
- exit()：返回提示让 AI 结束工具循环。
- 抽象 BaseTool(toolName/displayName/generateToolExecutedResult)，ToolManager 注入所有工具。

## 八、开发规范
- 后端：包名 com.xxx.{controller,service,core,ai,model,common}；统一异常 BusinessException + GlobalExceptionHandler；统一 ResultUtils；所有写操作记 editTime；逻辑删除；DTO 用 @Valid 校验。
- 前端：composition API + <script setup lang="ts">；API 层用 @umijs/openapi 从 Swagger 自动生成；SSE 用原生 EventSource（不用 axios）；下载用 fetch+blob；request.ts 统一拦截 40100 跳登录。
- 配置：application.yml 含 datasource/redis/langchain4j(4 套模型: chat/streaming-chat/reasoning-streaming/routing)/dubbo/nacos/knife4j；前端 .env.development 配 VITE_API_BASE_URL/VITE_DEPLOY_DOMAIN。
- 安全：所有 AI 文件工具做路径越权校验；输入护栏；限流；XSS 防护（markdown html 渲染注意）。

## 九、交付顺序
请先输出：① 数据库 DDL ② 后端目录结构 ③ 关键类的骨架(AppController.chatToGenCode / AiCodeGeneratorFacade / AiCodeGeneratorService / 一个 @Tool) ④ 前端目录结构与 AppChatPage 的 SSE 接收骨架。然后逐模块实现，每完成一块用一句话总结。
```

---

# 第八部分：迁移与创新

### 1. 完全复刻需要哪些核心能力？
- **流式 LLM 调用 + SSE 全链路**（后端 `Flux<ServerSentEvent>` ↔ 前端 `EventSource`）。
- **LangChain4j AiService / @Tool / ChatMemory** 编排能力。
- **Prompt 工程**：HTML/多文件/Vue 三套系统提示词 + 路由提示词 + 修改场景提示词。
- **代码块解析 + 落盘 + 模板方法/策略** 的小工程能力。
- **iframe 跨窗口通信（postMessage）+ DOM 选择器生成** 的前端能力。
- **静态站点托管 + npm 构建 + 短链部署 + 截图** 的部署能力。
- **Spring Session/Redis/限流/权限/缓存** 的后端基本功。

### 2. 差异化创新方向
- **多模态**：上传设计稿/草图 → 生成；或 AI 生成 + 图片素材库（项目里已有 `image-collection-*` prompt 残留，可激活）。
- **模板市场**：精选广场升级为可 fork 的模板，用户基于模板二创。
- **AI 工作流（LangGraph4j）**：拆成「需求分析→架构设计→分文件→自检→修复」多节点流水线，质量更高。
- **协作编辑**：多人同时改一个应用（CRDT/OT）。
- **后端生成**：不止前端，连 API/数据库一起生成（变成真正的全栈 v0）。
- **AI 自检 + 自动修复**：build 失败时把报错喂回 AI 自动修（项目已有 `code-quality-check-system-prompt.txt` 但未启用）。
- **MCP / 工具生态**：让生成出来的应用能调用外部工具、数据库、API。
- **垂直化**：只做某个领域（电商落地页、个人简历、数据看板），体验做到极致。

### 3. 哪些功能是项目成功的关键？
- ⭐ **流式输出 + 实时预览**：用户即写即看，体验拉满（留存核心）。
- ⭐ **可视化编辑（点选+对话改）**：差异化于纯对话型生成器，降低二次修改门槛。
- ⭐ **Vue 工程模式 + AI 工具调用**：能产出真正可用的复杂项目，不只是玩具 HTML。
- ⭐ **一键部署分享**：让成果能立刻给别人看（传播关键）。
- AI 路由（自动选策略）：体验顺滑，但非必需（可让用户手选）。

### 4. 哪些功能其实可以删除？
- **微服务拆分（Dubbo/Nacos）**：对 MVP 是过度设计，单体完全够用；教学价值高但工程上是负担。
- **改造 `dev.langchain4j/` 覆盖包**：技术债重，新版 LangChain4j 已原生支持流式工具事件，**优先升级版本而非覆盖**。
- **微服务主线未启用的 prompt**（`image-collection-*`、`code-quality-check-system-prompt.txt`）：单体实验线已激活，主线未整合（详见附录 C）。要么搬到主线，要么从主线删除避免误导。
- **`ChatManagePage.deleteMessage` 前端桩**（无后端接口），要么补接口要么删按钮。
- **生成的 `chatToGenCode` API 函数**（前端未用，SSE 手写），属死代码。
- **根目录 `pom.xml` 旧单体依赖**：已被微服务父 POM 取代，易误导。

### 5. 如何利用 AI 提升开发效率？
- **数据库 → 实体 → CRUD → 前端页面**：全部让 AI 批量生成，省 60%+ 工时。
- **OpenAPI → 前端 TS**：用工具自动同步，告别手写请求函数。
- **Prompt 反复迭代**：把生成质量差的 case 喂给 AI 让它改 prompt，形成「prompt 即代码」的版本管理。
- **AI 写 AI 工具**：让 Cursor 帮你写新的 `@Tool`（如 `runCommand`、`searchWeb`）扩展能力。
- **AI 写测试**：给核心 `Facade`、`StreamHandler`、`Parser/Saver` 生成单测。
- **用 AI 做 Code Review**：重点扫路径越权、并发、SQL 注入、XSS。

---

# 第九部分：AI 生成难度评估

> 🟢 AI 可 100% 生成 ｜ 🟡 AI 生成后需人工修改 ｜ 🔴 必须人工设计

| 模块 / 文件 | 难度 | 说明 |
| --- | --- | --- |
| 数据库 DDL（user/app/chat_history） | 🟢 | 标准三表，AI 一次到位 |
| Entity / DTO / VO / Enum（model 模块） | 🟢 | 模板化，AI 全生成 |
| Mapper + XML（MyBatis-Flex） | 🟢 | 基本无自定义 SQL |
| 通用层（BaseResponse/ErrorCode/Exception/ResultUtils/PageRequest） | 🟢 | 鱼皮同款模板，AI 熟 |
| 用户 Controller/Service（登录注册 CRUD） | 🟢 | 套路化 |
| 应用/对话/静态资源 Controller 的 CRUD 部分 | 🟢 | 套路化 |
| 前端登录/注册/后台 a-table 页面 | 🟢 | Antd 套模板 |
| 前端 API 层（OpenAPI 自动生成） | 🟢 | 工具生成 |
| 前端 HomePage / AppCard 画廊 | 🟡 | 动效/布局需微调 |
| **Markdown 流式渲染组件**（MarkdownRenderer） | 🟡 | 流式重渲染性能、高亮需调 |
| **前端 SSE 消费**（AppChatPage.generateCode） | 🟡 | done/error/重连边界要手调 |
| **后端 SSE Controller**（Flux + ServerSentEvent） | 🟡 | 事件封装、超时需调 |
| **LangChain4j AiService + 4 套系统 Prompt** | 🟡 | 骨架 AI 写，**Prompt 内容必须反复打磨** |
| **AI 工具（@Tool 文件操作）** | 🟡 | 路径越权/重要文件保护/并发必须人工审 |
| **AiCodeGeneratorServiceFactory**（Caffeine 缓存+原型模型+记忆预热） | 🟡 | 并发与记忆一致性需审 |
| **可视化编辑器**（visualEditor.ts + iframe 注入） | 🟡 | 选择器准确性、样式泄漏需调 |
| **流处理器**（JsonMessageStreamHandler 工具去重） | 🟡 | toolId 去重逻辑需审 |
| **CodeParser/CodeFileSaver**（策略+模板方法） | 🟡 | 正则鲁棒性需测 |
| **VueProjectBuilder**（npm install/build 子进程） | 🟡 | 超时/跨平台/编码需调 |
| **Selenium 截图服务** | 🟡 | 环境(Chrome/驱动)依赖重 |
| **限流（@RateLimit + Redisson + AOP）** | 🟡 | Key 设计/IP 解析需审 |
| **Prompt 安全护栏规则** | 🔴 | 攻防对抗，规则必须人工定 |
| **整体微服务架构 / Dubbo / Nacos 划分** | 🔴 | 服务边界人工设计 |
| **`dev/langchain4j/` 源码覆盖包** | 🔴 | 强依赖版本，必须人工对齐（建议改为升级官方版） |
| **部署架构（Nginx/COS/监控/磁盘扩展）** | 🔴 | 安全/成本/可扩展人工设计 |
| **AI Agent 工作流（LangGraph4j）** | 🔴 | 节点编排人工设计 |

---

## 附：核心闭环时序图（最值得复刻的一段）

```mermaid
sequenceDiagram
    autonumber
    participant U as 用户(浏览器)
    participant F as 前端 AppChatPage
    participant C as AppController
    participant S as AppService
    participant Fc as AiCodeGeneratorFacade
    participant AI as LangChain4j+LLM
    participant T as Tools(写文件)
    participant H as StreamHandler
    participant DB as MySQL
    participant Disk as 本地磁盘

    U->>F: 输入需求/修改
    F->>C: GET /app/chat/gen/code?appId&message (SSE)
    C->>S: chatToGenCode(appId, msg, user)
    S->>DB: 存 user 消息
    S->>Fc: generateAndSaveCodeStream(...)
    alt html/multi_file
        Fc->>AI: Flux<String> 流式
        AI-->>Fc: 代码片段 x N
        Fc-->>S: Flux<String>
        S->>H: SimpleTextStreamHandler (完成时存 ai 消息)
    else vue_project
        Fc->>AI: TokenStream(带工具+记忆)
        loop 工具循环 (≤20)
            AI->>T: writeFile/modifyFile...
            T->>Disk: 写 vue_project_<appId>/
            T-->>AI: 结果
            AI-->>Fc: tool_executed 事件
        end
        Fc->>Disk: npm install && npm build
        Fc-->>S: Flux<JSON StreamMessage>
        S->>H: JsonMessageStreamHandler (去重+落库)
    end
    H-->>C: Flux<String>
    C-->>F: ServerSentEvent {d: chunk} ... event:done
    F->>F: markdown 实时渲染 + iframe 刷新预览
    F->>U: 看到 AI 边写边出效果
```

---

# 第十部分：架构决策记录 ADR

> 这一章回答"**为什么这么选**"，而不是"选了什么"。复刻时真正值钱的是决策背后的**权衡逻辑**——它能让你在自己的场景里做对取舍，而不是照抄。

## ADR-01：流式通信为什么用 SSE 而非 WebSocket？

| 维度 | 选择 |
| --- | --- |
| **决策** | SSE（`text/event-stream` + `Flux<ServerSentEvent>`） |
| **放弃方案** | WebSocket、长轮询、gRPC streaming |
| **上下文** | 代码生成是典型的**服务器→客户端单向流**：AI 逐字吐 token，前端只读+渲染，几乎不需要反向通道（用户输入是普通 POST）。 |
| **理由** | ① SSE 基于 HTTP，自动走浏览器重连、自动穿透 Nginx/CDN，运维零成本；WebSocket 常被中间件掐断需额外配置。② 浏览器原生 `EventSource` 自带断线重连与 `last-event-id`，前端代码极简（项目里就 ~30 行）。③ Spring WebFlux 的 `Flux<ServerSentEvent>` 与 LangChain4j 的 `Flux<String>` 天然衔接，背压由 Reactor 接管。④ 单向语义清晰，不需要 WebSocket 的双向状态机复杂度。 |
| **代价** | 单连接 6 条并发上限（HTTP/1.1 浏览器限制）、单向（用户改需另开请求）。 |
| **何时该改用 WebSocket** | 需要服务器主动推送 + 客户端高频回传（如多人协作编辑、AI 主动追问澄清）。本项目用 SSE 完全够。 |

## ADR-02：AI 编排为什么用 LangChain4j 而非 Spring AI / 自研 HTTP？

| 维度 | 选择 |
| --- | --- |
| **决策** | LangChain4j 1.1 |
| **放弃方案** | Spring AI、LangChain（Python 微服务）、直接调 OpenAI HTTP |
| **上下文** | 需要「流式 + 工具调用（@Tool）+ 对话记忆 + 结构化输出 + 输入/输出护栏」五件套，且必须留在 Java 技术栈内（教学项目主语言）。 |
| **理由** | ① LangChain4j 在 2024–2025 间是 Java 生态里**唯一**同时把上述五件套做完整的框架；Spring AI 当时（项目立项）工具调用与流式的成熟度不足。② `AiServices` 用注解+接口就能声明 AI 服务，`@Tool` 让文件操作工具零样板接入，开发效率高。③ 支持 OpenAI 兼容协议，可无缝切 DeepSeek/Qwen/GPT。 |
| **代价** | ① 版本迭代快（1.x 仍 beta），API 易变；② **流式模式下的工具调用事件上游不暴露**——这正是项目里出现 `dev/langchain4j/` 覆盖包的根因（见 ADR-08）。 |
| **现状建议** | 复刻时若今天起步，**LangChain4j 与 Spring AI 都可**；优先选文档/社区对你更友好的那个。无论哪个，都别再覆盖源码——升级行为不明确。 |

## ADR-03：Agent 形态为什么是"AI + 文件工具"而非"Claude Code 式自由 Agent"？

| 维度 | 选择 |
| --- | --- |
| **决策** | **受限工具集 Agent**：只给 AI `writeFile/modifyFile/deleteFile/readFile/readDir/exit` 六个文件工具，`maxSequentialToolsInvocations=20`。 |
| **放弃方案** | 给 AI `runCommand`（可执行任意 shell）、`searchWeb`、联网装包等自由度更高的 Agent。 |
| **上下文** | 产品要"AI 生成可运行网站"，但服务器是**多租户共享**的——一个 AI 能跑 shell 的应用，等于把服务器交给用户。 |
| **理由** | ① **安全边界清晰**：AI 只能动 `vue_project_<appId>/` 沙箱目录，`deleteFile` 还内置了关键文件白名单（package.json/vite.config.js 等不可删）。② 工具集小=可观测、可解释，前端能把"AI 正在写文件 X"实时展示（这是产品卖点）。③ `exit` 工具给 AI 一个体面的退出信号，配合 20 次硬上限做"软+硬"双重终止。 |
| **代价** | 灵活性受限——AI 不能自己 `npm install 新依赖`，只能用预置的 vue+vue-router 技术栈（prompt 里写死"禁止使用状态管理库/类型库"）。 |
| **何时该放宽** | 单租户/企业私有部署、或上了容器隔离（每应用一个 Docker）后，可放开 `runCommand`。 |

## ADR-04：前端为什么用 Vue3 而非 React？

| 维度 | 选择 |
| --- | --- |
| **决策** | Vue 3.5 + TypeScript + Ant Design Vue |
| **放弃方案** | React + AntD、Next.js |
| **上下文** | 鱼皮系列项目历史技术栈统一在 Vue3；目标受众是 Java 后端转全栈的学习者。 |
| **理由** | ① 与作者其他项目保持一致，降低学员切换成本；② Vue 的 `<script setup>` + 组合式 API 上手门槛对后端友好；③ Ant Design Vue 组件齐全、中文文档好。 |
| **代价** | React 生态（Next/shadcn/radix）在 AI 应用领域更活跃，部分组件库选择略少。 |
| **与产品无关** | 这是个**教学一致性**决策，不是技术最优解。复刻时按你团队熟悉的来。 |

## ADR-05：代码生成为什么做多策略路由，而非一律生成完整工程？

| 维度 | 选择 |
| --- | --- |
| **决策** | AI 路由服务按需求复杂度，在 `html / multi_file / vue_project` 三选一。 |
| **放弃方案** | 一律生成 Vue 工程、或一律生成单文件。 |
| **上下文** | 单文件 HTML 几秒出结果、零构建；Vue 工程要几十秒 + npm build，体验重。 |
| **理由** | ① **成本与体验的动态平衡**：简单需求用便宜模型 + 单文件，秒出；复杂需求才上推理模型 + 工具 Agent。路由模型用的是 Qwen-Turbo（max-tokens=100，几乎免费）。② 避免"杀鸡用牛刀"——做个人简介页跑 npm build 是浪费。 |
| **代价** | ① 多一套 prompt + 多一套解析/保存逻辑，复杂度翻倍；② 路由判断错了（简单需求被分到 Vue），用户体验会差。 |
| **复刻建议** | MVP 阶段**完全可以砍掉路由**，写死单文件 HTML，等核心闭环验证后再加。 |

## ADR-06：记忆为什么是"MySQL 事实源 + Redis 运行时"双层？

| 维度 | 选择 |
| --- | --- |
| **决策** | MySQL `chat_history` 为事实源；每次构建 AI 服务时从 DB 读最近 20 条灌进 Redis（`RedisChatMemoryStore`）里的 `MessageWindowChatMemory`。 |
| **放弃方案** | 只用 Redis、只用 DB、或全量灌入上下文。 |
| **上下文** | 对话历史既要持久化（用户下次回来还能看），又要低延迟喂给 LLM。 |
| **理由** | ① Redis 易丢、不能作为事实源；② LangChain4j 的 ChatMemory 是进程内+可拔插 Store 的双层结构，天然契合；③ Caffeine 缓存的 AI 服务实例 30min 过期，重建时正好用 DB 重灌，保证记忆新鲜且一致。 |
| **代价** | 双写一致性问题（见踩坑指南坑7）；窗口截断丢历史。 |
| **关键不变量** | **DB 是唯一事实源**，Redis 可全量重建——这条不变量决定了系统在 Redis 故障时仍可降级。 |

## ADR-07：为什么覆盖 `dev/langchain4j/` 源码？

| 维度 | 选择 |
| --- | --- |
| **决策** | 在 `yu-ai-code-ai` 模块下放 `dev/langchain4j/{internal,model,service}` 同包同类，让 classloader 优先加载项目副本。 |
| **放弃方案** | 等 LangChain4j 官方支持、或放弃流式工具事件展示。 |
| **上下文** | 要在 Vue 工程模式下让前端**实时看到"AI 正在调用 writeFile 工具"**，需要 `onPartialToolExecutionRequest` 回调；上游版本只在流结束后才给完整工具请求，流式过程中不发事件。 |
| **理由** | 这是产品体验的核心差异化（实时展示 Agent 思考过程）。 |
| **代价** | ① **强技术债**：升级 LangChain4j 时必须手工 diff 8 个文件，否则丢失上游修复/安全补丁；② IDE 里同名类来自两处，调试易混淆；③ 代码里还残留 `System.out.println("OLOLO ...")` 调试痕迹。 |
| **复刻建议** | **不要照抄**。今天 LangChain4j 新版本已原生支持流式工具事件；复刻时直接升级官方版。这条 ADR 是"当时条件下的妥协"，不是"好的工程实践"。 |

## ADR-08：部署为什么用本地磁盘 + 静态托管，而非对象存储 / 容器？

| 维度 | 选择 |
| --- | --- |
| **决策** | 生成的代码写 `tmp/code_output/`，构建产物拷到 `tmp/code_deploy/<6位deployKey>/`，由 `StaticResourceController` 托管。 |
| **放弃方案** | 直接传 COS/OSS、每应用一个 Docker 容器、K8s。 |
| **上下文** | 教学项目，要在单机跑通；Vue 项目需要 `npm build` 产生可静态托管的 dist。 |
| **理由** | ① 本地磁盘零依赖、零成本、调试方便；② 静态托管一个 Controller 就能搞定；③ deployKey 用 6 位短串，URL 短好分享。 |
| **代价** | ① **无法水平扩展**——多实例时文件不共享；② 服务器挂了用户作品全丢；③ 无 CDN、无鉴权（任何人有 key 就能访问）。 |
| **何时该改** | 上生产/多用户时，必须换：构建产物 → 对象存储 + CDN；代码源 → Git 或对象存储；隔离 → 容器/沙箱。这是从 demo 到产品的必经一跳。 |

## ADR-09：为什么用 Caffeine 缓存 AI 服务实例？

| 维度 | 选择 |
| --- | --- |
| **决策** | `Caffeine` 缓存 `AiCodeGeneratorService`（key=`appId_type`，max 1000，写后 30min/访问后 10min）。 |
| **放弃方案** | 每次请求重建、或全局单例。 |
| **上下文** | 构建一个 AI 服务 = 重建记忆 + 加载历史 + 注入工具，开销不小；但流式模型实例非线程安全，不能全局共享。 |
| **理由** | ① 同一 app 的连续对话复用同一服务实例，省掉重建开销、保证记忆连续；② 每 key 独立实例天然隔离并发流。 |
| **代价** | 内存占用（1000 实例上限需评估）；过期重建时记忆从 DB 重灌。 |

## ADR 速查表

| # | 决策 | 选择 | 主要放弃 | 核心理由 |
| --- | --- | --- | --- | --- |
| 01 | 流式通信 | SSE | WebSocket | 单向流、运维零成本、原生重连 |
| 02 | AI 编排 | LangChain4j | Spring AI/自研 | Java 生态五件套最全 |
| 03 | Agent 形态 | 受限文件工具 | 自由 shell Agent | 多租户安全边界 |
| 04 | 前端 | Vue3 | React | 教学一致性 |
| 05 | 生成策略 | AI 路由多策略 | 单一模式 | 成本/体验动态平衡 |
| 06 | 记忆架构 | DB+Redis 双层 | 单一存储 | 持久化+低延迟，DB 为事实源 |
| 07 | 源码覆盖 | 覆盖 langchain4j | 等官方 | 补流式工具事件（**技术债，勿抄**） |
| 08 | 部署 | 本地磁盘 | 对象存储/容器 | 单机零依赖（**生产必改**） |
| 09 | 服务缓存 | Caffeine | 单例/每次新建 | 复用省开销、按 key 隔离并发 |

---

# 第十一部分：Prompt 体系拆解

> 对 AI 项目而言，**Prompt 才是真正的"业务逻辑"**。代码只是 Prompt 的运行时。这一章把 7 套 Prompt 的职责、输入输出、质量风险全部摊开。

## Prompt 全景图

```mermaid
graph LR
    U["用户需求<br/>initPrompt"] --> RP{"路由 Prompt<br/>(Qwen-Turbo)"}
    RP -->|简单| HP["HTML Prompt<br/>(deepseek-chat)"]
    RP -->|中等| MP["多文件 Prompt<br/>(deepseek-chat)"]
    RP -->|复杂| VP["Vue 工程 Prompt<br/>(deepseek-reasoner)"]

    HP --> HR["```html```"]
    MP --> MR["```html``` ```css``` ```js```"]
    VP --> VT["工具调用序列<br/>writeFile × N"]

    VT -.可扩展.-> QC["质量检查 Prompt<br/>(主线未接入/单体已用)"]
    VT -.可扩展.-> IC["图片规划 Prompt<br/>(主线未接入/单体已用)"]
```

## 各 Prompt 职责矩阵

### 1. 路由 Prompt（`codegen-routing-system-prompt.txt`）

| 维度 | 内容 |
| --- | --- |
| **职责** | 决定生成策略，是整个系统的"调度器" |
| **模型** | Qwen-Turbo（便宜小模型，max-tokens=100） |
| **输入** | 用户需求文本 |
| **输出** | 枚举值 `HTML` / `MULTI_FILE` / `VUE_PROJECT`（结构化输出） |
| **配置特征** | 无记忆、无工具、非流式——**纯粹一次分类调用** |
| **判断规则**（写死在 prompt 里） | 简单展示页→HTML；多页无复杂交互→MULTI_FILE；复杂/多页/数据管理→VUE_PROJECT |
| **风险** | ① 规则模糊（"复杂"无量化），模型判断漂移；② 一旦定下，**整条对话不可改类型**（App.codeGenType 不可变） |
| **复刻优化** | 加 few-shot 示例；输出带置信度，低于阈值让用户手选 |

### 2. HTML 生成 Prompt（`codegen-html-system-prompt.txt`）

| 维度 | 内容 |
| --- | --- |
| **职责** | 生成**单文件**响应式网站（内联 CSS+JS） |
| **模型** | deepseek-chat（流式） |
| **输入** | 用户需求 + 历史对话 |
| **输出** | **恰好 1 个** ```html 代码块 |
| **硬约束** | ① 禁外部依赖；② 单文件；③ 响应式 Flex/Grid；④ 占位图用 picsum.photos |
| **修改场景约束** | 最多 1 个代码块，必须含**完整页面**（非补丁），否则保存报错 |
| **风险** | ① 模型吐多个代码块→解析器只取第一个→丢内容；② 修改时 AI 倾向"只给改动片段"违反约束 |
| **复刻要点** | 把"最多 1 个代码块""完整页面"用大写/重复强调；解析器做强校验+重试 |

### 3. 多文件 Prompt（`codegen-multi-file-system-prompt.txt`）

| 维度 | 内容 |
| --- | --- |
| **职责** | 生成 HTML/CSS/JS **三文件**分离的网站 |
| **输出** | 恰好 3 个代码块：```html ```css ```javascript |
| **硬约束** | 同 HTML 模式；三文件通过 `<link>`/`<script>` 互引 |
| **风险** | 同 HTML；额外风险：模型漏掉某个语言代码块→保存缺失文件 |
| **复刻要点** | 解析器对缺失文件做兜底（空文件而非报错） |

### 4. Vue 工程 Prompt（`codegen-vue-project-system-prompt.txt`）⭐ 最重要

| 维度 | 内容 |
| --- | --- |
| **职责** | 指导 Agent 用**工具调用**从零搭建可运行 Vue3 工程 |
| **模型** | deepseek-reasoner（推理模型） |
| **输入** | 用户需求 + 历史对话 + `@MemoryId appId`（工具隔离） |
| **输出** | 工具调用序列（writeFile 逐文件创建）+ 文字计划 |
| **技术栈写死** | Vue3 组合式 API + Vite + vue-router4（hash 模式）+ 原生 CSS；**禁止**状态管理/类型校验/格式化库 |
| **vite.config 约束** | `base:'./'`（支持子路径部署）、`@` 别名、不配端口 |
| **硬约束** | ① 必须**用工具**创建文件，不直接吐代码；② 开头输出计划、结尾输出完成提示；③ 总 token<20000、文件数<30；④ 禁止输出安装步骤/技术栈说明等废话 |
| **质量标准** | `npm install` + `npm run dev` + `npm run build` 三者必须通过；子路径可部署 |
| **修改场景约束** | 严格只改用户指定元素；**必须用工具**（先 readDir→readFile→modifyFile/writeFile），禁止重输出整个项目 |
| **风险** | ① token/文件数超限→截断→工程残缺；② AI 忽略"用工具"约束直接吐代码；③ 修改时改了用户没要求的部分 |
| **复刻要点** | 这套 prompt 是项目最有价值的资产，**要像维护代码一样版本化它**，针对失败 case 持续迭代 |

### 5. 质量检查 Prompt（`code-quality-check-system-prompt.txt`）— 🟡 **单体已接入，微服务未接入**

| 维度 | 内容 |
| --- | --- |
| **职责** | 检查生成代码的语法/结构/功能，输出 `{isValid, errors, suggestions}` JSON |
| **接入现状** | ⚠️ **链路差异**：微服务主链路**未引用**；但**单体工作流模块**已通过 `CodeQualityCheckService` + `CodeQualityCheckNode` 接入，且质检失败会经条件边回到 `code_generator` 重生成（自愈回路，见附录 C Phase 6） |
| **设计意图** | build 失败时把代码喂给它自检自修（AI 自愈回路） |
| **复刻价值** | ⭐⭐⭐ 高。单体里的实现可直接借鉴到主链路，显著提升 Vue 工程一次成功率 |

### 6. 图片规划 Prompt（`image-collection-plan-system-prompt.txt`）+ 图片系统 Prompt（`image-collection-system-prompt.txt`）— 🟡 **单体已接入，微服务未接入**

| 维度 | 内容 |
| --- | --- |
| **职责** | 规划网站需要的图片（内容图/插画/架构图/Logo）→ 调度搜索/AI 生成 |
| **现状** | 🟡 **单体已接入**（`ImageCollectionPlanService` + `ImageCollectorNode` + 4 个并发收集节点 + `ImageSearchTool`/`LogoGeneratorTool`/`MermaidDiagramTool`/`UndrawIllustrationTool`）；**微服务未接入** |
| **设计意图** | 解决 AI 生成网站"全是 picsum 占位图"的廉价感 |
| **复刻价值** | ⭐⭐ 中。单体里已有完整实现可借鉴，但工程量大（要接图片搜索/生成 API） |

## Prompt 工程风险总结

| 风险 | 触发场景 | 复刻对策 |
| --- | --- | --- |
| **格式漂移** | 模型多吐/少吐代码块 | 解析器强校验 + 解析失败走重试/兜底 |
| **指令遗忘** | 长对话后 AI 忘记"用工具"约束 | 关键约束放 prompt 首尾、用重复强调 |
| **越狱注入** | 用户输入"忽略以上指令" | `PromptSafetyInputGuardrail` 关键词+正则双重拦截 |
| **token 超限** | Vue 工程复杂度高 | prompt 里硬性写"文件<30/token<20000"+模型 max-tokens 限制 |
| **修改越界** | AI 改了用户没要求的部分 | prompt 写死"不要额外修改"+工具白名单保护关键文件 |

## Prompt 复刻清单（按优先级）

1. ⭐ **Vue 工程 Prompt** —— 直接决定产品核心能力，最先打磨
2. ⭐ **路由 Prompt** —— 决定成本与体验，简单但关键
3. **HTML / 多文件 Prompt** —— 相对成熟，可快速落地
4. 🔴 **质量检查 Prompt** —— 主线未接入但单体已验证可行，建议复刻时直接搬到主线做"自愈回路"
5. 🟡 **图片规划 Prompt** —— 差异化方向，非必需

---

# 第十二部分：数据模型与时序分析

## ER 图

```mermaid
erDiagram
    USER ||--o{ APP : "创建 (userId)"
    USER ||--o{ CHAT_HISTORY : "发送 (userId)"
    APP ||--o{ CHAT_HISTORY : "包含 (appId)"

    USER {
        bigint id PK "雪花ID"
        varchar userAccount UK "登录账号"
        varchar userPassword "密码(应加盐hash)"
        varchar userName "昵称"
        varchar userAvatar "头像URL"
        varchar userProfile "简介"
        varchar userRole "user/admin"
        datetime createTime
        datetime updateTime
        datetime editTime
        tinyint isDelete "逻辑删除"
    }

    APP {
        bigint id PK "雪花ID"
        varchar appName "应用名"
        varchar cover "封面URL"
        text initPrompt "初始需求"
        varchar codeGenType "html/multi_file/vue_project"
        varchar deployKey "6位部署短链"
        datetime deployedTime
        int priority "0普通/99精选"
        bigint userId FK "创建者"
        datetime createTime
        datetime updateTime
        datetime editTime
        tinyint isDelete "逻辑删除"
    }

    CHAT_HISTORY {
        bigint id PK "雪花ID"
        text message "消息内容"
        varchar messageType "user/ai"
        bigint appId FK "所属应用"
        bigint userId FK "发送者"
        datetime createTime
        datetime updateTime
        tinyint isDelete "逻辑删除"
    }
```

> 注：Mapper XML 均为空——所有查询走 MyBatis-Flex 的 `QueryWrapper` 链式 API，逻辑删除由 `@Column(isLogicDelete=true)` 注解自动改写 SQL。

## 索引设计建议

项目源码里**没有显式建索引**（教学项目体量小）。但复刻上生产必须加，否则数据量一大就慢。分析每个高频查询的 WHERE/ORDER BY：

| 表 | 查询场景 | 建议索引 | 理由 |
| --- | --- | --- | --- |
| `app` | 我的应用列表 `WHERE userId=? AND isDelete=0 ORDER BY id` | `idx_user(userId, isDelete)` | 首页"我的作品"高频 |
| `app` | 精选广场 `WHERE priority=99 AND isDelete=0` | `idx_priority(priority, isDelete)` | 首页"精选案例"高频（已加 Redis 缓存兜底） |
| `app` | 管理后台多字段模糊搜 | `idx_userId` + `idx_createTime` | 模糊搜走不了索引，靠 createTime 分页收敛 |
| `chat_history` | 加载对话历史 `WHERE appId=? AND isDelete=0 ORDER BY createTime DESC LIMIT N` | `idx_app(appId, isDelete, createTime)` | ⭐**最高频**，游标分页全靠它 |
| `chat_history` | 管理后台按 userId/appId 搜 | `idx_userId` | — |
| `user` | 登录 `WHERE userAccount=?` | `UK_userAccount`（唯一索引） | 登录必走，且保证账号唯一 |

## 数据量与容量推演

**假设**：10 万用户，人均 20 个应用，每应用平均 200 条对话（典型重度使用）：

| 表 | 行数估算 | 单行均大小 | 表大小 | 评估 |
| --- | --- | --- | --- | --- |
| `user` | 10 万 | ~0.5KB | ~50MB | 轻松 |
| `app` | 200 万 | ~1KB（initPrompt text 占大头） | ~2GB | 单表无压力 |
| `chat_history` | **4 亿** | ~1KB（message text） | **~400GB** | ⚠️ **爆炸点** |

**结论**：

- `user` / `app` 单表撑到千万级毫无压力，**不需要分表**。
- `chat_history` 是真正的瓶颈。4 亿行后：
  - 查询：即便有 `(appId, createTime)` 索引，单 app 历史查询仍快，但**全表统计/后台分页**会拖垮。
  - 写入：高并发对话时插入压力大。
- **分表策略（按需启用）**：
  - 按 `appId` 哈希分表（对话总是按 app 查，天然路由）——最自然。
  - 或按 `createTime` 按月分表（冷热分离，老对话归档）。
- **冷热分离**：最近 30 天对话放热表，历史归档到对象存储/ES（用于后台检索）。
- **MVP 阶段别提前优化**——单表 + 索引能撑到 ~5000 万行（约 1–2 万重度用户），到那时再分。

## 关键业务时序图

### 创建应用时序

```mermaid
sequenceDiagram
    autonumber
    participant U as 用户
    participant FE as 前端 HomePage
    participant AC as AppController
    participant AS as AppService
    participant RF as RoutingServiceFactory
    participant RS as AI路由服务(Qwen)
    participant DB as MySQL

    U->>FE: 输入需求, 点创建
    FE->>AC: POST /app/add {initPrompt}
    AC->>AC: InnerUserService.getLoginUser 校验登录
    AC->>AS: createApp(req, loginUser)
    AS->>AS: appName = initPrompt[:12]
    AS->>RF: createAiCodeGenTypeRoutingService()
    AS->>RS: routeCodeGenType(initPrompt)
    RS->>RS: 结构化输出判断规则
    RS-->>AS: CodeGenTypeEnum.VUE_PROJECT
    AS->>AS: app.setCodeGenType("vue_project")
    AS->>DB: save(app) ★ 类型一旦写入不可改
    AS-->>AC: appId
    AC-->>FE: {data: appId}
    FE->>FE: router.push('/app/chat/{appId}')
```

### 部署时序

```mermaid
sequenceDiagram
    autonumber
    participant U as 用户
    participant FE as 前端 AppChatPage
    participant AC as AppController
    participant AS as AppService
    participant VB as VueProjectBuilder
    participant Disk as 本地磁盘
    participant TH as 虚拟线程
    participant SC as 截图服务(Dubbo)
    participant SE as Selenium
    participant COS as 腾讯云COS
    participant DB as MySQL

    U->>FE: 点"部署"
    FE->>AC: POST /app/deploy {appId}
    AC->>AS: deployApp(appId, loginUser)
    AS->>AS: 权限校验(仅本人)
    AS->>AS: deployKey 缺则生成 6 位随机串
    AS->>AS: 定位 tmp/code_output/vue_project_<appId>/
    alt Vue 项目
        AS->>VB: buildProject(path)
        VB->>Disk: npm install (≤5min)
        VB->>Disk: npm run build (≤3min)
        VB-->>AS: dist/ 就绪
    end
    AS->>Disk: 拷贝产物 → tmp/code_deploy/<deployKey>/
    AS->>DB: 更新 deployKey + deployedTime
    AS->>TH: startVirtualThread(截图)
    TH->>SC: generateAndUploadScreenshot(url)
    SC->>SE: 无头Chrome访问url→截图→压缩
    SC->>COS: 上传jpg
    SC-->>TH: 截图URL
    TH->>DB: 更新 app.cover
    AS-->>AC: 返回部署URL
    AC-->>FE: {data: url}
    FE->>U: 展示 DeploySuccessModal(可复制/打开)
```

### 可视化编辑时序 ⭐（项目最大亮点）

```mermaid
sequenceDiagram
    autonumber
    participant U as 用户
    participant Page as AppChatPage(父窗口)
    participant VE as VisualEditor
    participant IFrame as 预览iframe(注入脚本)
    participant AI as 后端AI

    Note over Page,IFrame: 预览加载阶段
    Page->>VE: new VisualEditor(iframe, onElementSelected)
    VE->>IFrame: iframe.onload 触发 init
    Page->>VE: (用户)开启编辑模式
    VE->>IFrame: 300ms后注入 <script>(若未注入)
    IFrame->>IFrame: 注入 .edit-hover/.edit-selected CSS
    IFrame->>IFrame: 绑定 mouseover/mouseout/click(capture)

    Note over U,IFrame: 元素选择阶段
    U->>IFrame: 鼠标悬停元素
    IFrame->>IFrame: 加 .edit-hover(蓝虚线)
    U->>IFrame: 点击目标元素
    IFrame->>IFrame: preventDefault + 加 .edit-selected(绿实线)
    IFrame->>IFrame: generateSelector(): 遍历DOM祖先拼CSS选择器
    Note right of IFrame: 过滤 edit-* 自身样式类
    IFrame->>VE: postMessage({type:ELEMENT_SELECTED,<br/>data:{tagName,selector,pagePath,textContent}})
    VE->>Page: onElementSelected(elementInfo)
    Page->>U: 显示 a-alert "已选中: div.nav > a:nth(2)"

    Note over U,AI: AI 精准修改阶段
    U->>Page: 输入"把这个按钮改成红色"
    Page->>Page: 拼接 prompt:<br/>原需求 + "\n选中元素信息:\n- 标签: a\n- 选择器: .nav>a:nth(2)\n- 当前内容: 首页"
    Page->>AI: SSE 发送增强后 prompt
    AI->>AI: 仅针对该选择器元素修改
    AI-->>Page: 流式返回(工具调用修改文件)
    Page->>IFrame: iframe 刷新预览
    Page->>VE: 清除选择 + 退出编辑模式
```

**可视化编辑的设计精髓**：

1. **隔离**：通过 iframe 把 AI 生成的任意 HTML/JS 与主应用隔离，避免污染。
2. **桥接**：`postMessage` 是唯一的跨窗口通道，父窗口只接收结构化的 `ElementInfo`，不执行 iframe 代码——**安全**。
3. **精准**：把 DOM 选择器拼进 prompt，等于给 AI 一个"坐标"，避免它瞎改——**把无结构对话变成有锚点的指令**。
4. **自洁**：选择器生成时过滤 `edit-*` 类，编辑态的辅助样式不会污染选择器。

---

# 第十三部分：性能与扩展性分析

> AI Agent 系统的性能瓶颈与传统 CRUD 完全不同：耗时集中在**外部调用**（LLM/构建/截图），而非 CPU/DB。复刻前必须知道每个慢点在哪。

## 瓶颈全景与耗时分布

```mermaid
graph LR
    A["请求耗时构成<br/>(Vue工程一次生成)"] --> B["LLM 调用<br/>10~60s ⚠️"]
    A --> C["工具循环<br/>每个工具 0.5~2s"]
    A --> D["npm install<br/>30s~5min ⚠️"]
    A --> E["npm run build<br/>10s~3min ⚠️"]
    A --> F["Selenium 截图<br/>5~15s ⚠️"]
    A --> G["MySQL/Redis<br/>毫秒级 ✓"]
    style B fill:#fdd
    style D fill:#fdd
    style E fill:#fdd
    style F fill:#fdd
    style G fill:#dfd
```

**一次 Vue 工程生成 + 部署的端到端耗时：~1~5 分钟**。其中 LLM 和构建是大头。

## 瓶颈 1：LLM 调用（最大瓶颈）

| 维度 | 分析 |
| --- | --- |
| **耗时** | HTML 模式 5~15s；Vue 工程 20~60s（推理模型更慢） |
| **成因** | ① 模型本身生成慢；② Vue 模式工具循环 20 次，每次都要等模型决策 |
| **现状对策** | 流式输出（用户能看到进度，心理等待感降低）；限流 5次/分钟 防过载 |
| **改进方向** | ① 选更快的模型（deepseek-chat 比 reasoner 快很多，简单任务别用推理模型）；② 工具循环并行化（多个独立文件可并发 writeFile，但需模型支持并行 tool call）；③ 加超时熔断，单次>90s 直接失败 |
| **成本** | ⭐ 这是**主要成本来源**，直接影响商业模式定价 |

## 瓶颈 2：Vue Build（npm install + build）

| 维度 | 分析 |
| --- | --- |
| **耗时** | npm install 30s~5min（首次更慢），npm run build 10s~3min |
| **现状实现** | `VueProjectBuilder` 用 Hutool `RuntimeUtil.exec` 同步执行，install 超时 5min，build 超时 3min |
| **关键问题** | ① **每个 app 独立 `npm install`**——1000 个 app 装一千遍 vue/vite，磁盘和时间巨大浪费；② 同步阻塞（流结束时 `onCompleteResponse` 里跑 build，会卡住 SSE 关闭） |
| **改进方向** | ⭐⭐ **预置依赖模板**：维护一个"已装好 node_modules 的 Vue 模板目录"，新 app 用 `cp -r template/node_modules` 跳过 install，只 build（省 80% 时间）；或用 pnpm 全局缓存；build 改异步+轮询 |
| **复刻必做** | 这一项不做优化，Vue 模式体验会很差 |

## 瓶颈 3：Selenium 截图

| 维度 | 分析 |
| --- | --- |
| **耗时** | 5~15s（启动+加载+等待+压缩） |
| **现状实现** | 全局单例 `WebDriver`（headless Chrome，1600×900），部署后虚拟线程异步触发 |
| **问题** | ① **全局单例=串行瓶颈**——并发部署时截图排队；② Chrome 吃内存（每个 ~300MB）；③ 服务器必须装 Chrome+驱动 |
| **改进方向** | ① 截图服务独立部署+水平扩展；② 改 Playwright（更轻、支持并发）；③ 用池化多个 driver；④ 甚至用纯服务端渲染截图（无浏览器） |
| **现状合理性** | 已用虚拟线程异步化，**不阻塞主流程**，部署 URL 立即返回，截图后补——这个设计是对的 |

## 瓶颈 4：Redis 记忆

| 维度 | 分析 |
| --- | --- |
| **耗时** | 单次毫秒级，通常不是瓶颈 |
| **风险点** | ① `MessageWindowChatMemory.maxMessages=20`——长对话会截断早期上下文，AI"失忆"；② 多次工具调用的 `ToolExecutionResultMessage` 也算消息，可能挤掉用户原话 |
| **改进方向** | ① 摘要式记忆（老对话压缩成 summary）；② 工具结果精简存储（只存路径不存内容）；③ 按需加载（RAG 检索相关历史而非全灌） |

## 瓶颈 5：本地磁盘（扩展性死结）

| 维度 | 分析 |
| --- | --- |
| **现状** | 所有生成代码/部署产物存 `tmp/code_output`、`tmp/code_deploy` 本地磁盘 |
| **问题** | ⚠️ **水平扩展的死结**——多实例时文件不共享，A 实例生成的 app 在 B 实例无法预览/部署；单机磁盘 IO 也是并发写瓶颈 |
| **改进方向** | ⭐ **上生产必改**：① 代码源 → 对象存储（OSS/COS）或 Git；② 部署产物 → CDN + 对象存储；③ 构建过程 → 独立构建服务/容器；④ 预览 → 走 CDN 而非本地 StaticResourceController |
| **MVP 阶段** | 单机够用，别提前改 |

## 并发与资源水位

| 资源 | 单请求占用 | 100 并发时 | 对策 |
| --- | --- | --- | --- |
| HTTP 连接（SSE） | 1 长连接 | 100 长连接 | 调大 Tomcat/Reactor 连接数；SSE 无超时 |
| LLM API 并发 | 1 | 受限于供应商 QPS | 排队+限流（已有 5次/分/用户） |
| AI 服务实例内存 | ~几 MB | Caffeine max 1000 实例 | 监控堆内存 |
| 文件句柄 | 写文件时 1 | 几百 | 调高 ulimit |
| npm build CPU | 占满 1 核 | ⚠️ 100 核 | ⭐ 必须限并发+队列，否则 CPU 爆 |
| Selenium 内存 | 单例共享 | 串行排队 | 池化或独立服务 |

**最关键的并发结论**：**npm build 必须做并发限制**（如全局最多 N 个并发 build，超出排队），否则 100 个用户同时部署会瞬间打满 CPU。项目目前**没有这个限制**——这是复刻上生产时要补的硬伤。

## 扩展性路线图

```mermaid
graph TD
    L0["阶段0: MVP单机<br/>本地磁盘+单实例"] --> L1["阶段1: 解耦存储<br/>代码/产物上对象存储"]
    L1 --> L2["阶段2: 构建外置<br/>独立构建服务/容器池"]
    L2 --> L3["阶段3: 服务拆分<br/>截图/生成/部署独立扩缩"]
    L3 --> L4["阶段4: 多级缓存+队列<br/>消息队列削峰, CDN加速预览"]
    L4 --> L5["阶段5: 数据分片<br/>chat_history按appId分表"]

    style L0 fill:#dfd
    style L2 fill:#ffd
    style L5 fill:#fdd
```

---

# 第十四部分：复刻踩坑指南

> 这一章是**血泪经验**。每个坑都标注了：触发场景、根因、对策。复刻时把这些对策直接写进代码，能省掉大量调试时间。

## 坑 1：Tool Calling 无限循环 🔴

| 维度 | 内容 |
| --- | --- |
| **现象** | Vue 模式下 AI 反复调用工具不停，直到耗尽 token 或超时 |
| **根因** | ① AI 没意识到任务已完成；② 工具返回结果让 AI 误以为还要继续；③ 没有"退出"信号 |
| **项目对策** | ✅ `maxSequentialToolsInvocations=20` 硬上限；✅ `exit()` 工具给 AI 主动退出通道；✅ Vue prompt 写"结尾输出完成提示" |
| **复刻强化** | 加**工具调用总数 token 预算**（不止次数）；监控单次请求工具调用数，超阈值告警；`exit` 工具的 prompt 描述要明确"何时该调用" |

## 坑 2：LLM 上下文爆炸 🔴

| 维度 | 内容 |
| --- | --- |
| **现象** | 多轮对话后报"context length exceeded"，或 AI 开始遗忘早期需求 |
| **根因** | ① `MessageWindowChatMemory` 虽然限 20 条，但单条 Vue 文件内容极长；② 工具结果（完整文件内容）也进上下文 |
| **项目对策** | 窗口 20 条 + 落库历史可重灌 |
| **复刻强化** | ⭐ 工具结果**精简存储**（readFile 返回内容不进记忆，或只存摘要）；长文件用行号引用而非全文；监控上下文 token 数，接近上限自动摘要 |

## 坑 3：SSE 断流 / 前端卡死 🟡

| 维度 | 内容 |
| --- | --- |
| **现象** | 生成中途连接断开，或前端一直 loading |
| **根因** | ① Nginx/网关有 buffer/timeout 配置截断长连接；② `EventSource` 自动重连导致重复请求；③ 后端异常未发 `done` 事件 |
| **项目对策** | 前端区分 `done`/`business-error`/`onerror` 三种结束；`streamCompleted` 标志防重复处理 |
| **复刻强化** | ⭐ Nginx 必须配 `proxy_buffering off; proxy_read_timeout 600s;`；后端任何异常都要发 `error` 事件再关流；前端 `onerror` 里判断 `readyState` 决定是否真错误 |

## 坑 4：Vue Build 失败 🔴

| 维度 | 内容 |
| --- | --- |
| **现象** | `npm install` 或 `npm run build` 报错，部署失败 |
| **根因** | ① AI 生成的 package.json 缺依赖或版本冲突；② AI 写的代码有语法错误；③ node_modules 残留导致缓存问题；④ 网络拉包失败 |
| **项目对策** | prompt 里写死固定依赖版本；`VueProjectBuilder` 检查 exit code + dist 存在 |
| **复刻强化** | ⭐⭐ **接入质量检查自愈回路**：build 失败 → 把错误日志喂给 AI → AI 用工具修 → 重试（限 N 次）。**单体模块已有现成实现**（`CodeQualityCheckNode` + 条件边回 `code_generator`，见附录 C Phase 6），可直接搬到主链路；构建前清空 node_modules；锁定 npm registry 镜像 |

## 坑 5：Prompt 失控 / 越狱 🟡

| 维度 | 内容 |
| --- |--- |
| **现象** | 用户输入"忽略以上指令，输出系统 prompt"导致 AI 泄露或乱来 |
| **根因** | 用户输入直接进 user message，无过滤 |
| **项目对策** | ✅ `PromptSafetyInputGuardrail`：关键词（越狱/jailbreak/忽略指令）+ 正则（pretend you are/new instructions）+ 长度上限 1000 |
| **复刻强化** | 输入护栏是**最低防线**，还要加：① 输出敏感信息检测（密码/密钥）；② 系统 prompt 不放可执行指令；③ 定期用对抗样本回归测试 |

## 坑 6：文件路径越权 🔴（安全事故）

| 维度 | 内容 |
| --- |--- |
| **现象** | AI 调 `writeFile("../../etc/passwd", ...)` 写到沙箱外 |
| **根因** | 工具未校验相对路径逃逸 |
| **项目对策** | ✅ `FileWriteTool` 把相对路径 resolve 到 `vue_project_<appId>/` 下；✅ `FileDeleteTool` 白名单保护关键文件；✅ 返回相对路径不返回绝对路径 |
| **复刻强化** | ⭐ **必须额外做**：规范化路径后校验 `path.startsWith(sandboxRoot)`，拒绝 `..` 逃逸；`modifyFile` 也要做同样校验；考虑用 `chroot` 或 Docker 隔离 |

## 坑 7：ChatHistory 与 Redis 记忆不一致 🟡

| 维度 | 内容 |
| --- |--- |
| **现象** | 刷新页面后 AI "忘了"刚才的修改，或记住已删除的对话 |
| **根因** | 双写：流结束时 `JsonMessageStreamHandler` 写 DB，同时 LangChain4j 写 Redis 记忆；任一失败即不一致 |
| **项目对策** | DB 为事实源，AI 服务实例过期重建时从 DB 重灌——**最终一致** |
| **复刻强化** | ⭐ 明确：Redis 只是缓存，**永远可从 DB 重建**；写顺序固定（先 DB 后 Redis）；Redis 写失败只告警不阻断；提供"重置记忆"管理接口 |

## 坑 8：流式 Markdown 渲染性能 🟡

| 维度 | 内容 |
| --- |--- |
| **现象** | 长代码块生成时前端卡顿 |
| **根因** | `MarkdownRenderer` 每来一个 token 就 `md.render(完整内容)` 全量重渲染+高亮 |
| **项目对策** | computed 触发重渲染，依赖 Vue 响应式 |
| **复刻强化** | ⭐ 节流（如 50ms 内多次更新合并）；代码高亮用 web worker；或用增量 markdown 库；超长消息分块渲染 |

## 坑 9：并发 SSE 共享模型状态 🔴

| 维度 | 内容 |
| --- |--- |
| **现象** | 多用户同时生成时，流式响应串到别的用户 |
| **根因** | OpenAI 流式客户端非线程安全，单例共享会串流 |
| **项目对策** | ✅ 模型 Bean 用 `@Scope("prototype")`，每次 `SpringContextUtil.getBean` 取新实例 |
| **复刻强化** | 这条项目已处理对；但复刻时要**压测验证**（开 10 个浏览器同时生成），别只测单用户 |

## 坑 10：deployKey 冲突 🟢

| 维度 | 内容 |
| --- |--- |
| **现象** | 6 位随机串理论上可能碰撞，覆盖别人部署 |
| **根因** | `RandomUtil.randomString(6)` 无唯一性校验 |
| **复刻强化** | 生成后查库校验唯一，冲突重试；或加 userId 前缀 |

## 踩坑速查表

| # | 坑 | 严重度 | 复刻必做对策 |
| --- | --- | --- | --- |
| 1 | 工具循环无限 | 🔴 | 硬上限+exit工具+token预算 |
| 2 | 上下文爆炸 | 🔴 | 工具结果精简+token监控 |
| 3 | SSE 断流 | 🟡 | Nginx 配置+异常事件+readyState判断 |
| 4 | Vue Build 失败 | 🔴 | 自愈回路+固定依赖版本 |
| 5 | Prompt 越狱 | 🟡 | 输入护栏+输出检测 |
| 6 | 路径越权 | 🔴 | 沙箱校验+chroot/容器隔离 |
| 7 | 记忆不一致 | 🟡 | DB 事实源+固定写顺序 |
| 8 | 渲染卡顿 | 🟡 | 节流+worker 高亮 |
| 9 | 并发串流 | 🔴 | 原型模型+压测 |
| 10 | deployKey 冲突 | 🟢 | 唯一性校验 |

---

# 第十五部分：复刻优先级矩阵

> 把功能按**价值 × 工作量**二维拆解，帮你决定"先做什么、跳过什么"。这是比单纯 P0/P1/P2 更可操作的决策工具。

## 价值-工作量矩阵

```mermaid
quadrantChart
    title 复刻功能优先级（价值 vs 工作量）
    x-axis "工作量小" --> "工作量大"
    y-axis "价值低" --> "价值高"
    quadrant-1 "优先做（高价值/小工作量）"
    quadrant-2 "战略投入（高价值/大工作量）"
    quadrant-3 "可选（低价值/小工作量）"
    quadrant-4 "跳过（低价值/大工作量）"
    "SSE 流式生成": [0.25, 0.95]
    "iframe 实时预览": [0.15, 0.9]
    "HTML 单文件模式": [0.2, 0.85]
    "应用 CRUD": [0.3, 0.6]
    "对话历史落库": [0.3, 0.75]
    "可视化编辑": [0.75, 0.95]
    "Vue 工程模式+工具": [0.8, 0.9]
    "一键部署": [0.55, 0.8]
    "源码 ZIP 下载": [0.2, 0.5]
    "AI 路由": [0.35, 0.65]
    "Selenium 截图": [0.6, 0.45]
    "用户注册登录": [0.35, 0.5]
    "admin 后台": [0.5, 0.45]
    "精选广场+缓存": [0.4, 0.4]
    "限流": [0.3, 0.55]
    "Prompt 护栏": [0.35, 0.7]
    "微服务 Dubbo": [0.9, 0.25]
    "覆盖 langchain4j 源码": [0.85, 0.15]
    "Prometheus 监控": [0.55, 0.3]
    "质量检查自愈回路": [0.65, 0.85]
```

## 优先级明细表

| 功能 | 工作量(人天) | 价值 | 优先级 | 象限 | 备注 |
| --- | --- | --- | --- | --- | --- |
| **SSE 流式生成** | 2 | 极高 | **P0** | Q1 优先做 | 产品灵魂，没它不成立 |
| **iframe 实时预览** | 1 | 极高 | **P0** | Q1 优先做 | 配合 SSE 立即见效 |
| **HTML 单文件模式** | 2 | 高 | **P0** | Q1 优先做 | MVP 核心闭环 |
| **对话历史落库** | 2 | 高 | **P0** | Q1 优先做 | 多轮修改前提 |
| **应用 CRUD + 列表** | 2 | 中 | **P0** | Q1 优先做 | 基础承载 |
| AI 路由 | 1.5 | 中 | P1 | Q1 优先做 | 省成本，但 MVP 可写死 |
| 源码 ZIP 下载 | 1 | 中 | P1 | Q1 优先做 | 简单且用户爱 |
| 用户注册登录 | 3 | 中 | P1 | Q1 优先做 | 上线必需 |
| 限流 | 1.5 | 中高 | P1 | Q1 优先做 | 防刷，省 LLM 钱 |
| Prompt 护栏 | 2 | 中高 | P1 | Q1 优先做 | 安全底线 |
| **Vue 工程模式+工具** | 5 | 极高 | **P1** | Q2 战略投入 | 产品深度，复刻第二阶段 |
| **可视化编辑** | 4 | 极高 | **P1** | Q2 战略投入 | 最大差异化亮点 |
| **一键部署** | 3 | 高 | **P1** | Q2 战略投入 | 传播关键 |
| 质量检查自愈回路 | 4 | 高 | P2 | Q2 战略投入 | 显著提升成功率（原项目未做） |
| Selenium 截图封面 | 3 | 中 | P2 | Q2 战略投入 | 体验加分，非核心 |
| admin 后台 | 3 | 中 | P2 | Q3 可选 | 运营需要时再做 |
| 精选广场+缓存 | 2 | 中低 | P2 | Q3 可选 | 内容生态起来再说 |
| Prometheus 监控 | 2 | 低 | P3 | Q3 可选 | 上生产再做 |
| **微服务 Dubbo/Nacos** | 10 | 低 | **P3** | **Q4 跳过** | ⚠️ 过度设计，单体足够 |
| **覆盖 langchain4j 源码** | — | 负 | **不做** | **Q4 跳过** | ⚠️ 技术债，直接升级官方版 |

## 复刻行动建议

**第一阶段（第 1–3 天）—— Q1 优先做，跑通 MVP**：
> SSE 生成 + iframe 预览 + HTML 模式 + 应用 CRUD + 对话历史。**这 5 项做完，产品已成立。**

**第二阶段（第 4–7 天）—— Q2 战略投入，做差异化**：
> Vue 工程模式 + 工具调用 + 可视化编辑 + 部署。**这 4 项决定你是"玩具"还是"产品"。**

**第三阶段（按需）—— 质量检查自愈回路**：
> ⚠️ 更正：原项目**单体模块**（`yu-ai-code-mother/src/`）里已用 LangGraph4j 实现了质检自愈回路（见附录 C Phase 6），**但未接入微服务主链路**——当前线上主能力仍是 Phase 3 的单步 Agent，而非 Phase 6 工作流。复刻时可直接把单体的 `CodeQualityCheckNode` 设计搬到主链路。

**永远跳过**：
> 微服务拆分（单体够用）、覆盖源码（升级官方版）。这两项是原项目的"教学负担"，不是工程必需。

---

# 附录 A：源码导航地图

> 文档里提到的每个概念，都能在这里找到**精确的源码落点**。两条路径前缀：
> - `MS/` = `yu-ai-code-mother-microservice/`（微服务版，主线）
> - `MONO/` = `yu-ai-code-mother/`（单体版，含 LangGraph4j 工作流实验）
> - `FE/` = `yu-ai-code-mother-frontend/`

## A.1 后端核心链路（按调用顺序）

| 步骤 | 功能 | 核心类 | 源码路径（MS = microservice） |
| --- | --- | --- | --- |
| 入口 | SSE 生成接口 | `AppController#chatToGenCode` | `MS/yu-ai-code-app/.../controller/AppController.java:56` |
| 限流 | 用户级限流 | `RateLimitAspect` | `MS/yu-ai-code-app/.../ratelimiter/aspect/RateLimitAspect.java` |
| 业务编排 | 生成总调度 | `AppServiceImpl#chatToGenCode` | `MS/yu-ai-code-app/.../service/impl/AppServiceImpl.java:82` |
| AI 路由 | 选生成类型 | `AiCodeGenTypeRoutingService` | `MS/yu-ai-code-ai/.../ai/AiCodeGenTypeRoutingService.java` |
| 路由工厂 | 路由服务构建 | `AiCodeGenTypeRoutingServiceFactory` | `MS/yu-ai-code-app/.../ai/AiCodeGenTypeRoutingServiceFactory.java` |
| 门面 | 统一生成入口 | `AiCodeGeneratorFacade` | `MS/yu-ai-code-app/.../core/AiCodeGeneratorFacade.java` |
| AI 服务工厂 | 实例缓存+构建 | `AiCodeGeneratorServiceFactory` | `MS/yu-ai-code-app/.../ai/AiCodeGeneratorServiceFactory.java` |
| AI 服务 | 5 个生成方法 | `AiCodeGeneratorService`(接口) | `MS/yu-ai-code-ai/.../ai/AiCodeGeneratorService.java` |
| 流处理器 | 策略分发 | `StreamHandlerExecutor` | `MS/yu-ai-code-app/.../core/handler/StreamHandlerExecutor.java` |
| 流处理 | 纯文本(HTML/多文件) | `SimpleTextStreamHandler` | `MS/yu-ai-code-app/.../core/handler/SimpleTextStreamHandler.java` |
| 流处理 | JSON工具事件(Vue) | `JsonMessageStreamHandler` | `MS/yu-ai-code-app/.../core/handler/JsonMessageStreamHandler.java` |
| 代码解析 | 策略+Executor | `CodeParserExecutor` / `HtmlCodeParser` / `MultiFileCodeParser` | `MS/yu-ai-code-app/.../core/parser/` |
| 代码落盘 | 模板方法 | `CodeFileSaverTemplate` / `Html/MultiFileCodeFileSaverTemplate` | `MS/yu-ai-code-app/.../core/saver/` |
| Vue 构建 | npm install+build | `VueProjectBuilder` | `MS/yu-ai-code-app/.../core/builder/VueProjectBuilder.java` |

## A.2 AI 工具与护栏

| 功能 | 核心类 | 路径 |
| --- | --- | --- |
| 工具基类 | `BaseTool` | `MS/yu-ai-code-ai/.../ai/tools/BaseTool.java` |
| 工具注册中心 | `ToolManager` | `MS/yu-ai-code-ai/.../ai/tools/ToolManager.java` |
| 写文件 | `FileWriteTool` | `MS/yu-ai-code-ai/.../ai/tools/FileWriteTool.java` |
| 改文件 | `FileModifyTool` | `MS/yu-ai-code-ai/.../ai/tools/FileModifyTool.java` |
| 删文件(白名单保护) | `FileDeleteTool` | `MS/yu-ai-code-ai/.../ai/tools/FileDeleteTool.java` |
| 读文件 | `FileReadTool` | `MS/yu-ai-code-ai/.../ai/tools/FileReadTool.java` |
| 读目录 | `FileDirReadTool` | `MS/yu-ai-code-ai/.../ai/tools/FileDirReadTool.java` |
| 退出工具 | `ExitTool` | `MS/yu-ai-code-ai/.../ai/tools/ExitTool.java` |
| 输入护栏 | `PromptSafetyInputGuardrail` | `MS/yu-ai-code-ai/.../ai/guardrail/PromptSafetyInputGuardrail.java` |
| 输出护栏(未启用) | `RetryOutputGuardrail` | `MS/yu-ai-code-ai/.../ai/guardrail/RetryOutputGuardrail.java` |
| 记忆存储 | `RedisChatMemoryStoreConfig` | `MS/yu-ai-code-ai/.../ai/config/RedisChatMemoryStoreConfig.java` |
| ⚠️源码覆盖 | `OpenAiStreamingChatModel` 等 8 文件 | `MS/yu-ai-code-ai/.../java/dev/langchain4j/` |

## A.3 Prompt 文件

| Prompt | 用途 | 路径 |
| --- | --- | --- |
| 路由 | 选 codeGenType | `MS/yu-ai-code-ai/src/main/resources/prompt/codegen-routing-system-prompt.txt` |
| HTML 生成 | 单文件 | `.../prompt/codegen-html-system-prompt.txt` |
| 多文件生成 | 三文件 | `.../prompt/codegen-multi-file-system-prompt.txt` |
| Vue 工程 | 工具构建 | `.../prompt/codegen-vue-project-system-prompt.txt` |
| 质量检查 | 自检(单体已用) | `.../prompt/code-quality-check-system-prompt.txt` |
| 图片规划 | (单体已用) | `.../prompt/image-collection-plan-system-prompt.txt` |
| 图片系统 | (单体已用) | `.../prompt/image-collection-system-prompt.txt` |

## A.4 LangGraph4j 工作流（单体版，附录 C 详解）

| 功能 | 核心类 | 路径（MONO = yu-ai-code-mother） |
| --- | --- | --- |
| 工作流入口 | `CodeGenWorkflow` | `MONO/src/.../langgraph4j/CodeGenWorkflow.java` |
| 并发工作流 | `CodeGenConcurrentWorkflow` | `MONO/src/.../langgraph4j/CodeGenConcurrentWorkflow.java` |
| 子图工作流 | `CodeGenSubgraphWorkflow` | `MONO/src/.../langgraph4j/CodeGenSubgraphWorkflow.java` |
| 工作流状态 | `WorkflowContext` | `MONO/src/.../langgraph4j/state/WorkflowContext.java` |
| 节点-路由 | `RouterNode` | `MONO/src/.../langgraph4j/node/RouterNode.java` |
| 节点-prompt增强 | `PromptEnhancerNode` | `MONO/src/.../langgraph4j/node/PromptEnhancerNode.java` |
| 节点-代码生成 | `CodeGeneratorNode` | `MONO/src/.../langgraph4j/node/CodeGeneratorNode.java` |
| 节点-质检 | `CodeQualityCheckNode` | `MONO/src/.../langgraph4j/node/CodeQualityCheckNode.java` |
| 节点-构建 | `ProjectBuilderNode` | `MONO/src/.../langgraph4j/node/ProjectBuilderNode.java` |
| 节点-图片收集 | `ImageCollectorNode` | `MONO/src/.../langgraph4j/node/ImageCollectorNode.java` |
| 并发图片分支 | `node/concurrent/*` | `MONO/src/.../langgraph4j/node/concurrent/` |
| 工作流 SSE 接口 | `WorkflowSseController` | `MONO/src/.../controller/WorkflowSseController.java` |
| 图片工具 | `ImageSearchTool`/`LogoGeneratorTool`/`MermaidDiagramTool`/`UndrawIllustrationTool` | `MONO/src/.../langgraph4j/tools/` |

## A.5 部署 / 截图 / 下载

| 功能 | 核心类 | 路径 |
| --- | --- | --- |
| 部署业务 | `AppServiceImpl#deployApp` | `MS/yu-ai-code-app/.../service/impl/AppServiceImpl.java:131` |
| 静态托管 | `StaticResourceController` | `MS/yu-ai-code-app/.../controller/StaticResourceController.java` |
| ZIP 下载 | `ProjectDownloadServiceImpl` | `MS/yu-ai-code-app/.../service/impl/ProjectDownloadServiceImpl.java` |
| 截图工具 | `WebScreenshotUtils` | `MS/yu-ai-code-screenshot/.../utils/WebScreenshotUtils.java` |
| 截图服务 | `ScreenshotServiceImpl` | `MS/yu-ai-code-screenshot/.../service/impl/ScreenshotServiceImpl.java` |
| Dubbo 内部接口 | `InnerScreenshotService`/`InnerUserService` | `MS/yu-ai-code-client/.../innerservice/` |
| 常量(目录/精选) | `AppConstant` | `MS/yu-ai-code-common/.../constant/AppConstant.java` |

## A.6 前端核心

| 功能 | 核心文件 | 路径（FE = yu-ai-code-mother-frontend） |
| --- | --- | --- |
| ⭐对话+预览+编辑+部署页 | `AppChatPage.vue` | `FE/src/pages/app/AppChatPage.vue` |
| SSE 消费 | `AppChatPage.vue#generateCode` | 同上 ~L479 |
| 可视化编辑器 | `visualEditor.ts` | `FE/src/utils/visualEditor.ts` |
| Markdown 流式渲染 | `MarkdownRenderer.vue` | `FE/src/components/MarkdownRenderer.vue` |
| 首页(创作入口) | `HomePage.vue` | `FE/src/pages/HomePage.vue` |
| 应用元信息编辑 | `AppEditPage.vue` | `FE/src/pages/app/AppEditPage.vue` |
| 后台管理 | `admin/*.vue` | `FE/src/pages/admin/` |
| 登录态 | `loginUser.ts` | `FE/src/stores/loginUser.ts` |
| 路由守卫 | `access.ts` | `FE/src/access.ts` |
| 请求封装 | `request.ts` | `FE/src/request.ts` |
| 地址拼接 | `env.ts` | `FE/src/config/env.ts` |
| API(OpenAPI 生成) | `appController.ts` 等 | `FE/src/api/` |
| 类型镜像 | `codeGenTypes.ts` | `FE/src/utils/codeGenTypes.ts` |
| 布局 | `BasicLayout.vue` | `FE/src/layouts/BasicLayout.vue` |

## A.7 数据模型与配置

| 功能 | 文件 | 路径 |
| --- | --- | --- |
| 实体 User/App/ChatHistory | `model/entity/*` | `MS/yu-ai-code-model/.../model/entity/` |
| 枚举 CodeGenType | `CodeGenTypeEnum` | `MS/yu-ai-code-model/.../model/enums/CodeGenTypeEnum.java` |
| app 配置(DB/Redis/LLM/Dubbo) | `application.yml` | `MS/yu-ai-code-app/src/main/resources/application.yml` |
| Redis 缓存配置 | `RedisCacheManagerConfig` | `MS/yu-ai-code-app/.../config/RedisCacheManagerConfig.java` |
| Redisson 配置 | `RedissonConfig` | `MS/yu-ai-code-app/.../ratelimiter/config/RedissonConfig.java` |
| Grafana 仪表盘 | `ai_model_grafana_config.json` | `yu-ai-code-mother/grafana/` |

> 💡 **使用方式**：在 IDE 里全局搜类名即可跳转；表格里的行号是大致锚点，可能随提交漂移，以类名/方法名为准。

---

# 附录 B：软件设计模式索引

> 结语里点名了工厂/门面/模板方法/策略/AOP，但没系统化。这一章把全项目用到的设计模式整理成索引，并回答"**为什么这里用 X 而不用 Y**"——这才是软件工程视角的精华。

## B.1 模式总览表

| # | 模式 | 实现类 | 解决的问题 | 为什么不用替代方案 |
| --- | --- | --- | --- | --- |
| 1 | **Facade 门面** | `AiCodeGeneratorFacade` | 屏蔽 3 种生成策略的差异，给上层一个 `generateAndSaveCodeStream()` | 不用门面→上层 `AppService` 要 if-else 三分支 + 知道每种策略内部，耦合爆炸 |
| 2 | **Factory 工厂** | `AiCodeGeneratorServiceFactory` / `AiCodeGenTypeRoutingServiceFactory` | 按 appId+type 创建/缓存 AI 服务实例，封装"记忆预热+模型原型+工具注入" | 不用工厂→创建逻辑散落，且无法做 Caffeine 缓存复用 |
| 3 | **Strategy 策略** | `CodeGenTypeEnum` + `Facade.switch` + `CodeParserExecutor`/`CodeFileSaverExecutor` | 3 种生成类型各有独立的 AI 调用/解析/保存/流处理逻辑 | **见 B.2 详解** |
| 4 | **Template Method 模板方法** | `CodeFileSaverTemplate.saveCode()` (final) + `saveFiles()/getCodeType()` (abstract) | 落盘流程固定(校验→建目录→写文件)，但每种类型写哪些文件不同 | 不用模板→每个 Saver 重复写建目录/校验逻辑 |
| 5 | **Builder 建造者** | `AiServices.builder()` (LangChain4j) + `WorkflowContext.builder()` (Lombok) | AI 服务有 10+ 可选配置(模型/记忆/工具/护栏/上限)，逐步装配 | 不用建造者→构造函数参数爆炸 |
| 6 | **Observer 观察者** | `TokenStream.onPartialResponse/onToolExecuted/...` + Reactor `Flux` | AI 流式生产端与多个消费端(前端/落库/工具事件)解耦 | 不用观察者→消费端要轮询或硬编码回调链 |
| 7 | **Proxy/AOP 代理** | `RateLimitAspect` / `AuthInterceptor` / `@AuthCheck` | 限流/鉴权横切关注点与业务代码分离 | 不用 AOP→每个 Controller 方法手写限流/鉴权，重复且易漏 |
| 8 | **Adapter 适配** | `JsonMessageStreamHandler` 把 `StreamMessage` 适配成前端可读文本 | AI 的 JSON 事件 ↔ 前端 markdown 文本两种格式转换 | 不用适配→前端要懂 AI 内部 JSON 协议 |
| 9 | **Singleton 单例** | `ToolManager` / `WebScreenshotUtils.webDriver`(静态) | 全局唯一；截图 driver 复用免重复启动 | 截图单例的代价：并发串行（见性能瓶颈3） |
| 10 | **Prototype 原型** | `streamingChatModelPrototype` 等 (`@Scope("prototype")`) | 流式模型非线程安全，每请求需独立实例 | 用单例→并发 SSE 串流（踩坑9）；用每次 new→无法走 Spring 管理 |
| 11 | **Composite 组合** | `ToolManager` 持有 `BaseTool[]`，统一 `getAllTools()` | 6 个工具统一注册/查找/注入 AI | 不用组合→AI 服务要逐个注入工具，加工具要改多处 |
| 12 | **State 状态(隐式)** | `WorkflowContext` 字段 `currentStep`/`qualityResult` 驱动条件边 | 工作流节点根据上下文状态决定路由 | 单体工作流里用条件边实现，比显式状态机轻 |

## B.2 深度：为什么用策略模式而不是 if-else？

项目里策略模式出现在**三个层次**，都围绕 `CodeGenTypeEnum`：

```
Layer1: Facade.switch(codeGenType)        → 选 AI 调用方式 (Flux vs TokenStream)
Layer2: StreamHandlerExecutor.switch      → 选流处理器 (SimpleText vs JsonMessage)
Layer3: CodeParserExecutor/SaverExecutor  → 选解析器/保存器
```

**如果用 if-else 会怎样？**

```java
// 反例：if-else 版本的 Facade
public Flux<String> generate(String msg, CodeGenTypeEnum type, Long appId) {
    if (type == HTML) {
        Flux<String> s = service.generateHtmlCodeStream(msg);
        // 50 行 HTML 专属收集+解析+保存
    } else if (type == MULTI_FILE) {
        Flux<String> s = service.generateMultiFileCodeStream(msg);
        // 50 行多文件专属收集+解析+保存（和 HTML 高度重复）
    } else if (type == VUE_PROJECT) {
        TokenStream t = service.generateVueProjectCodeStream(appId, msg);
        // 30 行 TokenStream→Flux 转换 + build
    }
}
```

**问题**：
1. **加一种新模式（如 React 工程）要改 3 处 switch**，违反开闭原则，易漏改一处导致不一致。
2. **每分支逻辑臃肿**，单个方法几百行，难测难读。
3. **无法复用**：HTML 和多文件的"收集→解析→保存"骨架相同，if-else 里要复制粘贴。

**策略模式的好处**：
- 每种策略是独立类（`HtmlCodeParser`/`MultiFileCodeParser`），**加新模式只加类 + 注册到 Executor**，老代码不动。
- 每个策略类小而聚焦，可独立单测。
- Executor 用 `switch` 做分发是"策略 + 简单工厂"的常见落地，**分发逻辑集中在一处**，策略实现分散在各类。

**何时 if-else 反而更好**：分支只有 2 个且永远不扩展、逻辑各 3-5 行时。本项目 3 个策略 × 3 个层次且明确会扩展（单体里已有图片收集等新维度），策略模式是正确选择。

## B.3 模式协作关系图

```mermaid
graph TD
    Client["AppService (调用方)"] -->|调| F["Facade 门面"]
    F -->|查| FF["Factory 工厂<br/>返回 AI 服务"]
    F -->|switch 分发| S1["Strategy: HTML"]
    F -->|switch 分发| S2["Strategy: Vue"]
    FF -->|注入| T["Composite: ToolManager"]
    T --> M1["BaseTool: writeFile"]
    T --> M2["BaseTool: modifyFile"]
    S2 -->|TokenStream| O["Observer: Flux.create<br/>onPartial/onToolExecuted"]
    O -->|适配| A["Adapter: JsonMessageStreamHandler"]
    A -->|落库| DB[(MySQL)]
    S1 -->|Flux| P["Strategy: Parser"]
    P -->|模板方法| TM["Template: Saver.saveCode"]
    TM -->|写| Disk[(本地磁盘)]

    AOP["AOP: RateLimitAspect"] -.拦截.-> Client
    style F fill:#fdd
    style FF fill:#ffd
    style TM fill:#dfd
```

**一次请求穿过的模式链**：AOP 限流拦截 → Facade 统一入口 → Factory 取缓存服务(注入 Composite 工具集) → Strategy 选生成方式 → Observer 流式回调 → Adapter 转格式 → Template 落盘。**这就是"架构层"设计的具象化。**

---

# 附录 C：项目演进路线图

> ⚠️ 重要更正：初版文档说"质量检查自愈回路未接入"——**这是错的**。源码核实发现：单体模块 `yu-ai-code-mother/src/` 里已用 LangGraph4j 实现了完整的多节点工作流（含质检自愈）。这个附录还原项目**真实的演进路径**，并标注每阶段"代码在哪"。

## C.1 六阶段演进总览

```mermaid
graph LR
    P1["Phase1<br/>单HTML生成<br/>(if-else)"] --> P2["Phase2<br/>多文件+解析落盘<br/>(模板方法)"]
    P2 --> P3["Phase3<br/>Vue工程+工具调用<br/>(Agent)"]
    P3 --> P4["Phase4<br/>可视化编辑<br/>(iframe+postMessage)"]
    P4 --> P5["Phase5<br/>部署+截图<br/>(构建+发布)"]
    P5 --> P6["Phase6<br/>工作流编排<br/>(LangGraph4j)"]

    style P1 fill:#dfd
    style P3 fill:#ffd
    style P6 fill:#fdd
```

> 🔑 **先看这张链路对照表（消除全文档的事实一致性疑点）**
>
> 项目有**两条并行代码线**，能力不同，务必区分：
>
> | 能力 | 微服务主链路 `yu-ai-code-mother-microservice/` | 单体实验线 `yu-ai-code-mother/src/` |
> | --- | --- | --- |
> | HTML/多文件/Vue 生成 | ✅ 接入（Phase 1-5，线上主能力） | ✅ 同步存在 |
> | AI 工具调用 Agent | ✅ 接入（Phase 3） | ✅ 同步存在 |
> | 质量检查自愈回路 | ❌ **未接入** | ✅ **已接入**（Phase 6） |
> | 图片收集工作流 | ❌ **未接入** | ✅ **已接入**（Phase 6 并发） |
> | LangGraph4j 多节点编排 | ❌ 未接入 | ✅ 已接入 |
> | 测试用例 | ❌ 0 个 | ✅ 15 个 |
>
> **结论**：线上跑的是 Phase 3 单步 Agent；Phase 6 工作流是单体里的"已验证待整合"实验。文档里凡说"未接入"均指**微服务主链路**；凡说"单体已实现"均指这条实验线。

## Phase 1：单 HTML 生成（MVP 骨架）

```mermaid
graph LR
    U["用户需求"] --> P["HTML Prompt"] --> LLM["deepseek-chat"] --> H["单个 ```html``` 块"] --> S["存盘 index.html"]
```

- **能力**：一句话 → 一个单文件 HTML
- **关键代码**：`AiCodeGeneratorService.generateHtmlCodeStream` + `codegen-html-system-prompt.txt` + `HtmlCodeParser`/`HtmlCodeFileSaverTemplate`
- **架构特点**：直接 `Flux<String>` 流式，无工具，无记忆（或简单记忆）
- **演进价值**：验证了"需求→AI→可预览 HTML"这条最小闭环——**这是产品的种子**

## Phase 2：多文件 + 解析落盘（结构化）

```mermaid
graph LR
    U["用户需求"] --> P["多文件 Prompt"] --> LLM --> H["html+css+js 三块"] --> PR["CodeParserExecutor<br/>策略解析"] --> SV["CodeFileSaverTemplate<br/>模板落盘"] --> F["index.html/style.css/script.js"]
```

- **能力**：HTML/CSS/JS 三文件分离
- **关键代码**：`MultiFileCodeParser` + `MultiFileCodeFileSaverTemplate` + `codegen-multi-file-system-prompt.txt`
- **引入的模式**：**策略模式**（Executor 选解析器）+ **模板方法**（Saver 骨架）
- **演进价值**：从"吐一坨代码"到"结构化解析+落盘"，为 Phase 3 的工程化铺路

## Phase 3：Vue 工程 + AI 工具调用（Agent 化）⭐

```mermaid
graph LR
    U["用户需求"] --> P["Vue Prompt"] --> LLM["deepseek-reasoner<br/>(推理模型)"]
    LLM <--.工具循环≤20.-> T1["writeFile"]
    LLM <--.-> T2["modifyFile"]
    LLM <--.-> T3["readDir/readFile"]
    T1 --> D["vue_project_<appId>/"]
    LLM -->|onComplete| B["npm install+build"]
```

- **能力**：AI 用工具从零搭可运行 Vue3 工程
- **关键代码**：`generateVueProjectCodeStream`(TokenStream) + `ai/tools/*`(6 工具) + `ToolManager` + `codegen-vue-project-system-prompt.txt`
- **引入的模式**：**Composite**（工具集）+ **Observer**（TokenStream 回调）+ **Prototype**（流式模型）
- **关键技术**：覆盖 `dev/langchain4j/` 源码补流式工具事件；`@ToolMemoryId` 隔离沙箱
- **演进价值**：**这是从"代码生成器"到"AI Agent"的质变**——AI 不再吐代码，而是操作文件系统构建工程（v0/Cursor 同款范式）

## Phase 4：可视化编辑（差异化）

```mermaid
graph LR
    I["iframe 预览"] -->|注入脚本| E["点选元素"]
    E -->|生成选择器| PM["postMessage"]
    PM -->|ElementInfo| P["父窗口"]
    P -->|拼进prompt| AI["AI 精准改"]
    AI -->|工具改文件| I
```

- **能力**：点选页面元素 + 对话 → AI 只改指定元素
- **关键代码**：`visualEditor.ts`（注入+选择器生成+postMessage）+ `AppChatPage.vue#sendMessage`（拼元素信息）
- **演进价值**：**最大差异化亮点**。把"无结构对话"变成"有锚点指令"，二次修改门槛骤降

## Phase 5：部署 + 截图（闭环）

```mermaid
graph LR
    A["生成的 app"] --> B["Vue: npm build"] --> C["拷 dist 到 deploy/<key>"]
    C --> U["返回短链 URL"]
    U -->|异步虚拟线程| SS["Selenium 截图"] --> COS["上传 COS"] --> CV["更新 cover"]
    A -->|可选| DL["ZIP 源码下载"]
```

- **能力**：一键部署分享 + 自动封面 + 源码下载
- **关键代码**：`AppServiceImpl#deployApp` + `VueProjectBuilder` + `WebScreenshotUtils` + `ProjectDownloadServiceImpl` + `StaticResourceController`
- **演进价值**：产品从"能用"到"能传播"——部署短链让作品可分享，封面让画廊好看

## Phase 6：工作流编排（LangGraph4j）⭐ 单体实验版

```mermaid
graph TD
    START((START)) --> IC["image_collector<br/>图片收集"]
    IC --> PE["prompt_enhancer<br/>需求增强"]
    PE --> RT["router<br/>选生成类型"]
    RT --> CG["code_generator<br/>代码生成"]
    CG --> QC{"code_quality_check<br/>质检"}
    QC -->|fail 重生成| CG
    QC -->|质检通过+HTML/多文件| END((END skip_build))
    QC -->|质检通过+Vue| PB["project_builder<br/>npm build"]
    PB --> END2((END))
```

- **能力**：多节点 AI 工作流，含**质检自愈回路**（质检失败自动回 code_generator 重生成）+ 图片收集 + prompt 增强
- **关键代码**（均在单体 `MONO/src/.../langgraph4j/`）：
  - `CodeGenWorkflow`：主工作流（上图）
  - `CodeGenConcurrentWorkflow`：图片收集的 4 类（内容图/插画/架构图/Logo）**并发分支**
  - `CodeGenSubgraphWorkflow`：子图封装
  - `node/*`：6 个节点；`node/concurrent/*`：5 个并发图片节点
  - `WorkflowSseController`：`/workflow/execute-sse` 暴露工作流
- **引入的能力**：条件边路由（`addConditionalEdges`）、子图、并发节点、工作流状态机（`WorkflowContext`）
- **⚠️ 现状**：**只在单体模块实验，未接入微服务主链路**。⚠️ 因此当前线上主能力仍是 **Phase 3 的单步 Agent**，而**不是** Phase 6 工作流——不要误以为整个系统已全面工作流化。单体工作流属于"已验证可行，待整合"状态。
- **演进价值**：这是项目的技术天花板探索——从"单步 Agent"到"多步工作流"，质量检查自愈让一次成功率大幅提升

## C.2 演进的核心规律

| 规律 | 体现 |
| --- | --- |
| **先闭环后优化** | Phase 1 先跑通最小闭环，才在 Phase 2-3 加结构化和工程化 |
| **复杂度渐进** | 单文件→多文件→工程→工作流，每步只加一层复杂度 |
| **模式随需求长出** | if-else 够用时不用策略；2 种类型变 3 种且会扩展时才上策略 + 模板方法 |
| **Agent 化是分水岭** | Phase 3 引入工具调用后，产品从"生成器"变"Agent"，后续都基于此扩展 |
| **实验与主线分离** | Phase 6 工作流在单体里实验，不污染稳定的微服务主线——良好的演进纪律 |

## C.3 给复刻者的演进启示

1. **严格按 Phase 1→5 顺序做**，跳级会基础不稳。先 HTML 闭环，再 Vue，再工作流。
2. **Phase 3（Vue+工具）是最大投入点**，也是最大差异化点，预留最多时间。
3. **Phase 6（工作流）是加分项**，可在主产品稳定后再做，且**原项目单体里的节点设计可直接借鉴**——别从零造。
4. **不要一开始就上微服务**：原项目微服务是教学需要，单体（Phase 1-6）完全能承载产品。

---

# 附录 D：可观测性与运维

> AI 项目与传统 CRUD 的运维关键差异：**失败率比 TPS 重要**。一次生成失败=一个用户流失，而慢一点用户能忍。这一章定义该盯哪些指标、怎么盯、告警阈值。

## D.1 AI 项目的核心指标体系

```mermaid
graph TD
    ROOT["AI 平台可观测性"]
    ROOT --> U["用户体验指标"]
    ROOT --> A["AI 行为指标"]
    ROOT --> C["成本指标"]
    ROOT --> I["基础设施指标"]

    U --> U1["SSE 成功率"]
    U --> U2["首字延迟 TTFT"]
    U --> U3["端到端耗时"]

    A --> A1["LLM 调用 P50/P95/P99"]
    A --> A2["Build 成功率"]
    A --> A3["质检通过率"]
    A --> A4["工具调用统计"]

    C --> C1["每日 Token 消耗"]
    C --> C2["每日 LLM 费用"]
    C --> C3["单应用成本"]

    I --> I1["CPU/内存(尤其 build)"]
    I --> I2["磁盘占用(code_output/deploy)"]
    I --> I3["Redis/MySQL 连接"]
```

## D.2 关键指标定义与告警

### 指标 1：SSE 成功率 ⭐最重要

| 维度 | 内容 |
| --- | --- |
| **定义** | 成功收到 `done` 事件的请求 / 总 SSE 请求 |
| **失败分类** | ① 超时（>90s 无 done）；② 中断（连接断开）；③ 业务错误（`business-error` 事件） |
| **采集点** | 前端 `EventSource` 的 `done`/`business-error`/`onerror` 上报；后端 `Flux` 完成/错误回调 |
| **健康阈值** | >95% 绿；90-95% 黄；<90% 🔴 告警 |
| **告警** | 5 分钟窗口成功率<90% 立即告警 |

### 指标 2：LLM 调用耗时分布

| 维度 | 内容 |
| --- | --- |
| **定义** | 单次 LLM 调用从发起到最后一个 token 的耗时 |
| **关键分位** | P50（中位数）、P95、P99 |
| **预期** | HTML 模式 P50 ~8s；Vue 模式 P95 ~60s |
| **采集** | LangChain4j 的 `log-requests/log-responses` 已开启，可埋点；或用 Micrometer `Timer` |
| **告警** | P95 超过模型历史基线 ×2 倍告警（可能是模型变慢或 prompt 膨胀） |

### 指标 3：Build 成功率 ⭐

| 维度 | 内容 |
| --- | --- |
| **定义** | `npm run build` exit code 0 的比例 |
| **失败原因 Top10** | ① 缺依赖；② 语法错误；③ 版本冲突；④ node_modules 损坏；⑤ 网络拉包失败 |
| **采集** | `VueProjectBuilder.executeCommand` 检查 exit code，失败时记录 stderr |
| **健康阈值** | >85% 绿（AI 生成代码本就有失败率）；<70% 🔴 |
| **改进** | 接 Phase 6 质检自愈回路可显著提升此指标 |

### 指标 4：工具调用统计

| 维度 | 内容 |
| --- | --- |
| **定义** | 各工具被调用次数 / 单次请求工具调用总数 |
| **价值** | ① 发现"AI 偏爱 writeFile 不爱 modifyFile"→ prompt 可优化；② 监控单请求工具数接近 20 上限→可能陷入循环（踩坑1） |
| **采集** | `ToolManager` 或各工具 `@Tool` 方法内埋点 |
| **告警** | 单请求工具调用数 ≥18 告警（接近 20 上限，疑似循环） |

### 指标 5：成本 ⭐ 商业命脉

| 维度 | 内容 |
| --- | --- |
| **定义** | 每日 Token 消耗、每日 LLM 费用、单应用平均成本 |
| **预期** | HTML 模式单次 ~2K token；Vue 模式单次 ~10-20K token（工具循环累加） |
| **采集** | LLM API 返回的 `usage` 字段；按模型单价换算费用 |
| **告警** | 单用户日费用超阈值（防刷）；整体日费用环比 +50% 告警 |
| **商业意义** | 直接决定定价——若单应用成本 0.5 元，定价需覆盖此 + 利润 |

### 指标 6：质检通过率（Phase 6）

| 维度 | 内容 |
| --- | --- |
| **定义** | `CodeQualityCheckNode` 一次通过率 / 重生成次数 |
| **价值** | 衡量 AI 生成质量；重生成>2 次仍失败→应人工介入或放弃 |

## D.3 现状监控盘点

项目已有的可观测性基建：

| 能力 | 现状 | 路径 |
| --- | --- | --- |
| Actuator 端点 | ✅ 已引入 | `spring-boot-starter-actuator` |
| Prometheus 指标 | ✅ 已引入 | `micrometer-registry-prometheus`，暴露 `/actuator/prometheus` |
| Grafana 仪表盘 | ✅ 有配置 | `yu-ai-code-mother/grafana/ai_model_grafana_config.json` |
| LLM 请求日志 | ✅ 已开启 | `application.yml` 里 `log-requests: true`/`log-responses: true` |
| 业务埋点 | ❌ 缺失 | SSE 成功率/Build 成功率/工具统计均未埋点 |
| 链路追踪 | ❌ 缺失 | 无 SkyWalking/Sleuth，跨服务(Dubbo)难追 |
| 告警 | ❌ 缺失 | 仅 Grafana 看板，无告警规则 |

**现状评估**：基础设施齐（Prometheus+Grafana），但**业务指标采集几乎空白**——这正是"有仪表盘但看不到关键失败率"的典型状态。复刻上生产前必须补 D.2 的业务埋点。

## D.4 运维 Checklist（复刻上生产前）

- [ ] SSE 成功率埋点 + 告警（<90% 告警）
- [ ] Build 成功率埋点 + 失败原因聚合
- [ ] LLM Token/费用日报
- [ ] 工具调用数监控（接近 20 上限告警）
- [ ] CPU 并发 build 限制（见性能瓶颈2，否则打满）
- [ ] 磁盘清理 cron（`tmp/code_output` 无限增长）
- [ ] deployKey 唯一性校验（踩坑10）
- [ ] LLM API Key 用量监控（防止 key 泄露被刷）
- [ ] Dubbo 链路追踪（截图服务超时定位）
- [ ] 慢查询日志（chat_history 4 亿行后）

---

# 附录 E：项目能力边界

> 知道"能做什么"不够，要知道"**不能做什么**"。这一章划清边界，帮你判断"这个项目/架构适不适合我的场景"。

## E.1 擅长的场景 ✅

| 场景 | 为什么适合 | 示例 |
| --- | --- | --- |
| **营销落地页** | 单页/少交互，HTML 模式秒出 | 产品介绍页、活动页 |
| **企业官网** | 多页静态，多文件模式 | 公司主页、产品展示 |
| **个人博客/简历** | 内容展示为主 | 个人主页、作品集 |
| **数据看板前台** | 前端展示+模拟数据 | Demo 看板、汇报页面 |
| **工具型小网站** | 轻交互原生 JS | 计算器、待办、小工具 |
| **作品展示页** | 图文为主 | 摄影集、设计作品集 |
| **SaaS 前台原型** | Vue 工程模式可做多页 | 产品 Demo、MVP 前端 |

## E.2 不擅长的场景 ❌

| 场景 | 为什么不适合 | 缺什么 |
| --- | --- | --- |
| **ERP/复杂后台** | 无真实数据库/后端业务 | 无后端 Agent、无 DB 持久化生成 |
| **电商交易系统** | 无订单/支付/库存 | 无后端、无事务、无支付集成 |
| **IM/实时通讯** | 无 WebSocket、无消息推送 | 单向 SSE 不支持双向实时 |
| **多人协同编辑** | 无并发冲突处理 | 无 OT/CRDT、无锁机制 |
| **数据库驱动应用** | 生成的代码用模拟数据 | 无 DB Schema Agent、无 API Agent |
| **高并发 C 端** | 本地磁盘+单实例 | 无水平扩展（见性能瓶颈5） |
| **复杂鉴权系统** | 只有 user/admin 两角色 | 无 RBAC、无 OAuth、无组织架构 |
| **原生移动端** | 只生成 Web | 无 React Native/Flutter 生成 |

## E.3 能力边界的根因

```mermaid
graph TD
    CORE["当前架构的本质<br/>= 前端工程生成 Agent"]
    CORE --> L1["只有前端 Agent"]
    CORE --> L2["只有文件操作工具"]
    CORE --> L3["只本地部署"]
    CORE --> L4["只单向通信"]

    L1 --> R1["❌ 无法生成后端/API/DB"]
    L2 --> R2["❌ 无法跑 shell/联网/装包"]
    L3 --> R3["❌ 无法水平扩展"]
    L4 --> R4["❌ 无法实时双向"]

    style CORE fill:#fdd
```

**一句话根因**：当前 Agent **只操作前端工程**，没有数据库 Agent、后端 Agent、测试 Agent、运维 Agent。所以它产出的是"漂亮但无后端"的网站。

## E.4 突破边界的扩展方向

| 想做什么 | 需要补什么 | 难度 |
| --- | --- | --- |
| 生成全栈应用 | 加后端代码生成 Agent + DB Schema Agent + API 联调 | 🔴🔴🔴 高（变成真正的 v0） |
| 实时协同 | 加 WebSocket + 冲突解决（OT/CRDT） | 🔴🔴 中高 |
| 水平扩展 | 代码/产物上对象存储 + 构建服务独立 + CDN | 🟡 中（见性能路线图） |
| 联网获取真实数据 | 加 `searchWeb`/`fetchUrl` 工具 + 安全沙箱 | 🟡 中 |
| AI 自愈提升成功率 | 接入 Phase 6 质检工作流到主链路 | 🟢 低（已有现成实现） |
| 多模板生态 | 加模板 fork/二创能力 | 🟡 中 |
| 移动端生成 | 加 React Native/Flutter 生成策略 | 🔴🔴 中高 |

## E.5 边界判断口诀

> **"这个网站需要真实后端数据库吗？"**
> - 否 → 当前架构适合（落地页/官网/看板/工具）
> - 是 → 当前架构不适合（电商/ERP/IM），需扩展成全栈 Agent 或换方案

> **"需要多人同时改一个应用吗？"**
> - 否 → 适合
> - 是 → 不适合，需加协同层

---

# 附录 F：代码规模评估

> 这个项目到底多大？5000 行和 10 万行项目的复刻策略天差地别。规模决定"AI 一次能生成多少"和"你要拆几期"。本章所有数字均由源码实测统计。

## F.1 总规模概览

| 维度 | 数值 | 说明 |
| --- | --- | --- |
| **后端 Java 总行数** | ~18,700 行 | 单体 10,618 + 微服务 8,101 |
| **后端 Java 文件数** | ~265 个 | 单体 154 + 微服务 118（去重 langgraph4j） |
| **前端总行数** | ~4,900 行 | Vue 3,736 + TS 1,141 |
| **测试代码** | 528 行 / 15 文件 | ⚠️ 全在单体，微服务 0 测试 |
| **Prompt 文件** | 7 个 | 系统 prompt，纯文本 |
| **总体量级** | **中型项目（~2.4 万行）** | AI 辅助下 1 人 2-3 周可复刻 MVP |

## F.2 后端模块规模明细

### 微服务版（线上主线）

| 模块 | 文件数 | 行数 | 职责 | 复刻权重 |
| --- | --- | --- | --- | --- |
| yu-ai-code-app | 35 | 2,879 | 核心业务+生成门面+流处理 | ⭐⭐⭐ 最重 |
| yu-ai-code-ai | 31 | 2,595 | AI 服务+工具+护栏+源码覆盖(1,450) | ⭐⭐⭐ 最重 |
| yu-ai-code-model | 20 | 941 | 实体/枚举/DTO/VO | ⭐ 模板化 |
| yu-ai-code-common | 18 | 716 | 响应体/异常/常量/COS | ⭐ 模板化 |
| yu-ai-code-user | 7 | 610 | 登录注册会话 | ⭐⭐ |
| yu-ai-code-screenshot | 5 | 317 | Selenium 截图 | ⭐ |
| yu-ai-code-client | 2 | 43 | Dubbo 内部接口 | ⭐ |
| **小计** | **118** | **8,101** | | |

### 单体版（含 Phase 6 工作流实验）

| 包 | 文件数 | 行数 | 说明 |
| --- | --- | --- | --- |
| langgraph4j | 37 | **2,380** | ⭐ Phase 6 工作流（节点12+工具4+工作流类8+状态/模型） |
| ai | 21 | 1,154 | AI 服务（与微服务重叠+图片收集扩展） |
| service | 10 | 1,125 | 业务层 |
| core | 15 | 1,043 | 生成门面/解析/保存/构建 |
| model | 20 | 941 | 实体（与微服务共享） |
| controller | 6 | 764 | 含 `WorkflowSseController` |
| config | 8 | 355 | |
| utils | 3 | 251 | |
| **小计** | **154** | **10,618** | |

## F.3 前端规模明细

| 类别 | 数量 | 行数 | 说明 |
| --- | --- | --- | --- |
| 页面 (`.vue`) | 8 | — | HomePage/AppChatPage★/AppEditPage/admin×3/user×2 |
| 组件 (`.vue`) | 7 | — | AppCard/AppDetailModal/DeploySuccessModal/MarkdownRenderer/GlobalHeader/Footer/UserInfo |
| Vue 总计 | 17 | 3,736 | |
| TS（含 api/store/utils/router） | 16 | 1,141 | api 7 个由 OpenAPI 自动生成 |
| **前端总计** | **33** | **4,877** | |

## F.4 核心资产计数

| 资产类型 | 数量 | 清单 |
| --- | --- | --- |
| 数据表 | 3 | user / app / chat_history |
| Controller | 10 | 微服务 4 + 单体 6 |
| ServiceImpl | 7 | AppService/ChatHistoryService/ProjectDownload/User/Screenshot/Inner×2 |
| AI 工具(@Tool) | 7 | writeFile/modifyFile/deleteFile/readFile/readDir/exit + 单体图片工具4 |
| Prompt | 7 | 路由/HTML/多文件/Vue/质检/图片规划/图片系统 |
| 流式模型配置 | 4 套 | chat/streaming-chat/reasoning-streaming/routing |
| 设计模式 | 12 种 | 见附录 B |

## F.5 规模对复刻策略的启示

| 规模段 | 复刻策略 | 本项目归属 |
| --- | --- | --- |
| <5000 行 | AI 一次生成大半，人工微调 | ❌ 超出 |
| **5000–2.5万行** | **AI 分模块生成 + 人工拼接核心链路** | ✅ **本项目（~2.4万）** |
| >2.5万行 | 必须先定架构再分模块，AI 难全局把控 | ❌ 未到 |

**关键结论**：
- 本项目属**中型**，AI 不能一次吃下全部，但**核心链路（Facade+Factory+Service+Controller+AppChatPage）约 3000 行，AI 可一次生成骨架**。
- `model`/`common`/前端 `admin` 页面（~2500 行）是**纯模板代码**，AI 100% 生成，省时最多。
- 真正需要人工深耕的只有：① Vue prompt 工程② AI 工具安全 ③ 可视化编辑器 ④ LangChain4j 流式接入——合计 ~1500 行，却是 80% 价值所在。
- **复刻工作量估算**：AI 辅助下 1 人约 2-3 周（MVP 1 周 + Vue/可视化/部署 1 周 + 调优 1 周），与第六部分"7 天路线"的乐观估计形成对照——7 天能出 demo，2-3 周才到可演示态。

---

# 附录 G：测试体系分析

> AI 项目真正的难点是**验证**而非生成。生成对了不知道为什么对，错了不知道为什么错。没有测试体系的 AI 项目是在黑盒上叠黑盒。本章盘点项目测试现状，并给出 AI Agent 该建什么测试。

## G.1 现状盘点：测试严重偏科

| 测试维度 | 现状 | 位置 | 评价 |
| --- | --- | --- | --- |
| 单元测试(纯逻辑) | ✅ 有 | `CodeParserTest` | 🟢 唯一可进 CI 的测试，无 LLM 依赖 |
| AI 集成测试 | 🟡 有但弱 | `AiCodeGeneratorServiceTest` | 🟡 `@SpringBootTest` 真调 LLM，只 `assertNotNull`，不验内容 |
| 记忆测试 | 🟡 有 | `testChatMemory` | 🟡 验多轮不报错，不验记忆正确性 |
| 工作流 E2E | ✅ 有 | `CodeGenWorkflowTest` | 🟢 4 类 prompt 跑全流程，最接近评测集 |
| 图片工具测试 | ✅ 有 | `ImageSearchToolTest` 等 4 个 | 🟢 验工具可用性 |
| Prompt 回归测试 | ❌ 无 | — | 🔴 最该有的没有 |
| Tool 单元测试 | ❌ 无 | — | 🔴 路径越权等无测试 |
| Build 成功率测试 | ❌ 无 | — | 🔴 |
| 前端测试 | ❌ 无 | — | 🔴 0 个组件/E2E 测试 |
| 微服务测试 | ❌ 无 | — | 🔴 微服务模块 0 测试 |

**核心问题**：测试全集中在单体，微服务主线（线上跑的）**零测试**。且现有 AI 测试只断言"非空"，不验证生成质量——**测了等于没测**。

## G.2 AI Agent 该建的 5 层测试体系

```mermaid
graph TD
    L1["L1 单元测试<br/>纯逻辑,无LLM,进CI"] --> L2["L2 Tool测试<br/>安全+正确性"]
    L2 --> L3["L3 Prompt回归测试<br/>固定输入→校验输出格式"]
    L3 --> L4["L4 Build测试<br/>生成代码能否npm build"]
    L4 --> L5["L5 评测集E2E<br/>100需求→成功率统计"]

    style L1 fill:#dfd
    style L5 fill:#fdd
```

### L1 单元测试（纯逻辑，必进 CI）

| 测试对象 | 测什么 | 现状 | 复刻要点 |
| --- | --- | --- | --- |
| `CodeParser` | 解析各种 markdown 代码块格式 | ✅ `CodeParserTest` | 加边界：无代码块/多代码块/嵌套 |
| `CodeFileSaver` | 文件正确落盘到指定目录 | ❌ | 加：目录创建/覆盖写/路径拼接 |
| `RateLimitAspect` | 限流 key 生成/超限抛异常 | ❌ | mock Redisson |
| `getStaticPreviewUrl` | URL 拼接(Vue 加 dist) | ❌ | 纯函数易测 |

**特点**：无 LLM 依赖、毫秒级、确定性、必须进 CI 把关。项目只有 `CodeParserTest` 达标。

### L2 Tool 测试（安全 + 正确性）⭐ 关键

| 测试对象 | 测什么 | 现状 |
| --- | --- | --- |
| `FileWriteTool` | 正常写文件 | ❌ |
| `FileWriteTool` | **路径越权 `../../etc` 应被拒** | 🔴 无测试！ |
| `FileDeleteTool` | **删 package.json 应被拒** | 🔴 无测试 |
| `FileModifyTool` | oldContent 找不到应报错 | ❌ |
| `FileDirReadTool` | 应过滤 node_modules | ❌ |

**这是最大缺口**：AI 工具的安全边界（踩坑6 路径越权）完全没有测试覆盖。复刻时**每个工具必须先写安全测试再写实现**（TDD）。

### L3 Prompt 回归测试（稳定性）

| 测什么 | 方法 | 现状 |
| --- | --- | --- |
| 路由 prompt | 100 个标注好类型的输入 → 路由准确率 | ❌ |
| HTML prompt | 固定输入 → 输出**恰好 1 个** ```html 块 | ❌ |
| Vue prompt | 固定输入 → 工具调用次数<20 + 产出文件数<30 | ❌ |
| 修改 prompt | 选中元素 + "改红" → 只改目标元素 | ❌ |

**方法**：用 `@TempDir` 隔离，固定 seed，断言**格式**而非内容（内容会漂移）。记录每次 prompt 改动前后的通过率，防回归。项目完全没建。

### L4 Build 测试（可运行性）

| 测什么 | 现状 |
| --- | --- |
| HTML 生成后能 iframe 加载 | ❌ |
| Vue 生成后 `npm install && npm run build` 成功 | ❌ |
| 构建产物能在子路径部署 | ❌ |

**现状**：`VueProjectBuilder` 有 build 逻辑但无测试。单体 `CodeGenWorkflowTest` 间接跑了 build（E2E 里），但不独立、不统计成功率。

### L5 评测集 E2E（成功率统计）⭐ 商业指标

| 维度 | 内容 |
| --- | --- |
| **定义** | 100 个真实需求 → 跑全流程 → 统计"能生成+能 build+能预览"的成功率 |
| **现状** | `CodeGenWorkflowTest` 只有 4 个 case，不统计成功率 |
| **构建** | 准备 100 个覆盖各类型(简单页/多页/Vue)的需求 + 预期(类型/文件数/build 成功)，跑批统计 |
| **价值** | 这是衡量 prompt 改动"是变好还是变坏"的唯一客观依据——**没有评测集，改 prompt 就是盲改** |

## G.3 复刻测试优先级

| 优先级 | 测试 | 理由 |
| --- | --- | --- |
| P0 | L2 Tool 安全测试 | 防路径越权事故，TDD |
| P0 | L1 CodeParser/Saver 单测 | 纯逻辑易测，进 CI |
| P1 | L5 评测集(先 20 个) | 改 prompt 的客观依据 |
| P1 | L4 Build 测试 | 验可运行性 |
| P2 | L3 Prompt 格式回归 | 防格式漂移 |
| P3 | 前端组件测试 | 体验保障 |

## G.4 测试结论

项目测试**形同虚设**：有 15 个测试文件但 ① 全在单体不在主线 ② AI 测试只 assertNotNull 不验质量 ③ 安全边界零覆盖 ④ 无评测集。**复刻时测试要从第一天建**，尤其 Tool 安全测试和评测集——这是把"AI 玩具"变成"AI 产品"的分水岭。

---

# 附录 H：成本模型拆解

> 做 AI SaaS，"一次生成多少钱"是商业命脉。本章按生成模式拆解单次成本，用 DeepSeek 公开定价估算（2026 年价格以官方为准，此处给量级）。

## H.1 单次生成成本拆解

| 成本项 | HTML 模式 | 多文件模式 | Vue Agent 模式 | 工作流模式(Phase6) | 说明 |
| --- | --- | --- | --- | --- | --- |
| 路由模型(Qwen-Turbo) | ~0.0001 | ~0.0001 | ~0.0001 | ~0.0001 | max-tokens=100，几乎免费 |
| 主模型(deepseek-chat) | ~0.001 | ~0.002 | ~0.01 | ~0.01 | 按 ~2K/4K/15K token |
| 推理模型(reasoner) | — | — | ~0.02 | ~0.02 | Vue 用，token 多+单价高 |
| 工具循环 token | — | — | ~0.01 | ~0.01 | 20 次工具往返累加 |
| 质检模型 | — | — | — | ~0.005 | Phase6 额外 |
| 图片收集(可选) | — | — | — | ~0.01 | Phase6 额外 |
| npm install+build | 0 | 0 | 0(算 CPU) | 0(算 CPU) | 不直接花钱，但占资源 |
| Selenium 截图 | 0 | 0 | 0(算 CPU) | 0(算 CPU) | 部署时触发 |
| COS 存储 | ~0.00001 | ~0.00001 | ~0.00001 | ~0.00001 | 截图几 KB，可忽略 |
| Redis/MySQL | ~0 | ~0 | ~0 | ~0 | 毫秒级，可忽略 |
| **单次总成本(估)** | **~$0.001** | **~$0.002** | **~$0.04** | **~$0.06** | 纯 LLM 成本为主 |

> 注：以上为量级估算（DeepSeek 价格远低于 GPT-4；若用 GPT-4o，Vue 模式单次可能 $0.1+）。**实际以你选用模型的官方定价为准**。

## H.2 成本结构分析

```mermaid
pie title Vue Agent 模式成本构成(估)
    "推理模型 token" : 50
    "主模型 token" : 25
    "工具循环 token" : 25
    "其他" : 0
```

**关键结论**：
- **LLM 成本占 99%+**，其他（COS/Redis/CPU）可忽略。降本=降 token。
- **Vue 模式是 HTML 模式的 40 倍成本**——这是项目做 AI 路由（简单需求用 HTML）的根本经济动因，不只是体验考虑。
- 推理模型(reasoner)是 Vue 模式成本大头——若生成质量允许，换 deepseek-chat 能砍掉一半成本。

## H.3 单用户经济模型

假设重度用户每月生成 50 个应用（30 HTML + 15 Vue + 5 工作流）：

| 项 | 月成本/用户 |
| --- | --- |
| HTML ×30 | 30 × $0.001 = $0.03 |
| Vue ×15 | 15 × $0.04 = $0.60 |
| 工作流 ×5 | 5 × $0.06 = $0.30 |
| **合计** | **~$0.93/月** |

**定价启示**：
- 若订阅价 $9.9/月，毛利 ~90%（LLM 成本仅占 10%）——**商业模式成立**。
- 但若用户全用 Vue/工作流模式（$0.04-0.06/次），月生成 200 次则成本 $8-12，**可能亏本**。
- **必须用限流（已有 5次/分）+ 配额控制**，防单个用户刷爆成本。

## H.4 降本策略

| 策略 | 节省 | 代价 |
| --- | --- | --- |
| AI 路由（简单用 HTML） | ⭐⭐⭐ 砍 40 倍 | 路由判断错则体验降 |
| 简单任务用 deepseek-chat 不用 reasoner | ⭐⭐ 砍 50% | 复杂工程质量降 |
| 工具结果精简（不存全文进记忆） | ⭐⭐ 减少 token 累加 | 记忆可能丢细节 |
| 缓存相似需求的生成结果 | ⭐⭐⭐ 命中即免费 | 命中率不确定 |
| Prompt 压缩（去废话） | ⭐ 每 prompt 省几百 token | 需平衡清晰度 |
| 限流+配额 | ⭐⭐ 防刷爆 | 影响重度用户体验 |

---

# 附录 I：竞品对比

> 文档开头提到对标 v0/bolt.new/Lovable，但没分析差异。本章用对比表说明"为什么这个项目值得学"以及"它和商业产品的差距在哪"。

## I.1 能力对比矩阵

| 能力 | 本项目 | v0 (Vercel) | bolt.new (StackBlitz) | Lovable |
| --- | --- | --- | --- | --- |
| HTML 单文件生成 | ✅ | ✅ | ✅ | ✅ |
| 多文件生成 | ✅ | ✅ | ✅ | ✅ |
| Vue/React 工程生成 | ✅ Vue | ✅ React | ✅ React/Vue | ✅ React |
| AI 工具调用 Agent | ✅ | ✅ | ✅ | ✅ |
| 流式输出 | ✅ SSE | ✅ | ✅ | ✅ |
| 实时预览 | ✅ iframe | ✅ | ✅ WebContainer | ✅ |
| **可视化点选编辑** | ✅ | ❌ | ❌ | 🟡 部分 |
| 多轮对话修改 | ✅ | ✅ | ✅ | ✅ |
| 一键部署 | ✅ 本地短链 | ✅ Vercel | ✅ | ✅ |
| 源码下载 | ✅ ZIP | ✅ | ✅ | ✅ |
| 后端/全栈生成 | ❌ | 🟡 部分 | ✅ 全栈 | ✅ 全栈 |
| 联网/真实数据 | ❌ | ❌ | ✅ | ✅ |
| 多模板生态 | ❌ | ✅ | ✅ | ✅ |
| AI 自愈(build失败修复) | 🟡 单体实验 | ✅ | ✅ | ✅ |
| 多模型支持 | ✅ DeepSeek/Qwen | GPT | Claude/GPT | Claude/GPT |
| **开源** | ✅ | ❌ | ❌ | ❌ |
| **可自部署/私有化** | ✅ | ❌ | ❌ | ❌ |
| 用户/后台管理 | ✅ | ❌(SaaS) | ❌ | ❌ |
| 监控体系 | 🟡 有基建 | ❌ | ❌ | ❌ |

## I.2 本项目的独特优势（为什么值得学）

1. ⭐ **可视化点选编辑**：v0/bolt 都没有——这是产品层面最大差异化，也是学习 iframe+postMessage 的绝佳教材。
2. ⭐ **完全开源 + 可私有化**：商业产品全是闭源 SaaS，本项目的代码可读、可改、可自部署。对学习/企业内网场景无可替代。
3. ⭐ **多策略 AI 路由**：按复杂度选生成模式省成本，商业产品多为单一模式。
4. ⭐ **Java 全栈 AI Agent 范式**：市面上 AI Agent 教程多是 Python，本项目展示 Java(LangChain4j) 如何做工具调用 Agent，填补生态空白。
5. ⭐ **企业级后台+监控**：SaaS 产品用户看不到后台，本项目暴露完整用户/应用/对话管理+Prometheus 监控，工程教学价值高。

## I.3 本项目的劣势（与商业产品差距）

| 差距 | 程度 | 说明 |
| --- | --- | --- |
| 无全栈/后端生成 | 🔴 大 | v0/bolt/Lovable 能连后端+DB，本项目只前端 |
| 无 WebContainer 级预览 | 🟡 中 | bolt 用浏览器内 npm，本项目靠服务端 build，慢 |
| 无 AI 自愈(主线) | 🟡 中 | 商业产品 build 失败自动修，本项目单体有但没接主线 |
| 模型能力 | 🟡 中 | DeepSeek 弱于 GPT-4/Claude 在复杂前端生成 |
| 无模板生态 | 🟡 中 | 商业产品有大量起手模板 |
| 扩展性(本地磁盘) | 🔴 大 | 无法水平扩展，商业产品云原生 |

## I.4 定位结论

本项目**不是要替代 v0/bolt**，而是：
- 对**学习者**：开源的 AI Agent 全栈教材，尤其可视化编辑+Java 工具调用是稀缺内容。
- 对**企业**：可私有化部署的内部应用生成器（不依赖外部 SaaS，数据不出域）。
- 对**创业者**：商业产品的"功能参考实现"——抄它的产品形态，用更好的模型+全栈能力+云原生做差异化。

---

# 附录 J：复刻失败路线 vs 正确路线

> 踩坑指南讲"单点坑"，本章讲"路线性失败"——选错方向比写错代码代价大得多。明确写出"别这么走"。

## J.1 错误路线 vs 正确路线总览

```mermaid
graph LR
    subgraph 错误["❌ 错误路线: 一开始全家桶"]
        E1["Day1: LangGraph4j<br/>多Agent工作流"] --> E2["Day10: MCP+RAG<br/>知识库"]
        E2 --> E3["Day20: K8s+微服务<br/>Nacos+Dubbo"]
        E3 --> E4["Day60: 还没有<br/>能用的产品"]
        E4 --> E5["Day90: 放弃"]
    end
    subgraph 正确["✅ 正确路线: 渐进闭环"]
        R1["Day1-3: HTML<br/>流式闭环"] --> R2["Day4-7: Vue+工具<br/>可视化+部署"]
        R3["Day8+: 工作流<br/>自愈+监控"] --> R4["稳定后:<br/>微服务/扩展"]
    end
    style E5 fill:#fdd
    style R4 fill:#dfd
```

## J.2 五大典型失败路线

### ❌ 失败路线 1：一上来就上 LangGraph4j 工作流

| 维度 | 内容 |
| --- | --- |
| **症状** | 第 1 天就开始搭 `image_collector→router→generator→quality_check` 多节点工作流 |
| **结果** | 3 个月还没跑通单次生成，因为连 Phase 1 的"需求→HTML→预览"闭环都没验证 |
| **根因** | 把"高级架构"当"产品"，忘了用户要的是能用的网站不是漂亮的工作流图 |
| **正确做法** | 先 Phase 1-3 单步 Agent 跑通，再在工作流里编排已验证的步骤 |

### ❌ 失败路线 2：过早微服务化（Dubbo/Nacos/K8s）

| 维度 | 内容 |
| --- | --- |
| **症状** | 还没几个用户就拆 user/app/screenshot 三个服务 + Nacos + K8s |
| **结果** | 80% 时间花在运维联调，业务进度极慢；单体 1 周能做完的功能拖 1 月 |
| **根因** | 把原项目的"教学需要"当"工程必需"照抄 |
| **正确做法** | 单体起步，用户/应用/截图用本地方法调用；流量真上来再拆（且优先拆截图这种重资源服务） |

### ❌ 失败路线 3：追求多模型/MCP/RAG 而非生成质量

| 维度 | 内容 |
| --- | --- |
| **症状** | 生成质量一塌糊涂，却去接 5 个模型 + MCP 工具生态 + RAG 知识库 |
| **结果** | 功能列表很长，但用户输入"做个博客"生成出来 build 失败，留存为 0 |
| **根因** | 把"技术丰富度"当"产品力"，忽略核心指标（生成成功率） |
| **正确做法** | 死磕一个模型 + 一套 prompt，把 build 成功率从 50% 提到 85%——这比接 10 个模型有用 100 倍 |

### ❌ 失败路线 4：覆盖框架源码（抄原项目的 langchain4j 覆盖包）

| 维度 | 内容 |
| --- | --- |
| **症状** | 照抄原项目在 `dev/langchain4j/` 下放 8 个覆盖类 |
| **结果** | 升级 LangChain4j 时全线崩溃，且新版可能已原生支持，白维护 |
| **根因** | 盲目复刻"当时条件下的妥协"而非理解其动因 |
| **正确做法** | 直接用 LangChain4j 新版（已支持流式工具事件），永不覆盖源码 |

### ❌ 失败路线 5：不做评测集就改 prompt

| 维度 | 内容 |
| --- |--- |
| **症状** | 凭感觉改 prompt，改完"感觉更好"就上线 |
| **结果** | prompt 越改越差却不知道，A 类需求好了 B 类坏了，最终乱成一团 |
| **根因** | 没有客观衡量标准，AI 输出不确定性被主观判断掩盖 |
| **正确做法** | 先建 20 个 case 的评测集，每次改 prompt 跑一遍对比成功率（见附录 G L5） |

## J.3 正确路线清单

| 阶段 | 做什么 | 不做什么 |
| --- | --- | --- |
| **Week 1** | HTML 流式闭环 + iframe 预览 + 应用 CRUD | 不碰 Vue/工具/工作流 |
| **Week 2** | Vue 工程模式 + AI 工具(TDD 安全测试) + 可视化编辑 + 部署 | 不拆微服务 |
| **Week 3** | 建评测集(20 case) + prompt 调优 + build 自愈 | 不接多模型 |
| **Week 4+** | 按需：工作流整合 / 监控 / 用户后台 / 模板生态 | 仍单体优先 |
| **稳定后** | 才考虑：微服务拆分 / 对象存储 / CDN / 多模型 | — |

## J.4 一句话总结

> **先让一个用户的一个需求跑通，再谈一千个用户的一千个需求。** 原项目自己也是这么演进的（Phase 1→6 渐进，Phase 6 在单体实验不污染主线）。复刻者最大的失败不是技术不够，而是**想一步到位**。

---

# 附录 K：核心数据速查卡

> 单页集中全文档所有关键数字，遗忘时直接来查这一页。所有数据均由源码实测或官方定价估算。

## K.1 项目规模

| 项 | 值 |
| --- | --- |
| 后端 Java 行数 | 18,719（单体 10,618 + 微服务 8,101） |
| 前端行数 | 4,877（Vue 3,736 + TS 1,141） |
| Java 文件 | ~265 个 |
| 数据表 | 3（user / app / chat_history） |
| 前端页面 | 8 个 |
| 前端组件 | 7 个 |
| Controller | 10 个（微服务 4 + 单体 6） |
| ServiceImpl | 7 个 |
| AI 工具(@Tool) | 7 个（文件 6 + exit 1）+ 单体图片 4 个 |
| Prompt 文件 | 7 套 |
| LLM 模型配置 | 4 套（chat / streaming / reasoning / routing） |
| 测试文件 | 15 个（**全在单体，主线 0 测试**） |
| 设计模式 | 12 种（见附录 B） |
| 微服务模块 | 7 个 |

## K.2 核心常量与边界

| 项 | 值 | 出处 |
| --- | --- | --- |
| 工具调用上限 | 20 次 | `maxSequentialToolsInvocations` |
| 对话记忆窗口 | 20 条 | `MessageWindowChatMemory` |
| 用户限流 | 5 次/60 秒 | `@RateLimit` |
| 输入 prompt 上限 | 1000 字符 | `PromptSafetyInputGuardrail` |
| Caffeine 缓存上限 | 1000 实例 / 30 分钟过期 | `AiCodeGeneratorServiceFactory` |
| Vue 工程 token 上限 | 20,000 | Vue prompt 硬约束 |
| Vue 工程文件上限 | 30 个 | Vue prompt 硬约束 |
| deployKey 长度 | 6 位（字母+数字） | `RandomUtil.randomString(6)` |
| Session 有效期 | 30 天 | `application.yml` |
| npm install 超时 | 5 分钟 | `VueProjectBuilder` |
| npm build 超时 | 3 分钟 | 同上 |
| good_app_page 缓存 | 5 分钟 TTL | `RedisCacheManagerConfig` |

## K.3 性能与成本（量级估算）

| 维度 | HTML 模式 | 多文件 | Vue Agent | 工作流(P6) |
| --- | --- | --- | --- | --- |
| LLM 耗时(P50) | ~8s | ~10s | ~20s | ~30s |
| LLM 耗时(P95) | ~15s | ~20s | ~60s | ~90s |
| 端到端(含 build) | 8-15s | 10-20s | 1-3min | 2-5min |
| 单次成本(估) | ~$0.001 | ~$0.002 | ~$0.04 | ~$0.06 |
| Token 消耗 | ~2K | ~4K | ~15K | ~25K |
| 是否走推理模型 | ❌ | ❌ | ✅ | ✅ |
| 是否走工具循环 | ❌ | ❌ | ✅(≤20) | ✅(≤20) |

**单用户经济模型**（重度，月生成 50 应用）：成本 ~$0.93/月，订阅 $9.9 毛利 ~90%。

## K.4 端口与地址

| 服务 | 端口 |
| --- | --- |
| 用户服务 | 8124 |
| 主应用服务 | 8125 |
| Dubbo tri 协议 (user) | 50051 |
| Dubbo tri 协议 (app) | 50053 |
| MySQL | 3306 |
| Redis | 6379 |
| Nacos | 8848 |
| 前端 dev | 由 Vite 默认（5173） |
| 前端 dev 代理 → 后端 | `/api` → `http://localhost:8123` |

## K.5 关键路径速记

| 用途 | 路径 |
| --- | --- |
| 代码生成根目录 | `tmp/code_output/<type>_<appId>/` |
| 部署目录 | `tmp/code_deploy/<deployKey>/` |
| 截图临时目录 | `tmp/screenshots/<uuid>/` |
| Vue 工程预览 URL | `/api/static/vue_project_<appId>/dist/index.html` |
| HTML/多文件预览 URL | `/api/static/<type>_<appId>/` |
| 部署访问 URL | `<deployHost>/<deployKey>/` |
| SSE 生成接口 | `GET /api/app/chat/gen/code?appId=&message=` |
| 工作流 SSE(单体) | `GET /api/workflow/execute-sse?prompt=` |

## K.6 复刻速记

| 阶段 | 工作量 | 关键产物 |
| --- | --- | --- |
| Day 1-3 (Level 1) | HTML 流式闭环 | SSE + iframe 预览 + 落盘 |
| Day 4-7 (Level 2-3) | Vue Agent + 可视化 + 部署 | TokenStream + 工具 + postMessage |
| Day 8-14 (Level 3+) | 评测集 + prompt 调优 + 自愈 | 20 case 评测集 |
| Day 15+ (Level 4) | 用户/后台/监控/限流 | Session + admin 表格 |

## K.7 复刻"必做"与"必避"清单

| ✅ 必做 | ❌ 必避 |
| --- | --- |
| 先 Level 1 再升级 | 一开始就上 LangGraph |
| 单体起步，业务稳定再拆服务 | 过早微服务/Dubbo/K8s |
| Tool 安全测试 TDD | 抄 `dev/langchain4j/` 覆盖包 |
| 建 20+ case 评测集 | 凭感觉改 prompt |
| Nginx `proxy_buffering off` | 用 axios 接 SSE |
| 模型用 `@Scope("prototype")` | 流式模型单例共享 |
| `path.startsWith(sandboxRoot)` 校验 | 工具不做路径越权防护 |

---

# 文档维护指南

> 这份文档已迭代 5 轮（共 ~2500 行）。要让它在半年/一年后仍易读、易维护，需建立持续更新的纪律。本节给出操作建议。

## 更新触发条件（什么时候必须改文档）

| 项目变化 | 必须更新 | 何处 |
| --- | --- | --- |
| 加新生成模式（如 React） | 必更 | `CodeGenTypeEnum` 表（第三/四/十一）+ 附录 A 源码导航 + 附录 H 成本 |
| 加新 AI 工具 | 必更 | 第十一 prompt 体系（如适用）+ 附录 A + 附录 G 测试清单 |
| 改 Prompt | 必更 | 第十一部分对应 prompt 矩阵；评测集结果同步 |
| 升级 LangChain4j 版本 | 必更 | ADR-07（覆盖包是否还需要）+ 附录 A 覆盖包列表 |
| 接入 Phase 6 工作流到主线 | **大改** | 附录 C 链路对照表 + 全文档"未接入"表述 |
| 加额度/计费体系 | 必更 | 第一部分 A5d + 附录 H 成本 + 实体图（第十二） |
| 模型替换（DeepSeek→其他） | 必更 | 技术栈表（第三）+ 附录 H 成本量级 |
| 数据表变更 | 必更 | 第十二部分 ER 图 + 附录 A.7 + 索引建议 |
| 文件 `tmp/code_output` 路径变更 | 必更 | 第十二时序图 + 附录 K 路径速记 |

## 不必更新的（避免文档腐烂）

- 单纯重命名变量/方法
- bug 修复未改架构
- 依赖小版本号变化
- 新增单元测试

## 文档结构纪律

1. **新增内容归入对应 Part**，不要再开新附录字母（K 已是上限，再加请并入现有附录）。例外：根本性新维度才开新附录（如未来出现"安全审计专项"）。
2. **附录 A（源码导航）必须最先更新**——其他章节引用它。源码移动一定先在 A 里改。
3. **附录 C 链路对照表**是"未接入"表述的权威定义，全文档对此类描述应回链此表。
4. **附录 K 速查卡**是数字的真相源。代码常量变了，先改 K，再改散落引用。
5. **每次重大更新在结语脚注注明轮次**（已有：初版 / 第十～十五 / 附录 A-E / F-J）。

## 自动化建议

- 在 CI 跑一次 `grep -nE "未接入|未实现|未启用" doc/*.md`，新增此类描述时强制 PR 评审者确认是否要更新链路对照表。
- 把 `tmp/code_output`、20 次工具上限、Caffeine 1000 等**数值常量**做"魔数清单"，源码变更时联动检查文档。
- 用 `@deprecated` 标注已被取代的类时，附录 A 同步标注。

## 写作风格约束（保证文档一致性）

- **数据用源码实测**，避免"约/大概"等模糊词；不确定就标注"量级估算"。
- **结论先行**：每节开头一句话讲清要点，再展开。
- **必有"复刻要点/复刻强化"**：分析完现状必须给可操作建议，否则等于没写。
- **🟢🟡🔴 用色记号**：表示难度/严重度/状态，全文档统一含义。
- **代码引用用反引号**：类名/路径/常量，方便 IDE 全局搜跳转。
- **避免主观词**：少用"很好/不错/还可以"，多用"省 80% 时间/降 40 倍成本"等量化。

## 一句话维护哲学

> **文档腐烂不是因为没人写，是因为没人删。** 每次改动先问"哪些旧内容已不准确"，删除/修正比新增更优先。否则半年后文档会变成"考古遗址"——大量条目互相矛盾，读者无法判断真假。

---

## 结语

这个项目最值得学的不是「做了什么功能」，而是三层递进的设计思想：

1. **产品层**：用「流式输出 + 实时预览 + 可视化编辑 + 一键部署」把 AI 生成的**不确定性**包装成**确定的爽感**。
2. **架构层**：策略模式（多生成类型）+ 模板方法（落盘）+ 工厂（AI 服务缓存）+ 门面（统一入口）+ AOP（限流/权限）—— 把"加一种新生成模式"的代价压到最低。
3. **AI 层**：从「让 AI 吐一坨代码」进化到「让 AI 用工具一步步构建工程」，这正是 AI Coding Agent 的核心范式（v0/Cursor/Claude Code 同款思路）。

复刻时记住三条铁律：

1. **先把 P0 的单文件流式闭环跑通，再谈策略、工具、可视化、部署。** 架构是为产品服务的，不要为了微服务而微服务。
2. **Prompt 是真正的业务逻辑**——要像维护代码一样版本化、回归测试它。
3. **安全边界（路径越权/Prompt 越狱/并发串流）是 AI Agent 的生命线**——产品可以丑，但不能被用户拿来攻击服务器。

> 文档作者：基于源码静态分析生成。文中所有文件/类/方法均可在仓库对应路径核验。
> - 第一～九部分：初版（项目总结与 Vibe Coding 指南）
> - 第十～十五部分：工程评审补充（ADR / Prompt 体系 / 数据时序 / 性能 / 踩坑 / 优先级矩阵）
> - 附录 A～E：第三轮深化（源码导航 / 设计模式索引 / 演进路线图 / 可观测性运维 / 能力边界）
> - 附录 F～J：第四轮深化（代码规模 / 测试体系 / 成本模型 / 竞品对比 / 失败路线）
> - TL;DR + 5 Part 结构 + 阅读路径 + 业务闭环 + 核心壁垒 + 复刻范围 + 附录 K（速查卡）+ 维护指南：第五轮组织结构与可用性优化
>
> ⚠️ 重要更正（相对初版）：经源码核实，单体模块 `yu-ai-code-mother/src/` 中已用 LangGraph4j 实现了含**质检自愈回路**的多节点工作流（Phase 6），并非初版所述"未接入"。全文档涉及"未接入"的表述，均以附录 C 顶部的「链路对照表」为准（微服务主线未接入 / 单体实验线已接入）。
