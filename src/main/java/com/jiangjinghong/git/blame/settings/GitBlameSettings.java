package com.jiangjinghong.git.blame.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 应用级持久化设置：行内 blame 开关、字段、日期格式、自定义模板。
 * <p>
 * 存储 {@code gitBlameSettings.xml}。运行时通过 {@link #getInstance()} 获取。
 * </p>
 */
@State(name = "com.jiangjinghong.git.blame.settings",
		storages = {@Storage("gitBlameSettings.xml")})
public final class GitBlameSettings implements PersistentStateComponent<GitBlameSettings.State> {

	public static final class State {
		public boolean enabled = true;
		public boolean showAuthor = true;
		public boolean showDate = true;
		public boolean showSubject = true;
		public String dateFormat = "RELATIVE";
		public String formatTemplate = "";
	}

	private State myState = new State();

	public static GitBlameSettings getInstance() {
		return ApplicationManager.getApplication().getService(GitBlameSettings.class);
	}

	@Override
	public State getState() {
		return myState;
	}

	@Override
	public void loadState(@NotNull State state) {
		XmlSerializerUtil.copyBean(state, myState);
	}

	public boolean isEnabled() {
		return myState.enabled;
	}

	public boolean isShowAuthor() {
		return myState.showAuthor;
	}

	public boolean isShowDate() {
		return myState.showDate;
	}

	public boolean isShowSubject() {
		return myState.showSubject;
	}

	@NotNull
	public DateFormatMode getDateFormat() {
		return DateFormatMode.fromString(myState.dateFormat);
	}

	@NotNull
	public String getFormatTemplate() {
		return myState.formatTemplate == null ? "" : myState.formatTemplate.trim();
	}
}
