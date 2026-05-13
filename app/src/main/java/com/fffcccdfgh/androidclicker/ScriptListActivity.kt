package com.fffcccdfgh.androidclicker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ScriptListActivity : AppCompatActivity() {

    private lateinit var scriptListContainer: LinearLayout
    private var pendingShareScript: ScriptStorage.SavedScript? = null

    private val scriptsChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            renderScriptList()
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { writeScriptToUri(it) }
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

        findViewById<Button>(R.id.importScriptButton).setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "*/*"))
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
                gravity = Gravity.CENTER
                setPadding(0, 48, 0, 0)
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
            setPadding(16, 12, 16, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
            }
            background = getDrawable(R.drawable.floating_control_bg)
        }

        val nameText = TextView(this).apply {
            text = script.name
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 8)
        }
        card.addView(nameText)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fun addButton(text: String, color: Int, onClick: () -> Unit) {
            val btn = TextView(this).apply {
                this.text = text
                setTextColor(color)
                textSize = 13f
                setPadding(0, 6, 16, 6)
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
            buttonRow.addView(btn)
        }

        addButton(getString(R.string.run_action), 0xFF00BCD4.toInt()) {
            runScript(script)
        }
        addButton(getString(R.string.edit_script), 0xFF4CAF50.toInt()) {
            editScript(script)
        }
        addButton(getString(R.string.rename_script), 0xFFFFC107.toInt()) {
            renameScript(script)
        }
        addButton(getString(R.string.delete_script), 0xFFFF8888.toInt()) {
            deleteScript(script)
        }
        addButton(getString(R.string.export_script), 0xFF2196F3.toInt()) {
            exportScript(script)
        }
        addButton(getString(R.string.share_script), 0xFFFF9800.toInt()) {
            shareScript(script)
        }

        card.addView(buttonRow)
        return card
    }

    private fun editScript(script: ScriptStorage.SavedScript) {
        val prefs = getSharedPreferences("tap_config", MODE_PRIVATE)
        prefs.edit()
            .putString("action_sequence", ActionStep.listToJson(script.actions))
            .putString("current_editing_script_name", script.name)
            .apply()
        val intent = Intent(this, FloatingControlService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun renameScript(script: ScriptStorage.SavedScript) {
        val input = android.widget.EditText(this).apply {
            setText(script.name)
            setTextColor(android.graphics.Color.BLACK)
            setHint(R.string.rename_script_hint)
            setHintTextColor(android.graphics.Color.GRAY)
            setBackgroundColor(android.graphics.Color.WHITE)
            setPadding(32, 16, 32, 16)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.rename_script_title)
            .setView(input)
            .setPositiveButton(R.string.yes) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(this, R.string.script_name_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newName != script.name && ScriptStorage.getScript(this, newName) != null) {
                    Toast.makeText(this, R.string.script_name_exists, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                ScriptStorage.deleteScript(this, script.name)
                ScriptStorage.saveNamedScript(this, newName, script.actions)
                val prefs = getSharedPreferences("tap_config", MODE_PRIVATE)
                if (prefs.getString("current_editing_script_name", null) == script.name) {
                    prefs.edit().putString("current_editing_script_name", newName).apply()
                }
                renderScriptList()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun runScript(script: ScriptStorage.SavedScript) {
        val intent = Intent(this, RunFloatingControlService::class.java).apply {
            putExtra(RunFloatingControlService.EXTRA_SCRIPT_JSON, ActionStep.listToJson(script.actions))
            putExtra(RunFloatingControlService.EXTRA_SCRIPT_NAME, script.name)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun deleteScript(script: ScriptStorage.SavedScript) {
        ScriptStorage.deleteScript(this, script.name)
        Toast.makeText(this, getString(R.string.script_deleted, script.name), Toast.LENGTH_SHORT).show()
        renderScriptList()
    }

    private fun exportScript(script: ScriptStorage.SavedScript) {
        val filename = getString(R.string.script_export_filename, script.name)
        pendingShareScript = script
        exportLauncher.launch(filename)
    }

    private fun writeScriptToUri(uri: Uri) {
        val script = pendingShareScript ?: return
        pendingShareScript = null
        try {
            val json = ScriptStorage.exportScriptToJson(script)
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, getString(R.string.script_exported), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "导出失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareScript(script: ScriptStorage.SavedScript) {
        val json = ScriptStorage.exportScriptToJson(script)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, json)
            putExtra(Intent.EXTRA_SUBJECT, script.name)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_script)))
    }

    private fun importScriptFromUri(uri: Uri) {
        try {
            val json = contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: run {
                Toast.makeText(this, getString(R.string.script_import_failed), Toast.LENGTH_SHORT).show()
                return
            }
            val name = ScriptStorage.importScriptFromJson(this, json)
            Toast.makeText(this, getString(R.string.script_imported, name), Toast.LENGTH_SHORT).show()
            renderScriptList()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.script_import_failed), Toast.LENGTH_SHORT).show()
        }
    }
}
