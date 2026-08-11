package io.github.honggi82.vr3dmobile.storage

import android.content.Context
import io.github.honggi82.vr3dmobile.domain.Vr3dManifest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

data class ProjectSummary(
    val packageId: String,
    val createdAt: Instant,
    val modelVariant: String,
    val directory: Path,
)

class ProjectRepository(context: Context) {
    val root: Path = context.filesDir.toPath().resolve("projects")

    init {
        Files.createDirectories(root)
    }

    fun list(): List<ProjectSummary> {
        if (!Files.exists(root)) return emptyList()
        val projects = mutableListOf<ProjectSummary>()
        Files.list(root).use { paths ->
            paths.forEach { directory ->
                if (!Files.isDirectory(directory) || directory.fileName.toString().startsWith(".import-")) return@forEach
                try {
                    val bytes = Files.readAllBytes(directory.resolve("manifest.json"))
                    val manifest = Vr3dManifest.parse(bytes)
                    if (directory.fileName.toString() == manifest.packageId) {
                        projects += ProjectSummary(manifest.packageId, manifest.createdAt, manifest.modelVariant, directory)
                    }
                } catch (_: Exception) {
                    // Corrupt local entries are hidden; imports are always verified before reaching this directory.
                }
            }
        }
        return projects.sortedByDescending(ProjectSummary::createdAt)
    }

    fun load(packageId: String): Pair<Vr3dManifest, Path>? {
        val directory = safeProjectPath(packageId) ?: return null
        if (!Files.isDirectory(directory)) return null
        return try {
            Vr3dManifest.parse(Files.readAllBytes(directory.resolve("manifest.json"))) to directory
        } catch (_: Exception) {
            null
        }
    }

    fun delete(packageId: String): Boolean {
        val directory = safeProjectPath(packageId) ?: return false
        if (!Files.exists(directory)) return false
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
        return true
    }

    private fun safeProjectPath(packageId: String): Path? {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val candidate = normalizedRoot.resolve(packageId).normalize()
        return candidate.takeIf { it.parent == normalizedRoot }
    }
}
