package com.dartbarrel.plugin.inspections

import com.dartbarrel.plugin.services.DartBarrelService
import com.dartbarrel.plugin.settings.DartBarrelSettings
import com.intellij.openapi.components.service
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class OutdatedBarrelDetectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        DartBarrelSettings.getInstance().apply {
            barrelFileName = "{folder_name}.dart"
            includeHeader = false
            headerComment = ""
            autoGenerate = false
            sortExports = true
            excludePatterns = mutableListOf(
                ".*\\.g\\.dart",
                ".*\\.freezed\\.dart",
            )
        }
    }

    fun testNeedsRegenerationDetectsOutdatedBarrel() {
        myFixture.tempDirFixture.createFile("lib/feature/foo.dart", "class Foo {}\n")
        myFixture.tempDirFixture.createFile(
            "lib/feature/feature.dart",
            "// This barrel is outdated\n",
        )

        val barrelFile = PsiManager.getInstance(project)
            .findFile(myFixture.findFileInTempDir("lib/feature/feature.dart"))!!
        val service = project.service<DartBarrelService>()

        assertTrue(
            "Barrel with wrong content should need regeneration",
            service.needsRegeneration(barrelFile),
        )
    }

    fun testNeedsRegenerationReturnsFalseWhenBarrelIsUpToDate() {
        myFixture.tempDirFixture.createFile("lib/feature/foo.dart", "class Foo {}\n")
        myFixture.tempDirFixture.createFile(
            "lib/feature/feature.dart",
            "export 'foo.dart';\n",
        )

        val barrelFile = PsiManager.getInstance(project)
            .findFile(myFixture.findFileInTempDir("lib/feature/feature.dart"))!!
        val service = project.service<DartBarrelService>()

        assertFalse(
            "Barrel with correct content should not need regeneration",
            service.needsRegeneration(barrelFile),
        )
    }

    fun testBarrelDetectionAndNeedsRegenerationTogether() {
        myFixture.tempDirFixture.createFile("lib/feature/foo.dart", "class Foo {}\n")
        myFixture.tempDirFixture.createFile("lib/feature/bar.dart", "class Bar {}\n")
        myFixture.tempDirFixture.createFile(
            "lib/feature/feature.dart",
            "export 'foo.dart';\n",
        )

        val barrelFile = PsiManager.getInstance(project)
            .findFile(myFixture.findFileInTempDir("lib/feature/feature.dart"))!!
        val service = project.service<DartBarrelService>()

        assertTrue("Should be recognized as barrel file", service.isBarrelFile(barrelFile))
        assertTrue(
            "Should need regeneration (missing bar.dart export)",
            service.needsRegeneration(barrelFile),
        )
    }
}

