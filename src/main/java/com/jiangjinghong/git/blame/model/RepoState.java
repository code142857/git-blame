package com.jiangjinghong.git.blame.model;

import org.jetbrains.annotations.NotNull;

/**
 * 仓库状态快照，用作 blame 缓存的失效键之一。
 * <p>
 * {@code stateKey} 由 {@code HEAD} 内容与其指向的 ref 值组合而成，
 * 当分支切换或 HEAD 变化时失效缓存。
 * </p>
 */
public class RepoState {

	@NotNull
	public final GitRootInfo gitRootInfo;

	@NotNull
	public final String stateKey;

	public RepoState(@NotNull GitRootInfo gitRootInfo, @NotNull String stateKey) {
		this.gitRootInfo = gitRootInfo;
		this.stateKey = stateKey;
	}
}
