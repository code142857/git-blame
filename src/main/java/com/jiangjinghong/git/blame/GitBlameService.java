package com.jiangjinghong.git.blame;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.jiangjinghong.git.blame.line.InlineBlameFormatter;
import com.jiangjinghong.git.blame.model.FileBlameData;
import com.jiangjinghong.git.blame.model.GitRootInfo;
import com.jiangjinghong.git.blame.model.LineBlameInfo;
import com.jiangjinghong.git.blame.model.RepoState;
import com.jiangjinghong.git.blame.settings.GitBlameSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 项目级 blame 服务：持有文件级 blame 缓存、git 仓库状态推导与异步加载逻辑。
 * <p>
 * 抽取自 {@code GitLinePainter}，让缓存正确按 project 隔离，并为状态栏 widget
 * 提供 blame 数据访问入口。非持久化——随 project 释放。
 * </p>
 */
public class GitBlameService {

	private static final Logger LOG = Logger.getInstance(GitBlameService.class);

	/** 文件级 git blame 输出缓存: filePath -> FileBlameData */
	private final Map<String, FileBlameData> fileBlameCache = new ConcurrentHashMap<>();

	private final Map<String, GitRootInfo> gitRootCache = new ConcurrentHashMap<>();

	private final Set<String> loadingFiles = ConcurrentHashMap.newKeySet();

	@NotNull
	public static GitBlameService getInstance(@NotNull Project project) {
		return project.getService(GitBlameService.class);
	}

	// ========== 公共 API ==========

	/**
	 * 返回光标行的格式化 blame 文本（供 {@link com.jiangjinghong.git.blame.line.GitLinePainter} 使用）。
	 * 缓存未命中时触发异步加载并返回 {@code null}；加载完成后重绘编辑器。
	 */
	@Nullable
	public String getFormattedBlameLine(@NotNull Project project, @NotNull VirtualFile file, int lineIndex) {
		FileBlameData fileBlame = getOrComputeFileBlame(project, file);
		if (fileBlame == null) {
			return null;
		}
		LineBlameInfo info = fileBlame.getLine(lineIndex);
		if (info == null) {
			return null;
		}
		return InlineBlameFormatter.format(info, GitBlameSettings.getInstance());
	}

	/**
	 * 返回光标行的 {@link LineBlameInfo}（含 hash），供状态栏 widget 使用。
	 * 缓存未命中时触发异步加载并返回 {@code null}。
	 */
	@Nullable
	public LineBlameInfo getBlameInfo(@NotNull Project project, @NotNull VirtualFile file, int lineIndex) {
		FileBlameData fileBlame = getOrComputeFileBlame(project, file);
		if (fileBlame == null) {
			return null;
		}
		return fileBlame.getLine(lineIndex);
	}

