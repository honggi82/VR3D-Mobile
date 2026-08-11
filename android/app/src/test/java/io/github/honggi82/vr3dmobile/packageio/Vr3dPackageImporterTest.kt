package io.github.honggi82.vr3dmobile.packageio

import io.github.honggi82.vr3dmobile.domain.ManifestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Vr3dPackageImporterTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun verifiesAndExtractsValidPackage() {
        val payloads = ManifestFixtures.payloads()
        val packagePath = temporary.root.toPath().resolve("valid.vr3d")
        writePackage(packagePath, ManifestFixtures.manifest(payloads), payloads)
        val library = temporary.newFolder("library").toPath()

        val imported = Vr3dPackageImporter.importPackage(packagePath, library)

        assertEquals(ManifestFixtures.PACKAGE_ID, imported.manifest.packageId)
        assertTrue(Files.isRegularFile(imported.directory.resolve("views/view_r4_c6.webp")))
    }

    @Test
    fun rejectsTraversalEntryBeforeExtraction() {
        val payloads = ManifestFixtures.payloads()
        val packagePath = temporary.root.toPath().resolve("traversal.vr3d")
        ZipOutputStream(Files.newOutputStream(packagePath)).use { zip ->
            add(zip, "manifest.json", ManifestFixtures.manifest(payloads).toByteArray(StandardCharsets.UTF_8))
            add(zip, "../outside.webp", byteArrayOf(1))
        }

        assertThrows(PackageImportException::class.java) {
            Vr3dPackageImporter.importPackage(packagePath, temporary.newFolder("traversal-library").toPath())
        }
        assertTrue(!Files.exists(temporary.root.toPath().resolve("outside.webp")))
    }

    @Test
    fun rejectsHashMismatchAndLeavesNoProject() {
        val payloads = ManifestFixtures.payloads()
        val manifest = ManifestFixtures.manifest(payloads)
        payloads["views/view_r0_c0.webp"] = byteArrayOf(99, 98, 97)
        val packagePath = temporary.root.toPath().resolve("mismatch.vr3d")
        writePackage(packagePath, manifest, payloads)
        val library = temporary.newFolder("mismatch-library").toPath()

        assertThrows(PackageImportException::class.java) {
            Vr3dPackageImporter.importPackage(packagePath, library)
        }
        assertTrue(!Files.exists(library.resolve(ManifestFixtures.PACKAGE_ID)))
    }

    private fun writePackage(path: java.nio.file.Path, manifest: String, payloads: Map<String, ByteArray>) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            add(zip, "manifest.json", manifest.toByteArray(StandardCharsets.UTF_8))
            payloads.forEach { (name, bytes) -> add(zip, name, bytes) }
        }
    }

    private fun add(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }
}
