package com.fffcccdfgh.androidclicker.feature.pvz.floating

import android.annotation.SuppressLint
import android.app.Service
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.fffcccdfgh.androidclicker.R
import com.fffcccdfgh.androidclicker.core.overlay.FloatingWindowLayoutPolicy
import com.fffcccdfgh.androidclicker.core.overlay.FloatingWindowSize
import com.fffcccdfgh.androidclicker.core.overlay.FloatingWindowSizePolicy
import com.fffcccdfgh.androidclicker.feature.pvz.calibration.PvzCalibrationStorage

class PvzCalibrationPanelController(
    private val service: Service,
    private val windowManagerProvider: () -> WindowManager?,
    private val calibrationFlowController: PvzCalibrationFlowController,
    private val overlayType: () -> Int,
    private val dp: (Float) -> Int,
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
    private var calibrationPanelView: View? = null
    private var calibrationPanelParams: WindowManager.LayoutParams? = null

    private fun currentProgramScreenSize() = calibrationFlowController.currentProgramScreenSize()

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

    fun toggleCalibrationPanel() {
        if (calibrationPanelView != null) {
            hideCalibrationPanel()
        } else {
            showCalibrationPanel()
        }
    }

    @SuppressLint("InflateParams")
    private fun showCalibrationPanel() {
        val wm = windowManagerProvider() ?: return
        if (calibrationPanelView != null) return

        val panel = LayoutInflater.from(service).inflate(R.layout.pvz_calibration_panel, null)
        val params = calibrationPanelParams ?: createCalibrationPanelParams()
        val panelSize = calculateCalibrationPanelSizeForCurrentDisplay()
        applyCalibrationPanelParams(params, panelSize)
        calibrationPanelView = panel
        calibrationPanelParams = params

        applyCalibrationPanelContentSize(panel, panelSize)
        bindPanelDrag(panel.findViewById(R.id.pvzCalibrationHeader), panel, params)
        panel.findViewById<View>(R.id.pvzCalibrationCloseButton).setOnClickListener {
            hideCalibrationPanel()
        }
        bindCalibrationStatusButtons(panel)

        showPanel(
            panel = panel,
            params = params,
            zoneKey = CALIBRATION_ZONE_KEY,
            viewProvider = { calibrationPanelView },
            paramsProvider = { calibrationPanelParams }
        )
    }

    private fun createCalibrationPanelParams(): WindowManager.LayoutParams {
        val panelSize = calculateCalibrationPanelSizeForCurrentDisplay()
        return WindowManager.LayoutParams(
            panelSize.widthPx,
            panelSize.heightPx,
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
            applyCalibrationPanelParams(this, panelSize)
        }
    }

    fun updateCalibrationPanelSizeForCurrentDisplay() {
        val panel = calibrationPanelView ?: return
        val params = calibrationPanelParams ?: return
        val panelSize = calculateCalibrationPanelSizeForCurrentDisplay()

        applyCalibrationPanelParams(params, panelSize)
        applyCalibrationPanelContentSize(panel, panelSize)
        FloatingWindowLayoutPolicy.updateIfAttached(windowManagerProvider(), panel, params)
    }

    private fun calculateCalibrationPanelSizeForCurrentDisplay(): FloatingWindowSize {
        val screen = currentProgramScreenSize()
        return FloatingWindowSizePolicy.calibrationPanelSize(screen.width, screen.height)
    }

    private fun applyCalibrationPanelParams(
        params: WindowManager.LayoutParams,
        panelSize: FloatingWindowSize
    ) {
        val screen = currentProgramScreenSize()
        FloatingWindowLayoutPolicy.applyCenteredSize(
            params = params,
            screenWidthPx = screen.width,
            screenHeightPx = screen.height,
            size = panelSize
        )
    }

    private fun applyCalibrationPanelContentSize(panel: View, panelSize: FloatingWindowSize) {
        val root = panel.findViewById<View>(R.id.pvzCalibrationPanel)
        val header = panel.findViewById<View>(R.id.pvzCalibrationHeader)
        val scroll = panel.findViewById<View>(R.id.pvzCalibrationEntryScroll)
        val horizontalPadding = root.paddingLeft + root.paddingRight
        val verticalPadding = root.paddingTop + root.paddingBottom
        val contentWidth = (panelSize.widthPx - horizontalPadding).coerceAtLeast(dp(120f))
        val scrollHeight = (panelSize.heightPx - verticalPadding - dp(46f)).coerceAtLeast(dp(160f))

        root.layoutParams = ViewGroup.LayoutParams(panelSize.widthPx, panelSize.heightPx)
        header.layoutParams = header.layoutParams.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        scroll.layoutParams = scroll.layoutParams.apply {
            width = contentWidth
            height = scrollHeight
        } as ViewGroup.LayoutParams
    }

    fun hideCalibrationPanel() {
        removePanel(calibrationPanelView, CALIBRATION_ZONE_KEY) {
            calibrationPanelView = null
        }
    }

    private fun bindCalibrationStatusButtons(panel: View) {
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationPlantSlotsStatus,
            PvzCalibrationStorage.PLANT_SLOTS
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationBoardStatus,
            PvzCalibrationStorage.BOARD
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationSunStatus,
            PvzCalibrationStorage.SUN
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationPlantFoodStatus,
            PvzCalibrationStorage.PLANT_FOOD
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationArtifactStatus,
            PvzCalibrationStorage.ARTIFACT
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationCucumberStatus,
            PvzCalibrationStorage.CUCUMBER
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationRechargeStatus,
            PvzCalibrationStorage.RECHARGE
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationCardsStatus,
            PvzCalibrationStorage.CARDS
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationStartBattleRelatedStatus,
            PvzCalibrationStorage.START_BATTLE_RELATED
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationOtherStatus,
            PvzCalibrationStorage.OTHER
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationEndlessSupplyStatus,
            PvzCalibrationStorage.ENDLESS_SUPPLY
        )
    }

    private fun bindCalibrationStatusButton(panel: View, viewId: Int, key: String) {
        val button = panel.findViewById<TextView>(viewId)
        updateCalibrationStatusButton(button, key)
        button.setOnClickListener {
            openCalibrationEditor(key)
        }
    }

    private fun updateCalibrationStatusButton(button: TextView, key: String) {
        val calibrated = PvzCalibrationStorage.isCalibrated(service, key)
        button.text = service.getString(
            if (calibrated) {
                R.string.pvz_calibration_calibrated
            } else {
                R.string.pvz_calibration_uncalibrated
            }
        )
        button.setTextColor(
            if (calibrated) {
                0xFF16A34A.toInt()
            } else {
                0xFF2563EB.toInt()
            }
        )
        button.background = ContextCompat.getDrawable(
            service,
            if (calibrated) {
                R.drawable.main_permission_done_bg
            } else {
                R.drawable.main_permission_action_bg
            }
        )
    }

    private fun openCalibrationEditor(key: String) {
        if (key == PvzCalibrationStorage.PLANT_SLOTS) {
            calibrationFlowController.showPlantSlotsCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.BOARD) {
            calibrationFlowController.showBoardCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.SUN) {
            calibrationFlowController.showSunCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.PLANT_FOOD) {
            calibrationFlowController.showPlantFoodCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.ARTIFACT) {
            calibrationFlowController.showArtifactCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.CUCUMBER) {
            calibrationFlowController.showCucumberCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.RECHARGE) {
            calibrationFlowController.showRechargeCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.CARDS) {
            calibrationFlowController.showCardsCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.START_BATTLE_RELATED) {
            calibrationFlowController.showStartBattleRelatedCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.OTHER) {
            calibrationFlowController.showOtherCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.ENDLESS_SUPPLY) {
            calibrationFlowController.showEndlessSupplyCalibrationPicker()
            return
        }
        Log.d(STOP_DEBUG_TAG, "PVZ2 calibration entry clicked: $key")
    }

    fun refreshCalibrationPanelStatuses() {
        val panel = calibrationPanelView ?: return
        bindCalibrationStatusButtons(panel)
    }

    private companion object {
        const val CALIBRATION_ZONE_KEY = "pvz_calibration_panel"
        const val STOP_DEBUG_TAG = "ClickerStopDebug"
    }
}
