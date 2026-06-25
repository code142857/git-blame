package com.jiangjinghong.git.blame.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Git 仓库根信息。
 * <p>
 * <ul>
 *   <li>{@code repoRoot}：工作区根目录（运行 {@code git blame} 时的 cwd）。</li>
 *   <li>{@code gitDir}：{@code .git} 目录，或 worktree/submodule 的 gitdir 指针目标
 *       （per-worktree 的 {@code HEAD} 在此读取）。</li>
 *   <li>{@code commonDir}：共享 git 目录，ref 与 packed-refs 存放处。
 *       普通仓库与 submodule 等于 {@code gitDir}；worktree 指向主仓库的 {@code .git}
 *       （由 {@code <gitDir>/commondir} 指明）。</li>
 * </ul>
 * </p>
 */
public class GitRootInfo {

	@Nullable
	public final String repoRoot;

	@Nullable
	public final File gitDir;

	@Nullable
	public final File commonDir;

	public GitRootInfo(@NotNull String repoRoot, @NotNull File gitDir, @NotNull File commonDir) {
		this.repoRoot = repoRoot;
		this.gitDir = gitDir;
		this.commonDir = commonDir;
	}

	private GitRootInfo() {
		this.repoRoot = null;
		this.gitDir = null;
		this.commonDir = null;
	}

	public static GitRootInfo notUnderGit() {
		return new GitRootInfo();
	}

	public boolean isUnderGit() {
		return repoRoot != null && gitDir != null;
	}
}
