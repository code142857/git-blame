package com.jiangjinghong.git.blame.widget;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * 状态栏 Git Blame widget 工厂：注册 {@link GitBlameWidget}，显示光标行 commit 的短 hash。
 */
public final class GitBlameWidgetFactory implements StatusBarWidgetFactory {

	public static final String WIDGET_ID = "GitBlameWidget";

	@Override
	@NotNull
	public String getId() {
		return WIDGET_ID;
	}

	@Override
	@Nls
	@NotNull
	public String getDisplayName() {
		return "Git Blame";
	}

	@Override
	@NotNull
	public StatusBarWidget createWidget(@NotNull Project project) {
		return new GitBlameWidget(project);
	}
}
