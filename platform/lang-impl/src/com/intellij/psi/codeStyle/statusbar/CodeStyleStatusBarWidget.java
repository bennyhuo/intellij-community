// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.statusbar;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor;
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings;
import com.intellij.util.concurrency.NonUrgentExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;

public final class CodeStyleStatusBarWidget extends EditorBasedStatusBarPopup implements CodeStyleSettingsListener {
  public static final String WIDGET_ID = CodeStyleStatusBarWidget.class.getName();

  private CodeStyleStatusBarPanel myPanel;

  public CodeStyleStatusBarWidget(@NotNull Project project) {
    super(project, true);
  }

  @Override
  protected @NotNull WidgetState getWidgetState(@Nullable VirtualFile file) {
    if (file == null) return WidgetState.HIDDEN;
    PsiFile psiFile = getPsiFile();
    if (psiFile == null) return WidgetState.HIDDEN;
    CodeStyleSettings settings = CodeStyle.getSettings(psiFile);
    IndentOptions indentOptions = CodeStyle.getIndentOptions(psiFile);
    if (settings instanceof TransientCodeStyleSettings) {
      return createWidgetState(psiFile, indentOptions, getUiContributor((TransientCodeStyleSettings)settings));
    }
    else {
      return createWidgetState(psiFile, indentOptions, getUiContributor(file, indentOptions));
    }
  }

  private static @Nullable CodeStyleStatusBarUIContributor getUiContributor(@NotNull TransientCodeStyleSettings settings) {
    final CodeStyleSettingsModifier modifier = settings.getModifier();
    return modifier != null ? modifier.getStatusBarUiContributor(settings) : null;
  }

  private static @Nullable CodeStyleStatusBarUIContributor getUiContributor(@NotNull VirtualFile file, @NotNull IndentOptions indentOptions) {
    FileIndentOptionsProvider provider = findProvider(file, indentOptions);
    if (provider != null) {
      return provider.getIndentStatusBarUiContributor(indentOptions);
    }
    return null;
  }

  private static @Nullable FileIndentOptionsProvider findProvider(@NotNull VirtualFile file, @NotNull IndentOptions indentOptions) {
    FileIndentOptionsProvider optionsProvider = indentOptions.getFileIndentOptionsProvider();
    if (optionsProvider != null) return optionsProvider;
    for (FileIndentOptionsProvider provider : FileIndentOptionsProvider.EP_NAME.getExtensions()) {
      CodeStyleStatusBarUIContributor uiContributor = provider.getIndentStatusBarUiContributor(indentOptions);
      if (uiContributor != null && uiContributor.areActionsAvailable(file)) {
        return provider;
      }
    }
    return null;
  }

  private static WidgetState createWidgetState(@NotNull PsiFile psiFile,
                                               @NotNull IndentOptions indentOptions,
                                               @Nullable CodeStyleStatusBarUIContributor uiContributor) {
    if (uiContributor != null) {
      return new MyWidgetState(uiContributor.getTooltip(), uiContributor.getStatusText(psiFile), uiContributor);
    }
    else {
      String indentInfo = IndentStatusBarUIContributor.getIndentInfo(indentOptions);
      String tooltip = IndentStatusBarUIContributor.createTooltip(indentInfo, null);
      return new MyWidgetState(tooltip, indentInfo, null);
    }
  }

  private @Nullable PsiFile getPsiFile() {
    Editor editor = getEditor();
    Project project = getProject();
    if (editor != null && !project.isDisposed()) {
      return PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    }
    return null;
  }

  @Override
  protected @Nullable ListPopup createPopup(@NotNull DataContext context) {
    WidgetState state = getWidgetState(context.getData(CommonDataKeys.VIRTUAL_FILE));
    Editor editor = getEditor();
    PsiFile psiFile = getPsiFile();
    if (state instanceof MyWidgetState && editor != null && psiFile != null) {
      final CodeStyleStatusBarUIContributor uiContributor = ((MyWidgetState)state).getContributor();
      AnAction[] actions = getActions(uiContributor, psiFile);
      ActionGroup actionGroup = new ActionGroup() {
        @Override
        public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
          return actions;
        }
      };
      return JBPopupFactory.getInstance().createActionGroupPopup(
        uiContributor != null ? uiContributor.getActionGroupTitle() : null, actionGroup, context,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
    }
    return null;
  }

  private static AnAction @NotNull [] getActions(final @Nullable CodeStyleStatusBarUIContributor uiContributor, @NotNull PsiFile psiFile) {
    List<AnAction> allActions = new ArrayList<>();
    if (uiContributor != null) {
      AnAction[] actions = uiContributor.getActions(psiFile);
      if (actions != null) {
        allActions.addAll(Arrays.asList(actions));
      }
    }
    if (uiContributor == null ||
        (uiContributor instanceof IndentStatusBarUIContributor) &&
        ((IndentStatusBarUIContributor)uiContributor).isShowFileIndentOptionsEnabled()) {
      allActions.add(CodeStyleStatusBarWidgetFactory.createDefaultIndentConfigureAction(psiFile));
    }
    if (uiContributor != null) {
      AnAction disabledAction = uiContributor.createDisableAction(psiFile.getProject());
      if (disabledAction != null) {
        allActions.add(disabledAction);
      }
      AnAction showAllAction = uiContributor.createShowAllAction(psiFile.getProject());
      if (showAllAction != null) {
        allActions.add(showAllAction);
      }
    }
    return allActions.toArray(AnAction.EMPTY_ARRAY);
  }

  @Override
  protected void registerCustomListeners() {
    Project project = getProject();
    ReadAction
      .nonBlocking(() -> CodeStyleSettingsManager.getInstance(project))
      .expireWith(this)
      .finishOnUiThread(ModalityState.any(),
                        manager -> {
                          manager.addListener(this);
                          Disposer.register(this, () -> CodeStyleSettingsManager.removeListener(project, this));
                        }
      ).submit(NonUrgentExecutor.getInstance());
  }

  @Override
  public void codeStyleSettingsChanged(@NotNull CodeStyleSettingsChangeEvent event) {
    update();
  }

  @Override
  protected @NotNull StatusBarWidget createInstance(@NotNull Project project) {
    return new CodeStyleStatusBarWidget(project);
  }

  @Override
  public @NotNull String ID() {
    return WIDGET_ID;
  }

  private static final class MyWidgetState extends WidgetState {
    private final @Nullable CodeStyleStatusBarUIContributor myContributor;

    private MyWidgetState(@NlsContexts.Tooltip String toolTip,
                          @NlsContexts.StatusBarText String text,
                          @Nullable CodeStyleStatusBarUIContributor uiContributor) {
      super(toolTip, text, true);
      myContributor = uiContributor;
      if (uiContributor != null) {
        setIcon(uiContributor.getIcon());
      }
    }

    public @Nullable CodeStyleStatusBarUIContributor getContributor() {
      return myContributor;
    }
  }

  @Override
  protected @NotNull JPanel createComponent() {
    myPanel = new CodeStyleStatusBarPanel();
    return myPanel;
  }

  @Override
  protected void updateComponent(@NotNull WidgetState state) {
    myPanel.setIcon(state.getIcon());
    myPanel.setText(state.getText());
    myPanel.setToolTipText(state.getToolTip());
  }

  @Override
  protected boolean isEmpty() {
    return StringUtil.isEmpty(myPanel.getText());
  }
}
