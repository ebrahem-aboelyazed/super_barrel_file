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

    fun testFileWithMatchingNameButNonBarrelContentNotDetected() {
        val settings = DartBarrelSettings.getInstance().apply {
            barrelFileName = "{folder_name}.dart"
        }

        val detector = BarrelFileDetector(
            PsiManager.getInstance(project),
            settings,
        )

        myFixture.tempDirFixture.createFile(
            "lib/feature/feature.dart",
            "class Feature {\n  void doSomething() {}\n}\n",
        )
        val file = myFixture.findFileInTempDir("lib/feature/feature.dart")

        assertFalse(
            "Should not detect feature.dart as barrel " +
                "just because name matches folder, " +
                "content must be export-only",
            detector.isBarrelFile(file),
        )
    }

    fun testEmptyFileNotDetected() {
        val settings = DartBarrelSettings.getInstance().apply {
            barrelFileName = "{folder_name}.dart"
        }

        val detector = BarrelFileDetector(
            PsiManager.getInstance(project),
            settings,
        )

        myFixture.tempDirFixture.createFile("lib/feature/feature.dart", "")
        val file = myFixture.findFileInTempDir("lib/feature/feature.dart")

        assertFalse(
            "Should not detect empty file as barrel",
            detector.isBarrelFile(file),
        )
    }

    fun testFileWithOnlyCommentsNotDetected() {
        val settings = DartBarrelSettings.getInstance().apply {
            barrelFileName = "{folder_name}.dart"
        }

        val detector = BarrelFileDetector(
            PsiManager.getInstance(project),
            settings,
        )

        myFixture.tempDirFixture.createFile(
            "lib/feature/feature.dart",
            "// This is a comment\n// Another comment\n",
        )
        val file = myFixture.findFileInTempDir("lib/feature/feature.dart")

        assertFalse(
            "Should not detect comment-only file as barrel",
            detector.isBarrelFile(file),
        )
    }
}

