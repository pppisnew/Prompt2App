# Router Design

> **状态**：Stub（待 Phase 4 完成后撰写）
> **对应 Phase**：Phase 4 · AI Router 重写
> **相关 ADR**：ADR-0003（待写）

---

## 设计要点（Phase 4 启动时展开）

### 两层路由架构

```
用户 prompt
   ↓
[Layer 1] 规则路由（毫秒级）
  关键词 + 字数 + 历史成功率
   ↓
   命中？ ─── 是 ──→ 返回策略
   ↓ 否
[Layer 2] LLM 兜底分类器
  小模型 + few-shot
  输出 {strategy, confidence, reason}
   ↓
   confidence ≥ 阈值？ ─── 是 ──→ 返回策略
   ↓ 否
人工标注队列 → 反哺评测集
```

### 待详写

- [ ] 规则定义表（关键词正则、阈值、命中优先级）
- [ ] LLM 分类器的 few-shot 例子（来自评测集）
- [ ] 策略：HTML / MULTI_FILE / VUE_PROJECT 的判定边界
- [ ] 路由埋点字段：`router_layer / matched_rule / llm_confidence / final_strategy`
- [ ] 路由准确率评估方法（与评测集 `expected_strategy` 对比）

---

> _此处暂为占位。Phase 4 启动时按 [`README.md`](./README.md) §写作规范 撰写。_
