package com.fffcccdfgh.androidclicker.feature.clicker

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fffcccdfgh.androidclicker.R
import com.fffcccdfgh.androidclicker.core.execution.ActionStep
import com.fffcccdfgh.androidclicker.core.storage.ScriptStorage
import com.fffcccdfgh.androidclicker.feature.clicker.floating.FloatingControlService
import com.fffcccdfgh.androidclicker.feature.clicker.floating.RunFloatingControlService
import java.io.File

class ScriptListActivity : AppCompatActivity() {

    private lateinit var scriptListContainer: LinearLayout

    private val scriptsChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            renderScriptList()
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { importScriptFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_script_list)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        scriptListContainer = findViewById(R.id.scriptListContainer)

        findViewById<TextView>(R.id.importScriptButton).setOnClickListener {
            importLauncher.launch(arrayOf("text/x-lua", "text/plain", "application/json", "*/*"))
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(ScriptStorage.ACTION_SCRIPTS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scriptsChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(scriptsChangedReceiver, filter)
        }
        renderScriptList()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(scriptsChangedReceiver)
        } catch (_: Exception) {
        }
    }

    private fun renderScriptList() {
        scriptListContainer.removeAllViews()
        val scripts = ScriptStorage.listScripts(this)

        if (scripts.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = getString(R.string.no_saved_scripts)
                textSize = 16f
                setTextColor(0xFF64748B.toInt())
                gravity = Gravity.CENTER
                background = roundedRect(0xFFFFFFFF.toInt(), 0xFFE5E7EB.toInt(), 18f)
                setPadding(dp(18f), dp(44f), dp(18f), dp(44f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            scriptListContainer.addView(emptyText)
            return
        }

        for (script in scripts) {
            val card = createScriptCard(script)
            scriptListContainer.addView(card)
        }
    }

    private fun createScriptCard(script: ScriptStorage.SavedScript): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(16f), dp(18f), dp(16f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14f)
            }
            background = roundedRect(0xFFFFFFFF.toInt(), 0xFFE5E7EB.toInt(), 18f)
        }

        val nameText = TextView(this).apply {
            text = script.name
            textSize = 20f
            setTextColor(0xFF111827.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        card.addView(nameText)

        val detailText = TextView(this).apply {
            text = scriptSummary(script)
            textSize = 13f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, dp(6f), 0, dp(14f))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        card.addView(detailText)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fun addButton(text: String, textColor: Int, fillColor: Int, strokeColor: Int, onClick: () -> Unit) {
            val btn = TextView(this).apply {
                this.text = text
                setTextColor(textColor)
                textSize = 13f
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedRect(fillColor, strokeColor, 21f)
                setPadding(dp(14f), 0, dp(14f), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(38f)
                ).apply {
                    marginEnd = dp(8f)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
            buttonRow.addView(btn)
        }

        addButton(getString(R.string.run_action), 0xFFFFFFFF.toInt(), 0xFF0F766E.toInt(), 0xFF0F766E.toInt()) {
            runScript(script)
        }
        addButton(getString(R.string.edit_script), 0xFFFFFFFF.toInt(), 0xFF166534.toInt(), 0xFF166534.toInt()) {
            editScript(script)
        }
        addButton(getString(R.string.rename_script), 0xFF92400E.toInt(), 0xFFFEF3C7.toInt(), 0xFFF59E0B.toInt()) {
            renameScript(script)
        }
        addButton(getString(R.string.share_script), 0xFF1D4ED8.toInt(), 0xFFEFF6FF.toInt(), 0xFF3B82F6.toInt()) {
            shareScript(script)
        }
        addButton(getString(R.string.delete_script), 0xFFBE123C.toInt(), 0xFFFFF1F2.toInt(), 0xFFFB7185.toInt()) {
            deleteScript(script)
        }

        card.addView(buttonRow)
        return card
    }

    private fun scriptSummary(script: ScriptStorage.SavedScript): String {
        val loopText = if (script.loopCount == 0) "\u65E0\u9650\u5FAA\u73AF" else "\u5FAA\u73AF ${script.loopCount} \u6B21"
        return "$loopText \u00B7 \u95F4\u9694 ${script.loopGapMs} ms \u00B7 ${script.actions.size} \u4E2A\u52A8\u4F5C"
    }

    private fun roundedRect(fillColor: Int, strokeColor: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            setStroke(dp(1f), strokeColor)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun dp(value: Float): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun editScript(script: ScriptStorage.SavedScript) {
        val prefs = getSharedPreferences("tap_config", MODE_PRIVATE)
        prefs.edit()
            .putString("action_sequence", ActionStep.listToJson(script.actions))
            .putString("current_editing_script_name", script.name)
            .putInt("loop_count", script.loopCount)
            .putLong("loop_gap_ms", script.loopGapMs)
            .putBoolean("loop_settings_saved", true)
            .apply()
        val intent = Intent(this, FloatingControlService::class.java)
        startForegroundService(intent)
    }

    private fun renameScript(script: ScriptStorage.SavedScript) {
        val dialog = android.app.AlertDialog.Builder(this).create()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22f), dp(20f), dp(22f), dp(18f))
            background = roundedRect(0xFFFFFFFF.toInt(), 0xFFDDE3EA.toInt(), 20f)
        }

        val title = TextView(this).apply {
            text = getString(R.string.rename_script_title)
            textSize = 20f
            setTextColor(0xFF111827.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        content.addView(title)

        val hint = TextView(this).apply {
            text = getString(R.string.rename_script_hint)
            textSize = 13f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, dp(6f), 0, dp(10f))
        }
        content.addView(hint)

        val input = android.widget.EditText(this).apply {
            setText(script.name)
            setSingleLine(true)
            selectAll()
            textSize = 16f
            setTextColor(0xFF111827.toInt())
            setHint(R.string.rename_script_hint)
            setHintTextColor(0xFF94A3B8.toInt())
            background = roundedRect(0xFFF8FAFC.toInt(), 0xFFE2E8F0.toInt(), 12f)
            setPadding(dp(14f), 0, dp(14f), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48f)
            )
        }
        content.addView(input)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(16f), 0, 0)
        }

        fun addDialogButton(text: String, textColor: Int, fillColor: Int, strokeColor: Int, onClick: () -> Unit) {
            val button = TextView(this).apply {
                this.text = text
                setTextColor(textColor)
                textSize = 14f
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedRect(fillColor, strokeColor, 19f)
                setPadding(dp(18f), 0, dp(18f), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(40f)
                ).apply {
                    marginStart = dp(8f)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
            buttonRow.addView(button)
        }

        addDialogButton(
            getString(R.string.cancel),
            0xFF475569.toInt(),
            0xFFF8FAFC.toInt(),
            0xFFCBD5E1.toInt()
        ) {
            dialog.dismiss()
        }
        addDialogButton(
            getString(R.string.save),
            0xFFFFFFFF.toInt(),
            0xFF111827.toInt(),
            0xFF111827.toInt()
        ) {
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(this, R.string.script_name_empty, Toast.LENGTH_SHORT).show()
                    return@addDialogButton
                }
                if (newName != script.name && ScriptStorage.getScript(this, newName) != null) {
                    Toast.makeText(this, R.string.script_name_exists, Toast.LENGTH_SHORT).show()
                    return@addDialogButton
                }
                ScriptStorage.deleteScript(this, script.name)
                ScriptStorage.saveNamedScript(this, newName, script.actions, script.loopCount, script.loopGapMs)
                val prefs = getSharedPreferences("tap_config", MODE_PRIVATE)
                if (prefs.getString("current_editing_script_name", null) == script.name) {
                    prefs.edit().putString("current_editing_script_name", newName).apply()
                }
                renderScriptList()
                dialog.dismiss()
        }

        content.addView(buttonRow)
        dialog.setView(content)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.86f).toInt(),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    private fun runScript(script: ScriptStorage.SavedScript) {
        val intent = Intent(this, RunFloatingControlService::class.java).apply {
            putExtra(RunFloatingControlService.EXTRA_SCRIPT_JSON, ActionStep.listToJson(script.actions))
            putExtra(RunFloatingControlService.EXTRA_SCRIPT_NAME, script.name)
            putExtra(RunFloatingControlService.EXTRA_LOOP_COUNT, script.loopCount)
            putExtra(RunFloatingControlService.EXTRA_LOOP_GAP_MS, script.loopGapMs)
        }
        startForegroundService(intent)
    }

    private fun deleteScript(script: ScriptStorage.SavedScript) {
        ScriptStorage.deleteScript(this, script.name)
        Toast.makeText(this, getString(R.string.script_deleted, script.name), Toast.LENGTH_SHORT).show()
        renderScriptList()
    }

    private fun shareScript(script: ScriptStorage.SavedScript) {
        try {
            val file = writeScriptShareFile(script)
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/x-lua"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, script.name)
                clipData = ClipData.newUri(contentResolver, file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_script)))
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "\u5206\u4eab\u5931\u8d25", Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeScriptShareFile(script: ScriptStorage.SavedScript): File {
        val dir = File(cacheDir, "shared_scripts").apply { mkdirs() }
        val file = File(dir, safeScriptFilename(script))
        file.writeText(ScriptStorage.exportScriptToLua(script), Charsets.UTF_8)
        return file
    }

    private fun safeScriptFilename(script: ScriptStorage.SavedScript): String {
        val filename = getString(R.string.script_export_filename, script.name).ifBlank {
            "script.lua"
        }
        return filename.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }

    private fun importScriptFromUri(uri: Uri) {
        try {
            val displayName = getDisplayName(uri)
            if (!hasExpectedScriptPrefix(displayName)) {
                Toast.makeText(this, getString(R.string.script_import_prefix_mismatch), Toast.LENGTH_SHORT).show()
                return
            }
            val json = contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: run {
                Toast.makeText(this, getString(R.string.script_import_failed), Toast.LENGTH_SHORT).show()
                return
            }
            val name = ScriptStorage.importScriptFromText(this, json, displayName)
            Toast.makeText(this, getString(R.string.script_imported, name), Toast.LENGTH_SHORT).show()
            renderScriptList()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.script_import_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasExpectedScriptPrefix(displayName: String?): Boolean {
        return displayName
            ?.substringAfterLast('/')
            ?.startsWith("脚本_") == true
    }

    private fun getDisplayName(uri: Uri): String? {
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
    }
}
