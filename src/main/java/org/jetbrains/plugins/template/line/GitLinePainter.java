package org.jetbrains.plugins.template.line;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorLinePainter;
import com.intellij.openapi.editor.LineExtensionInfo;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行内 Git Blame 信息显示
 * <p>
 * 参考 GitToolBox 实现，在光标所在行末尾显示 git blame 信息。 通过 EditorLinePainter 扩展点，IDE 会对每个可见行调用
 * getLineExtensions。
 * </p>
 *
 * <p>
 * 实现原理： 1. 检查光标是否在此行（只在光标行显示） 2. 执行 git blame --incremental 获取 blame 数据 3. 解析 author /
 * date / subject 4. 格式化为灰色斜体文本追加在行尾
 * </p>
 */
public class GitLinePainter extends EditorLinePainter {

	private static final Logger LOG = Logger.getInstance(GitLinePainter.class);

	/** 自定义颜色 key */
	private static final TextAttributesKey INLINE_BLAME_KEY = TextAttributesKey
		.createTextAttributesKey("GITTOOLBOX_INLINE_BLAME");

	/** 文件级 git blame 输出缓存: filePath -> FileBlameData */
	private final Map<String, FileBlameData> fileBlameCache = new ConcurrentHashMap<>();

	private final Map<String, GitRootInfo> gitRootCache = new ConcurrentHashMap<>();

	private final Set<String> loadingFiles = ConcurrentHashMap.newKeySet();

	@Override
	@Nullable
	public Collection<LineExtensionInfo> getLineExtensions(@NotNull Project project, @NotNull VirtualFile file,
			int lineIndex) {

		Editor editor = getCurrentEditor(project, file);
		if (editor == null) {
			return null;
		}
		int caretLine = getCaretLine(editor);
		if (caretLine != lineIndex) {
			return null; // 非光标行，不显示
		}

		// 1. 前置条件检查
		if (!shouldShow(project, file)) {
			return null;
		}

		// 3. 获取 blame 文本
		String blameText = getBlameText(project, file, lineIndex);
		if (blameText == null || blameText.isEmpty()) {
			return null;
		}

		// 4. 构造 LineExtensionInfo（灰色斜体）
		TextAttributes attrs = getBlameTextAttributes();
		return Collections.singletonList(new LineExtensionInfo(blameText, attrs));
	}

	// ========== 条件检查 ==========

	private boolean shouldShow(@NotNull Project project, @NotNull VirtualFile file) {
		// 不在 Dumb Mode（索引模式）
		if (DumbService.isDumb(project)) {
			return false;
		}
		// 只处理文件（排除目录）
		if (file.isDirectory()) {
			return false;
		}
		// 检查是否在 git 仓库中
		return isUnderGit(project, file);
	}

	private boolean isUnderGit(@NotNull Project project, @NotNull VirtualFile file) {
		return getGitRootInfo(file).isUnderGit();
	}

	// ========== 编辑器工具 ==========

	@Nullable
	private Editor getCurrentEditor(@NotNull Project project, @NotNull VirtualFile file) {
		FileEditorManager fem = FileEditorManager.getInstance(project);
		for (var fileEditor : fem.getAllEditors(file)) {
			if (fileEditor instanceof TextEditor textEditor) {
				return textEditor.getEditor();
			}
		}
		// fallback: selectedTextEditor
		Editor selected = fem.getSelectedTextEditor();
		if (selected != null) {
			VirtualFile selectedFile = FileDocumentManager.getInstance().getFile(selected.getDocument());
			if (file.equals(selectedFile)) {
				return selected;
			}
		}
		return null;
	}

	private int getCaretLine(@NotNull Editor editor) {
		CaretModel caretModel = editor.getCaretModel();
		if (caretModel.isUpToDate()) {
			return caretModel.getLogicalPosition().line;
		}
		return -1;
	}

	// ========== Blame 数据获取 ==========

	@Nullable
	private String getBlameText(@NotNull Project project, @NotNull VirtualFile file, int lineIndex) {
		// 获取文件级 blame 数据（带缓存）
		FileBlameData fileBlame = getOrComputeFileBlame(project, file);
		if (fileBlame == null) {
			return null;
		}

		// 取出该行的 blame 信息并格式化
		return fileBlame.getFormattedLine(lineIndex);
	}

