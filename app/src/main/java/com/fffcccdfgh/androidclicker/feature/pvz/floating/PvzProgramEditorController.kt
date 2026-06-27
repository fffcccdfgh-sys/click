package com.fffcccdfgh.androidclicker.feature.pvz.floating

import android.annotation.SuppressLint
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.fffcccdfgh.androidclicker.R
import com.fffcccdfgh.androidclicker.core.overlay.FloatingWindowLayoutPolicy
import com.fffcccdfgh.androidclicker.core.overlay.FloatingWindowSize
import com.fffcccdfgh.androidclicker.core.overlay.FloatingWindowSizePolicy
import com.fffcccdfgh.androidclicker.core.picker.AreaSelectionView
import com.fffcccdfgh.androidclicker.core.picker.ColorPickerOverlayView
import com.fffcccdfgh.androidclicker.core.program.ProgramCodeScrollBar
import com.fffcccdfgh.androidclicker.core.program.ProgramEditorTextActions
import com.fffcccdfgh.androidclicker.core.program.ProgramEditorTextResult
import com.fffcccdfgh.androidclicker.core.program.ProgramEditorWindowPolicy
import com.fffcccdfgh.androidclicker.core.program.ProgramLineNumberView
import com.fffcccdfgh.androidclicker.core.program.ProgramLuaAssist
import com.fffcccdfgh.androidclicker.core.program.ProgramLuaTemplate
import com.fffcccdfgh.androidclicker.core.program.ProgramScreenSize
import com.fffcccdfgh.androidclicker.core.program.ProgramTemplateMenuScrollBar
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenCaptureDisplayReader
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenCaptureManager
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenCapturePointMapper
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenshotHider
import com.fffcccdfgh.androidclicker.feature.pvz.PvzScriptStorage

