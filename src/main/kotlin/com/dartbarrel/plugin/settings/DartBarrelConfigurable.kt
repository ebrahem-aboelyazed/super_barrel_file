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

    private val barrelFileNameCombo = ComboBox(arrayOf("{folder_name}.dart", "index.dart", "Custom..."))
    private val customBarrelFileName = JBTextField()
    private val includeExportsCheckBox = JBCheckBox("Include export statements")
    private val includeImportsCheckBox = JBCheckBox("Include import statements")
    private val showHidePrivateCheckBox = JBCheckBox("Use 'hide' for private elements")
    private val showShowPublicCheckBox = JBCheckBox("Use 'show' for public elements")
    private val includeHeaderCheckBox = JBCheckBox("Include header comment")
    private val headerCommentArea = JBTextArea(3, 40)
    private val autoGenerateCheckBox = JBCheckBox("Auto-generate on file changes")
    private val excludePatternsArea = JBTextArea(3, 40)
    private val sortExportsCheckBox = JBCheckBox("Sort export statements")
    private val groupByDirectoryCheckBox = JBCheckBox("Group exports by directory")
    private val includeSubdirectoriesCheckBox = JBCheckBox("Include subdirectories")

    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Dart Barrel Files"

    override fun createComponent(): JComponent {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Barrel file name:"), barrelFileNameCombo, 1, false)
            .addLabeledComponent(JBLabel("Custom name:"), customBarrelFileName, 1, false)
            .addComponent(includeExportsCheckBox, 1)
            .addComponent(includeImportsCheckBox, 1)
            .addComponent(showHidePrivateCheckBox, 1)
            .addComponent(showShowPublicCheckBox, 1)
            .addComponent(includeHeaderCheckBox, 1)
            .addLabeledComponent(JBLabel("Header comment:"), headerCommentArea, 1, false)
            .addComponent(autoGenerateCheckBox, 1)
            .addLabeledComponent(JBLabel("Exclude patterns (regex):"), excludePatternsArea, 1, false)
            .addComponent(sortExportsCheckBox, 1)
            .addComponent(groupByDirectoryCheckBox, 1)
            .addComponent(includeSubdirectoriesCheckBox, 1)
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
                includeExportsCheckBox.isSelected != settings.includeExports ||
                includeImportsCheckBox.isSelected != settings.includeImports ||
                showHidePrivateCheckBox.isSelected != settings.showHidePrivate ||
                showShowPublicCheckBox.isSelected != settings.showShowPublic ||
                includeHeaderCheckBox.isSelected != settings.includeHeader ||
                headerCommentArea.text != settings.headerComment ||
                autoGenerateCheckBox.isSelected != settings.autoGenerate ||
                excludePatternsArea.text != settings.excludePatterns.joinToString("\n") ||
                sortExportsCheckBox.isSelected != settings.sortExports ||
                groupByDirectoryCheckBox.isSelected != settings.groupByDirectory
    }

    override fun apply() {
        settings.barrelFileName = getSelectedBarrelFileName()
        settings.includeExports = includeExportsCheckBox.isSelected
        settings.includeImports = includeImportsCheckBox.isSelected
        settings.showHidePrivate = showHidePrivateCheckBox.isSelected
        settings.showShowPublic = showShowPublicCheckBox.isSelected
        settings.includeHeader = includeHeaderCheckBox.isSelected
        settings.headerComment = headerCommentArea.text
        settings.autoGenerate = autoGenerateCheckBox.isSelected
        settings.excludePatterns = excludePatternsArea.text.split("\n").toMutableList()
        settings.sortExports = sortExportsCheckBox.isSelected
        settings.groupByDirectory = groupByDirectoryCheckBox.isSelected
        settings.includeSubdirectories = includeSubdirectoriesCheckBox.isSelected
        settings.useIndexAsDefault = barrelFileNameCombo.selectedItem == "index.dart"
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
        includeExportsCheckBox.isSelected = settings.includeExports
        includeImportsCheckBox.isSelected = settings.includeImports
        showHidePrivateCheckBox.isSelected = settings.showHidePrivate
        showShowPublicCheckBox.isSelected = settings.showShowPublic
        includeHeaderCheckBox.isSelected = settings.includeHeader
        headerCommentArea.text = settings.headerComment
        autoGenerateCheckBox.isSelected = settings.autoGenerate
        excludePatternsArea.text = settings.excludePatterns.joinToString("\n")
        sortExportsCheckBox.isSelected = settings.sortExports
        groupByDirectoryCheckBox.isSelected = settings.groupByDirectory
        includeSubdirectoriesCheckBox.isSelected = settings.includeSubdirectories
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