	@Nullable
	private FileBlameData getOrComputeFileBlame(@NotNull Project project, @NotNull VirtualFile file) {
		RepoState repoState = getRepoState(file);
		if (repoState == null) {
			return null;
		}

		String filePath = file.getPath();
		FileBlameData cached = fileBlameCache.get(filePath);

		// 检查缓存是否有效（文件未修改，且 HEAD/ref 未变化）
		if (cached != null && cached.modificationStamp == file.getModificationStamp()
				&& cached.repoStateKey.equals(repoState.stateKey)) {
			return cached;
		}

		scheduleFileBlameLoad(project, file, repoState);
		return null;
	}

	private void scheduleFileBlameLoad(@NotNull Project project, @NotNull VirtualFile file,
			@NotNull RepoState repoState) {
		String filePath = file.getPath();
		String loadingKey = filePath + "\n" + file.getModificationStamp() + "\n" + repoState.stateKey;
		if (!loadingFiles.add(loadingKey)) {
			return;
		}

		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			try {
				FileBlameData blameData = computeFileBlame(project, file, repoState);
				RepoState currentState = getRepoState(file);
				if (currentState != null && blameData.modificationStamp == file.getModificationStamp()
						&& blameData.repoStateKey.equals(currentState.stateKey)) {
					fileBlameCache.put(filePath, blameData);
					if (!blameData.isEmpty()) {
						repaintEditors(project, file);
					}
				}
			}
			finally {
				loadingFiles.remove(loadingKey);
			}
		});
	}

	private void repaintEditors(@NotNull Project project, @NotNull VirtualFile file) {
		ApplicationManager.getApplication().invokeLater(() -> {
			if (project.isDisposed()) {
				return;
			}
			for (var fileEditor : FileEditorManager.getInstance(project).getAllEditors(file)) {
				if (fileEditor instanceof TextEditor textEditor) {
					textEditor.getEditor().getComponent().repaint();
				}
			}
		});
	}

	private FileBlameData computeFileBlame(@NotNull Project project, @NotNull VirtualFile file,
			@NotNull RepoState repoState) {
		String projectBase = project.getBasePath();
		if (projectBase == null) {
			return FileBlameData.empty(file.getModificationStamp(), repoState.stateKey);
		}

		// 计算相对路径
		String relativePath = file.getPath().substring(repoState.gitRootInfo.repoRoot.length() + 1);

		try {
			// 执行 git blame --incremental -l -t -w HEAD -- <file>
			ProcessBuilder pb = new ProcessBuilder("git", "blame", "--incremental", "-l", "-t", "-w", "HEAD", "--",
					relativePath);
			pb.directory(new File(repoState.gitRootInfo.repoRoot));
			pb.redirectErrorStream(true);
			// 设置 UTF-8 编码
			pb.environment().put("GIT_TERMINAL_PROMPT", "0");

			Process process = pb.start();
			FileBlameData blameData = new FileBlameData(file.getModificationStamp(), repoState.stateKey);

			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				parseIncrementalBlame(reader, blameData);
			}

			int exitCode = process.waitFor();
			if (exitCode == 0 && !blameData.isEmpty()) {
				return blameData;
			}
			else if (LOG.isDebugEnabled()) {
				LOG.debug("git blame exited with code " + exitCode + " for " + file.getPath());
			}
		}
		catch (Exception e) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("git blame failed for " + file.getPath(), e);
			}
		}
		return FileBlameData.empty(file.getModificationStamp(), repoState.stateKey);
	}

	/**
	 * 解析 git blame --incremental 输出
	 *
	 * 格式示例: <pre>
	 * abc123... 1 1 5          ← commit hash, orig line, final line, num lines
	 * author John Doe          ← 作者
	 * author-time 1700000000   ← 时间戳
	 * summary Fix bug          ← 提交摘要
	 * </pre>
	 */
	private void parseIncrementalBlame(BufferedReader reader, FileBlameData blameData) throws Exception {
		// git blame --incremental 输出格式：
		// 同一个 commit 可能出现多次（每行一条记录），例如：
		// abc123... 1 1 5 ← commit 行：hash, orig-line, final-line, num-lines
		// author 张三
		// author-time 1700000000
		// summary fix bug
		// abc123... 5 5 5 ← 同一个 commit，另一行
		// author 张三
		// ...
		// 所以需要缓存已解析的 commit 信息，遇到重复 hash 直接复用

		Map<String, LineBlameInfo> commitCache = new HashMap<>();
		LineBlameInfo currentInfo = null;

		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			if (line.isEmpty())
				continue;

			// commit 行: <40-char-hex-hash> <orig-line> <final-line> [<num-lines>]
			if (line.length() > 41 && line.charAt(40) == ' ' && isHexHash(line.substring(0, 40))) {
				String hash = line.substring(0, 40);
				String[] parts = line.split("\\s+");
				int finalLine = Integer.parseInt(parts[2]) - 1; // 转为 0-based
				int lineCount = parts.length > 3 ? Integer.parseInt(parts[3]) : 1;

				currentInfo = commitCache.computeIfAbsent(hash, ignored -> new LineBlameInfo());
				blameData.setLines(finalLine, lineCount, currentInfo);
			}
			else if (line.startsWith("author ")) {
				String author = line.substring(7);
				// 尝试补全当前 hash 对应的行
				if (currentInfo != null) {
					currentInfo.author = author;
				}
			}
			else if (line.startsWith("author-time ")) {
				long timestamp = Long.parseLong(line.substring(12));
				String date = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
					.format(java.time.Instant.ofEpochSecond(timestamp).atZone(java.time.ZoneId.systemDefault()));
				if (currentInfo != null) {
					currentInfo.date = date;
				}
			}
			else if (line.startsWith("summary ")) {
				String subject = line.substring(8);
				if (currentInfo != null) {
					currentInfo.subject = subject;
				}
			}
		}
	}

	/**
	 * 判断字符串是否为40位十六进制 hash
	 */
	private boolean isHexHash(String s) {
		if (s.length() != 40)
			return false;
		for (int i = 0; i < 40; i++) {
			char c = s.charAt(i);
			if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
				return false;
			}
		}
		return true;
	}

	@Nullable
	private String findGitRoot(String filePath) {
		File dir = new File(filePath).getParentFile();
		while (dir != null) {
			if (new File(dir, ".git").exists()) {
				return dir.getAbsolutePath();
			}
			dir = dir.getParentFile();
		}
		return null;
	}

	@NotNull
	private GitRootInfo getGitRootInfo(@NotNull VirtualFile file) {
		return gitRootCache.computeIfAbsent(file.getPath(), ignored -> {
			String repoRoot = findGitRoot(file.getPath());
			if (repoRoot == null) {
				return GitRootInfo.notUnderGit();
			}
			File gitEntry = new File(repoRoot, ".git");
			File gitDir = resolveGitDir(repoRoot, gitEntry);
			if (gitDir == null) {
				return GitRootInfo.notUnderGit();
			}
			return new GitRootInfo(repoRoot, gitDir);
		});
	}

	@Nullable
	private File resolveGitDir(@NotNull String repoRoot, @NotNull File gitEntry) {
		if (gitEntry.isDirectory()) {
			return gitEntry;
		}
		if (gitEntry.isFile()) {
			try {
				String text = java.nio.file.Files.readString(gitEntry.toPath(), StandardCharsets.UTF_8).trim();
				String prefix = "gitdir:";
				if (text.startsWith(prefix)) {
					String gitDirPath = text.substring(prefix.length()).trim();
					Path path = Path.of(gitDirPath);
					if (!path.isAbsolute()) {
						path = Path.of(repoRoot).resolve(path).normalize();
					}
					File gitDir = path.toFile();
					if (gitDir.isDirectory()) {
						return gitDir;
					}
				}
			}
			catch (IOException e) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Failed to resolve git dir for " + repoRoot, e);
				}
			}
		}
		return null;
	}

	@Nullable
	private RepoState getRepoState(@NotNull VirtualFile file) {
		GitRootInfo gitRootInfo = getGitRootInfo(file);
		if (!gitRootInfo.isUnderGit()) {
			return null;
		}
		return new RepoState(gitRootInfo, readGitStateKey(gitRootInfo.gitDir));
	}

	@NotNull
	private String readGitStateKey(@NotNull File gitDir) {
		File headFile = new File(gitDir, "HEAD");
		try {
			String head = java.nio.file.Files.readString(headFile.toPath(), StandardCharsets.UTF_8).trim();
			String refPrefix = "ref:";
			if (head.startsWith(refPrefix)) {
				String refName = head.substring(refPrefix.length()).trim();
				String refValue = readRefValue(gitDir, refName);
				return head + "@" + refValue;
			}
			return head;
		}
		catch (IOException e) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Failed to read git HEAD from " + gitDir, e);
			}
			return "";
		}
	}

	@NotNull
	private String readRefValue(@NotNull File gitDir, @NotNull String refName) throws IOException {
		File looseRef = new File(gitDir, refName);
		if (looseRef.isFile()) {
			return java.nio.file.Files.readString(looseRef.toPath(), StandardCharsets.UTF_8).trim();
		}

		File packedRefs = new File(gitDir, "packed-refs");
		if (packedRefs.isFile()) {
			String targetSuffix = " " + refName;
			try (BufferedReader reader = java.nio.file.Files.newBufferedReader(packedRefs.toPath(),
					StandardCharsets.UTF_8)) {
				for (String line = reader.readLine(); line != null; line = reader.readLine()) {
					if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '^') {
						continue;
					}
					if (line.endsWith(targetSuffix)) {
						return line.substring(0, line.indexOf(' '));
					}
				}
			}
		}
		return "";
	}

	// ========== 样式 ==========

	@NotNull
	private TextAttributes getBlameTextAttributes() {
		EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
		TextAttributes attrs = scheme.getAttributes(INLINE_BLAME_KEY);
		if (attrs != null && attrs.getForegroundColor() != null) {
			return attrs;
		}
		// 默认灰色斜体
		Color grayColor = JBColor.GRAY;
		return new TextAttributes(grayColor, null, null, null, Font.ITALIC);
	}

	// ========== 内部数据结构 ==========

	/**
	 * 文件级 blame 数据（解析 git blame --incremental 的输出）
	 */
	private static class FileBlameData {

		final long modificationStamp;

		final String repoStateKey;

		/** 行号 -> blame 信息 */
		final Map<Integer, LineBlameInfo> lines = new ConcurrentHashMap<>();

		FileBlameData(long modificationStamp, @NotNull String repoStateKey) {
			this.modificationStamp = modificationStamp;
			this.repoStateKey = repoStateKey;
		}

		static FileBlameData empty(long modificationStamp, @NotNull String repoStateKey) {
			return new FileBlameData(modificationStamp, repoStateKey);
		}

		void setLines(int startLine, int lineCount, LineBlameInfo info) {
			for (int line = startLine; line < startLine + lineCount; line++) {
				lines.put(line, info);
			}
		}

		boolean isEmpty() {
			return lines.isEmpty();
		}

		@Nullable
		String getFormattedLine(int line) {
			LineBlameInfo info = lines.get(line);
			if (info == null)
				return null;
			return InlineBlameFormatter.format(info.author, info.date, info.subject);
		}

	}

	/**
	 * 单行 blame 信息（可变，支持增量填充）
	 */
	private static class LineBlameInfo {

		String author;

		String date;

		String subject;

		LineBlameInfo() {
		}

	}

	private static class GitRootInfo {

		@Nullable
		final String repoRoot;

		@Nullable
		final File gitDir;

		GitRootInfo(@NotNull String repoRoot, @NotNull File gitDir) {
			this.repoRoot = repoRoot;
			this.gitDir = gitDir;
		}

		private GitRootInfo() {
			this.repoRoot = null;
			this.gitDir = null;
		}

		static GitRootInfo notUnderGit() {
			return new GitRootInfo();
		}

		boolean isUnderGit() {
			return repoRoot != null && gitDir != null;
		}

	}

	private static class RepoState {

		final GitRootInfo gitRootInfo;

		final String stateKey;

		RepoState(@NotNull GitRootInfo gitRootInfo, @NotNull String stateKey) {
			this.gitRootInfo = gitRootInfo;
			this.stateKey = stateKey;
		}

	}

}
