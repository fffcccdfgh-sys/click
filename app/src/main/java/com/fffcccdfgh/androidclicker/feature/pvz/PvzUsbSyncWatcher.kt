package com.fffcccdfgh.androidclicker.feature.pvz

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class PvzUsbSyncUpdate(
    val code: String,
    val scriptName: String,
    val signature: String
)

data class PvzUsbSyncBatch(
    val batchId: String,
    val mode: String,
    val scripts: List<PvzUsbSyncUpdate>
) {
    val replaceAll: Boolean
        get() = mode == MODE_REPLACE_ALL

    companion object {
        const val MODE_MERGE = "merge"
        const val MODE_REPLACE_ALL = "replace_all"
    }
}

class PvzUsbSyncWatcher(private val context: Context) {
    private val syncDir: File?
        get() = context.getExternalFilesDir(SYNC_DIR_NAME)

    fun readLatestUpdate(): PvzUsbSyncUpdate? {
        val dir = syncDir ?: return null
        val scriptFile = File(dir, SCRIPT_FILE_NAME)
        if (!scriptFile.isFile) return null

        val code = try {
            scriptFile.readUtf8TextWithoutBom()
        } catch (_: Exception) {
            return null
        }
        if (code.isBlank()) return null

        val meta = readMeta(File(dir, META_FILE_NAME))
        val scriptName = meta?.optString("scriptName")
            ?.takeIf { it.isNotBlank() }
            ?: scriptFile.nameWithoutExtension
        val updatedAt = meta?.optString("updatedAt")
            ?.takeIf { it.isNotBlank() }
            ?: scriptFile.lastModified().toString()
        val sourcePath = meta?.optString("sourcePath")
            ?.takeIf { it.isNotBlank() }
            ?: scriptFile.absolutePath
        val signature = "$sourcePath|$updatedAt|${scriptFile.length()}|${code.hashCode()}"

        return PvzUsbSyncUpdate(
            code = code,
            scriptName = scriptName,
            signature = signature
        )
    }

    fun readBatch(): PvzUsbSyncBatch? {
        val dir = syncDir ?: return null
        val batchDir = File(dir, BATCH_DIR_NAME)
        val manifest = File(batchDir, BATCH_MANIFEST_FILE_NAME)
        if (!manifest.isFile) return null

        val root = try {
            JSONObject(manifest.readUtf8TextWithoutBom())
        } catch (_: Exception) {
            return null
        }
        val batchId = root.optString("batchId").takeIf { it.isNotBlank() } ?: return null
        val mode = root.optString("mode")
            .takeIf { it == PvzUsbSyncBatch.MODE_REPLACE_ALL || it == PvzUsbSyncBatch.MODE_MERGE }
            ?: PvzUsbSyncBatch.MODE_MERGE
        val scriptsJson = root.optJSONArray("scripts") ?: JSONArray()
        val scripts = mutableListOf<PvzUsbSyncUpdate>()

        for (i in 0 until scriptsJson.length()) {
            val item = scriptsJson.optJSONObject(i) ?: continue
            val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
            val path = item.optString("path").takeIf { it.isNotBlank() } ?: continue
            val scriptFile = File(batchDir, path)
            val update = readUpdateFile(scriptFile, name) ?: continue
            scripts.add(update)
        }

        if (scripts.isEmpty()) return null
        return PvzUsbSyncBatch(batchId = batchId, mode = mode, scripts = scripts)
    }

    private fun readMeta(metaFile: File): JSONObject? {
        if (!metaFile.isFile) return null
        return try {
            JSONObject(metaFile.readUtf8TextWithoutBom())
        } catch (_: Exception) {
            null
        }
    }

    private fun readUpdateFile(scriptFile: File, explicitName: String? = null): PvzUsbSyncUpdate? {
        val code = try {
            scriptFile.readUtf8TextWithoutBom()
        } catch (_: Exception) {
            return null
        }
        if (code.isBlank()) return null

        val scriptName = explicitName
            ?: scriptFile.nameWithoutExtension
                .removePrefix("pvz2_")
                .takeIf { it.isNotBlank() }
            ?: scriptFile.nameWithoutExtension
        val signature = "${scriptFile.absolutePath}|${scriptFile.lastModified()}|${scriptFile.length()}|${code.hashCode()}"
        return PvzUsbSyncUpdate(
            code = code,
            scriptName = scriptName,
            signature = signature
        )
    }

    companion object {
        const val SYNC_DIR_NAME = "sync"
        const val SCRIPT_FILE_NAME = "pvz2.lua"
        const val META_FILE_NAME = "pvz2.meta.json"
        const val BATCH_DIR_NAME = "batch"
        const val BATCH_MANIFEST_FILE_NAME = "pvz2_batch.json"
    }
}

private fun File.readUtf8TextWithoutBom(): String {
    return readText(Charsets.UTF_8).removePrefix("\uFEFF")
}
