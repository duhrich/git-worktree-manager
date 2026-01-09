package com.github.gitworktreemanager

import com.intellij.icons.AllIcons
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JPanel

class WorktreeToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = WorktreePanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)

        val refreshAction = object : AnAction("Refresh", "Refresh worktree list", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) {
                panel.refresh()
            }
        }

        toolWindow.setTitleActions(listOf(refreshAction))
    }
}

class WorktreePanel(private val project: Project) : JPanel(BorderLayout()) {

    private val listModel = DefaultListModel<WorktreeInfo>()
    private val worktreeList = JBList(listModel)

    init {
        worktreeList.cellRenderer = WorktreeCellRenderer()
        worktreeList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val selected = worktreeList.selectedValue
                    if (selected != null && !selected.isCurrent) {
                        openWorktree(selected)
                    }
                }
            }
        })

        add(JBScrollPane(worktreeList), BorderLayout.CENTER)
        refresh()
    }

    fun refresh() {
        val service = project.service<WorktreeService>()
        val worktrees = service.listWorktrees()

        listModel.clear()
        worktrees.forEach { listModel.addElement(it) }
    }

    private fun openWorktree(worktree: WorktreeInfo) {
        val path = Path.of(worktree.path)
        ProjectUtil.openOrImport(path, OpenProjectTask())
    }
}

class WorktreeCellRenderer : ColoredListCellRenderer<WorktreeInfo>() {

    override fun customizeCellRenderer(
        list: JList<out WorktreeInfo>,
        value: WorktreeInfo?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        if (value == null) return

        icon = AllIcons.Vcs.Branch

        val nameAttributes = if (value.isCurrent) {
            SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
        } else {
            SimpleTextAttributes.REGULAR_ATTRIBUTES
        }

        append(value.displayName, nameAttributes)
        append("  ")
        append(value.branch, SimpleTextAttributes.GRAYED_ATTRIBUTES)

        if (value.isCurrent) {
            append("  (current)", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
        }
    }
}
