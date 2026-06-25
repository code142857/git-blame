package com.jiangjinghong.git.blame.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.jiangjinghong.git.blame.GitBlameService;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Settings &rarr; Tools &rarr; Git Blame 配置面板。
 */
public class GitBlameConfigurable implements Configurable {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");
	private static final Set<String> KNOWN_PLACEHOLDERS = Set.of("author", "date", "subject");

	private JBCheckBox enabledCheckBox;
	private JBCheckBox showAuthorCheckBox;
	private JBCheckBox showDateCheckBox;
	private JBCheckBox showSubjectCheckBox;
	private ComboBox<DateFormatMode> dateFormatCombo;
	private JBTextField formatTemplateField;

	@Nls
	@Override
	public String getDisplayName() {
		return "Git Blame";
	}

	@Nullable
	@Override
	public JComponent createComponent() {
		enabledCheckBox = new JBCheckBox("Show inline Git Blame on the caret line");
		showAuthorCheckBox = new JBCheckBox("Author");
		showDateCheckBox = new JBCheckBox("Date");
		showSubjectCheckBox = new JBCheckBox("Commit subject");
		dateFormatCombo = new ComboBox<>(DateFormatMode.values());
		dateFormatCombo.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
					boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof DateFormatMode) {
					setText(((DateFormatMode) value).getDisplayName());
				}
				return this;
			}
		});
		formatTemplateField = new JBTextField();
		formatTemplateField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				updateFieldsEnabled();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				updateFieldsEnabled();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				updateFieldsEnabled();
			}
		});

		JPanel fieldsPanel = new JPanel(new GridLayout(1, 3, 8, 0));
		fieldsPanel.add(showAuthorCheckBox);
		fieldsPanel.add(showDateCheckBox);
		fieldsPanel.add(showSubjectCheckBox);

		JBLabel hintLabel = new JBLabel(
				"Placeholders: {author} {date} {subject}  —  non-empty template overrides the fields above.");
		hintLabel.setCopyable(false);

		return FormBuilder.createFormBuilder()
				.addComponent(enabledCheckBox)
				.addLabeledComponent("Fields to show:", fieldsPanel)
				.addLabeledComponent("Date format:", dateFormatCombo)
				.addLabeledComponent("Format template:", formatTemplateField)
				.addComponent(hintLabel)
				.addComponentFillVertically(new JPanel(), 0)
				.getPanel();
	}

	@Override
	public boolean isModified() {
		GitBlameSettings s = GitBlameSettings.getInstance();
		return s.isEnabled() != enabledCheckBox.isSelected()
				|| s.isShowAuthor() != showAuthorCheckBox.isSelected()
				|| s.isShowDate() != showDateCheckBox.isSelected()
				|| s.isShowSubject() != showSubjectCheckBox.isSelected()
				|| s.getDateFormat() != dateFormatCombo.getSelectedItem()
				|| !s.getFormatTemplate().equals(formatTemplateField.getText().trim());
	}

	@Override
	public void apply() throws ConfigurationException {
		String template = formatTemplateField.getText().trim();
		if (!template.isEmpty()) {
			Matcher m = PLACEHOLDER.matcher(template);
			Set<String> unknown = new HashSet<>();
			while (m.find()) {
				if (!KNOWN_PLACEHOLDERS.contains(m.group(1))) {
					unknown.add(m.group(1));
				}
			}
			if (!unknown.isEmpty()) {
				throw new ConfigurationException(
						"Unknown template placeholders: " + unknown + ". Allowed: {author}, {date}, {subject}.");
			}
		}
		GitBlameSettings.State state = GitBlameSettings.getInstance().getState();
		state.enabled = enabledCheckBox.isSelected();
		state.showAuthor = showAuthorCheckBox.isSelected();
		state.showDate = showDateCheckBox.isSelected();
		state.showSubject = showSubjectCheckBox.isSelected();
		state.dateFormat = ((DateFormatMode) dateFormatCombo.getSelectedItem()).name();
		state.formatTemplate = template;

		for (Project p : ProjectManager.getInstance().getOpenProjects()) {
			if (!p.isDisposed()) {
				GitBlameService.getInstance(p).onSettingsChanged();
			}
		}
	}

	@Override
	public void reset() {
		GitBlameSettings s = GitBlameSettings.getInstance();
		enabledCheckBox.setSelected(s.isEnabled());
		showAuthorCheckBox.setSelected(s.isShowAuthor());
		showDateCheckBox.setSelected(s.isShowDate());
		showSubjectCheckBox.setSelected(s.isShowSubject());
		dateFormatCombo.setSelectedItem(s.getDateFormat());
		formatTemplateField.setText(s.getFormatTemplate());
		updateFieldsEnabled();
	}

	@Override
	public void disposeUIResources() {
		enabledCheckBox = null;
		showAuthorCheckBox = null;
		showDateCheckBox = null;
		showSubjectCheckBox = null;
		dateFormatCombo = null;
		formatTemplateField = null;
	}

	private void updateFieldsEnabled() {
		boolean useTemplate = !formatTemplateField.getText().trim().isEmpty();
		showAuthorCheckBox.setEnabled(!useTemplate);
		showDateCheckBox.setEnabled(!useTemplate);
		showSubjectCheckBox.setEnabled(!useTemplate);
		dateFormatCombo.setEnabled(!useTemplate);
	}
}
