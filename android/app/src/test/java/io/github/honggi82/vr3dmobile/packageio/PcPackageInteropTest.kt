package io.github.honggi82.vr3dmobile.packageio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class PcPackageInteropTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun importsPackageProducedByPcPipeline() {
        val packagePath = temporary.root.toPath().resolve("pc-reference.vr3d")
        requireNotNull(javaClass.getResourceAsStream("/pc-reference.vr3d")).use { input ->
            Files.copy(input, packagePath)
        }

        val imported = Vr3dPackageImporter.importPackage(
            packagePath,
            temporary.newFolder("pc-library").toPath(),
        )

        assertEquals("vits", imported.manifest.modelVariant)
        assertEquals(35, imported.manifest.views.size)
        assertEquals(37, imported.manifest.files.size)
        assertTrue(Files.isRegularFile(imported.directory.resolve("depth.png")))
    }
}
