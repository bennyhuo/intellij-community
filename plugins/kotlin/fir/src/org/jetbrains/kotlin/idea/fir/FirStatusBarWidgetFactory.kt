// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import org.jetbrains.kotlin.idea.KotlinIcons
import javax.swing.Icon

private class FirStatusBarWidgetFactory: StatusBarWidgetFactory {
    override fun getId(): String = ID

    override fun getDisplayName(): String = "FIR IDE"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = Widget()

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

    companion object {
        const val ID = "kotlin.fir.ide"
    }
}

private class Widget : StatusBarWidget, StatusBarWidget.IconPresentation {
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    override fun ID(): String = FirStatusBarWidgetFactory.ID
    override fun getTooltipText(): String = "FIR IDE"
    override fun getIcon(): Icon = KotlinIcons.FIR
}