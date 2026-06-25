package com.jiangjinghong.git.blame.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件级 blame 数据（解析 {@code git blame --incremental} 的输出）。
 * <p>
 * 缓存有效性由 {@link #modificationStamp}（{@code VirtualFile#getModificationStamp}）
 * 与 {@link #repoStateKey}（{@code HEAD} + ref 值）共同决定。
 * </p>
 */
public class FileBlameData {

	public final long modificationStamp;

	@NotNull
	public final String repoStateKey;

	/** 行号 -> blame 信息（0-based） */
	private final Map<Integer, LineBlameInfo> lines = new ConcurrentHashMap<>();

	public FileBlameData(long modificationStamp, @NotNull String repoStateKey) {
		this.modificationStamp = modificationStamp;
		this.repoStateKey = repoStateKey;
	}

	public static FileBlameData empty(long modificationStamp, @NotNull String repoStateKey) {
		return new FileBlameData(modificationStamp, repoStateKey);
	}

	public void setLines(int startLine, int lineCount, @NotNull LineBlameInfo info) {
		for (int line = startLine; line < startLine + lineCount; line++) {
			lines.put(line, info);
		}
	}

	public boolean isEmpty() {
		return lines.isEmpty();
	}

	@Nullable
	public LineBlameInfo getLine(int line) {
		return lines.get(line);
	}
}
