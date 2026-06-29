package com.jiangjinghong.git.blame.widget;

import com.jiangjinghong.git.blame.model.LineBlameInfo;
import com.jiangjinghong.git.blame.settings.GitBlameSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 格式化 commit 详情多行文本，供 {@link CommitDetailPopup} 显示。
 * <p>
 * 结构仿 {@code git show}（不含 diff）：
 * <pre>
 * commit &lt;hash&gt;
 * Author: &lt;author&gt;
 * Date:   &lt;date&gt;
 *
 *     &lt;message&gt;
 * </pre>
 * 纯函数，便于单测；日期格式沿用 {@link GitBlameSettings#getDateFormat()}。
 * </p>
 */
public final class CommitDetailFormatter {

	private CommitDetailFormatter() {
	}

	/**
	 * @param fullMessage 完整 commit message（{@code git show -s --format=%B}）；
	 *                    为 {@code null}/空时回退 {@code info.subject}
	 */
	@NotNull
	public static String format(@NotNull LineBlameInfo info, @Nullable String fullMessage,
			@NotNull GitBlameSettings settings) {
		StringBuilder sb = new StringBuilder();

		if (info.hash != null && !info.hash.isEmpty()) {
			sb.append("commit ").append(info.hash);
		}
		if (info.author != null && !info.author.isEmpty()) {
			appendLine(sb).append("Author: ").append(info.author);
		}
		if (info.authorTime > 0) {
			String date = settings.getDateFormat().format(info.authorTime);
			if (!date.isEmpty()) {
				appendLine(sb).append("Date:   ").append(date);
			}
		}

		String message = pickMessage(fullMessage, info.subject);
		if (!message.isEmpty()) {
			sb.append("\n\n");
			// 每行缩进 4 空格（git show 风格）
			for (String line : message.split("\n", -1)) {
				sb.append("    ").append(line).append('\n');
			}
			sb.deleteCharAt(sb.length() - 1); // 去掉末尾多余换行
		}
		return sb.toString();
	}

	@NotNull
	private static StringBuilder appendLine(@NotNull StringBuilder sb) {
		if (sb.length() > 0) {
			sb.append('\n');
		}
		return sb;
	}

	@NotNull
	private static String pickMessage(@Nullable String fullMessage, @Nullable String subject) {
		if (fullMessage != null) {
			String trimmed = fullMessage.trim();
			if (!trimmed.isEmpty()) {
				return trimmed;
			}
		}
		return subject == null ? "" : subject.trim();
	}
}
