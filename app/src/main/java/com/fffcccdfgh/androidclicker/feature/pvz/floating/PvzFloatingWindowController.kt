package com.fffcccdfgh.androidclicker.feature.pvz.floating

import android.app.Service
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.fffcccdfgh.androidclicker.R
import com.fffcccdfgh.androidclicker.core.execution.ActionSequenceExecutor
import com.fffcccdfgh.androidclicker.core.execution.ControlZoneChecker
import com.fffcccdfgh.androidclicker.core.execution.ExecutionOverlayWindowPolicy
import com.fffcccdfgh.androidclicker.core.overlay.FloatingPanelController
import com.fffcccdfgh.androidclicker.core.overlay.FloatingWindowLayoutPolicy
import com.fffcccdfgh.androidclicker.core.overlay.FloatingWindowSize
import com.fffcccdfgh.androidclicker.core.overlay.FloatingWindowSizePolicy
import com.fffcccdfgh.androidclicker.core.program.ProgramScreenSize
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenshotHider

class PvzFloatingWindowController(
    private val service: Service,
    private val windowManagerProvider: () -> WindowManager?,
    private val currentProgramScreenSize: () -> ProgramScreenSize,
    private val currentTitleProvider: () -> String,
    private val executeCurrentProgram: () -> Unit,
    private val showEditor: () -> Unit,
    private val toggleCalibrationPanel: () -> Unit,
    private val showSavePanel: () -> Unit,
    private val hideSavePanel: () -> Unit,
    private val toggleStopButtonPositioning: () -> Unit,
    private val updateStopPositionButton: () -> Unit,
    private val stopExecution: () -> Unit,
    private val stopService: () -> Unit
) {
    var floatingView: View? = null
        private set
    private var floatingParams: WindowManager.LayoutParams? = null
    private var floatingTouchThrough = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private val floatingPanelController by lazy {
        FloatingPanelController(
            windowManagerProvider = windowManagerProvider,
            touchThroughProvider = { floatingTouchThrough }
        )
    }

    fun showFloatingControl() {
        val wm = windowManagerProvider() ?: return
        val view = LayoutInflater.from(service).inflate(R.layout.pvz_floating_control, null)
        floatingView = view
        val controlSize = calculateFloatingControlSizeForCurrentDisplay()

        floatingParams = WindowManager.LayoutParams(
            controlSize.widthPx,
            controlSize.heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
            alpha = ExecutionOverlayWindowPolicy.NORMAL_ALPHA
        }

        bindDrag(view.findViewById(R.id.pvzDragHandle), view)
        bindDrag(view.findViewById(R.id.pvzCollapsedTitle), view)

        bindStartStopTouch(view.findViewById(R.id.pvzStartButton)) {
            executeCurrentProgram()
        }
        bindStartStopTouch(view.findViewById(R.id.pvzCollapsedStartButton)) {
            executeCurrentProgram()
        }

        view.findViewById<View>(R.id.pvzEditButton).setOnClickListener {
            showEditor()
        }
        view.findViewById<View>(R.id.pvzCalibrationButton).setOnClickListener {
            toggleCalibrationPanel()
        }
        view.findViewById<View>(R.id.pvzSaveButton).setOnClickListener {
            showSavePanel()
        }
        view.findViewById<View>(R.id.pvzStopPositionButton).setOnClickListener {
            toggleStopButtonPositioning()
        }
        view.findViewById<View>(R.id.pvzFoldButton).setOnClickListener {
            collapseFloatingControl()
        }
        view.findViewById<View>(R.id.pvzCollapsedExpandButton).setOnClickListener {
            expandFloatingControl()
        }
        bindClose(view.findViewById(R.id.pvzCloseButton))
        bindClose(view.findViewById(R.id.pvzCollapsedCloseButton))

        updateFloatingTitle()
        updateStopPositionButton()
        applyExpandedFloatingControlLayout(view, controlSize)
        wm.addView(view, floatingParams)

        ControlZoneChecker.register(ZONE_KEY) { getControlZoneRect() }
        ScreenshotHider.register(
            ZONE_KEY,
            hide = { floatingView?.visibility = View.INVISIBLE },
            reveal = { floatingView?.visibility = View.VISIBLE }
        )
    }

    fun dpToPx(dp: Int): Int = (dp * service.resources.displayMetrics.density).toInt()

    private fun calculateFloatingControlSizeForCurrentDisplay(): FloatingWindowSize {
        val screen = currentProgramScreenSize()
        return FloatingWindowSizePolicy.expandedControlSize(screen.width, screen.height)
    }

    private fun calculateCollapsedControlSizeForCurrentDisplay(): FloatingWindowSize {
        val screen = currentProgramScreenSize()
        return FloatingWindowSizePolicy.collapsedControlSize(screen.width, screen.height)
    }

    fun updateFloatingControlSizeForCurrentDisplay() {
        val view = floatingView ?: return
        val expandedControls = view.findViewById<View>(R.id.pvzExpandedControls)
        val collapsedControls = view.findViewById<View>(R.id.pvzCollapsedControls)
        val saveConfirmPanel = view.findViewById<View>(R.id.pvzSaveConfirmPanel)
        if (saveConfirmPanel.visibility == View.VISIBLE) {
            return
        }

        if (collapsedControls.visibility == View.VISIBLE) {
            applyCollapsedFloatingControlLayout(view, calculateCollapsedControlSizeForCurrentDisplay())
            return
        }

        if (expandedControls.visibility == View.VISIBLE) {
            applyExpandedFloatingControlLayout(view, calculateFloatingControlSizeForCurrentDisplay())
        }
    }

    private fun applyCollapsedFloatingControlLayout(view: View, controlSize: FloatingWindowSize) {
        val root = view.findViewById<View>(R.id.pvzFloatingRoot)
        val collapsedControls = view.findViewById<LinearLayout>(R.id.pvzCollapsedControls)
        val collapsedTitle = view.findViewById<TextView>(R.id.pvzCollapsedTitle)
        val collapsedButtons = listOf<View>(
            view.findViewById(R.id.pvzCollapsedStartButton),
            view.findViewById(R.id.pvzCollapsedCloseButton),
            view.findViewById(R.id.pvzCollapsedExpandButton)
        )

        collapsedControls.layoutParams = collapsedControls.layoutParams?.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }

        val horizontalPadding = root.paddingLeft + root.paddingRight
        val verticalPadding = root.paddingTop + root.paddingBottom
        val contentWidth = (controlSize.widthPx - horizontalPadding).coerceAtLeast(1)
        val contentHeight = (controlSize.heightPx - verticalPadding).coerceAtLeast(1)
        val gapPx = (contentWidth / 72).coerceIn(dpToPx(2), dpToPx(8))
        val availableWidth = (contentWidth - gapPx * COLLAPSED_CONTROL_GAP_COUNT).coerceAtLeast(1)
        val itemWidth = (availableWidth / COLLAPSED_CONTROL_ITEM_COUNT).coerceAtLeast(1)

        collapsedTitle.minimumWidth = 0
        collapsedTitle.setPadding(0, collapsedTitle.paddingTop, 0, collapsedTitle.paddingBottom)
        collapsedTitle.gravity = Gravity.CENTER
        collapsedTitle.layoutParams = (collapsedTitle.layoutParams as LinearLayout.LayoutParams).apply {
            width = itemWidth
            height = contentHeight
            weight = 0f
            marginStart = 0
            marginEnd = 0
        }

        collapsedButtons.forEach { button ->
            button.minimumWidth = 0
            button.setPadding(0, button.paddingTop, 0, button.paddingBottom)
            button.layoutParams = (button.layoutParams as LinearLayout.LayoutParams).apply {
                width = itemWidth
                height = contentHeight
                weight = 0f
                marginStart = gapPx
                marginEnd = 0
            }
        }

        floatingParams?.let { lp ->
            lp.width = controlSize.widthPx
            lp.height = controlSize.heightPx
            if (view.isAttachedToWindow) {
                windowManagerProvider()?.updateViewLayout(view, lp)
            }
        }
    }

    private fun applyExpandedFloatingControlLayout(view: View, controlSize: FloatingWindowSize) {
        val root = view.findViewById<View>(R.id.pvzFloatingRoot)
        val expandedControls = view.findViewById<View>(R.id.pvzExpandedControls)
        val dragHandle = view.findViewById<TextView>(R.id.pvzDragHandle)
        val foldButton = view.findViewById<TextView>(R.id.pvzFoldButton)
        val closeButton = view.findViewById<TextView>(R.id.pvzCloseButton)
        val mainActionRow = view.findViewById<LinearLayout>(R.id.pvzMainActionRow)
        val actionButtons = listOf<View>(
            view.findViewById(R.id.pvzStartButton),
            view.findViewById(R.id.pvzEditButton),
            view.findViewById(R.id.pvzCalibrationButton),
            view.findViewById(R.id.pvzSaveButton),
            view.findViewById(R.id.pvzStopPositionButton)
        )

        expandedControls.layoutParams = expandedControls.layoutParams?.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }

        val horizontalPadding = root.paddingLeft + root.paddingRight
        val verticalPadding = root.paddingTop + root.paddingBottom
        val contentWidth = (controlSize.widthPx - horizontalPadding).coerceAtLeast(1)
        val contentHeight = (controlSize.heightPx - verticalPadding).coerceAtLeast(1)
        val gapPx = (contentWidth / 64).coerceIn(dpToPx(2), dpToPx(8))
        val buttonHeight = ((contentHeight - gapPx) / 2).coerceAtLeast(1)
        val actionButtonWidth = ((contentWidth - gapPx * (actionButtons.size - 1)) / actionButtons.size)
            .coerceAtLeast(1)

        mainActionRow.layoutParams = (mainActionRow.layoutParams as LinearLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = 0
            weight = 1f
            topMargin = gapPx
        }

        actionButtons.forEachIndexed { index, button ->
            button.minimumWidth = 0
            button.setPadding(0, button.paddingTop, 0, button.paddingBottom)
            button.layoutParams = (button.layoutParams as LinearLayout.LayoutParams).apply {
                width = actionButtonWidth
                height = buttonHeight
                weight = 0f
                marginStart = if (index == 0) 0 else gapPx
            }
        }

        listOf(foldButton, closeButton).forEach { button ->
            button.minimumWidth = 0
            button.setPadding(0, button.paddingTop, 0, button.paddingBottom)
            button.layoutParams = (button.layoutParams as LinearLayout.LayoutParams).apply {
                width = actionButtonWidth
                height = buttonHeight
                weight = 0f
                marginStart = gapPx
            }
        }

        dragHandle.layoutParams = (dragHandle.layoutParams as LinearLayout.LayoutParams).apply {
            width = 0
            height = buttonHeight
            weight = 1f
            marginStart = 0
        }

        floatingParams?.let { lp ->
            lp.width = controlSize.widthPx
            lp.height = controlSize.heightPx
            if (view.isAttachedToWindow) {
                windowManagerProvider()?.updateViewLayout(view, lp)
            }
        }
    }

    private fun bindDrag(handle: View, view: View) {
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = floatingParams?.x ?: 0
                    initialY = floatingParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    floatingParams?.let { params ->
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManagerProvider()?.updateViewLayout(view, params)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun bindStartStopTouch(button: View, onStart: () -> Unit) {
        var stopTriggered = false
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ActionSequenceExecutor.isRunning) {
                        stopTriggered = true
                        stopExecution()
                    } else {
                        stopTriggered = false
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (stopTriggered) {
                        stopTriggered = false
                    } else if (!ActionSequenceExecutor.isRunning) {
                        onStart()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    stopTriggered = false
                    true
                }
                else -> false
            }
        }
    }

    private fun bindClose(button: View) {
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ActionSequenceExecutor.isRunning) {
                        stopExecution()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    stopService()
                    Toast.makeText(service, R.string.pvz_floating_stopped, Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    private fun collapseFloatingControl() {
        val view = floatingView ?: return
        hideSavePanel()
        view.findViewById<View>(R.id.pvzSaveConfirmPanel).visibility = View.GONE
        disableOverlayFocus()
        view.findViewById<View>(R.id.pvzExpandedControls).visibility = View.GONE
        view.findViewById<View>(R.id.pvzCollapsedControls).visibility = View.VISIBLE
        applyCollapsedFloatingControlLayout(view, calculateCollapsedControlSizeForCurrentDisplay())
    }

    private fun expandFloatingControl() {
        val view = floatingView ?: return
        view.findViewById<View>(R.id.pvzExpandedControls).visibility = View.VISIBLE
        view.findViewById<View>(R.id.pvzCollapsedControls).visibility = View.GONE
        applyExpandedFloatingControlLayout(view, calculateFloatingControlSizeForCurrentDisplay())
    }

    fun hideFloatingControl() {
        hideSavePanel()
        ScreenshotHider.unregister(ZONE_KEY)
        ControlZoneChecker.unregister(ZONE_KEY)
        val view = floatingView
        if (view != null) {
            try { windowManagerProvider()?.removeView(view) } catch (_: Exception) {}
        }
        floatingView = null
        floatingParams = null
    }

    private fun getControlZoneRect(): Rect? {
        if (floatingTouchThrough) return null
        val view = floatingView ?: return null
        val params = floatingParams ?: return null
        if (view.width <= 0 || view.height <= 0) return null
        return Rect(params.x, params.y, params.x + view.width, params.y + view.height)
    }

    fun setFloatingTouchThrough(enabled: Boolean) {
        floatingTouchThrough = enabled
        val params = floatingParams ?: return
        params.flags = if (enabled) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        params.alpha = if (enabled) {
            ExecutionOverlayWindowPolicy.TOUCH_THROUGH_ALPHA
        } else {
            ExecutionOverlayWindowPolicy.NORMAL_ALPHA
        }
        val view = floatingView ?: return
        if (view.isAttachedToWindow) {
            windowManagerProvider()?.updateViewLayout(view, params)
        }
    }

    fun enableOverlayFocus() {
        updateFloatingFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
    }

    fun disableOverlayFocus() {
        updateFloatingFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    private fun updateFloatingFlags(flags: Int) {
        val params = floatingParams ?: return
        params.flags = flags
        if (!floatingTouchThrough) {
            params.alpha = ExecutionOverlayWindowPolicy.NORMAL_ALPHA
        }
        val view = floatingView ?: return
        if (view.isAttachedToWindow) {
            windowManagerProvider()?.updateViewLayout(view, params)
        }
    }


    fun overlayType(): Int {
        return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    }

    private fun createFullScreenPickerParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    fun dp(value: Int): Int = dp(value.toFloat())

    fun dp(value: Float): Int {
        return (value * service.resources.displayMetrics.density + 0.5f).toInt()
    }

    fun roundedRect(fillColor: Int, strokeColor: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            setStroke(dp(1f), strokeColor)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    fun bindPanelDrag(handle: View, panel: View, params: WindowManager.LayoutParams) {
        floatingPanelController.bindDrag(handle, panel, params)
    }

    fun removePanel(view: View?, zoneKey: String, onRemoved: () -> Unit) {
        floatingPanelController.remove(view, zoneKey, onRemoved)
    }

    fun panelZoneRect(view: View?, params: WindowManager.LayoutParams?): Rect? {
        return floatingPanelController.zoneRect(view, params)
    }

    fun updateFloatingTitle() {
        val view = floatingView ?: return
        val name = currentTitleProvider()
        view.findViewById<TextView>(R.id.pvzDragHandle).text = name
        view.findViewById<TextView>(R.id.pvzCollapsedTitle).text = name.take(3)
    }

    fun showPanel(
        panel: View,
        params: WindowManager.LayoutParams,
        zoneKey: String,
        viewProvider: () -> View?,
        paramsProvider: () -> WindowManager.LayoutParams?
    ) {
        floatingPanelController.show(
            panel = panel,
            params = params,
            zoneKey = zoneKey,
            viewProvider = viewProvider,
            paramsProvider = paramsProvider
        )
    }

    private companion object {
        const val ZONE_KEY = "pvz_floating_control"
        const val COLLAPSED_CONTROL_ITEM_COUNT = 4
        const val COLLAPSED_CONTROL_GAP_COUNT = 3
    }
}
