package com.jiangjinghong.git.blame.line;

import com.jiangjinghong.git.blame.model.LineBlameInfo;
import com.jiangjinghong.git.blame.settings.DateFormatMode;
import com.jiangjinghong.git.blame.settings.GitBlameSettings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 纯 JUnit 测试 {@link InlineBlameFormatter}。直接构造 {@link GitBlameSettings} 实例
 * （不经过平台 service 容器），设置 state 后传入 formatter。
 */
public class InlineBlameFormatterTest {

	private static final long EPOCH = 1700000000L; // 2023-11-14 22:13:20 UTC

	@Test
	public void disabledReturnsNull() {
		GitBlameSettings s = newSettings(false, true, true, true, DateFormatMode.ISO_DATE, "");
		LineBlameInfo info = info("aaa", "John", EPOCH, "Fix bug");
		assertNull(InlineBlameFormatter.format(info, s));
	}

	@Test
	public void allFieldsOnNoTemplate() {
		GitBlameSettings s = newSettings(true, true, true, true, DateFormatMode.ISO_DATE, "");
		LineBlameInfo info = info("aaa", "John", EPOCH, "Fix bug");
		String out = InlineBlameFormatter.format(info, s);
		assertNotNull(out);
		assertTrue("应以前缀两空格开头: " + out, out.startsWith("  "));
		assertTrue("应包含作者: " + out, out.contains("John"));
		assertTrue("应包含分隔符: " + out, out.contains(" • "));
		assertTrue("应包含摘要: " + out, out.contains("Fix bug"));
		assertTrue("应包含日期(yyyy-MM-dd): " + out, out.matches(".*\\d{4}-\\d{2}-\\d{2}.*"));
	}

	@Test
	public void authorOnly() {
		GitBlameSettings s = newSettings(true, true, false, false, DateFormatMode.ISO_DATE, "");
		LineBlameInfo info = info("aaa", "John", EPOCH, "Fix bug");
		assertEquals("  John", InlineBlameFormatter.format(info, s));
	}

	@Test
	public void subjectOnly() {
		GitBlameSettings s = newSettings(true, false, false, true, DateFormatMode.ISO_DATE, "");
		LineBlameInfo info = info("aaa", "John", EPOCH, "Fix bug");
		assertEquals("  Fix bug", InlineBlameFormatter.format(info, s));
	}

	@Test
	public void noFieldsReturnsJustPrefix() {
		GitBlameSettings s = newSettings(true, false, false, false, DateFormatMode.ISO_DATE, "");
		LineBlameInfo info = info("aaa", "John", EPOCH, "Fix bug");
		assertEquals("  ", InlineBlameFormatter.format(info, s));
	}

	@Test
	public void templateOverridesFields() {
		GitBlameSettings s = newSettings(true, false, false, false, DateFormatMode.ISO_DATE,
				"{author} @ {date} [{subject}]");
		LineBlameInfo info = info("aaa", "John", EPOCH, "Fix bug");
		String out = InlineBlameFormatter.format(info, s);
		assertNotNull(out);
		assertTrue(out.startsWith("  "));
		assertTrue("模板应渲染作者: " + out, out.contains("John @ "));
		assertTrue("模板应渲染摘要: " + out, out.contains("[Fix bug]"));
		assertTrue("模板应渲染日期: " + out, out.matches(".*\\d{4}-\\d{2}-\\d{2}.*"));
	}

	@Test
	public void templateWithoutDatePlaceholder() {
		GitBlameSettings s = newSettings(true, true, true, true, DateFormatMode.ISO_DATE,
				"{author} - {subject}");
		LineBlameInfo info = info("aaa", "John", EPOCH, "Fix bug");
		assertEquals("  John - Fix bug", InlineBlameFormatter.format(info, s));
	}

	@Test
	public void subjectTruncatedInFieldMode() {
		GitBlameSettings s = newSettings(true, false, false, true, DateFormatMode.ISO_DATE, "");
		String longSubject = "x".repeat(70);
		String out = InlineBlameFormatter.format(info("aaa", "John", EPOCH, longSubject), s);
		assertNotNull(out);
		// 截断后应为 57 个 x 加 "..."，总长 60
		assertTrue("应包含截断标记: " + out, out.contains("..."));
		assertTrue("摘要部分应为 60 字符: " + out, out.endsWith("x".repeat(57) + "..."));
	}

	@Test
	public void subjectTruncatedInTemplateMode() {
		GitBlameSettings s = newSettings(true, true, true, true, DateFormatMode.ISO_DATE, "{subject}");
		String longSubject = "y".repeat(80);
		String out = InlineBlameFormatter.format(info("aaa", "John", EPOCH, longSubject), s);
		assertNotNull(out);
		assertTrue("模板模式也应截断: " + out, out.endsWith("y".repeat(57) + "..."));
	}

	@Test
	public void relativeDateFormatContainsAgo() {
		GitBlameSettings s = newSettings(true, false, true, false, DateFormatMode.RELATIVE, "");
		LineBlameInfo info = info("aaa", "John", EPOCH, "Fix bug");
		String out = InlineBlameFormatter.format(info, s);
		assertNotNull(out);
		assertTrue("相对时间应以 ago 结尾: " + out, out.trim().endsWith("ago"));
	}

	@Test
	public void isoDatetimeFormat() {
		GitBlameSettings s = newSettings(true, false, true, false, DateFormatMode.ISO_DATETIME, "");
		LineBlameInfo info = info("aaa", "John", EPOCH, "Fix bug");
		String out = InlineBlameFormatter.format(info, s);
		assertNotNull(out);
		assertTrue("应包含 yyyy-MM-dd HH:mm: " + out, out.matches(".*\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}.*"));
	}

	@Test
	public void isoDatetimeWithSecondsFormat() {
		GitBlameSettings s = newSettings(true, false, true, false, DateFormatMode.ISO_DATETIME_WITH_SECONDS, "");
		LineBlameInfo info = info("aaa", "John", EPOCH, "Fix bug");
		String out = InlineBlameFormatter.format(info, s);
		assertNotNull(out);
		assertTrue("应包含 yyyy-MM-dd HH:mm:ss: " + out,
				out.matches(".*\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.*"));
	}

	@Test
	public void nullAuthorAndSubjectHandled() {
		GitBlameSettings s = newSettings(true, true, false, true, DateFormatMode.ISO_DATE, "");
		LineBlameInfo info = info("aaa", null, EPOCH, null);
		String out = InlineBlameFormatter.format(info, s);
		assertEquals("  ", out);
	}

	// ========== helpers ==========

	private static LineBlameInfo info(String hash, String author, long authorTime, String subject) {
		LineBlameInfo i = new LineBlameInfo();
		i.hash = hash;
		i.author = author;
		i.authorTime = authorTime;
		i.subject = subject;
		return i;
	}

	private static GitBlameSettings newSettings(boolean enabled, boolean showAuthor, boolean showDate,
			boolean showSubject, DateFormatMode dateFormat, String template) {
		GitBlameSettings s = new GitBlameSettings();
		GitBlameSettings.State state = s.getState();
		state.enabled = enabled;
		state.showAuthor = showAuthor;
		state.showDate = showDate;
		state.showSubject = showSubject;
		state.dateFormat = dateFormat.name();
		state.formatTemplate = template;
		return s;
	}
}
