# 文档一致性审查报告

## 1. 审查摘要

| 项目 | 内容 |
|------|------|
| **审查对象** | `docs/superpowers/specs/2026-06-02-ui-design-unification.md` + `docs/superpowers/plans/2026-06-02-ui-design-unification.md` |
| **审查轮次** | 第 1 轮 / 共 3 轮 |
| **审查时间** | 2026-06-02 |
| **最终判定** | **CONDITIONAL PASS** |

### 各维度结果速览

| 维度 | 维度名称 | 本轮 | 状态 |
|------|----------|------|------|
| D1 | 上下文一致性 | FAIL → **已修复** | ✅ |
| D2 | 内部逻辑自洽 | FAIL → **已修复** | ✅ |
| D3 | 外部事实一致性 | FAIL → **已修复** | ✅ |
| D4 | 技术可行性 | FAIL → **已修复** | ✅ |
| D5 | 可执行性 | FAIL → **已修复** | ✅ |
| D6 | 前置依赖完备性 | FAIL → **已修复** | ✅ |
| D7 | 验收标准明确性 | FAIL → **已修复** | ✅ |
| D8 | 边界完备性 | FAIL → **已修复** | ✅ |
| D9 | 结构清晰性 | PASS | ✅ |

## 2. 修复记录

### 第 1 轮修复

| Issue ID | 严重级别 | 修改位置 | 修改摘要 |
|----------|----------|----------|----------|
| D3-001/D1-001/D2-001 | P0 | Spec §2.1 Motion.kt | `EmphasizedDecelerate` → `EaseOut`，`EmphasizedAccelerate` → `EaseIn` |
| D3-002/D1-003/D2-002 | P0 | Spec §2.2 页面过渡 | `MaterialSharedAxisX` → 实际 slide+fade API 描述 |
| D4-001 | P0 | Plan Task 4 | "添加依赖" → "验证 API 可用性"（可能已合并进 material3） |
| D4-002/D8-008 | P0 | Plan Task 9 | LazyVerticalGrid 补充 `GridItemSpan(maxLineSpan)` 处理全宽元素 |
| D1-004/D7-002 | P0 | Plan Task 10 | 补充 SettingsScreen `RoundedCornerShape(20.dp)` → `shapes.large` 步骤 |
| D1-002/D2-003 | P1 | Spec §3.3 | "删除 isAmoledTheme()" → "委托到 LocalAmoledMode.current" |
| D3-006/D4-007 | P1 | Spec+Plan | `compositionLocalOf` → `staticCompositionLocalOf` |
| D1-005/D6-002 | P1 | Spec §3.1 | `AppTypography` → `Typography` |
| D8-003 | P1 | Plan Task 3-11 | 每个编译验证步骤后添加回滚策略 |
| D5-002/D6-003 | P1 | Plan Task 9 | 消除 API 双方案不确定性，只保留 `calculateWindowSizeClass` |
| D4-004 | P1 | Plan Task 9 | 补充 `@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)` |
| D7-006/D4-008 | P1 | Plan Task 8 | 补充 AmoledSurface shape 替换安全性说明 |

**提交记录：**
- `0b82490 docs: fix spec issues from doc-consistency-review (P0/P1)`
- `4903505 docs: fix plan issues from doc-consistency-review (P0/P1)`

## 3. 残留问题（P2 改进建议）

> 以下 P2 问题已识别但不阻塞实施，建议后续迭代中优化。

### [P2] D8-001: Shape 替换全局覆盖率
- **位置**: Plan 全文
- **描述**: 代码库中约 28 个文件使用了硬编码 `RoundedCornerShape`，计划仅覆盖 AmoledCard.kt 和 SettingsScreen。其余文件（ChatInputBar 16 处、ServerProvidersScreen 7 处等）未纳入。
- **建议**: 在 Phase 3 打磨阶段增加一个全局 Shape 审计 Task，用 `rg "RoundedCornerShape"` 逐一评估是否应替换为主题值。

### [P2] D9-003: Spec/Plan 代码块重复
- **位置**: Spec §1.1/§2.1 vs Plan Task 1/Task 2
- **描述**: Shape.kt 和 Motion.kt 的代码在两份文档中逐字重复，存在同步风险。
- **建议**: Spec 只保留接口定义和设计决策，完整实现代码仅在 Plan 中出现。

### [P2] D9-002: Plan 无目录
- **位置**: Plan 文档顶部
- **描述**: 619 行 12 个 Task 的文档缺少目录索引。
- **建议**: 在 File Structure 表后添加 Task 索引。

### [P2] D8-006: Dynamic Color + AMOLED 共存
- **位置**: Spec §3.1
- **描述**: Dynamic Color 激活且 AMOLED 开启时，shapes 走 AmoledShapes 但 colorScheme 非纯黑，未讨论此组合下的视觉效果。
- **建议**: 在验证清单中增加此项，手动验证。

### [P2] D7-007: 品牌动画无回归验证
- **位置**: Plan Task 11
- **描述**: PulsingDotsIndicator 1200ms 脉冲"保持不变"仅为文字提醒，无验证步骤。
- **建议**: Task 12 最终验证中添加 `rg "1200" PulsingDotsIndicator.kt` 确认。

## 4. 结论

### 最终判定: **CONDITIONAL PASS**

所有 P0/P1 问题已在第 1 轮修复中解决。残留 5 个 P2 改进建议不影响实施正确性，建议在后续迭代中处理。

**建议**: 文档可进入实施阶段。建议实施时优先关注 Task 4（API 可用性验证）和 Task 9（Grid span + API 确认）两个曾有不确定性的 Task，首次编译时仔细观察。

---

*报告由 doc-consistency-review 技能自动生成 · 生成时间: 2026-06-02*
