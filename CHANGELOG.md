<!-- Keep a Changelog guide -> https://keepachangelog.com -->

## [Unreleased]

### Added

- 状态栏短 hash 可点击：点击弹出当前行 commit 的完整详情（完整 hash、作者、时间、完整 commit message），message body 通过 `git show -s --format=%B <hash>` 后台拉取。
- 新增 `CommitDetailFormatter`（commit 详情多行文本格式化，纯函数）及单测。

### Changed

- `GitBlameWidget` 由 `TextPresentation` 改造为 `CustomStatusBarWidget`，提供可点击的自定义组件（`JBLabel`）。
- `GitBlameService` 新增 `fetchCommitMessage`：后台执行 `git show -s --format=%B <hash>` 拉取完整 message，EDT 回调。

## [1.0.0] - 2026-06-25

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
