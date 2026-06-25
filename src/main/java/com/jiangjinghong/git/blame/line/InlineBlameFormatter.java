package com.jiangjinghong.git.blame.line;

import com.jiangjinghong.git.blame.model.LineBlameInfo;
import com.jiangjinghong.git.blame.settings.GitBlameSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 格式化 Git Blame 行内显示文本。settings-aware：读取 {@link GitBlameSettings}
 * 决定字段、日期格式与是否使用自定义模板。
 */
public final class InlineBlameFormatter {

	private static final int SUBJECT_MAX = 60;

	private InlineBlameFormatter() {
	}

	/**
	 * 按 settings 格式化 blame 信息。返回 {@code null} 表示应隐藏（开关关闭）。
	 */
	@Nullable
	public static String format(@NotNull LineBlameInfo info, @NotNull GitBlameSettings settings) {
		if (!settings.isEnabled()) {
			return null;
		}
		String template = settings.getFormatTemplate();
		if (!template.isEmpty()) {
			return formatByTemplate(info, settings, template);
		}
		return formatByFields(info, settings);
	}

	@NotNull
	private static String formatByTemplate(@NotNull LineBlameInfo info, @NotNull GitBlameSettings settings,
			@NotNull String template) {
		String author = info.author == null ? "" : info.author;
		String date = settings.getDateFormat().format(info.authorTime);
		String subject = truncate(info.subject);
		return "  " + template
				.replace("{author}", author)
				.replace("{date}", date)
				.replace("{subject}", subject);
	}

	@NotNull
	private static String formatByFields(@NotNull LineBlameInfo info, @NotNull GitBlameSettings settings) {
		StringBuilder sb = new StringBuilder("  ");

		boolean hasAuthor = settings.isShowAuthor() && isNotBlank(info.author);
		String dateStr = settings.isShowDate() ? settings.getDateFormat().format(info.authorTime) : null;
		boolean hasDate = isNotBlank(dateStr);
		boolean hasSubject = settings.isShowSubject() && isNotBlank(info.subject);

		if (hasAuthor && hasDate) {
			sb.append(info.author).append(", ").append(dateStr);
		}
		else if (hasAuthor) {
			sb.append(info.author);
		}
		else if (hasDate) {
			sb.append(dateStr);
		}

		if (hasSubject) {
			if (hasAuthor || hasDate) {
				sb.append(" \u2022 "); // " • "
			}
			sb.append(truncate(info.subject));
		}

		return sb.toString();
	}

	@NotNull
	private static String truncate(@Nullable String s) {
		if (s == null) {
			return "";
		}
		return s.length() > SUBJECT_MAX ? s.substring(0, SUBJECT_MAX - 3) + "..." : s;
	}

	private static boolean isNotBlank(@Nullable String s) {
		return s != null && !s.trim().isEmpty();
	}
}
