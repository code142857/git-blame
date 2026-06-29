package com.jiangjinghong.git.blame.widget;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.jiangjinghong.git.blame.GitBlameService;
import com.jiangjinghong.git.blame.model.LineBlameInfo;
import com.jiangjinghong.git.blame.settings.GitBlameSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 状态栏 widget：显示光标行 commit 的短 hash。点击弹出 commit 详情（{@link CommitDetailPopup}）。
 * <p>
 * 以 {@link CustomStatusBarWidget} 方式提供自定义 {@link JLabel} 组件，自行监听点击；
 * 光标移动通过 {@link EditorEventMulticaster} 的 {@link CaretListener} 跟踪，更新 label 文本与 tooltip。
 * </p>
 */
public final class GitBlameWidget extends EditorBasedWidget implements CustomStatusBarWidget {

	private static final int SHORT_HASH_LEN = 8;

	/** 与行内 blame 一致的灰色（亮/暗主题分别取色） */
	private static final JBColor BLAME_GRAY = new JBColor(new Color(0x787878), new Color(0x909090));

	private final CaretListener caretListener = new CaretListener() {
		@Override
		public void caretPositionChanged(@NotNull CaretEvent e) {
			if (isOurEditor(e.getEditor())) {
				refresh();
			}
		}
	};

	private final JLabel label;

	public GitBlameWidget(@NotNull Project project) {
		super(project);
		label = new JLabel();
		label.setFont(JBFont.label());
		label.setForeground(BLAME_GRAY);
		label.setBorder(JBUI.Borders.empty(0, 6));
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(@NotNull MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON1) {
					showDetail();
				}
			}
		});
		refresh();
	}

	@Override
	@NotNull
	public String ID() {
		return GitBlameWidgetFactory.WIDGET_ID;
	}

	@Override
	public void install(@NotNull StatusBar statusBar) {
		super.install(statusBar);
		EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
		multicaster.addCaretListener(caretListener, this);
	}

	@Override
	@Nullable
	public JComponent getComponent() {
		return label;
	}

	@Override
	@Nullable
	public StatusBarWidget.WidgetPresentation getPresentation() {
		// 使用 CustomStatusBarWidget 自定义组件，不提供 presentation
		return null;
	}

	// ========== 详情 popup ==========

	private void showDetail() {
		Project project = getProject();
		if (project == null || project.isDisposed()) {
			return;
		}
		LineBlameInfo info = currentBlameInfo();
		if (info == null || info.hash == null || info.hash.isEmpty()) {
			return;
		}
		VirtualFile file = currentFile();
		if (file == null) {
			return;
		}
		CommitDetailPopup.show(project, file, info, label);
	}

	// ========== 刷新 ==========

	private void refresh() {
		String shortHash = computeShortHash();
		label.setText(shortHash.isEmpty() ? "" : " " + shortHash + " ");
		label.setToolTipText(buildTooltip());
		label.setVisible(!shortHash.isEmpty());
	}

	@NotNull
	private String computeShortHash() {
		if (!GitBlameSettings.getInstance().isEnabled()) {
			return "";
		}
		LineBlameInfo info = currentBlameInfo();
		if (info == null || info.hash == null || info.hash.length() < SHORT_HASH_LEN) {
			return "";
		}
		return info.hash.substring(0, SHORT_HASH_LEN);
	}

	@Nullable
	private String buildTooltip() {
		LineBlameInfo info = currentBlameInfo();
		if (info == null || info.hash == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder(info.hash);
		if (info.author != null && !info.author.isEmpty()) {
			sb.append("  ").append(info.author);
		}
		if (info.authorTime > 0) {
			sb.append("  ").append(GitBlameSettings.getInstance().getDateFormat().format(info.authorTime));
		}
		if (info.subject != null && !info.subject.isEmpty()) {
			sb.append("  •  ").append(info.subject);
		}
		return sb.toString();
	}

	// ========== 编辑器工具 ==========

	@Nullable
	private LineBlameInfo currentBlameInfo() {
		Project project = getProject();
		if (project == null || project.isDisposed()) {
			return null;
		}
		Editor editor = getEditor();
		if (editor == null) {
			return null;
		}
		VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
		if (file == null) {
			return null;
		}
		int line = editor.getCaretModel().getLogicalPosition().line;
		return GitBlameService.getInstance(project).getBlameInfo(project, file, line);
	}

	@Nullable
	private VirtualFile currentFile() {
		Editor editor = getEditor();
		if (editor == null) {
			return null;
		}
		return FileDocumentManager.getInstance().getFile(editor.getDocument());
	}
}
