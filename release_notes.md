## v2.0.0-beta.67

### Nested Scroll Fix
- **Inertia punch-through** — 展开卡片内滑动到边界不会再导致主列表猛烈加速
- 使用方案B：条件消费 NestedScrollConnection，仅在边界时拦截剩余速度
- 应用于 ReasoningBlock、ToolCallCard、SearchToolCard、TaskToolCard

### Expandable Cards
- **半屏高统一** — 所有可展开卡片（ToolCallCard、SearchToolCard、TaskToolCard、ReasoningBlock）统一半屏高度限制
- **SearchToolCard / TaskToolCard** — 内容不再截断，可正常滚动
- **ToolCallCard** — 展开内容增加半屏高限制 + 滚动

### ReasoningBlock 改进
- **方形设计** — 去除圆角，左侧渐变竖线，更易区分思考过程和工具卡片
- **字体统一** — Header 使用 labelMedium，内容使用 small 字号，与工具卡片保持一致
- **展开状态持久化** — 滑走再回来不再自动折叠

### Token & Stats Enhancement
- **富文本底栏** — 回复底部显示：供应商图标 + 模型名 + 耗时 + tokens + cost
- **会话级总数** — TopBar 令牌数/费用使用服务器端 Session 聚合，而非仅已加载消息

### Response Header
- **本地时间戳** — "Response" 标题旁显示消息创建时间（HH:mm）

### Text Selection
- **内联文本选择** — 长按助理 Markdown 内容直接显示原生选择手柄（透明叠加层方案）