	/**
	 * 异步拉取某 commit 的完整 message body（{@code git show -s --format=%B}）。
	 * 后台执行，{@code invokeLater} 在 EDT 回调；project 释放后不回调。
	 *
	 * @param file 用于定位 git 仓库根（与 blame 共用 {@link GitRootInfo} 缓存）
	 */
	public void fetchCommitMessage(@NotNull Project project, @NotNull VirtualFile file, @NotNull String hash,
			@NotNull Consumer<String> callback) {
		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			String message = readCommitMessage(file, hash);
			ApplicationManager.getApplication().invokeLater(() -> {
				if (!project.isDisposed()) {
					callback.accept(message);
				}
			});
		});
	}

	/**
	 * 设置变更后重绘所有打开的编辑器。格式在读取时计算，无需失效 blame 缓存。
	 */
	public void onSettingsChanged() {
		ApplicationManager.getApplication().invokeLater(() -> {
			for (Project project : ProjectManager.getInstance().getOpenProjects()) {
				if (project.isDisposed()) {
					continue;
				}
				FileEditorManager fem = FileEditorManager.getInstance(project);
				for (var fileEditor : fem.getAllEditors()) {
					if (fileEditor instanceof TextEditor textEditor) {
						textEditor.getEditor().getComponent().repaint();
					}
				}
			}
		});
	}

	// ========== 条件检查 ==========

	private boolean shouldShow(@NotNull Project project, @NotNull VirtualFile file) {
		if (com.intellij.openapi.project.DumbService.isDumb(project)) {
			return false;
		}
		if (file.isDirectory()) {
			return false;
		}
		return getGitRootInfo(file).isUnderGit();
	}

	// ========== Blame 数据获取 ==========

	@Nullable
	private FileBlameData getOrComputeFileBlame(@NotNull Project project, @NotNull VirtualFile file) {
		if (!shouldShow(project, file)) {
			return null;
		}
		RepoState repoState = getRepoState(file);
		if (repoState == null) {
			return null;
		}

		String filePath = file.getPath();
		FileBlameData cached = fileBlameCache.get(filePath);

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

		String relativePath = file.getPath().substring(repoState.gitRootInfo.repoRoot.length() + 1);

		try {
			ProcessBuilder pb = new ProcessBuilder("git", "blame", "--incremental", "-l", "-t", "-w", "HEAD", "--",
					relativePath);
			pb.directory(new File(repoState.gitRootInfo.repoRoot));
			pb.redirectErrorStream(true);
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

	@NotNull
	private String readCommitMessage(@NotNull VirtualFile file, @NotNull String hash) {
		GitRootInfo root = getGitRootInfo(file);
		if (!root.isUnderGit() || root.repoRoot == null) {
			return "";
		}
		try {
			ProcessBuilder pb = new ProcessBuilder("git", "show", "-s", "--format=%B", hash);
			pb.directory(new File(root.repoRoot));
			pb.redirectErrorStream(true);
			pb.environment().put("GIT_TERMINAL_PROMPT", "0");

			Process process = pb.start();
			StringBuilder sb = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (sb.length() > 0) {
						sb.append('\n');
					}
					sb.append(line);
				}
			}
			process.waitFor();
			return sb.toString().trim();
		}
		catch (Exception e) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("git show failed for commit " + hash, e);
			}
			return "";
		}
	}

	/**
	 * 解析 {@code git blame --incremental} 输出，填充 {@code blameData}。
	 * <p>
	 * 同一个 commit 可能跨多行出现；按 hash 缓存 {@link LineBlameInfo} 复用。
	 * package-private 以便单测同包访问。
	 * </p>
	 */
	static void parseIncrementalBlame(@NotNull BufferedReader reader, @NotNull FileBlameData blameData)
			throws Exception {
		Map<String, LineBlameInfo> commitCache = new HashMap<>();
		LineBlameInfo currentInfo = null;

		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			if (line.isEmpty()) {
				continue;
			}

			// commit 行: <40-char-hex-hash> <orig-line> <final-line> [<num-lines>]
			if (line.length() > 41 && line.charAt(40) == ' ' && isHexHash(line.substring(0, 40))) {
				String hash = line.substring(0, 40);
				String[] parts = line.split("\\s+");
				int finalLine = Integer.parseInt(parts[2]) - 1; // 转为 0-based
				int lineCount = parts.length > 3 ? Integer.parseInt(parts[3]) : 1;

				currentInfo = commitCache.computeIfAbsent(hash, ignored -> {
					LineBlameInfo info = new LineBlameInfo();
					info.hash = hash;
					return info;
				});
				blameData.setLines(finalLine, lineCount, currentInfo);
			}
			else if (line.startsWith("author ")) {
				if (currentInfo != null) {
					currentInfo.author = line.substring(7);
				}
			}
			else if (line.startsWith("author-time ")) {
				if (currentInfo != null) {
					currentInfo.authorTime = Long.parseLong(line.substring(12));
				}
			}
			else if (line.startsWith("summary ")) {
				if (currentInfo != null) {
					currentInfo.subject = line.substring(8);
				}
			}
		}
	}

	/**
	 * 判断字符串是否为 40 位十六进制 hash。
	 */
	static boolean isHexHash(@NotNull String s) {
		if (s.length() != 40) {
			return false;
		}
		for (int i = 0; i < 40; i++) {
			char c = s.charAt(i);
			if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
				return false;
			}
		}
		return true;
	}

	// ========== Git 仓库状态推导 ==========

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
			File commonDir = resolveCommonDir(gitDir);
			return new GitRootInfo(repoRoot, gitDir, commonDir);
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

	/**
	 * 解析 worktree 的 common dir。
	 * <p>
	 * worktree 的 {@code <gitDir>/commondir} 文件内容为指向主仓库 {@code .git} 的相对路径
	 * （如 {@code ../..}）。ref 与 packed-refs 存放在 common dir；普通仓库与 submodule
	 * 没有该文件，回退为 {@code gitDir} 本身。
	 * </p>
	 */
	@NotNull
	private File resolveCommonDir(@NotNull File gitDir) {
		File commondirFile = new File(gitDir, "commondir");
		if (!commondirFile.isFile()) {
			return gitDir;
		}
		try {
			String text = java.nio.file.Files.readString(commondirFile.toPath(), StandardCharsets.UTF_8).trim();
			Path path = Path.of(text);
			if (!path.isAbsolute()) {
				path = gitDir.toPath().resolve(path).normalize();
			}
			File commonDir = path.toFile();
			if (commonDir.isDirectory()) {
				return commonDir;
			}
		}
		catch (IOException e) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Failed to resolve common dir for " + gitDir, e);
			}
		}
		return gitDir;
	}

	@Nullable
	private RepoState getRepoState(@NotNull VirtualFile file) {
		GitRootInfo gitRootInfo = getGitRootInfo(file);
		if (!gitRootInfo.isUnderGit()) {
			return null;
		}
		File commonDir = gitRootInfo.commonDir != null ? gitRootInfo.commonDir : gitRootInfo.gitDir;
		return new RepoState(gitRootInfo, readGitStateKey(gitRootInfo.gitDir, commonDir));
	}

	@NotNull
	private String readGitStateKey(@NotNull File gitDir, @NotNull File commonDir) {
		File headFile = new File(gitDir, "HEAD");
		try {
			String head = java.nio.file.Files.readString(headFile.toPath(), StandardCharsets.UTF_8).trim();
			String refPrefix = "ref:";
			if (head.startsWith(refPrefix)) {
				String refName = head.substring(refPrefix.length()).trim();
				String refValue = readRefValue(commonDir, refName);
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
	private String readRefValue(@NotNull File commonDir, @NotNull String refName) throws IOException {
		File looseRef = new File(commonDir, refName);
		if (looseRef.isFile()) {
			return java.nio.file.Files.readString(looseRef.toPath(), StandardCharsets.UTF_8).trim();
		}

		File packedRefs = new File(commonDir, "packed-refs");
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
}
