package com.dartbarrel.plugin.services

import com.dartbarrel.plugin.settings.DartBarrelSettings
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DartBarrelServiceTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        DartBarrelSettings.getInstance().apply {
            barrelFileName = "{folder_name}.dart"
            includeHeader = false
            headerComment = ""
            autoGenerate = true
            sortExports = true
            excludePatterns = mutableListOf(
                ".*\\.g\\.dart",
                ".*\\.freezed\\.dart",
                ".*\\.gr\\.dart",
                ".*\\.config\\.dart",
                ".*\\.part\\.dart",
            )
        }
    }

    fun testManualGenerationRemainsStableDuringImmediateSync() {
        createDartFile("lib/feature/foo.dart", "class Foo {}\n")
        createDartFile("lib/feature/bar.dart", "class Bar {}\n")

        val directory = findDirectory("lib/feature")
        val service = project.service<DartBarrelService>()
        val plan = service.prepareGeneration(directory)

        assertEquals(
            listOf("bar.dart", "foo.dart"),
            plan.candidates.map { it.relativePath },
        )

        val generated = service.generateBarrelFile(
            directory = directory,
            plan = plan,
            selectedRelativePaths = plan.candidates.map { it.relativePath }.toSet(),
            barrelFileName = plan.barrelFileName,
        )

        assertNotNull(generated)
        assertEquals(
            "export 'bar.dart';\nexport 'foo.dart';\n",
            generated!!.text,
        )

        service.synchronizeExistingBarrel(directory.virtualFile)

        assertEquals(
            "export 'bar.dart';\nexport 'foo.dart';\n",
            findPsiFile("lib/feature/feature.dart").text,
        )
    }

    fun testPrepareGenerationPrefersNestedBarrelsOverCoveredFiles() {
        createDartFile("lib/feature/foo.dart", "class Foo {}\n")
        createDartFile("lib/feature/nested/a.dart", "class A {}\n")
        createDartFile("lib/feature/nested/b.dart", "class B {}\n")
        createDartFile(
            "lib/feature/nested/nested.dart",
            "export 'a.dart';\nexport 'b.dart';\n",
        )

        val directory = findDirectory("lib/feature")
        val service = project.service<DartBarrelService>()
        val plan = service.prepareGeneration(directory)

        assertEquals(
            listOf("foo.dart", "nested/nested.dart"),
            plan.candidates.map { it.relativePath },
        )
    }

    fun testRegenerateBarrelIncludesNewlyAddedFiles() {
        createDartFile("lib/feature/foo.dart", "class Foo {}\n")

        val directory = findDirectory("lib/feature")
        val service = project.service<DartBarrelService>()
        val initialPlan = service.prepareGeneration(directory)
        service.generateBarrelFile(
            directory = directory,
            plan = initialPlan,
            selectedRelativePaths = initialPlan.candidates.map { it.relativePath }.toSet(),
            barrelFileName = initialPlan.barrelFileName,
        )

        createDartFile("lib/feature/baz.dart", "class Baz {}\n")

        service.regenerateBarrelFile(findPsiFile("lib/feature/feature.dart"))

        assertEquals(
            "export 'baz.dart';\nexport 'foo.dart';\n",
            findPsiFile("lib/feature/feature.dart").text,
        )
    }

    private fun createDartFile(path: String, text: String) {
        myFixture.tempDirFixture.createFile(path, text)
    }

    private fun findDirectory(path: String): PsiDirectory {
        val virtualFile = myFixture.findFileInTempDir(path)
        return PsiManager.getInstance(project).findDirectory(virtualFile)!!
    }

    private fun findPsiFile(path: String): PsiFile {
        val virtualFile = myFixture.findFileInTempDir(path)
        return PsiManager.getInstance(project).findFile(virtualFile)!!
    }
}

