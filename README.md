# Git Blame

[![JetBrains plugins](https://img.shields.io/jetbrains/plugin/v/me.jinghong.git.svg)](https://plugins.jetbrains.com/plugin/me.jinghong.git)
[![Version](https://img.shields.io/jetbrains/plugin/coverage/me.jinghong.git.svg)](https://plugins.jetbrains.com/plugin/me.jinghong.git)
[![Build](https://github.com/JetBrains/intellij-platform-plugin-template/workflows/Build/badge.svg)](https://github.com/JetBrains/intellij-platform-plugin-template/actions)

一个轻量级的 IntelliJ 平台插件，在编辑器光标所在行末尾**实时显示 Git Blame 信息**（作者、时间、提交摘要）。

![Git Blame inline preview](.github/readme/intellij-platform-plugin-template-dark.svg#gh-dark-mode-only)
![Git Blame inline preview](.github/readme/intellij-platform-plugin-template-light.svg#gh-light-mode-only)

## 功能特性

- **行内 Blame 显示**：仅在光标所在行末尾追加 blame 文本，不打扰其他行。
- **完整信息**：作者名 + 提交时间 + 提交摘要，格式如下：

  ```
    张三, 2024-01-15 10:30 • fix bug
  ```

- **状态栏 hash**：状态栏显示光标行 commit 的短 hash，鼠标悬浮查看完整 40 位 hash + 作者 + 时间 + 摘要。
- **可配置**：Settings → Tools → Git Blame 提供总开关、字段选择（author/date/subject）、日期格式（相对时间 / `yyyy-MM-dd` / `yyyy-MM-dd HH:mm`）、自定义格式模板（`{author}` `{date}` `{subject}`）。
- **默认样式**：灰色斜体，与 IDE 主题协调；可通过 `EditorColorsScheme` 自定义 `GIT_INLINE_BLAME` 颜色。
- **性能优化**：
  - 文件级 blame 结果缓存（project 级 `GitBlameService`），文件未修改且 `HEAD` / ref 未变化时复用。
  - 异步加载（`executeOnPooledThread`），不阻塞 EDT。
  - 增量解析 `git blame --incremental` 输出，commit 信息按 hash 复用。
  - Dumb Mode（索引重建）下自动暂停。
- **worktree / submodule**：解析 worktree 的 `commondir` 指针与 submodule 的 `.git` file → gitdir 指针。
- **零依赖**：直接调用本地 `git` 可执行文件，不依赖 IntelliJ Git4Idea API，兼容所有基于 IntelliJ 平台的 IDE。

## 兼容性

| 项目             | 值                          |
|----------------|----------------------------|
| 插件 ID         | `me.jinghong.git`          |
| Vendor         | jinghong                   |
| 当前版本         | 2.6.0                       |
| 目标平台         | IntelliJ IDEA 2026.1+       |
| 最低 since-build | 由 IntelliJ Platform Gradle Plugin 自动推导 |
| 系统要求         | 本地已安装 `git` 且项目位于 git 仓库中 |

## 安装

### 从 JetBrains Marketplace 安装（推荐）

> TODO：插件发布后在 Marketplace 搜索 "Git Blame" 即可安装。

### 从源码构建

```bash
# 1. 克隆仓库
git clone <repo-url> git-blame
cd git-blame

# 2. 构建 plugin zip
./gradlew buildPlugin

# 3. 产物位于 build/distributions/
```

然后在 IntelliJ IDEA 中：`Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...` → 选择 `build/distributions/Git_Blame-*.zip`。

### 本地调试

```bash
./gradlew runIde
```

会在沙箱 IDE 中启动插件，便于调试。

## 使用方式

1. 用 IntelliJ 打开任意一个 git 仓库下的项目。
2. 打开任意已提交过的文件。
3. 将光标移动到某一行。
4. 该行末尾会显示：`  作者, 时间 • 提交摘要`。
5. 状态栏同时显示该行 commit 的短 hash，悬浮查看完整信息。

> 仅在已提交到 git 的行上显示。新文件、未提交的修改行不会显示 blame 信息。

### 配置

`Settings` → `Tools` → `Git Blame`：

- **总开关**：关闭后不显示行内 blame 与状态栏 hash。
- **字段选择**：勾选要显示的 author / date / subject。
- **日期格式**：相对时间（"3 days ago"）/ `yyyy-MM-dd` / `yyyy-MM-dd HH:mm`。
- **格式模板**：非空时覆盖字段选择，支持占位符 `{author}` `{date}` `{subject}`，摘要超 60 字符自动截断。

## 实现原理

插件核心通过 IntelliJ 平台的 [`EditorLinePainter`](https://plugins.jetbrains.com/docs/intellij/editor-api.html) 扩展点实现：

```
src/main/java/com/jiangjinghong/git/blame/
├── GitBlameService.java        # project service：blame 缓存、异步加载、repo 状态推导
├── line/
│   ├── GitLinePainter.java     # EditorLinePainter 扩展：光标行末尾显示 blame
│   └── InlineBlameFormatter.java # 文本格式化：settings-aware 拼接
├── model/                      # LineBlameInfo / FileBlameData / GitRootInfo / RepoState
├── settings/                   # GitBlameSettings / GitBlameConfigurable / DateFormatMode
└── widget/                     # GitBlameWidgetFactory / GitBlameWidget（状态栏 hash）
```

### 工作流程

1. IDE 对每个可见行调用 `GitLinePainter#getLineExtensions`。
2. 检查总开关；判断当前行是否为光标行；不是则直接返回 `null`。
3. 前置条件检查：非 Dumb Mode、非目录、文件在 git 仓库下（由 `GitBlameService` 承担）。
4. 查询文件级 blame 缓存：
   - 缓存命中（文件未修改 + `HEAD`/ref 未变化）→ 直接取出该行 `LineBlameInfo`。
   - 缓存未命中 → 调度后台任务执行 `git blame`，解析后写回缓存并触发重绘。
5. 调用 `InlineBlameFormatter.format(info, GitBlameSettings)` 按 settings 拼接文本。
6. 以灰色斜体 `TextAttributes` 包装为 `LineExtensionInfo` 返回。

状态栏 widget（`GitBlameWidget`）独立通过 `EditorEventMulticaster` 的 `CaretListener` 跟踪光标移动，从同一 `GitBlameService` 缓存读取 `LineBlameInfo.hash` 渲染短 hash。

### 调用的 git 命令

```bash
git blame --incremental -l -t -w HEAD -- <relative-path>
```

- `--incremental`：增量输出，便于边读边解析。
- `-l`：使用长 commit hash。
- `-t`：时间戳为 Unix epoch（便于格式化）。
- `-w`：忽略空白差异。
- `HEAD`：仅对当前 HEAD 提交做 blame。

### 缓存与失效

| 缓存           | key            | 失效条件                                           |
|---------------|----------------|--------------------------------------------------|
| `fileBlameCache` | 文件绝对路径 | `VirtualFile#getModificationStamp` 变化 或 git `HEAD`/ref 变化 |
| `gitRootCache`   | 文件绝对路径 | 进程生命周期内不失效（git 仓库根目录不会移动）              |
| `loadingFiles`   | 路径+stamp+ref | 一次加载完成后自动移除                              |

仓库状态通过直接读取 `.git/HEAD` 与 `.git/refs/...`、`.git/packed-refs` 推导，避免依赖外部库。

## 项目配置

| 文件                     | 作用                                                       |
|------------------------|-----------------------------------------------------------|
| `gradle.properties`    | 插件 `group`、`version`、Gradle 行为开关                       |
| `settings.gradle.kts`  | Gradle 插件版本、IntelliJ Platform 仓库扩展                      |
| `build.gradle.kts`     | 目标 IntelliJ 平台版本、依赖、`runIde` 调试参数                  |
| `src/main/resources/META-INF/plugin.xml` | 插件元数据与扩展注册（`editor.linePainter`、`statusBarWidgetFactory`、`projectService`、`applicationService`、`applicationConfigurable`） |

### 预定义 Run/Debug 配置

`.run/` 目录下提供了三组配置：

| 配置名              | 等价 Gradle 任务    | 用途                          |
|------------------|------------------|-----------------------------|
| Run Plugin       | `:runIde`        | 在沙箱 IDE 中启动插件进行手动调试     |
| Run Tests        | `:check`         | 运行测试                         |
| Run Verifications | `:verifyPlugin`  | 校验插件在多个目标 IDE 版本下的兼容性        |

## 开发

### 环境要求

- JDK 21+
- Gradle 9.5.0（由 Gradle Wrapper 自动下载）
- 本地安装 `git`

### 常用命令

```bash
./gradlew buildPlugin       # 构建插件 zip
./gradlew runIde            # 在沙箱 IDE 中调试运行
./gradlew check             # 运行测试
./gradlew verifyPlugin      # 兼容性校验
./gradlew wrapper --gradle-version <ver> && ./gradlew wrapper   # 升级 Gradle Wrapper
```

### 日志

运行时的 `idea.log` 中可看到 `GitLinePainter` 在 debug 级别输出的诊断信息（git 失败、HEAD 读取失败等）。

## 限制与已知问题

- 仅对 `HEAD` 提交做 blame，不显示 working tree 中未提交修改的来源。
- 状态栏 widget 在设置切换后不会即时显隐（widget 注册时机由平台决定）；行内 blame 会立即响应。
- 颜色通过 IDE 的 Color Scheme 设置项 `GIT_INLINE_BLAME` 调整，未注册为可视化颜色设置项。

## 路线图

- [x] 设置面板：开关、格式、显示字段选择
- [x] 包名重命名
- [x] 在状态栏显示当前行 commit 的完整 hash
- [x] 支持 worktree / submodule
- [ ] 注册 `GIT_INLINE_BLAME` 为可视化 Color Scheme 项
- [ ] 设置切换后状态栏 widget 即时显隐

## 许可证

[MIT](./LICENSE)

## 致谢

- [GitToolBox](https://plugins.jetbrains.com/plugin/7499-gittoolbox) — 行内 blame 的交互灵感来源。
- [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template) — 项目脚手架。
- [IntelliJ Platform SDK 文档](https://plugins.jetbrains.com/docs/intellij?from=IJPluginTemplate)
