package io.github.honggi82.vr3dmobile.domain

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

data class SourceInfo(val path: String, val width: Int, val height: Int)
data class ViewSpec(val row: Int, val column: Int, val pitch: Int, val roll: Int, val path: String)
data class FileDigest(val sha256: String, val size: Long)

data class Vr3dManifest(
    val packageId: String,
    val createdAt: Instant,
    val source: SourceInfo,
    val modelVariant: String,
    val views: List<ViewSpec>,
    val files: Map<String, FileDigest>,
) {
    companion object {
        const val SCHEMA_VERSION = "1.0"
        val ROLL_ANGLES = listOf(-12, -8, -4, 0, 4, 8, 12)
        val PITCH_ANGLES = listOf(-8, -4, 0, 4, 8)

        fun parse(bytes: ByteArray): Vr3dManifest {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val text = try {
                decoder.decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: Exception) {
                throw JsonFormatException("manifest.json is not valid UTF-8")
            }
            return parse(text)
        }

        fun parse(text: String): Vr3dManifest {
            val root = StrictJson.parse(text).objectValue("manifest")
            root.exactKeys(
                "manifest",
                setOf("schemaVersion", "packageId", "createdAt", "source", "depth", "model", "viewGrid", "files"),
            )
            requireExact(root.required("schemaVersion").stringValue("schemaVersion"), SCHEMA_VERSION, "schemaVersion")

            val packageId = root.required("packageId").stringValue("packageId")
            if (!UUID_PATTERN.matches(packageId)) throw JsonFormatException("packageId must be a canonical UUID")
            try {
                UUID.fromString(packageId)
            } catch (_: IllegalArgumentException) {
                throw JsonFormatException("packageId must be a canonical UUID")
            }

            val createdAtText = root.required("createdAt").stringValue("createdAt")
            val createdAt = try {
                Instant.parse(createdAtText)
            } catch (_: DateTimeParseException) {
                throw JsonFormatException("createdAt must be an ISO-8601 UTC instant")
            }

            val sourceObject = root.required("source").objectValue("source")
            sourceObject.exactKeys("source", setOf("path", "mime", "width", "height"))
            requireExact(sourceObject.required("path").stringValue("source.path"), "source.webp", "source.path")
            requireExact(sourceObject.required("mime").stringValue("source.mime"), "image/webp", "source.mime")
            val width = boundedInt(sourceObject.required("width"), "source.width", 1, 1920)
            val height = boundedInt(sourceObject.required("height"), "source.height", 1, 1920)

            val depthObject = root.required("depth").objectValue("depth")
            depthObject.exactKeys("depth", setOf("path", "encoding", "nearValue"))
            requireExact(depthObject.required("path").stringValue("depth.path"), "depth.png", "depth.path")
            requireExact(depthObject.required("encoding").stringValue("depth.encoding"), "png16", "depth.encoding")
            if (depthObject.required("nearValue").longValue("depth.nearValue") != 65_535L) {
                throw JsonFormatException("depth.nearValue must be 65535")
            }

            val modelObject = root.required("model").objectValue("model")
            modelObject.exactKeys("model", setOf("name", "variant"))
            requireExact(modelObject.required("name").stringValue("model.name"), "Video-Depth-Anything", "model.name")
            val variant = modelObject.required("variant").stringValue("model.variant")
            if (variant !in setOf("vitl", "vits")) throw JsonFormatException("model.variant is invalid")

            val gridObject = root.required("viewGrid").objectValue("viewGrid")
            gridObject.exactKeys("viewGrid", setOf("rollAngles", "pitchAngles", "views"))
            requireAngles(gridObject.required("rollAngles"), ROLL_ANGLES, "viewGrid.rollAngles")
            requireAngles(gridObject.required("pitchAngles"), PITCH_ANGLES, "viewGrid.pitchAngles")
            val views = parseViews(gridObject.required("views"))
            val files = parseFiles(root.required("files"))

            val expectedPaths = buildSet {
                add("source.webp")
                add("depth.png")
                views.forEach { add(it.path) }
            }
            if (files.keys != expectedPaths) throw JsonFormatException("files must describe exactly the 37 payload files")

            return Vr3dManifest(
                packageId = packageId,
                createdAt = createdAt,
                source = SourceInfo("source.webp", width, height),
                modelVariant = variant,
                views = views.sortedWith(compareBy(ViewSpec::row, ViewSpec::column)),
                files = files,
            )
        }

        private fun parseViews(value: JsonValue): List<ViewSpec> {
            val rawViews = value.arrayValue("viewGrid.views")
            if (rawViews.size != 35) throw JsonFormatException("viewGrid.views must contain exactly 35 entries")
            val result = mutableListOf<ViewSpec>()
            val seen = mutableSetOf<Pair<Int, Int>>()
            rawViews.forEachIndexed { index, raw ->
                val view = raw.objectValue("viewGrid.views[$index]")
                view.exactKeys("viewGrid.views[$index]", setOf("row", "column", "pitch", "roll", "path"))
                val row = boundedInt(view.required("row"), "view.row", 0, 4)
                val column = boundedInt(view.required("column"), "view.column", 0, 6)
                val pitch = view.required("pitch").longValue("view.pitch").toInt()
                val roll = view.required("roll").longValue("view.roll").toInt()
                val path = view.required("path").stringValue("view.path")
                val expectedPath = "views/view_r${row}_c${column}.webp"
                if (pitch != PITCH_ANGLES[row] || roll != ROLL_ANGLES[column] || path != expectedPath) {
                    throw JsonFormatException("View $row,$column does not match its grid position")
                }
                if (!seen.add(row to column)) throw JsonFormatException("Duplicate view position: $row,$column")
                result += ViewSpec(row, column, pitch, roll, path)
            }
            if (seen.size != 35) throw JsonFormatException("Every grid position must appear once")
            return result
        }

        private fun parseFiles(value: JsonValue): Map<String, FileDigest> {
            val files = value.objectValue("files")
            val result = linkedMapOf<String, FileDigest>()
            files.values.forEach { (path, rawDigest) ->
                if (!isSafePayloadPath(path)) throw JsonFormatException("Invalid payload path: $path")
                val digest = rawDigest.objectValue("files.$path")
                digest.exactKeys("files.$path", setOf("sha256", "size"))
                val sha256 = digest.required("sha256").stringValue("files.$path.sha256")
                if (!SHA256_PATTERN.matches(sha256)) throw JsonFormatException("Invalid SHA-256 for $path")
                val size = digest.required("size").longValue("files.$path.size")
                if (size < 1) throw JsonFormatException("Invalid size for $path")
                result[path] = FileDigest(sha256, size)
            }
            return result
        }

        private fun requireAngles(value: JsonValue, expected: List<Int>, name: String) {
            val actual = value.arrayValue(name).mapIndexed { index, item -> item.longValue("$name[$index]").toInt() }
            if (actual != expected) throw JsonFormatException("$name does not match the v1 grid")
        }

        private fun boundedInt(value: JsonValue, name: String, minimum: Int, maximum: Int): Int {
            val longValue = value.longValue(name)
            if (longValue !in minimum.toLong()..maximum.toLong()) {
                throw JsonFormatException("$name must be between $minimum and $maximum")
            }
            return longValue.toInt()
        }

        private fun requireExact(actual: String, expected: String, name: String) {
            if (actual != expected) throw JsonFormatException("$name must be $expected")
        }

        private fun isSafePayloadPath(path: String): Boolean =
            path == "source.webp" || path == "depth.png" || VIEW_PATH_PATTERN.matches(path)

        private val UUID_PATTERN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
        private val VIEW_PATH_PATTERN = Regex("^views/view_r[0-4]_c[0-6]\\.webp$")
    }
}
