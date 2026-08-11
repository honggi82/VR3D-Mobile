package io.github.honggi82.vr3dmobile.domain

import java.security.MessageDigest

object ManifestFixtures {
    const val PACKAGE_ID = "123e4567-e89b-12d3-a456-426614174000"

    fun payloads(): LinkedHashMap<String, ByteArray> = linkedMapOf<String, ByteArray>().apply {
        put("source.webp", byteArrayOf(1, 2, 3))
        put("depth.png", byteArrayOf(4, 5, 6))
        for (row in 0..4) for (column in 0..6) {
            put("views/view_r${row}_c${column}.webp", byteArrayOf(row.toByte(), column.toByte(), 7))
        }
    }

    fun manifest(payloads: Map<String, ByteArray> = payloads()): String {
        val views = buildList {
            for (row in 0..4) for (column in 0..6) {
                add("""{"row":$row,"column":$column,"pitch":${Vr3dManifest.PITCH_ANGLES[row]},"roll":${Vr3dManifest.ROLL_ANGLES[column]},"path":"views/view_r${row}_c${column}.webp"}""")
            }
        }.joinToString(",")
        val files = payloads.entries.joinToString(",") { (path, bytes) ->
            """"$path":{"sha256":"${sha256(bytes)}","size":${bytes.size}}"""
        }
        return """{"schemaVersion":"1.0","packageId":"$PACKAGE_ID","createdAt":"2026-08-12T00:00:00Z","source":{"path":"source.webp","mime":"image/webp","width":3,"height":2},"depth":{"path":"depth.png","encoding":"png16","nearValue":65535},"model":{"name":"Video-Depth-Anything","variant":"vitl"},"viewGrid":{"rollAngles":[-12,-8,-4,0,4,8,12],"pitchAngles":[-8,-4,0,4,8],"views":[$views]},"files":{$files}}"""
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
