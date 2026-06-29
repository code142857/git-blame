package com.jiangjinghong.git.blame.widget;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.jiangjinghong.git.blame.GitBlameService;
import com.jiangjinghong.git.blame.model.LineBlameInfo;
import com.jiangjinghong.git.blame.settings.GitBlameSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * 点击状态栏短 hash 时弹出的 commit 详情 popup。
 * <p>
 * 先用缓存的 {@link LineBlameInfo}（hash/作者/时间/summary）立即渲染，
 * 后台通过 {@link GitBlameService#fetchCommitMessage} 拉取完整 message body 补全。
 * popup 关闭（disposed）后不再更新，避免竞态。
 * </p>
 */
public final class CommitDetailPopup {

	private static final int COLUMNS = 60;

	private CommitDetailPopup() {
	}

	/**
	 * 在 {@code anchor} 组件下方弹出详情。
	 *
	 * @param file 当前编辑器文件，用于定位 git 仓库（为空则只显示缓存信息）
	 */
	public static void show(@NotNull Project project, @Nullable VirtualFile file, @NotNull LineBlameInfo info,
			@NotNull JComponent anchor) {
		GitBlameSettings settings = GitBlameSettings.getInstance();

		JBTextArea textArea = new JBTextArea(CommitDetailFormatter.format(info, null, settings));
		textArea.setEditable(false);
		textArea.setColumns(COLUMNS);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		textArea.setFont(JBFont.label());
		textArea.setCaretPosition(0);

		JBScrollPane scrollPane = new JBScrollPane(textArea);
		scrollPane.setPreferredSize(JBUI.size(520, 220));

		JBPopup popup = JBPopupFactory.getInstance()
				.createComponentPopupBuilder(scrollPane, textArea)
				.setTitle("Git Blame — " + shortHash(info.hash))
				.setResizable(true)
				.setMovable(true)
				.setRequestFocus(true)
				.createPopup();
		popup.showUnderneathOf(anchor);

		// 后台补完整 message body
		if (file != null && info.hash != null && !info.hash.isEmpty()) {
			GitBlameService.getInstance(project).fetchCommitMessage(project, file, info.hash, message -> {
				if (!popup.isDisposed() && message != null && !message.isEmpty()) {
					textArea.setText(CommitDetailFormatter.format(info, message, settings));
					textArea.setCaretPosition(0);
				}
			});
		}
	}

	@NotNull
	private static String shortHash(@Nullable String hash) {
		if (hash == null || hash.isEmpty()) {
			return "";
		}
		return hash.length() >= 8 ? hash.substring(0, 8) : hash;
	}
}
