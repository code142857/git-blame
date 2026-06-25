package org.jetbrains.plugins.template.line;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 格式化 Git Blame 行内显示文本
 */
public final class InlineBlameFormatter {

    private InlineBlameFormatter() {}

    /**
     * 格式化 blame 信息为行内显示文本
     *
     * @param author  作者名
     * @param date    日期字符串
     * @param subject 提交信息摘要
     * @return 格式化后的文本，如 " 张三, 2024-01-15 10:30 • fix bug"
     */
    @NotNull
    public static String format(@Nullable String author, @Nullable String date, @Nullable String subject) {
        StringBuilder sb = new StringBuilder();
        sb.append("  "); // 前缀空格

        boolean hasAuthor = isNotBlank(author);
        boolean hasDate = isNotBlank(date);
        boolean hasSubject = isNotBlank(subject);

        if (hasAuthor && hasDate) {
            sb.append(author).append(", ").append(date);
        } else if (hasAuthor) {
            sb.append(author);
        } else if (hasDate) {
            sb.append(date);
        }

        if (hasSubject) {
            if (hasAuthor || hasDate) {
                sb.append(" \u2022 "); // " • "
            }
            // 截断过长的 subject
            String trimmedSubject = subject.length() > 60
                    ? subject.substring(0, 57) + "..."
                    : subject;
            sb.append(trimmedSubject);
        }

        return sb.toString();
    }

    private static boolean isNotBlank(@Nullable String s) {
        return s != null && !s.trim().isEmpty();
    }
}
