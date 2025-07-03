package com.dartbarrel.plugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "DartBarrelSettings",
    storages = [Storage("DartBarrelSettings.xml")]
)
class DartBarrelSettings : PersistentStateComponent<DartBarrelSettings> {

    var barrelFileName: String = "{folder_name}.dart"
    var useIndexAsDefault: Boolean = false
    var includeExports: Boolean = true
    var includeImports: Boolean = false
    var showHidePrivate: Boolean = false
    var showShowPublic: Boolean = false
    var includeHeader: Boolean = true
    var headerComment: String = "// Auto-generated barrel file"
    var autoGenerate: Boolean = false
    var excludePatterns: MutableList<String> = mutableListOf(".*\\.g\\.dart", ".*\\.freezed\\.dart")
    var sortExports: Boolean = true
    var groupByDirectory: Boolean = false
    var includeSubdirectories: Boolean = false

    override fun getState(): DartBarrelSettings = this

    override fun loadState(state: DartBarrelSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): DartBarrelSettings {
            return ApplicationManager.getApplication()
                .getService(DartBarrelSettings::class.java)
        }
    }
}