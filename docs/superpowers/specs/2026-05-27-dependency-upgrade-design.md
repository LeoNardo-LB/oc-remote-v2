# 依赖全面升级设计

> 日期: 2026-05-27
> 状态: 待批准
> 原则: 允许破坏性变更，全量升级到最新稳定版

## 升级总览

| 组件 | 当前版本 | 目标版本 | 变更级别 |
|------|---------|---------|---------|
| **Kotlin** | 2.0.21 | 2.3.21 | 大版本 |
| **AGP** | 8.13.0 | 9.2.1 | 大版本 |
| **Gradle** | 8.13 | 9.5.1 | 大版本 |
| **compileSdk** | 34 | 36 | |
| **targetSdk** | 34 | 35 | |
| **KSP** | 未使用 | 2.3.8 | 新引入 |
| **Compose BOM** | 2024.12.01 | 2026.05.01 | 大跳跃 |
| **activity-compose** | 1.9.1 | 1.13.0 | 大版本 |
| **lifecycle-runtime-ktx** | 2.8.4 | 2.10.0 | 大版本 |
| **navigation-compose** | 2.7.7 | 2.9.8 | 大版本 |
| **hilt-navigation-compose** | 1.2.0 | 1.3.0 | 小版本 |
| **Hilt** | 2.51 (KAPT) | 2.59.2 (KSP) | + KSP 迁移 |
| **core-ktx** | 1.13.1 | 1.18.0 | 大版本 |
| **appcompat** | 1.7.0 | 1.7.1 | 补丁 |
| **datastore-preferences** | 1.1.1 | 1.2.1 | 小版本 |
| **webkit** | 1.11.0 | 1.16.0 | 大版本 |
| **Ktor** | 2.3.11 | 3.5.0 | 大版本 breaking |
| **kotlinx-serialization-json** | 1.7.1 | 1.11.0 | 大版本 |
| **kotlinx-coroutines-android** | 1.8.1 | 1.11.0 | 大版本 |
| **Coil** | 2.6.0 | 3.4.0 | 大版本 breaking |
| **Markdown Renderer** | 0.28.0 | 0.41.0 | 大版本 |
| **Accompanist SwipeRefresh** | 0.34.0 | 移除 | 迁移到 Material3 |
| **mockk** | 1.14.0 | 1.14.9 | 补丁 |

## 分阶段执行计划

### Phase 1: 构建系统 + KSP 迁移

**改动文件**: `build.gradle.kts`(root), `app/build.gradle.kts`, `gradle-wrapper.properties`

- Gradle 8.13 → 9.5.1
- AGP 8.13.0 → 9.2.1
- Kotlin 2.0.21 → 2.3.21（所有 4 处 plugin 版本）
- 引入 KSP 2.3.8 插件
- Hilt kapt → ksp（移除 `kotlin("kapt")`，移除 `kapt { correctErrorTypes = true }`）
- compileSdk 34 → 36, targetSdk 34 → 35
- 移除 kotlin-stdlib force resolution（升级后不再需要）
- 验证: `gradlew assembleRelease` 编译通过

### Phase 2: AndroidX + Compose 全量升级

**改动文件**: `app/build.gradle.kts`

- Compose BOM → 2026.05.01
- activity-compose → 1.13.0
- lifecycle-runtime-ktx → 2.10.0
- navigation-compose → 2.9.8
- hilt-navigation-compose → 1.3.0
- core-ktx → 1.18.0
- appcompat → 1.7.1
- datastore-preferences → 1.2.1
- webkit → 1.16.0
- 验证: 编译通过 + 基本功能可用

### Phase 3: Ktor 3.x 升级

**改动文件**: `app/build.gradle.kts`, Ktor 相关源码

- Ktor 2.3.11 → 3.5.0（7 个模块全部升级）
- 检查 ByteReadChannel 使用，迁移到 kotlinx-io API（如有）
- WebSocket Duration 配置: `java.time.Duration` → `kotlin.time.Duration`
- 检查 SSE 流式传输兼容性
- 验证: 编译通过 + SSE 流式对话可用

### Phase 4: Coil 3 + Markdown Renderer

**改动文件**: `app/build.gradle.kts`, 图片加载相关源码

- Coil: `io.coil-kt:coil-compose:2.6.0` → `io.coil-kt.coil3:coil-compose:3.4.0`
- 新增 `io.coil-kt.coil3:coil-network-okhttp:3.4.0`（网络图片必须）
- 代码中 `coil.*` → `coil3.*` package 迁移
- Markdown Renderer: `multiplatform-markdown-renderer-coil2` → `multiplatform-markdown-renderer-coil3`
- Markdown Renderer 版本: 0.28.0 → 0.41.0
- 验证: 编译通过 + Markdown 渲染 + 图片加载正常

### Phase 5: 移除 Accompanist SwipeRefresh

**改动文件**: `app/build.gradle.kts`, 使用 SwipeRefresh 的源码

- 移除 `accompanist-swiperefresh` 依赖
- 查找所有使用 `SwipeRefresh` / `rememberSwipeRefreshState` 的地方
- 替换为 Material3 `pullToRefresh` modifier（`Modifier.pullToRefresh(isRefreshing, onRefresh)`）
- 验证: 编译通过 + 下拉刷新功能正常

### Phase 6: kotlinx + 测试库

**改动文件**: `app/build.gradle.kts`

- kotlinx-serialization-json → 1.11.0
- kotlinx-coroutines-android → 1.11.0
- kotlinx-coroutines-test → 1.11.0
- mockk → 1.14.9
- 验证: 编译通过 + 测试通过（如有）

## Breaking Changes 清单

### Ktor 2.x → 3.x
- 内部 I/O 迁移到 kotlinx-io（底层 API 有变化）
- WebSocket Duration 类型变化
- Artifact ID 不变（客户端模块）

### Coil 2.x → 3.x
- Maven group: `io.coil-kt` → `io.coil-kt.coil3`
- Package: `coil.*` → `coil3.*`
- 网络图片需要额外 `coil-network-okhttp` 依赖
- `modelEqualityDelegate` 配置方式变更

### Hilt KAPT → KSP
- 移除 `kotlin("kapt")` 插件
- `kapt(...)` → `ksp(...)`
- 移除 `kapt { correctErrorTypes = true }`
- 依赖名: `hilt-android-compiler` → `hilt-compiler`

### Markdown Renderer
- `multiplatform-markdown-renderer-coil2` → `multiplatform-markdown-renderer-coil3`

### Accompanist → Material3
- `SwipeRefresh` → `Modifier.pullToRefresh()`
- `rememberSwipeRefreshState()` → `PullToRefreshState`

## 风险与回退

- 每个 Phase 独立编译验证，失败可单独回退
- Ktor 3.x SSE 兼容性是最大风险点——如不兼容可降回 2.3.12（最新 2.x 补丁）
- AGP 9.x 与现有构建配置可能有冲突，保留 Gradle 8.13 + AGP 8.13.0 作为回退方案

## 涉及文件清单

| 文件 | Phase |
|------|-------|
| `gradle/wrapper/gradle-wrapper.properties` | 1 |
| `build.gradle.kts` (root) | 1 |
| `app/build.gradle.kts` | 1, 2, 3, 4, 5, 6 |
| `app/src/main/kotlin/.../di/*` | 1 (Hilt KSP 无代码变化) |
| Ktor 相关源码 (ApiClient, SSE 等) | 3 |
| Coil/图片加载相关源码 | 4 |
| Markdown 渲染相关源码 | 4 |
| SwipeRefresh 使用处 | 5 |
