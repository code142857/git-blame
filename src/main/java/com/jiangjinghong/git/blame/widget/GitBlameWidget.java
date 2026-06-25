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
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.jiangjinghong.git.blame.GitBlameService;
import com.jiangjinghong.git.blame.model.LineBlameInfo;
import com.jiangjinghong.git.blame.settings.GitBlameSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * 状态栏 widget：显示光标行 commit 的短 hash，悬浮查看完整 hash + 作者 + 时间 + 摘要。
 * <p>
 * {@link EditorBasedWidget} 负责跟踪活动编辑器/文件切换；光标移动通过
 * {@link EditorEventMulticaster} 注册的 {@link CaretListener} 监听（用 {@code this}
 * 作为 Disposable，随 widget 释放）。
 * </p>
 */
public final class GitBlameWidget extends EditorBasedWidget implements StatusBarWidget.TextPresentation {

	private final CaretListener caretListener = new CaretListener() {
		@Override
		public void caretPositionChanged(@NotNull CaretEvent e) {
			if (isOurEditor(e.getEditor())) {
				updateIfVisible();
			}
		}
	};

	public GitBlameWidget(@NotNull Project project) {
		super(project);
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
	public WidgetPresentation getPresentation() {
		return this;
	}

	@Override
	@Nullable
	public String getText() {
		if (!GitBlameSettings.getInstance().isEnabled()) {
			return "";
		}
		LineBlameInfo info = currentBlameInfo();
		if (info == null || info.hash == null) {
			return "";
		}
		return info.hash.substring(0, 8);
	}

	@Override
	@Nullable
	public String getTooltipText() {
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
			sb.append("  \u2022  ").append(info.subject);
		}
		return sb.toString();
	}

	@Override
	public float getAlignment() {
		return Component.CENTER_ALIGNMENT;
	}

	// ========== 内部 ==========

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

	private void updateIfVisible() {
		StatusBar statusBar = getStatusBar();
		if (statusBar != null) {
			statusBar.updateWidget(ID());
		}
	}
}
