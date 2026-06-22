package com.fffcccdfgh.androidclicker

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PvzScriptListActivity : AppCompatActivity() {

    private lateinit var scriptListContainer: LinearLayout
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val usbSyncWatcher by lazy { PvzUsbSyncWatcher(this) }
    private var usbSyncJob: Job? = null
    private var pendingUsbSyncUpdate: PvzUsbSyncUpdate? = null
    private var pendingUsbSyncBatch: PvzUsbSyncBatch? = null
    private var usbSyncDialog: android.app.AlertDialog? = null
    private var usbSyncDialogMessage: TextView? = null

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
        setContentView(R.layout.activity_pvz_script_list)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        scriptListContainer = findViewById(R.id.pvzScriptListContainer)

        findViewById<TextView>(R.id.importPvzScriptButton).setOnClickListener {
            importLauncher.launch(arrayOf("text/x-lua", "text/plain", "application/json", "*/*"))
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(PvzScriptStorage.ACTION_SCRIPTS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scriptsChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(scriptsChangedReceiver, filter)
        }
        startUsbSyncPolling()
        renderScriptList()
    }

    override fun onPause() {
        super.onPause()
        usbSyncJob?.cancel()
        usbSyncJob = null
        usbSyncDialog?.dismiss()
        usbSyncDialog = null
        usbSyncDialogMessage = null
        try {
            unregisterReceiver(scriptsChangedReceiver)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.coroutineContext[Job]?.cancel()
    }

    private fun renderScriptList() {
        scriptListContainer.removeAllViews()
        val scripts = PvzScriptStorage.listScripts(this)

        if (scripts.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = getString(R.string.no_saved_pvz_scripts)
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
            scriptListContainer.addView(createScriptCard(script))
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

        card.addView(TextView(this).apply {
            text = script.name
            textSize = 20f
            setTextColor(0xFF111827.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        card.addView(TextView(this).apply {
            text = scriptSummary(script)
            textSize = 13f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, dp(6f), 0, dp(14f))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

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
        return getString(R.string.pvz_script_summary, script.actions.size)
    }

    private fun runScript(script: ScriptStorage.SavedScript) {
        val intent = Intent(this, RunFloatingControlService::class.java).apply {
            putExtra(RunFloatingControlService.EXTRA_SCRIPT_JSON, ActionStep.listToJson(script.actions))
            putExtra(RunFloatingControlService.EXTRA_SCRIPT_NAME, script.name)
            putExtra(RunFloatingControlService.EXTRA_LOOP_COUNT, script.loopCount)
            putExtra(RunFloatingControlService.EXTRA_LOOP_GAP_MS, script.loopGapMs)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun editScript(script: ScriptStorage.SavedScript) {
        loadScriptIntoPvzFloating(script)
        startPvzFloating()
    }

    private fun loadScriptIntoPvzFloating(script: ScriptStorage.SavedScript) {
        val programCode = script.actions.firstOrNull { it.type == ActionStep.TYPE_PROGRAM }?.code.orEmpty()
        getSharedPreferences(PvzFloatingControlService.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(PvzFloatingControlService.KEY_PROGRAM_CODE, programCode)
            .putString(PvzFloatingControlService.KEY_SCRIPT_NAME, script.name)
            .apply()
    }

    private fun startPvzFloating() {
        val intent = Intent(this, PvzFloatingControlService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun renameScript(script: ScriptStorage.SavedScript) {
        val dialog = android.app.AlertDialog.Builder(this).create()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22f), dp(20f), dp(22f), dp(18f))
            background = roundedRect(0xFFFFFFFF.toInt(), 0xFFDDE3EA.toInt(), 20f)
        }

        content.addView(TextView(this).apply {
            text = getString(R.string.rename_pvz_script_title)
            textSize = 20f
            setTextColor(0xFF111827.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        content.addView(TextView(this).apply {
            text = getString(R.string.rename_script_hint)
            textSize = 13f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, dp(6f), 0, dp(10f))
        })

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

        addDialogButton(getString(R.string.cancel), 0xFF475569.toInt(), 0xFFF8FAFC.toInt(), 0xFFCBD5E1.toInt()) {
            dialog.dismiss()
        }
        addDialogButton(getString(R.string.save), 0xFFFFFFFF.toInt(), 0xFF111827.toInt(), 0xFF111827.toInt()) {
            val newName = input.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(this, R.string.script_name_empty, Toast.LENGTH_SHORT).show()
                return@addDialogButton
            }
            if (newName != script.name && PvzScriptStorage.getScript(this, newName) != null) {
                Toast.makeText(this, R.string.script_name_exists, Toast.LENGTH_SHORT).show()
                return@addDialogButton
            }
            PvzScriptStorage.deleteScript(this, script.name)
            PvzScriptStorage.saveNamedScript(this, newName, script.actions, script.loopCount, script.loopGapMs)
            val prefs = getSharedPreferences(PvzFloatingControlService.PREFS_NAME, MODE_PRIVATE)
            if (prefs.getString(PvzFloatingControlService.KEY_SCRIPT_NAME, null) == script.name) {
                prefs.edit().putString(PvzFloatingControlService.KEY_SCRIPT_NAME, newName).apply()
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

    private fun deleteScript(script: ScriptStorage.SavedScript) {
        PvzScriptStorage.deleteScript(this, script.name)
        Toast.makeText(this, getString(R.string.script_deleted, script.name), Toast.LENGTH_SHORT).show()
        renderScriptList()
    }

    private fun shareScript(script: ScriptStorage.SavedScript) {
        try {
            val file = writeScriptShareFile(script)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/x-lua"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, script.name)
                clipData = ClipData.newUri(contentResolver, file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_script)))
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "分享失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeScriptShareFile(script: ScriptStorage.SavedScript): File {
        val dir = File(cacheDir, "shared_pvz_scripts").apply { mkdirs() }
        val file = File(dir, safeScriptFilename(script))
        file.writeText(PvzScriptStorage.exportScriptToLua(script), Charsets.UTF_8)
        return file
    }

    private fun safeScriptFilename(script: ScriptStorage.SavedScript): String {
        val base = script.name.replace(Regex("""[\\/:*?"<>|]"""), "_")
        return "pvz2_$base.lua"
    }

    private fun importScriptFromUri(uri: Uri) {
        try {
            val displayName = getDisplayName(uri)
            if (!hasExpectedScriptPrefix(displayName)) {
                Toast.makeText(this, getString(R.string.pvz_script_import_prefix_mismatch), Toast.LENGTH_SHORT).show()
                return
            }
            val text = contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText().removePrefix("\uFEFF")
            } ?: run {
                Toast.makeText(this, getString(R.string.script_import_failed), Toast.LENGTH_SHORT).show()
                return
            }
            val name = PvzScriptStorage.importScriptFromText(this, text, displayName)
            Toast.makeText(this, getString(R.string.script_imported, name), Toast.LENGTH_SHORT).show()
            renderScriptList()
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.script_import_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasExpectedScriptPrefix(displayName: String?): Boolean {
        return displayName
            ?.substringAfterLast('/')
            ?.startsWith("pvz2_") == true
    }

    private fun startUsbSyncPolling() {
        if (usbSyncJob?.isActive == true) return
        usbSyncJob = activityScope.launch {
            while (isActive) {
                val batch = withContext(Dispatchers.IO) {
                    usbSyncWatcher.readBatch()
                }
                val lastSeenBatchId = getLastSeenUsbSyncBatchId()
                if (batch != null && batch.batchId != lastSeenBatchId) {
                    saveLastSeenUsbSyncBatchId(batch.batchId)
                    showOrUpdateUsbSyncBatchDialog(batch)
                    delay(USB_SYNC_POLL_INTERVAL_MS)
                    continue
                }

                val update = withContext(Dispatchers.IO) {
                    usbSyncWatcher.readLatestUpdate()
                }
                val lastSeen = getLastSeenUsbSyncSignature()
                if (update != null && update.signature != lastSeen) {
                    saveLastSeenUsbSyncSignature(update.signature)
                    showOrUpdateUsbSyncDialog(update)
                }
                delay(USB_SYNC_POLL_INTERVAL_MS)
            }
        }
    }

    private fun showOrUpdateUsbSyncDialog(update: PvzUsbSyncUpdate) {
        pendingUsbSyncBatch = null
        pendingUsbSyncUpdate = update
        usbSyncDialog?.dismiss()
        usbSyncDialogMessage?.text = getString(R.string.pvz_usb_sync_message_named, update.scriptName)
        showUsbSyncDialog(
            message = getString(R.string.pvz_usb_sync_message_named, update.scriptName),
            confirmText = getString(R.string.overwrite),
            onConfirm = { applyPendingUsbSyncUpdate() },
            onCancel = { pendingUsbSyncUpdate = null }
        )
    }

    private fun showOrUpdateUsbSyncBatchDialog(batch: PvzUsbSyncBatch) {
        pendingUsbSyncUpdate = null
        pendingUsbSyncBatch = batch
        usbSyncDialog?.dismiss()
        val messageRes = if (batch.replaceAll) {
            R.string.pvz_usb_sync_batch_replace_message
        } else {
            R.string.pvz_usb_sync_batch_message
        }
        val confirmTextRes = if (batch.replaceAll) {
            R.string.replace_all
        } else {
            R.string.update_all
        }
        showUsbSyncDialog(
            message = getString(messageRes, batch.scripts.size),
            confirmText = getString(confirmTextRes),
            onConfirm = { applyPendingUsbSyncBatch() },
            onCancel = { pendingUsbSyncBatch = null }
        )
    }

    private fun showUsbSyncDialog(
        message: String,
        confirmText: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22f), dp(20f), dp(22f), dp(18f))
            background = roundedRect(0xFFFFFFFF.toInt(), 0xFFDDE3EA.toInt(), 20f)
        }

        content.addView(TextView(this).apply {
            text = getString(R.string.pvz_usb_sync_title)
            textSize = 20f
            setTextColor(0xFF111827.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        usbSyncDialogMessage = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(0xFF475569.toInt())
            setPadding(0, dp(8f), 0, 0)
        }
        content.addView(usbSyncDialogMessage)

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

        addDialogButton(getString(R.string.cancel), 0xFF475569.toInt(), 0xFFF8FAFC.toInt(), 0xFFCBD5E1.toInt()) {
            onCancel()
            usbSyncDialog?.dismiss()
        }
        addDialogButton(confirmText, 0xFFFFFFFF.toInt(), 0xFF111827.toInt(), 0xFF111827.toInt()) {
            onConfirm()
        }

        content.addView(buttonRow)

        usbSyncDialog = android.app.AlertDialog.Builder(this).create().apply {
            setView(content)
            setOnDismissListener {
                usbSyncDialog = null
                usbSyncDialogMessage = null
            }
            setOnShowListener {
                window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                window?.setLayout(
                    (resources.displayMetrics.widthPixels * 0.86f).toInt(),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT
                )
            }
            show()
        }
    }

    private fun applyPendingUsbSyncUpdate() {
        val update = pendingUsbSyncUpdate ?: return
        saveUsbSyncUpdateAsScript(update)
        getSharedPreferences(PvzFloatingControlService.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(PvzFloatingControlService.KEY_PROGRAM_CODE, update.code)
            .putString(PvzFloatingControlService.KEY_SCRIPT_NAME, update.scriptName)
            .apply()
        sendBroadcast(Intent(PvzScriptStorage.ACTION_SCRIPTS_CHANGED).apply {
            setPackage(packageName)
        })
        pendingUsbSyncUpdate = null
        usbSyncDialog?.dismiss()
        renderScriptList()
        Toast.makeText(this, R.string.pvz_usb_sync_applied, Toast.LENGTH_SHORT).show()
    }

    private fun applyPendingUsbSyncBatch() {
        val batch = pendingUsbSyncBatch ?: return
        if (batch.replaceAll) {
            for (script in PvzScriptStorage.listScripts(this)) {
                PvzScriptStorage.deleteScript(this, script.name)
            }
        }
        for (update in batch.scripts) {
            saveUsbSyncUpdateAsScript(update)
        }
        val first = batch.scripts.firstOrNull()
        if (first != null) {
            getSharedPreferences(PvzFloatingControlService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PvzFloatingControlService.KEY_PROGRAM_CODE, first.code)
                .putString(PvzFloatingControlService.KEY_SCRIPT_NAME, first.scriptName)
                .apply()
        }
        sendBroadcast(Intent(PvzScriptStorage.ACTION_SCRIPTS_CHANGED).apply {
            setPackage(packageName)
        })
        pendingUsbSyncBatch = null
        usbSyncDialog?.dismiss()
        renderScriptList()
        Toast.makeText(this, getString(R.string.pvz_usb_sync_batch_imported, batch.scripts.size), Toast.LENGTH_SHORT).show()
    }

    private fun saveUsbSyncUpdateAsScript(update: PvzUsbSyncUpdate) {
        val action = ActionStep(
            type = ActionStep.TYPE_PROGRAM,
            code = update.code,
            delayBeforeMs = 1L,
            repeatCount = 1
        )
        PvzScriptStorage.saveNamedScript(
            this,
            update.scriptName,
            listOf(action),
            loopCount = 1,
            loopGapMs = 0L
        )
    }

    private fun getLastSeenUsbSyncSignature(): String? {
        return getSharedPreferences(PvzFloatingControlService.PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_USB_SYNC_SIGNATURE, null)
    }

    private fun saveLastSeenUsbSyncSignature(signature: String) {
        getSharedPreferences(PvzFloatingControlService.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_USB_SYNC_SIGNATURE, signature)
            .apply()
    }

    private fun getLastSeenUsbSyncBatchId(): String? {
        return getSharedPreferences(PvzFloatingControlService.PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_USB_SYNC_BATCH_ID, null)
    }

    private fun saveLastSeenUsbSyncBatchId(batchId: String) {
        getSharedPreferences(PvzFloatingControlService.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_USB_SYNC_BATCH_ID, batchId)
            .apply()
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

    companion object {
        private const val USB_SYNC_POLL_INTERVAL_MS = 1000L
        private const val KEY_USB_SYNC_SIGNATURE = "usb_sync_signature"
        private const val KEY_USB_SYNC_BATCH_ID = "usb_sync_batch_id"
    }
}
