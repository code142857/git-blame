package com.jiangjinghong.git.blame.model;

/**
 * 单行 blame 信息（可变，支持增量填充）。
 * <p>
 * 在 {@code parseIncrementalBlame} 中按 commit hash 复用同一个实例，逐步填入字段。
 * 解析完成后发布到 {@link FileBlameData#getLine(int)} 供 EDT 读取。
 * </p>
 */
public class LineBlameInfo {

	/** 40 位 commit hash */
	public String hash;

	public String author;

	/** 原始时间戳（epoch seconds），由 formatter 按 settings 格式化 */
	public long authorTime;

	public String subject;

	public LineBlameInfo() {
	}
}
