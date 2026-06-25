package com.jiangjinghong.git.blame.line;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorLinePainter;
import com.intellij.openapi.editor.LineExtensionInfo;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.jiangjinghong.git.blame.GitBlameService;
import com.jiangjinghong.git.blame.settings.GitBlameSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.Collections;

/**
 * 行内 Git Blame 显示：通过 {@link EditorLinePainter} 扩展点，在光标所在行末尾追加 blame 文本。
 * <p>
 * 本类只负责"是否为光标行 + 取 editor + 样式包装"，blame 数据获取与缓存由
 * {@link GitBlameService} 承担。
 * </p>
 */
public class GitLinePainter extends EditorLinePainter {

	private static final TextAttributesKey INLINE_BLAME_KEY = TextAttributesKey
		.createTextAttributesKey("GITTOOLBOX_INLINE_BLAME");

	@Override
	@Nullable
	public Collection<LineExtensionInfo> getLineExtensions(@NotNull Project project, @NotNull VirtualFile file,
			int lineIndex) {

		if (!GitBlameSettings.getInstance().isEnabled()) {
			return null;
		}

		Editor editor = getCurrentEditor(project, file);
		if (editor == null) {
			return null;
		}
		int caretLine = getCaretLine(editor);
		if (caretLine != lineIndex) {
			return null; // 非光标行，不显示
		}

		String blameText = GitBlameService.getInstance(project).getFormattedBlameLine(project, file, lineIndex);
		if (blameText == null || blameText.isEmpty()) {
			return null;
		}

		TextAttributes attrs = getBlameTextAttributes();
		return Collections.singletonList(new LineExtensionInfo(blameText, attrs));
	}

	// ========== 编辑器工具 ==========

	@Nullable
	private Editor getCurrentEditor(@NotNull Project project, @NotNull VirtualFile file) {
		FileEditorManager fem = FileEditorManager.getInstance(project);
		for (var fileEditor : fem.getAllEditors(file)) {
			if (fileEditor instanceof TextEditor textEditor) {
				return textEditor.getEditor();
			}
		}
		Editor selected = fem.getSelectedTextEditor();
		if (selected != null) {
			VirtualFile selectedFile = FileDocumentManager.getInstance().getFile(selected.getDocument());
			if (file.equals(selectedFile)) {
				return selected;
			}
		}
		return null;
	}

	private int getCaretLine(@NotNull Editor editor) {
		CaretModel caretModel = editor.getCaretModel();
		if (caretModel.isUpToDate()) {
			return caretModel.getLogicalPosition().line;
		}
		return -1;
	}

	// ========== 样式 ==========

	@NotNull
	private TextAttributes getBlameTextAttributes() {
		EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
		TextAttributes attrs = scheme.getAttributes(INLINE_BLAME_KEY);
		if (attrs != null && attrs.getForegroundColor() != null) {
			return attrs;
		}
		// 默认灰色斜体
		Color grayColor = JBColor.GRAY;
		return new TextAttributes(grayColor, null, null, null, Font.ITALIC);
	}
}
