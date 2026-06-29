package com.jiangjinghong.git.blame.widget;

import com.jiangjinghong.git.blame.model.LineBlameInfo;
import com.jiangjinghong.git.blame.settings.DateFormatMode;
import com.jiangjinghong.git.blame.settings.GitBlameSettings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 纯 JUnit 测试 {@link CommitDetailFormatter}。沿用 {@code InlineBlameFormatterTest} 的模式：
 * 直接 new {@link GitBlameSettings}（不走平台容器），设置 state 后传入。
 */
public class CommitDetailFormatterTest {

	private static final long EPOCH = 1700000000L; // 2023-11-14 22:13:20 UTC
	private static final String HASH = "0123456789abcdef0123456789abcdef01234567";

	@Test
	public void fullMessageOverridesSubjectAndIndentsEachLine() {
		GitBlameSettings s = newSettings(DateFormatMode.ISO_DATETIME);
		LineBlameInfo info = info(HASH, "John Doe", EPOCH, "Fix bug");
		String out = CommitDetailFormatter.format(info, "Fix bug\n\nBody line 1\nBody line 2", s);

		assertTrue("应含 commit hash 行: " + out, out.contains("commit " + HASH));
		assertTrue("应含 Author 行: " + out, out.contains("Author: John Doe"));
		assertTrue("应含 Date 行: " + out, out.contains("Date:   "));
		assertTrue("应含缩进后的 message: " + out, out.contains("\n    Fix bug"));
		assertTrue("空行也应缩进: " + out, out.contains("\n    \n"));
		assertTrue("body 行缩进: " + out, out.contains("    Body line 1"));
		assertFalse("不应回退到孤立的 subject 行", out.contains("Fix bug\nFix bug"));
	}

	@Test
	public void nullFullMessageFallsBackToSubject() {
		GitBlameSettings s = newSettings(DateFormatMode.ISO_DATE);
		LineBlameInfo info = info(HASH, "John Doe", EPOCH, "Fix bug");
		String out = CommitDetailFormatter.format(info, null, s);

		assertTrue("回退 subject 应缩进显示: " + out, out.contains("\n    Fix bug"));
		assertFalse(out.contains("    \n")); // 没有多余空行
	}

	@Test
	public void blankFullMessageFallsBackToSubject() {
		GitBlameSettings s = newSettings(DateFormatMode.ISO_DATE);
		LineBlameInfo info = info(HASH, "John Doe", EPOCH, "Fix bug");
		String out = CommitDetailFormatter.format(info, "   \n  ", s);
		assertTrue("空白 body 应回退 subject: " + out, out.contains("    Fix bug"));
	}

	@Test
	public void missingAuthorAndDateOmitThoseLines() {
		GitBlameSettings s = newSettings(DateFormatMode.ISO_DATE);
		LineBlameInfo info = info(HASH, null, 0L, "Fix bug");
		String out = CommitDetailFormatter.format(info, null, s);

		assertTrue("仍含 commit 行: " + out, out.contains("commit " + HASH));
		assertFalse("author 为空不应有 Author 行: " + out, out.contains("Author:"));
		assertFalse("time<=0 不应有 Date 行: " + out, out.contains("Date:"));
		assertTrue(out.contains("    Fix bug"));
	}

	@Test
	public void emptyMessageDropsMessageSection() {
		GitBlameSettings s = newSettings(DateFormatMode.ISO_DATE);
		LineBlameInfo info = info(HASH, "John Doe", EPOCH, null);
		String out = CommitDetailFormatter.format(info, null, s);

		assertTrue("仍含 commit 行: " + out, out.contains("commit " + HASH));
		assertFalse("无 message 时不应出现双换行段: " + out, out.contains("\n\n"));
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

	private static GitBlameSettings newSettings(DateFormatMode dateFormat) {
		GitBlameSettings s = new GitBlameSettings();
		GitBlameSettings.State state = s.getState();
		state.enabled = true;
		state.dateFormat = dateFormat.name();
		state.formatTemplate = "";
		return s;
	}
}
