<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Git Blame Changelog

All notable changes to the **Git Blame** IntelliJ plugin are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> 此项目从 [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template) v2.5.0 脚手架派生而来。模板自身的历史（0.0.1 ~ 2.5.0）不在此记录，本日志只描述 Git Blame 插件自身的演进。

## [Unreleased]

### Added

- **设置面板**（Settings → Tools → Git Blame）：总开关、字段 checkbox（author/date/subject）、日期格式下拉（相对时间 / `yyyy-MM-dd` / `yyyy-MM-dd HH:mm`）、自定义格式模板（占位符 `{author}` `{date}` `{subject}`，非空时覆盖 checkbox 组合，`apply` 时校验未知占位符）。
- **状态栏 widget**：通过 `StatusBarWidgetFactory`（`StatusBarEditorBasedWidgetFactory` + `EditorBasedWidget`）在状态栏显示光标行 commit 的短 hash，悬浮查看完整 40 位 hash + 作者 + 时间 + 摘要；光标移动通过 `EditorEventMulticaster` 的 `CaretListener`（disposable 重载）跟踪。
- `LineBlameInfo` 新增 `hash` 与 `authorTime` 字段，供状态栏 widget 与设置驱动的日期格式化使用。
- 纯 JUnit 测试：`InlineBlameFormatterTest`（12 例，覆盖 checkbox 组合 / 模板覆盖 / 截断 / 三种日期格式）、`GitBlameServiceParseTest`（3 例，覆盖增量解析 / commit 复用 / `isHexHash`）。

### Changed

- **包名重命名**：`org.jetbrains.plugins.template` → `com.jiangjinghong.git.blame`，Java 源码迁移至 `src/main/java/com/jiangjinghong/git/blame/`，`plugin.xml` 的 `editor.linePainter` FQN 同步更新。
- **架构重构**：抽取 project 级 `GitBlameService` 承担 blame 缓存、异步加载与 repo 状态推导；`GitLinePainter` 瘦身为只管 caret 行 + 委托 service。修复了 `EditorLinePainter` 作为 app 级单例导致缓存跨项目共享的潜在 bug。
- 模型类外移到 `model/` 包：`LineBlameInfo`、`FileBlameData`、`GitRootInfo`、`RepoState`。
- `InlineBlameFormatter` 改为 settings-aware：新签名 `format(LineBlameInfo, GitBlameSettings)`，日期格式化由 `DateFormatMode` 在读取时计算。
- `plugin.xml`：替换模板占位 `<description>` 为真实功能描述；新增 `projectService`、`applicationService`、`applicationConfigurable`、`statusBarWidgetFactory` 扩展注册。

### Fixed

- **worktree 支持**：`GitBlameService` 读取 `<gitDir>/commondir` 解析共享 git 目录，ref 与 `packed-refs` 从 common dir 解析（此前 worktree 下 ref 值解析为空，缓存失效键脆弱）。
- `GitRootInfo` 新增 `commonDir` 字段；`readGitStateKey` 从 per-worktree `gitDir` 读 `HEAD`、从 `commonDir` 解析 ref 值。
- submodule 路径已由既有 `.git` file → gitdir 解析覆盖，`findGitRoot` 向上查找第一个 `.git` 即停，正确停留在 submodule 根。

### Removed

- 删除空的 `src/main/java/org/` 树与空的 `src/main/resources/messages/` 目录。
- 删除 `plugin.xml` 中无用的 `<resource-bundle>messages.MyBundle</resource-bundle>` 与注释掉的 `toolWindow`/`postStartupActivity` 行。

## [2.6.0] - 2026-06-25

### Added

- 新增行内 Git Blame 显示：通过 `EditorLinePainter` 扩展点，在光标所在行末尾追加 `  作者, 时间 • 提交摘要` 文本。
- `GitLinePainter`：调用 `git blame --incremental -l -t -w HEAD -- <file>` 获取 blame 数据，按 commit hash 复用 `LineBlameInfo` 进行增量解析。
- `InlineBlameFormatter`：统一格式化 blame 文本，提交摘要超过 60 字符时截断并补 `...`。
- 文件级 blame 缓存：以 `VirtualFile#getModificationStamp` + git `HEAD`/ref 状态为失效键，文件未修改且分支未切换时直接复用。
- 异步加载：blame 计算调度到 `application.executeOnPooledThread`，不阻塞 EDT；完成后 `invokeLater` 触发编辑器重绘。
- 仓库状态检测：直接读取 `.git/HEAD`、`.git/refs/...` 与 `.git/packed-refs` 推导失效键，不依赖 Git4Idea API。
- 兼容 worktree 场景的 `.git` file（gitdir 指针）解析。
- 默认灰色斜体样式，可通过 Editor Color Scheme 的 `GIT_INLINE_BLAME` 自定义。
- Dumb Mode（索引重建）下自动暂停 blame 渲染。

### Changed

- 插件元数据：`id` 改为 `me.jinghong.git`，`name` 改为 `Git Blame`，`vendor` 改为 `jinghong`。
- 目标 IntelliJ Platform 升级到 `2026.1`。
- `gradle.properties`：`group` 改为 `com.jiangjinghong.git.blame`，`version` 升到 `2.6.0`。
- `settings.gradle.kts`：`rootProject.name` 改为 `Git Blame`，IntelliJ Platform Gradle Plugin 升级到 `2.16.0`。
- `build.gradle.kts`：使用 `intellijIdea("2026.1")` 依赖helper，移除冗余的 `pluginConfiguration` / `pluginVerification` / `signing` / `publishing` 显式配置。

### Removed

- 移除模板样例代码：`MyBundle.kt`、`MyProjectService.kt`、`MyProjectActivity.kt`、`MyToolWindowFactory.kt`。
- 移除模板样例测试：`MyPluginTest.kt` 与 `src/test/testData/rename/*`。
- 移除 `plugin.xml` 中的 `toolWindow` 与 `postStartupActivity` 扩展注册。
- 移除 `messages/MyBundle.properties` 资源文件。
- 移除 Qodana、Kover、UI 测试相关配置（继承自模板）。

## [2.5.0] - 2026-04-17

- 从 [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template) v2.5.0 创建脚手架，作为 Git Blame 插件的起点。

<!-- 参考链接：发布后请将仓库地址替换为实际 URL -->
[Unreleased]: https://github.com/jiangjinghong/git-blame/compare/2.6.0...HEAD
[2.6.0]: https://github.com/jiangjinghong/git-blame/releases/tag/2.6.0
[2.5.0]: https://github.com/jiangjinghong/git-blame/releases/tag/2.5.0
