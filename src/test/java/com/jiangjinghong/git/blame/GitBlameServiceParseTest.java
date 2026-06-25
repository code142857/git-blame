package com.jiangjinghong.git.blame;

import com.jiangjinghong.git.blame.model.FileBlameData;
import com.jiangjinghong.git.blame.model.LineBlameInfo;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.StringReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * 纯 JUnit 测试 {@link GitBlameService#parseIncrementalBlame(BufferedReader, FileBlameData)}。
 * 喂入样例 {@code git blame --incremental} 输出，验证行映射、字段填充与 commit 复用。
 */
public class GitBlameServiceParseTest {

	private static final String HASH_A = "1111111111111111111111111111111111111111"; // 40 hex
	private static final String HASH_B = "2222222222222222222222222222222222222222";

	@Test
	public void parsesLinesAndFillsFields() throws Exception {
		String output = String.join("\n",
				HASH_A + " 1 1 1",
				"author John Doe",
				"author-time 1700000000",
				"summary Fix bug",
				HASH_A + " 2 2 1",
				"author John Doe",
				"author-time 1700000000",
				"summary Fix bug",
				HASH_B + " 3 3 1",
				"author Jane Smith",
				"author-time 1710000000",
				"summary Add feature",
				"");

		FileBlameData data = new FileBlameData(1L, "key");
		try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
			GitBlameService.parseIncrementalBlame(reader, data);
		}

		// 第 1 行（0-based 0）
		LineBlameInfo line0 = data.getLine(0);
		assertNotNull(line0);
		assertEquals(HASH_A, line0.hash);
		assertEquals("John Doe", line0.author);
		assertEquals(1700000000L, line0.authorTime);
		assertEquals("Fix bug", line0.subject);

		// 第 2 行复用同一 commit 的 LineBlameInfo
		LineBlameInfo line1 = data.getLine(1);
		assertNotNull(line1);
		assertSame("同一 commit 应复用 LineBlameInfo 实例", line0, line1);

		// 第 3 行是新 commit
		LineBlameInfo line2 = data.getLine(2);
		assertNotNull(line2);
		assertEquals(HASH_B, line2.hash);
		assertEquals("Jane Smith", line2.author);
		assertEquals(1710000000L, line2.authorTime);
		assertEquals("Add feature", line2.subject);

		// 第 4 行不存在
		assertNull(data.getLine(3));
	}

	@Test
	public void multiLineCommitRangeMapped() throws Exception {
		// <hash> <orig> <final> <num-lines>：final=2，num-lines=3 → 行 1,2,3 (0-based)
		String output = String.join("\n",
				HASH_A + " 5 2 3",
				"author John Doe",
				"author-time 1700000000",
				"summary Big commit",
				"");

		FileBlameData data = new FileBlameData(1L, "key");
		try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
			GitBlameService.parseIncrementalBlame(reader, data);
		}

		LineBlameInfo line1 = data.getLine(1);
		LineBlameInfo line2 = data.getLine(2);
		LineBlameInfo line3 = data.getLine(3);
		assertNotNull(line1);
		assertNotNull(line2);
		assertNotNull(line3);
		assertSame(line1, line2);
		assertSame(line2, line3);
		assertEquals("Big commit", line1.subject);
		assertNull(data.getLine(0));
	}

	@Test
	public void isHexHashValidates40HexChars() {
		assertEquals(true, GitBlameService.isHexHash("1111111111111111111111111111111111111111"));
		assertEquals(false, GitBlameService.isHexHash("short"));
		assertEquals(false, GitBlameService.isHexHash("111111111111111111111111111111111111111g")); // g 不是 hex
		assertEquals(false, GitBlameService.isHexHash("")); // 空
	}
}
