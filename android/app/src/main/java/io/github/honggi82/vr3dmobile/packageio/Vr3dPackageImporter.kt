package io.github.honggi82.vr3dmobile.packageio

import io.github.honggi82.vr3dmobile.domain.Vr3dManifest
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class PackageImportException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class ImportedPackage(val manifest: Vr3dManifest, val directory: Path)

object Vr3dPackageImporter {
    const val MAX_PACKAGE_BYTES = 512L * 1024L * 1024L
    private const val MAX_MANIFEST_BYTES = 512L * 1024L
    private const val MAX_ENTRY_BYTES = 128L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 512L * 1024L * 1024L
    private const val MAX_ENTRIES = 64
    private const val MAX_COMPRESSION_RATIO = 200L

    fun importPackage(packagePath: Path, libraryRoot: Path): ImportedPackage {
        if (!packagePath.fileName.toString().lowercase().endsWith(".vr3d")) {
            throw PackageImportException("The selected file must use the .vr3d extension")
        }
        val packageSize = try {
            Files.size(packagePath)
        } catch (error: IOException) {
            throw PackageImportException("The package cannot be read", error)
        }
        if (packageSize !in 1..MAX_PACKAGE_BYTES) throw PackageImportException("Package size is outside the allowed limit")
        requireZipSignature(packagePath)

        Files.createDirectories(libraryRoot)
        try {
            ZipFile(packagePath.toFile()).use { zip ->
                val entries = collectEntries(zip)
                val manifestEntry = entries[MANIFEST_PATH]
                    ?: throw PackageImportException("manifest.json is missing")
                if (manifestEntry.size !in 1..MAX_MANIFEST_BYTES) {
                    throw PackageImportException("manifest.json size is invalid")
                }
                val manifestBytes = readLimited(zip, manifestEntry, MAX_MANIFEST_BYTES)
                val manifest = try {
                    Vr3dManifest.parse(manifestBytes)
                } catch (error: IllegalArgumentException) {
                    throw PackageImportException("Manifest validation failed: ${error.message}", error)
                }
                validateEntrySet(entries, manifest)
                return extractVerified(zip, entries, manifest, manifestBytes, libraryRoot)
            }
        } catch (error: PackageImportException) {
            throw error
        } catch (error: IOException) {
            throw PackageImportException("The ZIP container is invalid or unreadable", error)
        }
    }

    private fun collectEntries(zip: ZipFile): Map<String, ZipEntry> {
        val result = linkedMapOf<String, ZipEntry>()
        val enumeration = zip.entries()
        var count = 0
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement()
            count++
            if (count > MAX_ENTRIES) throw PackageImportException("ZIP has too many entries")
            validateZipPath(entry.name)
            if (entry.isDirectory) {
                if (entry.name != "views/") throw PackageImportException("Unexpected directory entry: ${entry.name}")
                continue
            }
            if (result.put(entry.name, entry) != null) {
                throw PackageImportException("Duplicate ZIP entry: ${entry.name}")
            }
        }
        return result
    }

    private fun validateEntrySet(entries: Map<String, ZipEntry>, manifest: Vr3dManifest) {
        val expected = manifest.files.keys + MANIFEST_PATH
        if (entries.keys != expected) {
            throw PackageImportException("ZIP entries do not exactly match the manifest")
        }
        var totalSize = 0L
        manifest.files.forEach { (path, digest) ->
            val entry = entries[path] ?: throw PackageImportException("Missing payload: $path")
            if (entry.size != digest.size || entry.size !in 1..MAX_ENTRY_BYTES) {
                throw PackageImportException("Declared size does not match ZIP metadata: $path")
            }
            if (entry.compressedSize < 0) throw PackageImportException("Compressed size is unavailable: $path")
            if (entry.size > maxOf(1L, entry.compressedSize) * MAX_COMPRESSION_RATIO) {
                throw PackageImportException("Compression ratio is unsafe: $path")
            }
            totalSize = Math.addExact(totalSize, entry.size)
            if (totalSize > MAX_TOTAL_BYTES) throw PackageImportException("Expanded package is too large")
        }
    }

    private fun extractVerified(
        zip: ZipFile,
        entries: Map<String, ZipEntry>,
        manifest: Vr3dManifest,
        manifestBytes: ByteArray,
        libraryRoot: Path,
    ): ImportedPackage {
        val normalizedRoot = libraryRoot.toAbsolutePath().normalize()
        val finalDirectory = normalizedRoot.resolve(manifest.packageId).normalize()
        if (!finalDirectory.startsWith(normalizedRoot)) throw PackageImportException("Unsafe package identifier")
        if (Files.exists(finalDirectory)) throw PackageImportException("This package is already imported")
        val staging = normalizedRoot.resolve(".import-${UUID.randomUUID()}").normalize()
        if (!staging.startsWith(normalizedRoot)) throw PackageImportException("Unsafe staging path")

        try {
            Files.createDirectory(staging)
            Files.write(staging.resolve(MANIFEST_PATH), manifestBytes, StandardOpenOption.CREATE_NEW)
            manifest.files.forEach { (path, expected) ->
                val target = staging.resolve(path).normalize()
                if (!target.startsWith(staging)) throw PackageImportException("Unsafe extraction path: $path")
                Files.createDirectories(target.parent)
                val actual = copyAndDigest(zip, entries.getValue(path), target, expected.size)
                if (actual != expected.sha256) throw PackageImportException("SHA-256 mismatch: $path")
            }
            try {
                Files.move(staging, finalDirectory, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(staging, finalDirectory)
            }
            return ImportedPackage(manifest, finalDirectory)
        } catch (error: Exception) {
            deleteTree(staging)
            if (error is PackageImportException) throw error
            throw PackageImportException("Package extraction failed", error)
        }
    }

    private fun copyAndDigest(zip: ZipFile, entry: ZipEntry, target: Path, expectedSize: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        BufferedInputStream(zip.getInputStream(entry)).use { input ->
            BufferedOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    count += read
                    if (count > expectedSize || count > MAX_ENTRY_BYTES) {
                        throw PackageImportException("Payload exceeded its declared size: ${entry.name}")
                    }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        if (count != expectedSize) throw PackageImportException("Payload size mismatch: ${entry.name}")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readLimited(zip: ZipFile, entry: ZipEntry, limit: Long): ByteArray {
        BufferedInputStream(zip.getInputStream(entry)).use { input ->
            val result = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var count = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                count += read
                if (count > limit) throw PackageImportException("manifest.json exceeds the size limit")
                result.write(buffer, 0, read)
            }
            return result.toByteArray()
        }
    }

    private fun requireZipSignature(path: Path) {
        val signature = ByteArray(4)
        val read = Files.newInputStream(path).use { it.read(signature) }
        if (read != 4 || signature[0] != 0x50.toByte() || signature[1] != 0x4b.toByte() ||
            signature[2] != 0x03.toByte() || signature[3] != 0x04.toByte()
        ) {
            throw PackageImportException("The file is not a supported ZIP package")
        }
    }

    private fun validateZipPath(path: String) {
        if (path.isEmpty() || path.length > 160 || path.contains('\\') || path.contains('\u0000') ||
            path.startsWith('/') || DRIVE_PREFIX.containsMatchIn(path) ||
            path.split('/').any { it.isEmpty() || it == "." || it == ".." }
        ) {
            if (path != "views/") throw PackageImportException("Unsafe ZIP path")
        }
    }

    private fun deleteTree(root: Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private const val MANIFEST_PATH = "manifest.json"
    private val DRIVE_PREFIX = Regex("^[A-Za-z]:")
}
