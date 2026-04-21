package com.dartbarrel.plugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class DartBarrelConfigurable : Configurable {

    private val settings = DartBarrelSettings.getInstance()

    private val barrelFileNameCombo = ComboBox(
        arrayOf("{folder_name}.dart", "index.dart", "Custom..."),
    )
    private val customBarrelFileName = JBTextField()
    private val includeHeaderCheckBox = JBCheckBox(
        "Include header comment",
    )
    private val headerCommentArea = JBTextArea(3, 40)
    private val autoGenerateCheckBox = JBCheckBox(
        "Automatically refresh existing barrels when Dart files change",
    )
    private val excludePatternsArea = JBTextArea(3, 40)
    private val sortExportsCheckBox = JBCheckBox("Sort export statements")

    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Dart Barrel Manager"

    override fun createComponent(): JComponent {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(
                JBLabel("Barrel file name:"),
                barrelFileNameCombo,
                1,
                false,
            )
            .addLabeledComponent(JBLabel("Custom name:"), customBarrelFileName, 1, false)
            .addComponent(includeHeaderCheckBox, 1)
            .addLabeledComponent(JBLabel("Header comment:"), headerCommentArea, 1, false)
            .addComponent(autoGenerateCheckBox, 1)
            .addLabeledComponent(
                JBLabel("Exclude patterns (regex):"),
                excludePatternsArea,
                1,
                false,
            )
            .addComponent(sortExportsCheckBox, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        // Setup listeners
        barrelFileNameCombo.addActionListener {
            customBarrelFileName.isEnabled = barrelFileNameCombo.selectedItem == "Custom..."
        }

        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        return getSelectedBarrelFileName() != settings.barrelFileName ||
                includeHeaderCheckBox.isSelected != settings.includeHeader ||
                headerCommentArea.text != settings.headerComment ||
                autoGenerateCheckBox.isSelected != settings.autoGenerate ||
                excludePatternsArea.text !=
                settings.excludePatterns.joinToString("\n") ||
                sortExportsCheckBox.isSelected != settings.sortExports
    }

    override fun apply() {
        settings.barrelFileName = getSelectedBarrelFileName()
        settings.includeHeader = includeHeaderCheckBox.isSelected
        settings.headerComment = headerCommentArea.text
        settings.autoGenerate = autoGenerateCheckBox.isSelected
        settings.excludePatterns = excludePatternsArea.text
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toMutableList()
        settings.sortExports = sortExportsCheckBox.isSelected
    }

    override fun reset() {
        when (settings.barrelFileName) {
            "{folder_name}.dart" -> barrelFileNameCombo.selectedItem = "{folder_name}.dart"
            "index.dart" -> barrelFileNameCombo.selectedItem = "index.dart"
            else -> {
                barrelFileNameCombo.selectedItem = "Custom..."
                customBarrelFileName.text = settings.barrelFileName
            }
        }

        customBarrelFileName.isEnabled = barrelFileNameCombo.selectedItem == "Custom..."
        includeHeaderCheckBox.isSelected = settings.includeHeader
        headerCommentArea.text = settings.headerComment
        autoGenerateCheckBox.isSelected = settings.autoGenerate
        excludePatternsArea.text = settings.excludePatterns.joinToString("\n")
        sortExportsCheckBox.isSelected = settings.sortExports
    }

    private fun getSelectedBarrelFileName(): String {
        return when (barrelFileNameCombo.selectedItem) {
            "{folder_name}.dart" -> "{folder_name}.dart"
            "index.dart" -> "index.dart"
            "Custom..." -> customBarrelFileName.text.trim().takeIf { it.isNotEmpty() } ?: "{folder_name}.dart"
            else -> "{folder_name}.dart"
        }
    }
}