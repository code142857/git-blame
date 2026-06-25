<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Git Blame Changelog

All notable changes to the **Git Blame** IntelliJ plugin are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> 此项目从 [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template) v2.5.0 脚手架派生而来。模板自身的历史（0.0.1 ~ 2.5.0）不在此记录，本日志只描述 Git Blame 插件自身的演进。

## [Unreleased]

### Added

- 路线图：设置面板（开关 / 格式 / 字段选择）
- 路线图：包名从 `org.jetbrains.plugins.template` 重命名为 `com.jiangjinghong.git.blame`
- 路线图：状态栏显示当前行 commit 完整 hash
- 路线图：支持 worktree / submodule

## [2.6.0] - 2026-06-25

### Added

- 新增行内 Git Blame 显示：通过 `EditorLinePainter` 扩展点，在光标所在行末尾追加 `  作者, 时间 • 提交摘要` 文本。
- `GitLinePainter`：调用 `git blame --incremental -l -t -w HEAD -- <file>` 获取 blame 数据，按 commit hash 复用 `LineBlameInfo` 进行增量解析。
- `InlineBlameFormatter`：统一格式化 blame 文本，提交摘要超过 60 字符时截断并补 `...`。
- 文件级 blame 缓存：以 `VirtualFile#getModificationStamp` + git `HEAD`/ref 状态为失效键，文件未修改且分支未切换时直接复用。
- 异步加载：blame 计算调度到 `application.executeOnPooledThread`，不阻塞 EDT；完成后 `invokeLater` 触发编辑器重绘。
- 仓库状态检测：直接读取 `.git/HEAD`、`.git/refs/...` 与 `.git/packed-refs` 推导失效键，不依赖 Git4Idea API。
- 兼容 worktree 场景的 `.git` file（gitdir 指针）解析。
- 默认灰色斜体样式，可通过 Editor Color Scheme 的 `GITTOOLBOX_INLINE_BLAME` 自定义。
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
