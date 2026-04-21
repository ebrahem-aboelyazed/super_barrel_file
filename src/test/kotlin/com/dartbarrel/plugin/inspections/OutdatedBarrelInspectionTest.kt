package com.dartbarrel.plugin.inspections

import com.dartbarrel.plugin.services.BarrelFileDetector
import com.dartbarrel.plugin.settings.DartBarrelSettings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class OutdatedBarrelInspectionTest : BasePlatformTestCase() {

    fun testBarrelFileDetectionWithFolderNameTemplate() {
        val settings = DartBarrelSettings.getInstance().apply {
            barrelFileName = "{folder_name}.dart"
        }

        val detector = BarrelFileDetector(
            PsiManager.getInstance(project),
            settings,
        )

        myFixture.tempDirFixture.createFile("lib/feature/feature.dart", "export 'foo.dart';\n")
        val barrelFile = myFixture.findFileInTempDir("lib/feature/feature.dart")

        assertTrue(
            "Should recognize feature.dart as barrel for feature folder",
            detector.isBarrelFile(barrelFile),
        )
    }

    fun testBarrelFileDetectionWithIndexDart() {
        val settings = DartBarrelSettings.getInstance().apply {
            barrelFileName = "index.dart"
        }

        val detector = BarrelFileDetector(
            PsiManager.getInstance(project),
            settings,
        )

        myFixture.tempDirFixture.createFile("lib/feature/index.dart", "export 'foo.dart';\n")
        val barrelFile = myFixture.findFileInTempDir("lib/feature/index.dart")

        assertTrue(
            "Should recognize index.dart as barrel",
            detector.isBarrelFile(barrelFile),
        )
    }

    fun testBarrelFileDetectionByContent() {
        val settings = DartBarrelSettings.getInstance().apply {
            barrelFileName = "custom.dart"
        }

        val detector = BarrelFileDetector(
            PsiManager.getInstance(project),
            settings,
        )

        myFixture.tempDirFixture.createFile("lib/feature/anything.dart", "export 'foo.dart';\n")
        val barrelFile = myFixture.findFileInTempDir("lib/feature/anything.dart")

        assertTrue(
            "Should recognize file with export-only content as barrel",
            detector.isBarrelFile(barrelFile),
        )
    }

    fun testNonBarrelFileNotDetected() {
        val settings = DartBarrelSettings.getInstance().apply {
            barrelFileName = "{folder_name}.dart"
        }

        val detector = BarrelFileDetector(
            PsiManager.getInstance(project),
            settings,
        )

        myFixture.tempDirFixture.createFile("lib/feature/foo.dart", "class Foo {}\n")
        val regularFile = myFixture.findFileInTempDir("lib/feature/foo.dart")

        assertFalse(
            "Should not recognize regular Dart file as barrel",
            detector.isBarrelFile(regularFile),
        )
    }
}

