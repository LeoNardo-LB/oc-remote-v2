# UI Polish: 5 项优化

> 日期: 2026-06-01
> 版本目标: v2.0.0-beta.115
> 状态: Draft

## 背景

用户在使用 OC Remote 聊天界面时提出 5 项 UI 优化需求，涵盖文案简化、Markdown 渲染增强、工具卡片信息补全和表格布局修正。

## 优化项

### 1. 搜索代码文案简化

**问题**: 搜索工具标题显示"搜索代码"，用户希望简化为"搜索"。

**改动**:
- 修改 `tool_search_code` 字符串资源（6+ 语言文件）
- 中文: `搜索代码` → `搜索`
- 英文: `Search code` → `Search`
- 其他语言同理简化（去掉"code"/"代码"/"コード"等后缀）
- 标题格式 `"搜索 · pattern"` 不变，仅基础文案缩短

**涉及文件**:
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh-rCN/strings.xml`
- `app/src/main/res/values-ja/strings.xml`
- `app/src/main/res/values-ko/strings.xml`
- `app/src/main/res/values-de/strings.xml`
- `app/src/main/res/values-es/strings.xml`
- `app/src/main/res/values-fr/strings.xml`
- `app/src/main/res/values-it/strings.xml`
- `app/src/main/res/values-pt-rBR/strings.xml`
- `app/src/main/res/values-pl/strings.xml`
- `app/src/main/res/values-ru/strings.xml`
- `app/src/main/res/values-tr/strings.xml`
- `app/src/main/res/values-uk/strings.xml`
- `app/src/main/res/values-ar/strings.xml`
- `app/src/main/res/values-id/strings.xml`

**验证**: 搜索工具（grep）标题显示为 `"搜索 · pattern"` 而非 `"搜索代码 · pattern"`。

---

### 2. Markdown 代码块添加复制按钮

**问题**: AI 回复中的代码块没有复制按钮，无法一键复制代码内容。

**现状**: 项目使用 mikepenz v0.41.0，通过 `highlightedCodeBlock` / `highlightedCodeFence` 渲染代码块，这两个组件不包含复制按钮。

**方案**: mikepenz v0.38.0+ 内置了 `MarkdownHighlightedCodeBlock` / `MarkdownHighlightedCodeFence`，支持 `showHeader = true` 参数，自动显示语言标签（左）+ 复制按钮（右）。

**改动**:
- 在 `MarkdownContent.kt` 中，将 `markdownComponents` 的 `codeBlock` 和 `codeFence` 替换为带 `showHeader = true` 的 `MarkdownHighlightedCodeBlock` / `MarkdownHighlightedCodeFence`
- `wordWrap=true` 模式下同样需要应用（当前该模式未使用 highlighted 组件）
- 需要初始化 `highlightsBuilder`（项目已引入 `multiplatform-markdown-renderer-code` 依赖）

**涉及文件**:
- `app/src/main/kotlin/.../markdown/MarkdownContent.kt`

**验证**: AI 回复中的代码块顶部显示语言标签和复制按钮，点击复制按钮可复制代码内容到剪贴板。

---

### 3. 表格自适应填满 + 内部滚动

**问题**: 当前自定义 `SimpleMarkdownTable` 存在两个问题:
1. 表格宽度不填满父容器左右内边距，存在宽度不足的情况
2. 内容超宽时整个表格横向滚动，而非仅在表格内部滚动

**现状**: 外层 `Box` 带 `.horizontalScroll()` 整体滚动；`SubcomposeLayout` 计算均匀列宽但不保证填满父宽度。

**方案**:
1. 外层 `Box` 去掉 `.horizontalScroll()`，改为 `Modifier.fillMaxWidth()`
2. `SubcomposeLayout` 外包一层带 `horizontalScroll` 的内部 `Box`
3. 列宽逻辑增加填满策略:
   - 表格自然宽度 ≤ 父宽度: 按比例拉伸列宽填满父宽度
   - 表格自然宽度 > 父宽度: 保持自然宽度，启用内部横向滚动
4. 边框 `border` 和 `clip` 保留在外层（固定宽度，不随内容滚动）

**涉及文件**:
- `app/src/main/kotlin/.../markdown/MarkdownTable.kt`

**验证**:
- 窄表格自动拉伸至填满左右内边距
- 宽表格可仅在表格内部左右滑动，不影响外部布局
- 表格边框始终贴齐容器宽度

---

### 4. 搜索工具展开后显示入参

**问题**: SearchToolCard 展开后只显示返回结果（Markdown output），不显示搜索的输入参数（pattern、path），用户无法直观了解搜索了什么。

**方案**: 在展开区域顶部（Markdown 输出之前）添加入参信息块:
- 显示 `pattern`（搜索模式）和 `path`（搜索目录，如有值）
- 样式参考 ReadToolCard 的文件路径标签: 等宽小字体 + 圆角 Surface 背景
- 格式: 每个参数一行。`pattern: xxx`（必显示），`path: /some/dir`（仅 path 非空时显示）

**涉及文件**:
- `app/src/main/kotlin/.../tools/cards/SearchToolCard.kt`

**验证**: 搜索工具展开后，结果上方显示搜索参数信息。

---

### 5. Read/Edit 标题短文件名确认

**问题**: 用户反馈 Read/Edit 卡片标题仍显示全路径。

**现状**: 代码已在 v2.0.0-beta.114 改为 `shortPath`:
- ReadToolCard line 68: `"${stringResource(R.string.tool_read)} · $shortPath"`
- EditToolCard line 92: `"${stringResource(R.string.chat_edit_label)} · $shortPath"`

**方案**: 此项可能因用户未更新到 beta.114 导致。如用户确认更新后仍显示全路径，再排查是否有其他代码路径覆盖了标题。

**验证**: 安装 beta.115 后确认标题显示短文件名。

---

## 不做的事

- 不更换 Markdown 渲染引擎（mikepenz v0.41.0 已满足需求）
- 不切换到官方表格组件（官方表格无斑马纹/边框/自适应列宽，维护者也建议自定义）
- 不修改 glob 搜索工具的文案（"查找文件"/"Find files" 保持不变）

## 风险

| 风险 | 缓解 |
|------|------|
| `MarkdownHighlightedCodeBlock` API 与当前用法不兼容 | 查阅 v0.41.0 源码确认参数签名 |
| 表格填满策略导致窄列过宽 | 保持最小列宽为内容自然宽度 |
| `showHeader` 在 wordWrap 模式下的表现 | 测试两种模式 |
