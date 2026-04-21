package com.dartbarrel.plugin.ui

import com.dartbarrel.plugin.model.BarrelExportCandidate
import com.dartbarrel.plugin.model.BarrelGenerationPlan
import com.dartbarrel.plugin.services.DartBarrelService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.DialogWrapper
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
    private val plan: BarrelGenerationPlan,
    private val barrelService: DartBarrelService,
) : DialogWrapper(project) {

    private val checkBoxList = CheckBoxList<ExportEntry>()
    private val fileNameField = JTextField(plan.barrelFileName)
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
        plan.candidates.forEach { item ->
            val label = buildItemLabel(item)
            val entry = ExportEntry(item, label)
            checkBoxList.addItem(entry, entry.label, true)
        }
    }

    private fun buildItemLabel(
        item: BarrelExportCandidate,
    ): String {
        return if (item.isNestedBarrel) {
            "\uD83D\uDCC1 ${item.relativePath} (barrel)"
        } else {
            item.relativePath
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

    fun getSelectedRelativePaths(): Set<String> {
        val selected = linkedSetOf<String>()
        for (i in 0 until checkBoxList.itemsCount) {
            if (checkBoxList.isItemSelected(i)) {
                val entry = checkBoxList.getItemAt(i)
                if (entry != null) {
                    selected.add(entry.item.relativePath)
                }
            }
        }
        return selected
    }

    fun getSelectedFileName(): String =
        fileNameField.text.trim()

    override fun doValidate(): ValidationInfo? {
        val fileName = getSelectedFileName()
        return when {
            fileName.isBlank() -> ValidationInfo(
                "Please enter a barrel file name.",
                fileNameField,
            )
            !fileName.endsWith(".dart") -> ValidationInfo(
                "Barrel files must use the .dart extension.",
                fileNameField,
            )
            else -> null
        }
    }

    private fun updatePreview() {
        val selectedFiles = getSelectedRelativePaths()
        val content = barrelService.buildPreviewContent(
            plan,
            selectedFiles,
        )
        previewArea.text = content
    }

    private data class ExportEntry(
        val item: BarrelExportCandidate,
        val label: String,
    ) {
        override fun toString(): String = label
    }
}