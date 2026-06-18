# Architecture

> 设计文档目录。**不在这里建天空楼阁**——每篇设计文档都对应一个已完成或正在进行的 Phase，按需写。

## 文件清单

| 文件 | 主题 | 落地 Phase | 状态 |
| --- | --- | --- | --- |
| [`system-overview.md`](./system-overview.md) | 整体架构 / 边界 / 数据流 | Phase 1 | 📝 Stub |
| [`module-design.md`](./module-design.md) | 模块化单体的领域分包 | Phase 1 | 📝 Stub |
| [`router-design.md`](./router-design.md) | 两层 AI Router 设计 | Phase 4 | 📝 Stub |
| [`agent-design.md`](./agent-design.md) | Tool Calling Agent + 三层安全 | Phase 3 | 📝 Stub |
| [`eval-design.md`](./eval-design.md) | 评测体系：执行器 + 评分器 + 报表 | Phase 5 | 📝 Stub |
| [`metric-design.md`](./metric-design.md) | 12 维 generation_metric 表 | Phase 6 | 📝 Stub |

## 写作规范

每篇设计文档应包含：

1. **目标与非目标**（明确边界）
2. **关键决策**（链接到对应 ADR）
3. **核心数据流 / 时序**（Mermaid 图）
4. **接口契约**（如有）
5. **失败模式与降级**（不写"happy path"完美主义文档）
6. **演进锚点**（哪些假设可能失效，未来如何调整）

## 与 ADR 的关系

```
ADR：为什么这么决定（一次性、不可变）
  ↓
设计文档：现在系统是什么样（活文档，跟代码一起演进）
```

ADR 写一次就锁定，设计文档随 Phase 推进更新。**两者不要混淆**。
