package io.github.honggi82.vr3dmobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Vr3dManifestTest {
    @Test
    fun parsesExactV1Manifest() {
        val manifest = Vr3dManifest.parse(ManifestFixtures.manifest())

        assertEquals(ManifestFixtures.PACKAGE_ID, manifest.packageId)
        assertEquals("vitl", manifest.modelVariant)
        assertEquals(35, manifest.views.size)
        assertEquals(37, manifest.files.size)
    }

    @Test
    fun rejectsUnknownRootProperty() {
        val invalid = ManifestFixtures.manifest().replaceFirst("{", "{\"unexpected\":true,")

        assertThrows(JsonFormatException::class.java) { Vr3dManifest.parse(invalid) }
    }

    @Test
    fun rejectsGridAngleDeviation() {
        val invalid = ManifestFixtures.manifest().replace("[-12,-8,-4,0,4,8,12]", "[-11,-8,-4,0,4,8,12]")

        assertThrows(JsonFormatException::class.java) { Vr3dManifest.parse(invalid) }
    }
}
