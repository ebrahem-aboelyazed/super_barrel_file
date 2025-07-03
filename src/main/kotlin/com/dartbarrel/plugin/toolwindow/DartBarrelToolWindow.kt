package com.dartbarrel.plugin.toolwindow

import com.dartbarrel.plugin.services.DartBarrelService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

@Service(Service.Level.PROJECT)
class DartBarrelToolWindow(private val project: Project) {

    private val toolWindowContent = SimpleToolWindowPanel(true, true)
    private val barrelFilesList = JBList<String>()
    private val barrelService = project.service<DartBarrelService>()

    init {
        setupUI()
    }

    private fun setupUI() {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(5)
        }

        // Create toolbar
        val toolbar = createToolbar()
        panel.add(toolbar, BorderLayout.NORTH)

        // Create list
        barrelFilesList.apply {
            cellRenderer = BarrelFileListCellRenderer()
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        val selected = selectedValue ?: return
                        openBarrelFile(selected)
                    }
                }
            })
        }

        val scrollPane = JBScrollPane(barrelFilesList)
        panel.add(scrollPane, BorderLayout.CENTER)

        toolWindowContent.setContent(panel)

        refreshBarrelFilesList()
    }

    private fun createToolbar(): JPanel {
        val toolbar = JPanel()
        toolbar.layout = BoxLayout(toolbar, BoxLayout.X_AXIS)

        val refreshButton = JButton("Refresh").apply {
            addActionListener { refreshBarrelFilesList() }
        }

        val generateAllButton = JButton("Generate All").apply {
            addActionListener { generateAllBarrelFiles() }
        }

        toolbar.add(refreshButton)
        toolbar.add(Box.createHorizontalStrut(5))
        toolbar.add(generateAllButton)
        toolbar.add(Box.createHorizontalGlue())

        return toolbar
    }

    private fun refreshBarrelFilesList() {
        val barrelFiles = findAllBarrelFiles()
        val model = DefaultListModel<String>()
        barrelFiles.forEach { model.addElement(it) }
        barrelFilesList.model = model
    }

    private fun findAllBarrelFiles(): List<String> {
        val barrelFiles = mutableListOf<String>()
        // TODO: Implement logic to find all barrel files in the project
        // This would traverse the project structure and find existing barrel files
        return barrelFiles
    }

    private fun generateAllBarrelFiles() {
        val psiManager = PsiManager.getInstance(project)
        val basePath = project.basePath
        val baseDir = basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) } ?: return

        val psiDirectory = psiManager.findDirectory(baseDir) ?: return

        // Only generate for the root/base directory
        ApplicationManager.getApplication().runWriteAction {
            barrelService.generateBarrelFile(psiDirectory)
        }
        refreshBarrelFilesList()
    }

    private fun openBarrelFile(fileName: String) {
        val basePath = project.basePath
        val baseDir = basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) } ?: return

        fun findFileRecursively(dir: VirtualFile): VirtualFile? {
            dir.children.forEach { child ->
                if (child.isDirectory) {
                    val found = findFileRecursively(child)
                    if (found != null) return found
                } else if (child.name == fileName) {
                    return child
                }
            }
            return null
        }

        val file = findFileRecursively(baseDir) ?: return
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    fun getContent(): JComponent = toolWindowContent

    // Optionally, you can define a custom cell renderer for the list
    private class BarrelFileListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): java.awt.Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            text = value?.toString() ?: ""
            return component
        }
    }
}