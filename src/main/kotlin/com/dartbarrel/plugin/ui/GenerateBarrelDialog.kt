package com.dartbarrel.plugin.ui

import com.dartbarrel.plugin.services.BarrelContentBuilder
import com.dartbarrel.plugin.services.DartBarrelService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
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
    private val barrelService: DartBarrelService,
) : DialogWrapper(project) {

    private val barrelFileName =
        barrelService.getBarrelFileName(directory)
    private val exportableItems =
        barrelService.resolveExportableItems(directory)
    private val checkBoxList = CheckBoxList<ExportEntry>()
    private val fileNameField = JTextField(barrelFileName)
    private val previewArea = JTextArea(8, 40)

    init {
        title = "Generate Barrel File"
        setupCheckBoxList()
        previewArea.isEditable = false
        previewArea.font = Font("monospaced", Font.PLAIN, 12)
        updatePreview()
        init()

        checkBoxList.setCheckBoxListListener { _, _ ->
            updatePreview()
        }
        fileNameField.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) =
                    updatePreview()

                override fun removeUpdate(e: DocumentEvent?) =
                    updatePreview()

                override fun changedUpdate(e: DocumentEvent?) =
                    updatePreview()
            },
        )
    }

    private fun setupCheckBoxList() {
        exportableItems.forEach { item ->
            val label = buildItemLabel(item)
            val entry = ExportEntry(item, label)
            checkBoxList.addItem(entry, entry.label, true)
        }
    }

    private fun buildItemLabel(
        item: BarrelContentBuilder.ExportableItem,
    ): String {
        val rootPath = directory.virtualFile.path
        val filePath = item.file.virtualFile?.path ?: ""
        val relative = filePath
            .removePrefix(rootPath)
            .removePrefix("/")

        return if (item.isSubBarrel) {
            "\uD83D\uDCC1 $relative (barrel)"
        } else {
            relative
        }
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        mainPanel.border = JBUI.Borders.empty(12)

        val namePanel = JPanel()
        namePanel.layout =
            BoxLayout(namePanel, BoxLayout.X_AXIS)
        namePanel.border = BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Barrel File Name",
            TitledBorder.LEADING,
            TitledBorder.TOP,
        )
        namePanel.add(Box.createHorizontalStrut(8))
        namePanel.add(JLabel("File name:"))
        namePanel.add(Box.createHorizontalStrut(8))
        namePanel.add(fileNameField)
        namePanel.add(Box.createHorizontalGlue())

        val filePanel = JPanel(BorderLayout())
        filePanel.border = BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Select Exports",
            TitledBorder.LEADING,
            TitledBorder.TOP,
        )
        val scrollPane = JBScrollPane(checkBoxList)
        scrollPane.preferredSize = Dimension(500, 200)
        scrollPane.verticalScrollBar.unitIncrement = 16
        filePanel.add(scrollPane, BorderLayout.CENTER)

        val previewPanel = JPanel(BorderLayout())
        previewPanel.border = BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Barrel File Preview",
            TitledBorder.LEADING,
            TitledBorder.TOP,
        )
        val previewScroll = JBScrollPane(previewArea)
        previewScroll.preferredSize = Dimension(500, 140)
        previewPanel.add(previewScroll, BorderLayout.CENTER)

        mainPanel.add(namePanel)
        mainPanel.add(Box.createVerticalStrut(12))
        mainPanel.add(filePanel)
        mainPanel.add(Box.createVerticalStrut(12))
        mainPanel.add(previewPanel)

        return mainPanel
    }

    fun getSelectedFiles(): List<PsiFile> {
        val selected = mutableListOf<PsiFile>()
        for (i in 0 until checkBoxList.itemsCount) {
            if (checkBoxList.isItemSelected(i)) {
                val entry = checkBoxList.getItemAt(i)
                if (entry != null) {
                    selected.add(entry.item.file)
                }
            }
        }
        return selected
    }

    fun getSelectedFileName(): String =
        fileNameField.text.trim()

    private fun updatePreview() {
        val selectedFiles = getSelectedFiles()
        val content = barrelService.buildPreviewContent(
            selectedFiles,
            directory,
        )
        previewArea.text = content
    }

    private data class ExportEntry(
        val item: BarrelContentBuilder.ExportableItem,
        val label: String,
    ) {
        override fun toString(): String = label
    }
}