class PvzProgramEditorController(
    private val service: Service,
    private val scriptSessionController: PvzScriptSessionController,
    private val windowManagerProvider: () -> WindowManager?,
    private val hideCalibrationPanel: () -> Unit,
    private val hideCalibrationPickerOverlayCallback: (Boolean) -> Unit,
    private val currentProgramScreenSize: () -> ProgramScreenSize,
    private val createFullScreenPickerParams: () -> WindowManager.LayoutParams,
    private val overlayType: () -> Int,
    private val dpCallback: (Float) -> Int,
    private val roundedRect: (Int, Int, Float) -> GradientDrawable,
    private val bindPanelDragCallback: (View, View, WindowManager.LayoutParams) -> Unit,
    private val showPanelCallback: (
        View,
        WindowManager.LayoutParams,
        String,
        () -> View?,
        () -> WindowManager.LayoutParams?
    ) -> Unit,
    private val removePanelCallback: (View?, String, () -> Unit) -> Unit
) {
    private var editorView: View? = null
    private var editorParams: WindowManager.LayoutParams? = null
    private var programTemplatePanelView: View? = null
    private var programTemplatePanelParams: WindowManager.LayoutParams? = null
    private var saveConfirmPanelView: View? = null
    private var saveConfirmPanelParams: WindowManager.LayoutParams? = null
    private var programAssistOverlayView: View? = null
    private var areaPickerBackgroundBitmap: Bitmap? = null
    private var pendingProgramDraftCode: String? = null
    private var pendingProgramDraftCursor: Int = 0
    private var pendingProgramRestoreCursor: Int? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    fun editorCodeInput(): EditText? = editorView?.findViewById(R.id.programCodeInput)

    private fun hideCalibrationPickerOverlay(revealOverlays: Boolean) {
        hideCalibrationPickerOverlayCallback(revealOverlays)
    }

    private fun dp(value: Int): Int = dp(value.toFloat())

    private fun dp(value: Float): Int = dpCallback(value)

    private fun bindPanelDrag(handle: View, panel: View, params: WindowManager.LayoutParams) {
        bindPanelDragCallback(handle, panel, params)
    }

    private fun showPanel(
        panel: View,
        params: WindowManager.LayoutParams,
        zoneKey: String,
        viewProvider: () -> View?,
        paramsProvider: () -> WindowManager.LayoutParams?
    ) {
        showPanelCallback(panel, params, zoneKey, viewProvider, paramsProvider)
    }

    private fun removePanel(view: View?, zoneKey: String, onRemoved: () -> Unit) {
        removePanelCallback(view, zoneKey, onRemoved)
    }

    fun showProgramEditor() {
        val wm = windowManagerProvider() ?: return
        hideEditor()
        hideCalibrationPanel()
        hideCalibrationPickerOverlay(true)

        val editor = LayoutInflater.from(service).inflate(R.layout.program_editor, null)
        editorView = editor

        val currentDisplay = ScreenCaptureDisplayReader.current(service)
        val editorSize = FloatingWindowSizePolicy.programEditorSizeForDisplay(
            displayWidthPx = currentDisplay.width,
            displayHeightPx = currentDisplay.height,
            resourceWidthPx = service.resources.displayMetrics.widthPixels,
            resourceHeightPx = service.resources.displayMetrics.heightPixels,
            density = service.resources.displayMetrics.density
        )

        val params = WindowManager.LayoutParams(
            editorSize.widthPx,
            editorSize.heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            ProgramEditorWindowPolicy.FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = ProgramEditorWindowPolicy.SOFT_INPUT_MODE
        }
        editorParams = params

        val codeInput = editor.findViewById<EditText>(R.id.programCodeInput)
        applyProgramEditorCodePanelHeight(editor, editorSize)
        configureProgramCodeInput(codeInput)
        codeInput.setText(scriptSessionController.loadCurrentProgramCode())
        val restoreCursor = pendingProgramRestoreCursor
        pendingProgramRestoreCursor = null
        codeInput.setSelection(
            ProgramEditorWindowPolicy.openCursorPosition(
                restoreCursor,
                codeInput.text?.length ?: 0
            )
        )
        if (restoreCursor == null) {
            codeInput.post { codeInput.scrollTo(0, 0) }
        }
        editor.findViewById<ProgramCodeScrollBar>(R.id.programCodeScrollBar).attachTo(codeInput)
        editor.findViewById<ProgramLineNumberView>(R.id.programLineNumbers).attachTo(codeInput)
        codeInput.isVerticalScrollBarEnabled = false
        bindProgramTextToolbar(editor, codeInput)
        bindProgramEditorDrag(editor.findViewById(R.id.programEditorTitle), editor, params)

        editor.findViewById<View>(R.id.templateButton).setOnClickListener {
            showProgramTemplatePanel(codeInput)
        }
        bindPvzProgramInsertTools(editor, codeInput)
        editor.findViewById<View>(R.id.testParseButton).setOnClickListener {
            testProgramParse(codeInput.text.toString())
        }
        editor.findViewById<View>(R.id.programCloseButton).setOnClickListener {
            hideEditor()
        }
        editor.findViewById<View>(R.id.programSaveButton).setOnClickListener {
            val code = codeInput.text.toString()
            if (!validateProgramCode(code)) return@setOnClickListener
            scriptSessionController.saveCurrentProgramDraft(code)
            hideEditor()
            Toast.makeText(service, R.string.program_action_saved, Toast.LENGTH_SHORT).show()
        }

        wm.addView(editor, params)
        focusProgramCodeInput(codeInput)
        ScreenshotHider.register(
            EDITOR_ZONE_KEY,
            hide = { editorView?.visibility = View.INVISIBLE },
            reveal = { editorView?.visibility = View.VISIBLE }
        )
    }

    fun updateProgramEditorSizeForCurrentDisplay() {
        val editor = editorView ?: return
        val params = editorParams ?: return
        val currentDisplay = ScreenCaptureDisplayReader.current(service)
        val editorSize = FloatingWindowSizePolicy.programEditorSizeForDisplay(
            displayWidthPx = currentDisplay.width,
            displayHeightPx = currentDisplay.height,
            resourceWidthPx = service.resources.displayMetrics.widthPixels,
            resourceHeightPx = service.resources.displayMetrics.heightPixels,
            density = service.resources.displayMetrics.density
        )

        FloatingWindowLayoutPolicy.applyCenterGravitySize(params, editorSize)
        applyProgramEditorCodePanelHeight(editor, editorSize)
        FloatingWindowLayoutPolicy.updateIfAttached(windowManagerProvider(), editor, params)
    }

    private fun applyProgramEditorCodePanelHeight(editor: View, editorSize: FloatingWindowSize) {
        val codePanel = editor.findViewById<View>(R.id.programCodePanel)
        codePanel.layoutParams = (codePanel.layoutParams as LinearLayout.LayoutParams).apply {
            height = Math.round(editorSize.heightPx * ProgramEditorWindowPolicy.CODE_PANEL_HEIGHT_RATIO)
            weight = 0f
        }
    }

    private fun bindPvzProgramInsertTools(editor: View, codeInput: EditText) {
        editor.findViewById<TextView>(R.id.programPickTapButton).text =
            service.getString(R.string.pvz_program_insert_tap)
        editor.findViewById<View>(R.id.programPickCoordButton).visibility = View.GONE
        editor.findViewById<TextView>(R.id.programPickSwipeButton).text =
            service.getString(R.string.pvz_program_insert_swipe)
        editor.findViewById<TextView>(R.id.programPickAreaButton).text =
            service.getString(R.string.pvz_program_insert_text)
        editor.findViewById<TextView>(R.id.programPickColorButton).text =
            service.getString(R.string.pvz_program_insert_color)

        editor.findViewById<View>(R.id.programPickTapButton).setOnClickListener {
            insertProgramSnippet(codeInput, "tap()")
        }
        editor.findViewById<View>(R.id.programPickSwipeButton).setOnClickListener {
            insertProgramSnippet(codeInput, "swipe(, , , )")
        }
        editor.findViewById<View>(R.id.programPickAreaButton).setOnClickListener {
            showPvzProgramTextAreaPicker(codeInput)
        }
        editor.findViewById<View>(R.id.programPickColorButton).setOnClickListener {
            showPvzProgramColorPicker(codeInput)
        }
    }

    private fun bindProgramEditorDrag(handle: View, editor: View, params: WindowManager.LayoutParams) {
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManagerProvider()?.updateViewLayout(editor, params)
                    true
                }
                else -> false
            }
        }
    }

    fun hideEditor() {
        hideProgramTemplatePanel()
        ScreenshotHider.unregister(EDITOR_ZONE_KEY)
        val view = editorView
        if (view != null) {
            try { windowManagerProvider()?.removeView(view) } catch (_: Exception) {}
        }
        editorView = null
        editorParams = null
    }

    private fun saveProgramDraftForAssist(codeInput: EditText) {
        pendingProgramDraftCode = codeInput.text.toString()
        pendingProgramDraftCursor = codeInput.selectionStart.coerceAtLeast(0)
    }

    private fun restoreProgramEditorWithSnippet(snippet: String?) {
        val code = pendingProgramDraftCode.orEmpty()
        if (snippet != null) {
            val result = ProgramLuaAssist.insertSnippet(
                code = code,
                cursor = pendingProgramDraftCursor,
                snippet = snippet
            )
            scriptSessionController.saveCurrentProgramDraft(result.code)
            pendingProgramRestoreCursor = result.cursor
        } else {
            scriptSessionController.saveCurrentProgramDraft(code)
            pendingProgramRestoreCursor = pendingProgramDraftCursor
        }
        pendingProgramDraftCode = null
        showProgramEditor()
    }

    private fun showPvzProgramTextAreaPicker(codeInput: EditText) {
        val wm = windowManagerProvider() ?: return
        saveProgramDraftForAssist(codeInput)
        hideEditor()
        hideCalibrationPanel()
        hideCalibrationPickerOverlay(false)

        // Capture screenshot before hiding overlays (overlays are system windows,
        // so they won't appear in the capture)
        val capturedImage = if (ScreenCaptureManager.isReady) {
            ScreenCaptureManager.refreshDisplayMetrics(service)
            ScreenCaptureManager.captureFrameSync()
        } else {
            null
        }
        val backgroundBitmap: Bitmap? = capturedImage?.let { image ->
            try {
                ScreenCaptureManager.imageToBitmap(image)
            } finally {
                try { image.close() } catch (_: Exception) {}
            }
        }
        // Recycle previous bitmap if any
        areaPickerBackgroundBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        areaPickerBackgroundBitmap = backgroundBitmap

        ScreenshotHider.hideAll()
        val view = LayoutInflater.from(service).inflate(R.layout.area_picker, null)
        programAssistOverlayView = view

        // Set screenshot as background if available
        if (backgroundBitmap != null) {
            view.findViewById<ImageView>(R.id.areaPickerBackground)?.setImageBitmap(backgroundBitmap)
        }

        val params = createFullScreenPickerParams()

        val selectionView = view.findViewById<AreaSelectionView>(R.id.areaSelectionView)
        val buttonsRow = view.findViewById<View>(R.id.areaPickerButtons)
        val saveBtn = view.findViewById<TextView>(R.id.areaPickerSaveBtn)
        val cancelBtn = view.findViewById<TextView>(R.id.areaPickerCancelBtn)
        view.findViewById<TextView>(R.id.areaPickerInstruction)
            .setText(R.string.pvz_program_text_picker_instruction)

        selectionView.onInteractionStarted = {
            buttonsRow.visibility = View.GONE
        }
        selectionView.onInteractionFinished = {
            buttonsRow.visibility = View.VISIBLE
        }

        saveBtn.setOnClickListener {
            val rect = selectionView.getSelectionRect()
            if (rect == null) {
                Toast.makeText(service, R.string.area_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val loc = IntArray(2)
            selectionView.getLocationOnScreen(loc)
            val screen = currentProgramScreenSize()
            val snippet = ProgramLuaAssist.textAreaSnippet(
                left = rect.left + loc[0],
                top = rect.top + loc[1],
                right = rect.right + loc[0],
                bottom = rect.bottom + loc[1],
                screenWidth = screen.width,
                screenHeight = screen.height
            )
            removePvzProgramAssistOverlay()
            restoreProgramEditorWithSnippet(snippet)
        }

        cancelBtn.setOnClickListener {
            removePvzProgramAssistOverlay()
            restoreProgramEditorWithSnippet(null)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            programAssistOverlayView = null
            // Recycle the background bitmap on failure
            areaPickerBackgroundBitmap?.let {
                if (!it.isRecycled) it.recycle()
            }
            areaPickerBackgroundBitmap = null
            ScreenshotHider.revealAll()
            Log.w(STOP_DEBUG_TAG, "Failed to show PVZ2 program text area picker", e)
            restoreProgramEditorWithSnippet(null)
        }
    }

    fun removePvzProgramAssistOverlay() {
        val view = programAssistOverlayView ?: return
        try {
            windowManagerProvider()?.removeView(view)
        } catch (_: Exception) {
        }
        programAssistOverlayView = null
        // Recycle the background bitmap to free memory
        areaPickerBackgroundBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        areaPickerBackgroundBitmap = null
        ScreenshotHider.revealAll()
    }

    private fun showPvzProgramColorPicker(codeInput: EditText) {
        val wm = windowManagerProvider() ?: return
        if (!ScreenCaptureManager.isReady) {
            Toast.makeText(service, R.string.screen_capture_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        ScreenCaptureManager.refreshDisplayMetrics(service)
        if (!ScreenCaptureManager.isReady) {
            Toast.makeText(service, R.string.screen_capture_not_ready, Toast.LENGTH_SHORT).show()
            return
        }

        saveProgramDraftForAssist(codeInput)
        hideEditor()
        hideCalibrationPanel()
        hideCalibrationPickerOverlay(false)
        ScreenshotHider.hideAll()

        val screenW = ScreenCaptureManager.getCaptureWidth()
        val screenH = ScreenCaptureManager.getCaptureHeight()
        Thread {
            Thread.sleep(300)
            val image = ScreenCaptureManager.captureFrameSync(COLOR_PICK_CAPTURE_TIMEOUT_MS)
            android.os.Handler(service.mainLooper).post {
                if (image == null) {
                    ScreenshotHider.revealAll()
                    Toast.makeText(service, R.string.condition_color_pick_failed, Toast.LENGTH_SHORT).show()
                    restoreProgramEditorWithSnippet(null)
                    return@post
                }

                val overlay = ColorPickerOverlayView(service)
                val initX = screenW / 2
                val initY = screenH / 2
                val initHex = samplePixelHex(image, initX, initY, screenW, screenH)
                overlay.setInitialPosition(initX, initY, initHex)
                overlay.colorSampler = { x, y ->
                    samplePixelHex(image, x, y, screenW, screenH)
                }
                overlay.onConfirm = { _, _, hex ->
                    try {
                        wm.removeView(overlay)
                    } catch (_: Exception) {
                    }
                    programAssistOverlayView = null
                    image.close()
                    ScreenshotHider.revealAll()
                    restoreProgramEditorWithSnippet("check_color(\"$hex\", 10, )")
                }
                overlay.onCancel = {
                    try {
                        wm.removeView(overlay)
                    } catch (_: Exception) {
                    }
                    programAssistOverlayView = null
                    image.close()
                    ScreenshotHider.revealAll()
                    restoreProgramEditorWithSnippet(null)
                }
                programAssistOverlayView = overlay
                val params = createFullScreenPickerParams()
                wm.addView(overlay, params)
            }
        }.start()
    }

    private fun samplePixelHex(
        image: android.media.Image,
        screenX: Int,
        screenY: Int,
        screenW: Int,
        screenH: Int
    ): String {
        val point = ScreenCapturePointMapper.mapScreenPointToCapturePoint(
            screenX = screenX,
            screenY = screenY,
            screenWidth = screenW,
            screenHeight = screenH,
            captureWidth = image.width,
            captureHeight = image.height
        ) ?: return "#000000"
        val pixel = ScreenCaptureManager.readPixel(image, point.x, point.y)
        val r = android.graphics.Color.red(pixel)
        val g = android.graphics.Color.green(pixel)
        val b = android.graphics.Color.blue(pixel)
        return String.format("#%02X%02X%02X", r, g, b)
    }

    private fun configureProgramCodeInput(codeInput: EditText) {
        codeInput.isFocusable = true
        codeInput.isFocusableInTouchMode = true
        codeInput.isLongClickable = true
        codeInput.setTextIsSelectable(true)
        codeInput.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        codeInput.filters = codeInput.filters.filterNot { it is android.text.InputFilter.LengthFilter }.toTypedArray()
        codeInput.setSelectAllOnFocus(false)
    }

    private fun focusProgramCodeInput(codeInput: EditText) {
        codeInput.post {
            codeInput.requestFocus()
            val imm = service.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(codeInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun bindProgramTextToolbar(editor: View, codeInput: EditText) {
        editor.findViewById<View>(R.id.programSelectAllButton).setOnClickListener {
            codeInput.requestFocus()
            codeInput.selectAll()
        }
        editor.findViewById<View>(R.id.programCopyButton).setOnClickListener {
            codeInput.requestFocus()
            val selectedText = ProgramEditorTextActions.selectedText(
                code = codeInput.text.toString(),
                selectionStart = codeInput.selectionStart,
                selectionEnd = codeInput.selectionEnd
            )
            if (selectedText == null) {
                Toast.makeText(service, R.string.program_edit_no_selection, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            writeProgramClipboard(selectedText)
            Toast.makeText(service, R.string.program_edit_copied, Toast.LENGTH_SHORT).show()
        }
        editor.findViewById<View>(R.id.programCutButton).setOnClickListener {
            codeInput.requestFocus()
            val code = codeInput.text.toString()
            val selectionStart = codeInput.selectionStart
            val selectionEnd = codeInput.selectionEnd
            val selectedText = ProgramEditorTextActions.selectedText(code, selectionStart, selectionEnd)
            val result = ProgramEditorTextActions.cutSelection(code, selectionStart, selectionEnd)
            if (selectedText == null || result == null) {
                Toast.makeText(service, R.string.program_edit_no_selection, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            writeProgramClipboard(selectedText)
            applyProgramTextResult(codeInput, result)
            Toast.makeText(service, R.string.program_edit_cut_done, Toast.LENGTH_SHORT).show()
        }
        editor.findViewById<View>(R.id.programPasteButton).setOnClickListener {
            codeInput.requestFocus()
            val clipboardText = readProgramClipboardText()
            if (clipboardText.isNullOrEmpty()) {
                Toast.makeText(service, R.string.program_edit_clipboard_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val result = ProgramEditorTextActions.pasteText(
                code = codeInput.text.toString(),
                selectionStart = codeInput.selectionStart,
                selectionEnd = codeInput.selectionEnd,
                pasteText = clipboardText
            )
            applyProgramTextResult(codeInput, result)
            Toast.makeText(service, R.string.program_edit_pasted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeProgramClipboard(text: String) {
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("PVZ2 Lua code", text))
    }

    private fun readProgramClipboardText(): String? {
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0).coerceToText(service)?.toString()
    }

    private fun applyProgramTextResult(codeInput: EditText, result: ProgramEditorTextResult) {
        codeInput.setText(result.code)
        codeInput.setSelection(result.cursor.coerceIn(0, result.code.length))
    }

    @SuppressLint("InflateParams")
    private fun showProgramTemplatePanel(codeInput: EditText) {
        val wm = windowManagerProvider() ?: return
        if (programTemplatePanelView != null) {
            hideProgramTemplatePanel()
            return
        }

        val panel = LayoutInflater.from(service).inflate(R.layout.floating_program_template_panel, null)
        val params = programTemplatePanelParams ?: createProgramTemplatePanelParams()
        val templateSize = calculateProgramTemplatePanelSizeForCurrentDisplay()
        applyProgramTemplatePanelParams(params, templateSize)
        programTemplatePanelView = panel
        programTemplatePanelParams = params

        bindPanelDrag(panel.findViewById(R.id.programTemplateHeader), panel, params)
        panel.findViewById<View>(R.id.programTemplateCloseButton).setOnClickListener {
            hideProgramTemplatePanel()
        }

        val templates = pvzQuickTemplates()
        val container = panel.findViewById<LinearLayout>(R.id.programTemplateContainer)
        container.removeAllViews()
        val rowWidth = programTemplateRowWidth(templateSize)
        for ((index, template) in templates.withIndex()) {
            val item = TextView(service).apply {
                text = template.title
                textSize = 14f
                setTextColor(0xFFF8FAFC.toInt())
                gravity = Gravity.CENTER_VERTICAL
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedRect(0xFF1E293B.toInt(), 0xFF334155.toInt(), 12f)
                setPadding(dp(14f), 0, dp(14f), 0)
                layoutParams = LinearLayout.LayoutParams(
                    rowWidth,
                    dp(PROGRAM_TEMPLATE_ROW_HEIGHT_DP)
                ).apply {
                    if (index > 0) topMargin = dp(8f)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    insertProgramSnippetInline(codeInput, template.snippet)
                    hideProgramTemplatePanel()
                    codeInput.requestFocus()
                }
            }
            container.addView(item)
        }

        val scrollView = panel.findViewById<ScrollView>(R.id.programTemplateScroll)
        val scrollBar = panel.findViewById<ProgramTemplateMenuScrollBar>(R.id.programTemplateScrollBar)
        scrollView.layoutParams = scrollView.layoutParams.apply {
            height = programTemplateScrollHeight(templateSize)
        }
        applyProgramTemplatePanelContentSize(panel, templateSize)
        scrollBar.attachTo(scrollView)

        showPanel(
            panel = panel,
            params = params,
            zoneKey = TEMPLATE_ZONE_KEY,
            viewProvider = { programTemplatePanelView },
            paramsProvider = { programTemplatePanelParams }
        )
    }

    private fun pvzQuickTemplates(): List<ProgramLuaTemplate> {
        return listOf(
            ProgramLuaTemplate(
                id = "pvz_plant_slots",
                title = service.getString(R.string.pvz_calibration_plant_slots),
                snippet = "plant_slots[].x, plant_slots[].y"
            ),
            ProgramLuaTemplate(
                id = "pvz_board",
                title = service.getString(R.string.pvz_calibration_board),
                snippet = "board[][].x, board[][].y"
            ),
            ProgramLuaTemplate(
                id = "pvz_sun",
                title = service.getString(R.string.pvz_calibration_sun),
                snippet = "sun_.x, sun_.y"
            ),
            ProgramLuaTemplate(
                id = "pvz_plant_food",
                title = service.getString(R.string.pvz_calibration_plant_food),
                snippet = "plant_food_.x, plant_food_.y"
            ),
            ProgramLuaTemplate(
                id = "pvz_artifact",
                title = service.getString(R.string.pvz_calibration_artifact),
                snippet = "artifact_.x, artifact_.y"
            ),
            ProgramLuaTemplate(
                id = "pvz_cucumber",
                title = service.getString(R.string.pvz_calibration_cucumber),
                snippet = "cucumber_.x, cucumber_.y"
            ),
            ProgramLuaTemplate(
                id = "pvz_recharge",
                title = service.getString(R.string.pvz_calibration_recharge),
                snippet = "recharge_.x, recharge_.y"
            ),
            ProgramLuaTemplate(
                id = "pvz_cards",
                title = service.getString(R.string.pvz_calibration_cards),
                snippet = "cards_poker.x, cards_poker.y"
            ),
            ProgramLuaTemplate(
                id = "pvz_start_battle_related",
                title = service.getString(R.string.pvz_calibration_start_battle_related),
                snippet = "start_battle.x, start_battle.y"
            ),
            ProgramLuaTemplate(
                id = "pvz_other",
                title = service.getString(R.string.pvz_calibration_other),
                snippet = "other_.x, other_.y"
            ),
            ProgramLuaTemplate(
                id = "pvz_endless_supply",
                title = service.getString(R.string.pvz_calibration_endless_supply),
                snippet = "endless_supply_ability_1.x, endless_supply_ability_1_area.left"
            )
        )
    }

    private fun createProgramTemplatePanelParams(): WindowManager.LayoutParams {
        val templateSize = calculateProgramTemplatePanelSizeForCurrentDisplay()
        return WindowManager.LayoutParams(
            templateSize.widthPx,
            templateSize.heightPx,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            FloatingWindowLayoutPolicy.applyCenteredPosition(
                params = this,
                screenWidthPx = service.resources.displayMetrics.widthPixels,
                screenHeightPx = service.resources.displayMetrics.heightPixels,
                size = templateSize
            )
        }
    }

    fun updateProgramTemplatePanelSizeForCurrentDisplay() {
        val panel = programTemplatePanelView ?: return
        val params = programTemplatePanelParams ?: return
        val templateSize = calculateProgramTemplatePanelSizeForCurrentDisplay()

        applyProgramTemplatePanelParams(params, templateSize)
        applyProgramTemplatePanelContentSize(panel, templateSize)
        FloatingWindowLayoutPolicy.updateIfAttached(windowManagerProvider(), panel, params)
    }

    private fun calculateProgramTemplatePanelSizeForCurrentDisplay(): FloatingWindowSize {
        val currentDisplay = ScreenCaptureDisplayReader.current(service)
        return FloatingWindowSizePolicy.programTemplateSize(
            screenWidthPx = currentDisplay.width.takeIf { it > 0 } ?: service.resources.displayMetrics.widthPixels,
            screenHeightPx = currentDisplay.height.takeIf { it > 0 } ?: service.resources.displayMetrics.heightPixels,
            density = service.resources.displayMetrics.density
        )
    }

    private fun applyProgramTemplatePanelParams(
        params: WindowManager.LayoutParams,
        templateSize: FloatingWindowSize
    ) {
        FloatingWindowLayoutPolicy.applyCenteredSize(
            params = params,
            screenWidthPx = service.resources.displayMetrics.widthPixels,
            screenHeightPx = service.resources.displayMetrics.heightPixels,
            size = templateSize
        )
    }

    private fun applyProgramTemplatePanelContentSize(panel: View, templateSize: FloatingWindowSize) {
        panel.layoutParams = ViewGroup.LayoutParams(templateSize.widthPx, templateSize.heightPx)
        panel.findViewById<View>(R.id.programTemplateHeader).layoutParams =
            panel.findViewById<View>(R.id.programTemplateHeader).layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
        val rowWidth = programTemplateRowWidth(templateSize)
        val container = panel.findViewById<LinearLayout>(R.id.programTemplateContainer)
        for (index in 0 until container.childCount) {
            container.getChildAt(index).layoutParams = LinearLayout.LayoutParams(
                rowWidth,
                dp(PROGRAM_TEMPLATE_ROW_HEIGHT_DP)
            ).apply {
                if (index > 0) topMargin = dp(8f)
            }
        }
        val scrollView = panel.findViewById<ScrollView>(R.id.programTemplateScroll)
        scrollView.layoutParams = scrollView.layoutParams.apply {
            width = rowWidth
            height = programTemplateScrollHeight(templateSize)
        }
    }

    private fun programTemplateRowWidth(templateSize: FloatingWindowSize): Int {
        return (templateSize.widthPx - dp(34f)).coerceAtLeast(dp(120f))
    }

    private fun programTemplateScrollHeight(templateSize: FloatingWindowSize): Int {
        return (templateSize.heightPx - dp(66f)).coerceAtLeast(dp(PROGRAM_TEMPLATE_ROW_HEIGHT_DP))
    }

    private fun hideProgramTemplatePanel() {
        removePanel(programTemplatePanelView, TEMPLATE_ZONE_KEY) {
            programTemplatePanelView = null
        }
    }

    private fun insertProgramSnippet(codeInput: EditText, snippet: String) {
        val result = ProgramLuaAssist.insertSnippet(
            code = codeInput.text.toString(),
            cursor = codeInput.selectionStart.coerceAtLeast(0),
            snippet = snippet
        )
        codeInput.setText(result.code)
        codeInput.setSelection(result.cursor.coerceIn(0, result.code.length))
    }

    private fun insertProgramSnippetInline(codeInput: EditText, snippet: String) {
        val code = codeInput.text.toString()
        val selectionStart = codeInput.selectionStart.coerceAtLeast(0)
        val selectionEnd = codeInput.selectionEnd.coerceAtLeast(0)
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, code.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, code.length)
        val result = code.substring(0, start) + snippet + code.substring(end)
        codeInput.setText(result)
        codeInput.setSelection((start + snippet.length).coerceIn(0, result.length))
    }

    private fun testProgramParse(code: String) {
        try {
            val globals = org.luaj.vm2.lib.jse.JsePlatform.standardGlobals()
            globals.load(code, "pvz2")
            Toast.makeText(service, R.string.program_parse_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(service, e.message ?: service.getString(R.string.program_parse_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateProgramCode(code: String): Boolean {
        if (code.isBlank()) {
            Toast.makeText(service, R.string.program_code_empty, Toast.LENGTH_SHORT).show()
            return false
        }
        try {
            val globals = org.luaj.vm2.lib.jse.JsePlatform.standardGlobals()
            globals.load(code, "pvz2")
        } catch (e: Exception) {
            Toast.makeText(service, e.message ?: service.getString(R.string.program_parse_failed), Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    @SuppressLint("InflateParams")
    fun showSaveConfirmPanel() {
        val code = scriptSessionController.loadCurrentProgramCode()
        if (code.isBlank()) {
            Toast.makeText(service, R.string.pvz_program_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val wm = windowManagerProvider() ?: return
        if (saveConfirmPanelView != null) {
            hideSaveConfirmPanel()
        }

        val panel = LayoutInflater.from(service).inflate(R.layout.floating_save_confirm_panel, null)
        val params = createSaveConfirmPanelParams()
        saveConfirmPanelView = panel
        saveConfirmPanelParams = params

        panel.findViewById<TextView>(R.id.savePanelTitle).text = service.getString(R.string.confirm_save_pvz_script)
        val input = panel.findViewById<EditText>(R.id.savePanelNameInput)
        input.setText(scriptSessionController.getCurrentScriptName() ?: PvzScriptStorage.nextAutoName(service))
        input.selectAll()
        bindPanelDrag(panel.findViewById(R.id.savePanelHeader), panel, params)
        panel.findViewById<View>(R.id.savePanelConfirmButton).setOnClickListener {
            saveCurrentProgramWithConfirmedName(input.text.toString().trim())
        }
        val dismiss = View.OnClickListener { hideSaveConfirmPanel() }
        panel.findViewById<View>(R.id.savePanelCancelButton).setOnClickListener(dismiss)

        showPanel(
            panel = panel,
            params = params,
            zoneKey = SAVE_CONFIRM_ZONE_KEY,
            viewProvider = { saveConfirmPanelView },
            paramsProvider = { saveConfirmPanelParams }
        )
        enableSaveConfirmInput(input)
    }

    private fun createSaveConfirmPanelParams(): WindowManager.LayoutParams {
        val screen = currentProgramScreenSize()
        val panelSize = FloatingWindowSizePolicy.saveConfirmPanelEstimatedSize(service.resources.displayMetrics.density)
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            FloatingWindowLayoutPolicy.applyCenteredPosition(this, screen.width, screen.height, panelSize)
        }
    }

    private fun enableSaveConfirmInput(input: EditText) {
        input.requestFocus()
        input.post {
            val imm = service.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun saveCurrentProgramWithConfirmedName(name: String) {
        val code = scriptSessionController.loadCurrentProgramCode()
        if (code.isBlank()) {
            Toast.makeText(service, R.string.pvz_program_empty, Toast.LENGTH_SHORT).show()
            return
        }
        if (name.isEmpty()) {
            Toast.makeText(service, R.string.script_name_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val oldName = scriptSessionController.getCurrentScriptName()
        if (name != oldName && PvzScriptStorage.getScript(service, name) != null) {
            Toast.makeText(service, R.string.script_name_exists, Toast.LENGTH_SHORT).show()
            return
        }
        scriptSessionController.saveCurrentProgramCode(name, code)
        hideSaveConfirmPanel()
        Toast.makeText(service, R.string.script_saved, Toast.LENGTH_SHORT).show()
    }

    fun hideSaveConfirmPanel() {
        removePanel(saveConfirmPanelView, SAVE_CONFIRM_ZONE_KEY) {
            saveConfirmPanelView = null
        }
        saveConfirmPanelParams = null
    }

    private companion object {
        const val EDITOR_ZONE_KEY = "pvz_program_editor"
        const val TEMPLATE_ZONE_KEY = "pvz_program_template_panel"
        const val SAVE_CONFIRM_ZONE_KEY = "pvz_save_confirm_panel"
        const val PROGRAM_TEMPLATE_ROW_HEIGHT_DP = 44f
        const val COLOR_PICK_CAPTURE_TIMEOUT_MS = 3000L
        const val STOP_DEBUG_TAG = "ClickerStopDebug"
    }
}
