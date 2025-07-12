package com.dartbarrel.plugin.ui

import com.dartbarrel.plugin.services.DartBarrelService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.*
import javax.swing.border.TitledBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class GenerateBarrelDialog(
    project: Project,
    private val directory: PsiDirectory,
    allFiles: List<PsiFile>,
    private val barrelService: DartBarrelService
) : DialogWrapper(project) {

    private val barrelFileName = barrelService.getBarrelFileName(directory)
    private val filteredFiles = allFiles.filter { it.name != barrelFileName }
    private val fileList = JBList(filteredFiles.map { it.name })
    private val fileNameField = JTextField(barrelFileName)
    private val previewArea = JTextArea(8, 40)

    init {
        title = "Generate Barrel File"
        fileList.setSelectionInterval(0, filteredFiles.size - 1)
        previewArea.isEditable = false
        previewArea.font = Font("monospaced", Font.PLAIN, 12)
        updatePreview()
        init()
        // Update preview on selection or file name change
        fileList.addListSelectionListener { updatePreview() }
        fileNameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updatePreview()
            override fun removeUpdate(e: DocumentEvent?) = updatePreview()
            override fun changedUpdate(e: DocumentEvent?) = updatePreview()
        })
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        mainPanel.border = JBUI.Borders.empty(12)

        // Barrel file name group
        val namePanel = JPanel()
        namePanel.layout = BoxLayout(namePanel, BoxLayout.X_AXIS)
        namePanel.border = BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Barrel File Name",
            TitledBorder.LEADING,
            TitledBorder.TOP
        )
        namePanel.add(Box.createHorizontalStrut(8))
        namePanel.add(JLabel("File name:"))
        namePanel.add(Box.createHorizontalStrut(8))
        namePanel.add(fileNameField)
        namePanel.add(Box.createHorizontalGlue())

        // File selection group
        val filePanel = JPanel(BorderLayout())
        filePanel.border = BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Select Files to Export",
            TitledBorder.LEADING,
            TitledBorder.TOP
        )
        fileList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        fileList.visibleRowCount = 10
        fileList.cellRenderer = FileListCellRenderer()
        val scrollPane = JBScrollPane(fileList)
        scrollPane.preferredSize = Dimension(500, 180)
        scrollPane.verticalScrollBar.unitIncrement = 16
        filePanel.add(scrollPane, BorderLayout.CENTER)

        // Preview group
        val previewPanel = JPanel(BorderLayout())
        previewPanel.border = BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Barrel File Preview",
            TitledBorder.LEADING,
            TitledBorder.TOP
        )
        val previewScroll = JBScrollPane(previewArea)
        previewScroll.preferredSize = Dimension(500, 140)
        previewPanel.add(previewScroll, BorderLayout.CENTER)

        // Add all groups to the main panel
        mainPanel.add(namePanel)
        mainPanel.add(Box.createVerticalStrut(12))
        mainPanel.add(filePanel)
        mainPanel.add(Box.createVerticalStrut(12))
        mainPanel.add(previewPanel)

        return mainPanel
    }

    fun getSelectedFiles(): List<String> =
        fileList.selectedValuesList

    fun getSelectedFileName(): String =
        fileNameField.text.trim()

    private fun updatePreview() {
        val selectedNames = fileList.selectedValuesList
        val selectedFiles = filteredFiles.filter { it.name in selectedNames }
        val content = barrelService
            .let {
                val method = it::class.java.getDeclaredMethod(
                    "buildBarrelContentWithRelativePaths",
                    List::class.java,
                    PsiDirectory::class.java
                )
                method.isAccessible = true
                method.invoke(it, selectedFiles, directory) as String
            }
        previewArea.text = content
    }

    private class FileListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (comp is JLabel && value is String) {
                comp.icon = UIManager.getIcon("FileView.fileIcon")
                comp.text = value
            }
            return comp
        }
    }
}