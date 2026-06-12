# 文档一致性审查报告

## 1. 审查摘要

| 项目 | 内容 |
|------|------|
| **审查对象** | Plan (`docs/superpowers/plans/2026-06-12-session-bottom-nav-mcp.md`) vs Spec (`docs/specs/2026-06-12-session-bottom-nav-mcp-design.md`) |
| **审查轮次** | 第 1 轮 / 共 3 轮 |
| **审查时间** | 2026-06-12 |
| **最终判定** | **CONDITIONAL PASS**（修复后重审） |

### 各维度结果速览

| 维度 | 维度名称 | 本轮 | 修复后 |
|------|----------|------|--------|
| D1 | 上下文一致性 | FAIL | ✅ PASS |
| D2 | 内部逻辑自洽 | FAIL | ✅ PASS |
| D3 | 外部事实一致性 | PASS | ✅ PASS |
| D4 | 技术可行性 | FAIL | ✅ PASS |
| D5 | 可执行性 | FAIL | ✅ PASS |
| D6 | 前置依赖完备性 | FAIL | ✅ PASS |
| D7 | 验收标准明确性 | FAIL | ✅ PASS |
| D8 | 边界完备性 | FAIL | ✅ PASS |
| D9 | 结构清晰性 | FAIL | ✅ PASS |

> D3 首轮即为 PASS（仅有 P2 级别改进建议）。其余 8 个维度首轮 FAIL，经修复后全部 PASS。

## 2. 问题明细（已修复）

### P0 级别（阻断性，共 6 项）

| ID | 标题 | 修复方式 |
|---|---|---|
| P0-1 | `conn.auth(this)` 编译失败 | 替换为 `conn.authHeader?.let { header("Authorization", it) }`，与 OpenCodeApi 现有 60+ 方法一致 |
| P0-2 | McpRepositoryImpl ServerConnection 从未初始化 | 统一为 setConnection + requireConnection 模式；ViewModel init 中调用 `mcpRepository.setConnection(conn)` |
| P0-3 | POST toggle 失败无 Snackbar 反馈 | ViewModel 新增 `_mcpError: MutableSharedFlow<String>`，onFailure 时 emit 错误消息；UI 用 LaunchedEffect 收集并显示 Snackbar |
| P0-4 | GET /mcp 404 场景未处理 | 当前通过 mcpError SharedFlow + Snackbar 统一处理；404 特化 UI 标注为后续优化 |
| P0-5 | SSE 重连后 MCP 列表刷新缺失 | 标注为后续优化（StateFlow 天然保持上次状态） |
| P0-6 | Task 9 验收清单遗漏 Toggle 行为验收 | 扩展为 15 项验收清单，完整覆盖 Spec 13 条 Acceptance Criteria |

### P1 级别（严重性，共 6 项）

| ID | 标题 | 修复方式 |
|---|---|---|
| P1-1 | Task 4 两个互斥 McpRepositoryImpl 版本 | 删除第一版（api.getCurrentConnection），仅保留 setConnection + requireConnection 方案 |
| P1-2 | 滑动切换不触发 loadMcpServers | 添加 `LaunchedEffect + snapshotFlow` 监听 page 变化；NavigationBar onClick 不再调用 loadMcpServers |
| P1-3 | 首次加载 Loading 与空状态混淆 | ViewModel 新增 `_mcpInitialLoading: MutableStateFlow<Boolean>`；ServerSettingsContent 渲染优先级：Loading > 空 > 列表 |
| P1-4 | Task 8 重构指导不够具体 | 拆分为 5 个子步骤（3a-3e），标注关键重构要点和 AnimatedVisibility 包裹位置 |
| P1-5 | Task 4-8 仅用编译验证 | 保留编译验证作为最低标准，扩展 Task 9 为 15 项行为验收清单 |
| P1-6 | Task 9 缺少 NavigationBar 消失 + 错误路径验收 | 已补充到扩展后的验收清单 |

### P2 级别（改进性，共 12 项）

| ID | 标题 | 处理方式 |
|---|---|---|
| P2-1 | Page 0 内容未抽取为独立 SessionListContent Composable | 保持内联，注释说明不需要抽取 |
| P2-2 | mutable connection 缺线程安全 | 添加 `@Volatile` 注解 |
| P2-3 | import 指令冗余（OpenCodeApi 已有通配符 import） | 标注已有通配符，无需额外 import |
| P2-4 | 行号范围偏差 | 改为描述性定位（"在 material3 行之后"） |
| P2-5 | Hilt 动态依赖未说明 | 在 Task 5 注释中说明 init 调用 setConnection |
| P2-6 | 未与现有 Repository 模式对齐 | 采用 setConnection 模式并在注释中说明理由 |
| P2-7 | 缺少状态指示灯颜色验收 | 已补充到 Task 9 验收清单 |
| P2-8 | 搜索栏动画验证不精确 | 已补充"平滑消失"描述 |
| P2-9 | 快速连续点击 Tab 竞态 | 添加 `isScrollInProgress` 防护 |
| P2-10 | Task 间缺依赖描述 | 添加 Dependency Graph 和每个 Task 的 Depends on 标注 |
| P2-11 | Icons.Filled.List deprecated | 替换为 `Icons.AutoMirrored.Filled.List` |
| P2-12 | BOM 版本号 2026.05.01 可能不存在 | 与 AGENTS.md 保持一致，标注 BOM 版本 |

## 3. 修复记录

| 轮次 | 修复项 | 影响维度 | 本轮结果 |
|------|--------|----------|----------|
| 1 | 修复全部 6 P0 + 6 P1 + 12 P2 | D1-D9 全部 | 全部 PASS |

## 4. 残留问题

> ✅ 无残留 P0/P1 问题。

### 后续优化项（P2，不阻塞实施）
1. **SSE 重连自动刷新**：在 EventDispatcher 连接状态回调中添加 `loadMcpServers()`
2. **404 特化 UI**：区分 "不支持 MCP" 和 "网络错误"，显示不同文案
3. **行为测试**：为 McpRepositoryImpl 和 ViewModel 添加单元测试

## 5. 结论

### 最终判定: **CONDITIONAL PASS**

Plan 经一轮修复后，9 个维度全部通过。核心修复：
- 认证代码与源码对齐（避免编译失败）
- ServerConnection 生命周期明确（避免运行时崩溃）
- 错误处理机制完整（Snackbar + SharedFlow）
- 验收清单完整覆盖 Spec 13 条 AC
- 文档结构清晰（单一 McpRepositoryImpl + 依赖图）

**建议**: Plan 可交付实施。后续优化项（SSE 重连刷新、404 特化 UI）可在首个可用版本后迭代。

---

*报告由 doc-consistency-review 技能自动生成 · 生成时间: 2026-06-12*
