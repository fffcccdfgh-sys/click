package com.fffcccdfgh.androidclicker.feature.pvz.floating

import android.annotation.SuppressLint
import android.app.Service
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.fffcccdfgh.androidclicker.R
import com.fffcccdfgh.androidclicker.core.picker.AreaSelectionView
import com.fffcccdfgh.androidclicker.core.program.ProgramCoordinateAdapter
import com.fffcccdfgh.androidclicker.core.program.ProgramScreenSize
import com.fffcccdfgh.androidclicker.core.program.ProgramScreenSizePolicy
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenCaptureDisplayReader
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenCaptureManager
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenshotHider
import com.fffcccdfgh.androidclicker.feature.pvz.calibration.PvzCalibrationArea
import com.fffcccdfgh.androidclicker.feature.pvz.calibration.PvzCalibrationPoint
import com.fffcccdfgh.androidclicker.feature.pvz.calibration.PvzCalibrationStorage
import com.fffcccdfgh.androidclicker.feature.pvz.calibration.PvzEndlessSupplyCalibrationView
import com.fffcccdfgh.androidclicker.feature.pvz.calibration.PvzPointCalibrationView

class PvzCalibrationFlowController(
    private val service: Service,
    private val windowManagerProvider: () -> WindowManager?,
    private val overlayType: () -> Int,
    private val refreshCalibrationPanelStatuses: () -> Unit
) {
    private var calibrationPickerView: View? = null
    private var areaPickerBackgroundBitmap: Bitmap? = null

    fun showPlantSlotsCalibrationPicker() {
        val wm = windowManagerProvider() ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = LayoutInflater.from(service).inflate(R.layout.area_picker, null)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        val selectionView = view.findViewById<AreaSelectionView>(R.id.areaSelectionView)
        val buttonsRow = view.findViewById<View>(R.id.areaPickerButtons)
        val saveBtn = view.findViewById<TextView>(R.id.areaPickerSaveBtn)
        val cancelBtn = view.findViewById<TextView>(R.id.areaPickerCancelBtn)
        view.findViewById<TextView>(R.id.areaPickerInstruction)
            .setText(R.string.pvz_plant_slots_picker_instruction)

        selectionView.divisionCount = PLANT_SLOT_COUNT
        selectionView.divisionOrientation = AreaSelectionView.DivisionOrientation.HORIZONTAL
        restorePlantSlotsCalibrationArea(selectionView)
        selectionView.onInteractionStarted = {
            buttonsRow.visibility = View.GONE
        }
        selectionView.onInteractionFinished = {
            buttonsRow.visibility = View.VISIBLE
        }

        saveBtn.setOnClickListener {
            val rect = selectionView.getSelectionRect()
            if (rect == null) {
                Toast.makeText(service, R.string.condition_area_too_small, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val loc = IntArray(2)
            selectionView.getLocationOnScreen(loc)
            val screenRect = Rect(
                rect.left + loc[0],
                rect.top + loc[1],
                rect.right + loc[0],
                rect.bottom + loc[1]
            )
            savePlantSlotsCalibration(screenRect)
            selectionView.cleanup()
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(service, R.string.pvz_plant_slots_calibration_saved, Toast.LENGTH_SHORT).show()
        }

        cancelBtn.setOnClickListener {
            selectionView.cleanup()
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(STOP_DEBUG_TAG, "Failed to show PVZ2 plant slots calibration picker", e)
        }
    }

    @SuppressLint("InflateParams")
    fun showBoardCalibrationPicker() {
        val wm = windowManagerProvider() ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = LayoutInflater.from(service).inflate(R.layout.area_picker, null)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        val selectionView = view.findViewById<AreaSelectionView>(R.id.areaSelectionView)
        val buttonsRow = view.findViewById<View>(R.id.areaPickerButtons)
        val saveBtn = view.findViewById<TextView>(R.id.areaPickerSaveBtn)
        val cancelBtn = view.findViewById<TextView>(R.id.areaPickerCancelBtn)
        view.findViewById<TextView>(R.id.areaPickerInstruction)
            .setText(R.string.pvz_board_picker_instruction)

        selectionView.divisionRows = PvzCalibrationStorage.BOARD_ROWS
        selectionView.divisionColumns = PvzCalibrationStorage.BOARD_COLUMNS
        restoreBoardCalibrationArea(selectionView)
        selectionView.onInteractionStarted = {
            buttonsRow.visibility = View.GONE
        }
        selectionView.onInteractionFinished = {
            buttonsRow.visibility = View.VISIBLE
        }

        saveBtn.setOnClickListener {
            val rect = selectionView.getSelectionRect()
            if (rect == null) {
                Toast.makeText(service, R.string.condition_area_too_small, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val loc = IntArray(2)
            selectionView.getLocationOnScreen(loc)
            val screenRect = Rect(
                rect.left + loc[0],
                rect.top + loc[1],
                rect.right + loc[0],
                rect.bottom + loc[1]
            )
            saveBoardCalibration(screenRect)
            selectionView.cleanup()
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(service, R.string.pvz_board_calibration_saved, Toast.LENGTH_SHORT).show()
        }

        cancelBtn.setOnClickListener {
            selectionView.cleanup()
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(STOP_DEBUG_TAG, "Failed to show PVZ2 board calibration picker", e)
        }
    }

    private fun restoreBoardCalibrationArea(selectionView: AreaSelectionView) {
        val area = PvzCalibrationStorage.getBoardArea(service) ?: return
        selectionView.setInitialRect(
            storedPercentXToEdgePx(area.left),
            storedPercentYToEdgePx(area.top),
            storedPercentXToEdgePx(area.right),
            storedPercentYToEdgePx(area.bottom)
        )
    }

    private fun saveBoardCalibration(rect: Rect) {
        val rowHeight = rect.height().toFloat() / PvzCalibrationStorage.BOARD_ROWS.toFloat()
        val columnWidth = rect.width().toFloat() / PvzCalibrationStorage.BOARD_COLUMNS.toFloat()
        val points = mutableListOf<PvzCalibrationStorage.GridPoint>()
        for (row in 1..PvzCalibrationStorage.BOARD_ROWS) {
            val centerY = rect.top + rowHeight * (row - 0.5f)
            for (column in 1..PvzCalibrationStorage.BOARD_COLUMNS) {
                val centerX = rect.left + columnWidth * (column - 0.5f)
                points.add(
                    PvzCalibrationStorage.GridPoint(
                        row = row,
                        column = column,
                        x = pixelXToStoredPercent(centerX.toInt()),
                        y = pixelYToStoredPercent(centerY.toInt())
                    )
                )
            }
        }
        PvzCalibrationStorage.saveBoard(
            service,
            left = pixelXToStoredPercent(rect.left),
            top = pixelYToStoredPercent(rect.top),
            right = pixelXToStoredPercent(rect.right),
            bottom = pixelYToStoredPercent(rect.bottom),
            centers = points
        )
    }

    fun showSunCalibrationPicker() {
        val wm = windowManagerProvider() ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = PvzPointCalibrationView(service)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        view.setPoints(sunCalibrationPoints())
        view.onSave = { points ->
            saveSunCalibration(points)
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(service, R.string.pvz_sun_calibration_saved, Toast.LENGTH_SHORT).show()
        }
        view.onCancel = {
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(STOP_DEBUG_TAG, "Failed to show PVZ2 sun calibration picker", e)
        }
    }

    private fun sunCalibrationPoints(): List<PvzCalibrationPoint> {
        val saved = PvzCalibrationStorage.getSunPoints(service).associateBy { it.key }
        if (saved.isNotEmpty()) {
            return sunPointSpecs().map { spec ->
                val point = saved[spec.key]
                PvzCalibrationPoint(
                    key = spec.key,
                    label = spec.label,
                    x = point?.x?.let { storedPercentXToPointPx(it).toFloat() } ?: spec.defaultX,
                    y = point?.y?.let { storedPercentYToPointPx(it).toFloat() } ?: spec.defaultY
                )
            }
        }
        return sunPointSpecs().map { spec ->
            PvzCalibrationPoint(
                key = spec.key,
                label = spec.label,
                x = spec.defaultX,
                y = spec.defaultY
            )
        }
    }

    private data class SunPointSpec(
        val key: String,
        val label: String,
        val defaultX: Float,
        val defaultY: Float
    )

    private fun sunPointSpecs(): List<SunPointSpec> {
        val screen = currentProgramScreenSize()
        val centerY = screen.height / 2f
        val labels = listOf(
            PvzCalibrationStorage.SUN_BUY_KEY to service.getString(R.string.pvz_sun_buy_key),
            PvzCalibrationStorage.SUN_AD to service.getString(R.string.pvz_sun_ad),
            PvzCalibrationStorage.SUN_10_DIAMOND to service.getString(R.string.pvz_sun_10_diamond),
            PvzCalibrationStorage.SUN_CLOSE to service.getString(R.string.pvz_sun_close)
        )
        return labels.mapIndexed { index, (key, label) ->
            SunPointSpec(
                key = key,
                label = label,
                defaultX = screen.width * (index + 1).toFloat() / (labels.size + 1).toFloat(),
                defaultY = centerY
            )
        }
    }

    private fun saveSunCalibration(points: List<PvzCalibrationPoint>) {
        PvzCalibrationStorage.saveSunPoints(
            service,
            points.map { point ->
                PvzCalibrationStorage.NamedPoint(
                    key = point.key,
                    x = pixelXToStoredPercent(point.x.toInt()),
                    y = pixelYToStoredPercent(point.y.toInt())
                )
            }
        )
    }

    fun showPlantFoodCalibrationPicker() {
        val wm = windowManagerProvider() ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = PvzPointCalibrationView(service)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        view.setPoints(plantFoodCalibrationPoints())
        view.onSave = { points ->
            savePlantFoodCalibration(points)
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(service, R.string.pvz_plant_food_calibration_saved, Toast.LENGTH_SHORT).show()
        }
        view.onCancel = {
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(STOP_DEBUG_TAG, "Failed to show PVZ2 plant food calibration picker", e)
        }
    }

    private fun plantFoodCalibrationPoints(): List<PvzCalibrationPoint> {
        val saved = PvzCalibrationStorage.getPlantFoodPoints(service).associateBy { it.key }
        if (saved.isNotEmpty()) {
            return plantFoodPointSpecs().map { spec ->
                val point = saved[spec.key]
                PvzCalibrationPoint(
                    key = spec.key,
                    label = spec.label,
                    x = point?.x?.let { storedPercentXToPointPx(it).toFloat() } ?: spec.defaultX,
                    y = point?.y?.let { storedPercentYToPointPx(it).toFloat() } ?: spec.defaultY
                )
            }
        }
        return plantFoodPointSpecs().map { spec ->
            PvzCalibrationPoint(
                key = spec.key,
                label = spec.label,
                x = spec.defaultX,
                y = spec.defaultY
            )
        }
    }

    private data class PlantFoodPointSpec(
        val key: String,
        val label: String,
        val defaultX: Float,
        val defaultY: Float
    )

    private fun plantFoodPointSpecs(): List<PlantFoodPointSpec> {
        val screen = currentProgramScreenSize()
        val centerY = screen.height / 2f
        val labels = listOf(
            PvzCalibrationStorage.PLANT_FOOD_BEAN to service.getString(R.string.pvz_plant_food_bean),
            PvzCalibrationStorage.PLANT_FOOD_PLUS to service.getString(R.string.pvz_plant_food_plus),
            PvzCalibrationStorage.PLANT_FOOD_BUY to service.getString(R.string.pvz_plant_food_buy)
        )
        return labels.mapIndexed { index, (key, label) ->
            PlantFoodPointSpec(
                key = key,
                label = label,
                defaultX = screen.width * (index + 1).toFloat() / (labels.size + 1).toFloat(),
                defaultY = centerY
            )
        }
    }

    private fun savePlantFoodCalibration(points: List<PvzCalibrationPoint>) {
        PvzCalibrationStorage.savePlantFoodPoints(
            service,
            points.map { point ->
                PvzCalibrationStorage.NamedPoint(
                    key = point.key,
                    x = pixelXToStoredPercent(point.x.toInt()),
                    y = pixelYToStoredPercent(point.y.toInt())
                )
            }
        )
    }

    fun showArtifactCalibrationPicker() {
        val wm = windowManagerProvider() ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = PvzPointCalibrationView(service)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        view.setPoints(artifactCalibrationPoints())
        view.onSave = { points ->
            saveArtifactCalibration(points)
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(service, R.string.pvz_artifact_calibration_saved, Toast.LENGTH_SHORT).show()
        }
        view.onCancel = {
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(STOP_DEBUG_TAG, "Failed to show PVZ2 artifact calibration picker", e)
        }
    }

    private fun artifactCalibrationPoints(): List<PvzCalibrationPoint> {
        val saved = PvzCalibrationStorage.getArtifactPoints(service).associateBy { it.key }
        if (saved.isNotEmpty()) {
            return artifactPointSpecs().map { spec ->
                val point = saved[spec.key]
                PvzCalibrationPoint(
                    key = spec.key,
                    label = spec.label,
                    x = point?.x?.let { storedPercentXToPointPx(it).toFloat() } ?: spec.defaultX,
                    y = point?.y?.let { storedPercentYToPointPx(it).toFloat() } ?: spec.defaultY
                )
            }
        }
        return artifactPointSpecs().map { spec ->
            PvzCalibrationPoint(
                key = spec.key,
                label = spec.label,
                x = spec.defaultX,
                y = spec.defaultY
            )
        }
    }

    private data class ArtifactPointSpec(
        val key: String,
        val label: String,
        val defaultX: Float,
        val defaultY: Float
    )

    private fun artifactPointSpecs(): List<ArtifactPointSpec> {
        val screen = currentProgramScreenSize()
        val centerY = screen.height / 2f
        val labels = listOf(
            PvzCalibrationStorage.ARTIFACT_MAIN to service.getString(R.string.pvz_artifact_main),
            PvzCalibrationStorage.ARTIFACT_SMALL to service.getString(R.string.pvz_artifact_small),
            PvzCalibrationStorage.ARTIFACT_MEDIUM to service.getString(R.string.pvz_artifact_medium),
            PvzCalibrationStorage.ARTIFACT_LARGE to service.getString(R.string.pvz_artifact_large),
            PvzCalibrationStorage.ARTIFACT_SWITCH to service.getString(R.string.pvz_artifact_switch),
            PvzCalibrationStorage.ARTIFACT_GOURD to service.getString(R.string.pvz_artifact_gourd),
            PvzCalibrationStorage.ARTIFACT_BOWLING to service.getString(R.string.pvz_artifact_bowling),
            PvzCalibrationStorage.ARTIFACT_EQUIPMENT to service.getString(R.string.pvz_artifact_equipment),
            PvzCalibrationStorage.ARTIFACT_CLOSE to service.getString(R.string.pvz_artifact_close)
        )
        return labels.mapIndexed { index, (key, label) ->
            ArtifactPointSpec(
                key = key,
                label = label,
                defaultX = screen.width * (index + 1).toFloat() / (labels.size + 1).toFloat(),
                defaultY = centerY
            )
        }
    }

    private fun saveArtifactCalibration(points: List<PvzCalibrationPoint>) {
        PvzCalibrationStorage.saveArtifactPoints(
            service,
            points.map { point ->
                PvzCalibrationStorage.NamedPoint(
                    key = point.key,
                    x = pixelXToStoredPercent(point.x.toInt()),
                    y = pixelYToStoredPercent(point.y.toInt())
                )
            }
        )
    }

    fun showCucumberCalibrationPicker() {
        val wm = windowManagerProvider() ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = PvzPointCalibrationView(service)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        view.setPoints(cucumberCalibrationPoints())
        view.onSave = { points ->
            saveCucumberCalibration(points)
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(service, R.string.pvz_cucumber_calibration_saved, Toast.LENGTH_SHORT).show()
        }
        view.onCancel = {
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(STOP_DEBUG_TAG, "Failed to show PVZ2 cucumber calibration picker", e)
        }
    }

    private fun cucumberCalibrationPoints(): List<PvzCalibrationPoint> {
        val saved = PvzCalibrationStorage.getCucumberPoints(service).associateBy { it.key }
        if (saved.isNotEmpty()) {
            return cucumberPointSpecs().map { spec ->
                val point = saved[spec.key]
                PvzCalibrationPoint(
                    key = spec.key,
                    label = spec.label,
                    x = point?.x?.let { storedPercentXToPointPx(it).toFloat() } ?: spec.defaultX,
                    y = point?.y?.let { storedPercentYToPointPx(it).toFloat() } ?: spec.defaultY
                )
            }
        }
        return cucumberPointSpecs().map { spec ->
            PvzCalibrationPoint(
                key = spec.key,
                label = spec.label,
                x = spec.defaultX,
                y = spec.defaultY
            )
        }
    }

    private data class CucumberPointSpec(
        val key: String,
        val label: String,
        val defaultX: Float,
        val defaultY: Float
    )

    private fun cucumberPointSpecs(): List<CucumberPointSpec> {
        val screen = currentProgramScreenSize()
        val centerY = screen.height / 2f
        val labels = listOf(
            PvzCalibrationStorage.CUCUMBER_MAIN to service.getString(R.string.pvz_cucumber_main),
            PvzCalibrationStorage.CUCUMBER_DROP to service.getString(R.string.pvz_cucumber_drop),
            PvzCalibrationStorage.CUCUMBER_CLOSE to service.getString(R.string.pvz_cucumber_close)
        )
        return labels.mapIndexed { index, (key, label) ->
            CucumberPointSpec(
                key = key,
                label = label,
                defaultX = screen.width * (index + 1).toFloat() / (labels.size + 1).toFloat(),
                defaultY = centerY
            )
        }
    }

    private fun saveCucumberCalibration(points: List<PvzCalibrationPoint>) {
        PvzCalibrationStorage.saveCucumberPoints(
            service,
            points.map { point ->
                PvzCalibrationStorage.NamedPoint(
                    key = point.key,
                    x = pixelXToStoredPercent(point.x.toInt()),
                    y = pixelYToStoredPercent(point.y.toInt())
                )
            }
        )
    }

    fun showRechargeCalibrationPicker() {
        val wm = windowManagerProvider() ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = PvzPointCalibrationView(service)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        view.setPoints(rechargeCalibrationPoints())
        view.onSave = { points ->
            saveRechargeCalibration(points)
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(service, R.string.pvz_recharge_calibration_saved, Toast.LENGTH_SHORT).show()
        }
        view.onCancel = {
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(STOP_DEBUG_TAG, "Failed to show PVZ2 recharge calibration picker", e)
        }
    }

    private fun rechargeCalibrationPoints(): List<PvzCalibrationPoint> {
        val saved = PvzCalibrationStorage.getRechargePoints(service).associateBy { it.key }
        if (saved.isNotEmpty()) {
            return rechargePointSpecs().map { spec ->
                val point = saved[spec.key]
                PvzCalibrationPoint(
                    key = spec.key,
                    label = spec.label,
                    x = point?.x?.let { storedPercentXToPointPx(it).toFloat() } ?: spec.defaultX,
                    y = point?.y?.let { storedPercentYToPointPx(it).toFloat() } ?: spec.defaultY
                )
            }
        }
        return rechargePointSpecs().map { spec ->
            PvzCalibrationPoint(
                key = spec.key,
                label = spec.label,
                x = spec.defaultX,
                y = spec.defaultY
            )
        }
    }

    private data class RechargePointSpec(
        val key: String,
        val label: String,
        val defaultX: Float,
        val defaultY: Float
    )

    private fun rechargePointSpecs(): List<RechargePointSpec> {
        val screen = currentProgramScreenSize()
        val centerY = screen.height / 2f
        val labels = listOf(
            PvzCalibrationStorage.RECHARGE_MAIN to service.getString(R.string.pvz_recharge_main),
            PvzCalibrationStorage.RECHARGE_CLOSE to service.getString(R.string.pvz_recharge_close)
        )
        return labels.mapIndexed { index, (key, label) ->
            RechargePointSpec(
                key = key,
                label = label,
                defaultX = screen.width * (index + 1).toFloat() / (labels.size + 1).toFloat(),
                defaultY = centerY
            )
        }
    }

    private fun saveRechargeCalibration(points: List<PvzCalibrationPoint>) {
        PvzCalibrationStorage.saveRechargePoints(
            service,
            points.map { point ->
                PvzCalibrationStorage.NamedPoint(
                    key = point.key,
                    x = pixelXToStoredPercent(point.x.toInt()),
                    y = pixelYToStoredPercent(point.y.toInt())
                )
            }
        )
    }

    fun showCardsCalibrationPicker() {
        val wm = windowManagerProvider() ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = PvzEndlessSupplyCalibrationView(service)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        view.setCalibration(cardsCalibrationArea(), cardsCalibrationPoints())
        view.onSave = { area, points ->
            saveCardsCalibration(area, points)
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(service, R.string.pvz_cards_calibration_saved, Toast.LENGTH_SHORT).show()
        }
        view.onCancel = {
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(STOP_DEBUG_TAG, "Failed to show PVZ2 cards calibration picker", e)
        }
    }

    private fun cardsCalibrationArea(): PvzCalibrationArea {
        val saved = PvzCalibrationStorage.getFinalWaveTextArea(service)
        val screen = currentProgramScreenSize()
        val rect = if (saved != null) {
            Rect(
                storedPercentXToEdgePx(saved.left),
                storedPercentYToEdgePx(saved.top),
                storedPercentXToEdgePx(saved.right),
                storedPercentYToEdgePx(saved.bottom)
            )
        } else {
            Rect(
                (screen.width * 0.35f).toInt(),
                (screen.height * 0.12f).toInt(),
                (screen.width * 0.65f).toInt(),
                (screen.height * 0.22f).toInt()
            )
        }
        return PvzCalibrationArea(
            label = service.getString(R.string.pvz_final_wave_text_area),
            rect = android.graphics.RectF(rect)
        )
    }

    private fun cardsCalibrationPoints(): List<PvzCalibrationPoint> {
        val screen = currentProgramScreenSize()
        val saved = PvzCalibrationStorage.getCardsPoints(service).associateBy { it.key }
        val labels = listOf(
            PvzCalibrationStorage.CARDS_POKER to service.getString(R.string.pvz_cards_poker),
            PvzCalibrationStorage.FINAL_WAVE_RED to service.getString(R.string.pvz_final_wave_red)
        )
        val centerY = screen.height / 2f
        return labels.mapIndexed { index, (key, label) ->
            val point = saved[key]
            PvzCalibrationPoint(
                key = key,
                label = label,
                x = point?.x?.let { storedPercentXToPointPx(it).toFloat() }
                    ?: screen.width * (index + 1).toFloat() / (labels.size + 1).toFloat(),
                y = point?.y?.let { storedPercentYToPointPx(it).toFloat() } ?: centerY
            )
        }
    }

    private fun saveCardsCalibration(area: PvzCalibrationArea, points: List<PvzCalibrationPoint>) {
        val rect = area.rect
        PvzCalibrationStorage.saveCards(
            service,
            textArea = PvzCalibrationStorage.Area(
                left = pixelXToStoredPercent(rect.left.toInt()),
                top = pixelYToStoredPercent(rect.top.toInt()),
                right = pixelXToStoredPercent(rect.right.toInt()),
                bottom = pixelYToStoredPercent(rect.bottom.toInt())
            ),
            points = points.map { point ->
                PvzCalibrationStorage.NamedPoint(
                    key = point.key,
                    x = pixelXToStoredPercent(point.x.toInt()),
                    y = pixelYToStoredPercent(point.y.toInt())
                )
            }
        )
    }

    fun showStartBattleRelatedCalibrationPicker() {
        val wm = windowManagerProvider() ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = PvzPointCalibrationView(service)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        view.setPoints(startBattleRelatedCalibrationPoints())
        view.onSave = { points ->
            saveStartBattleRelatedCalibration(points)
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(service, R.string.pvz_start_battle_related_calibration_saved, Toast.LENGTH_SHORT).show()
        }
        view.onCancel = {
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(STOP_DEBUG_TAG, "Failed to show PVZ2 start battle related calibration picker", e)
        }
    }

    private fun startBattleRelatedCalibrationPoints(): List<PvzCalibrationPoint> {
        val saved = PvzCalibrationStorage.getStartBattlePoints(service).associateBy { it.key }
        if (saved.isNotEmpty()) {
            return startBattleRelatedPointSpecs().map { spec ->
                val point = saved[spec.key]
                PvzCalibrationPoint(
                    key = spec.key,
                    label = spec.label,
                    x = point?.x?.let { storedPercentXToPointPx(it).toFloat() } ?: spec.defaultX,
                    y = point?.y?.let { storedPercentYToPointPx(it).toFloat() } ?: spec.defaultY
                )
            }
        }
        return startBattleRelatedPointSpecs().map { spec ->
            PvzCalibrationPoint(
                key = spec.key,
                label = spec.label,
                x = spec.defaultX,
                y = spec.defaultY
            )
        }
    }

    private data class StartBattleRelatedPointSpec(
        val key: String,
        val label: String,
        val defaultX: Float,
        val defaultY: Float
    )

    private fun startBattleRelatedPointSpecs(): List<StartBattleRelatedPointSpec> {
        val screen = currentProgramScreenSize()
        val labels = listOf(
            PvzCalibrationStorage.START_BATTLE to service.getString(R.string.pvz_start_battle),
            PvzCalibrationStorage.CARD_START_BATTLE to service.getString(R.string.pvz_card_start_battle),
            PvzCalibrationStorage.START_BATTLE_PAIR to service.getString(R.string.pvz_start_battle_pair),
            PvzCalibrationStorage.START_BATTLE_DECK_1 to service.getString(R.string.pvz_start_battle_deck_1),
            PvzCalibrationStorage.START_BATTLE_DECK_2 to service.getString(R.string.pvz_start_battle_deck_2),
            PvzCalibrationStorage.START_BATTLE_DECK_3 to service.getString(R.string.pvz_start_battle_deck_3)
        )
        val columns = 3
        val rows = (labels.size + columns - 1) / columns
        return labels.mapIndexed { index, (key, label) ->
            val column = index % columns
            val row = index / columns
            StartBattleRelatedPointSpec(
                key = key,
                label = label,
                defaultX = screen.width * (column + 1).toFloat() / (columns + 1).toFloat(),
                defaultY = screen.height * (row + 1).toFloat() / (rows + 1).toFloat()
            )
        }
    }

    private fun saveStartBattleRelatedCalibration(points: List<PvzCalibrationPoint>) {
        PvzCalibrationStorage.saveStartBattlePoints(
            service,
            points.map { point ->
                PvzCalibrationStorage.NamedPoint(
                    key = point.key,
                    x = pixelXToStoredPercent(point.x.toInt()),
                    y = pixelYToStoredPercent(point.y.toInt())
                )
            }
        )
    }

    fun showOtherCalibrationPicker() {
        val wm = windowManagerProvider() ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = PvzPointCalibrationView(service)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        view.setPoints(otherCalibrationPoints())
        view.onSave = { points ->
            saveOtherCalibration(points)
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(service, R.string.pvz_other_calibration_saved, Toast.LENGTH_SHORT).show()
        }
        view.onCancel = {
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(STOP_DEBUG_TAG, "Failed to show PVZ2 other calibration picker", e)
        }
    }

    private fun otherCalibrationPoints(): List<PvzCalibrationPoint> {
        val saved = PvzCalibrationStorage.getOtherPoints(service).associateBy { it.key }
        if (saved.isNotEmpty()) {
            return otherPointSpecs().map { spec ->
                val point = saved[spec.key]
                PvzCalibrationPoint(
                    key = spec.key,
                    label = spec.label,
                    x = point?.x?.let { storedPercentXToPointPx(it).toFloat() } ?: spec.defaultX,
                    y = point?.y?.let { storedPercentYToPointPx(it).toFloat() } ?: spec.defaultY
                )
            }
        }
        return otherPointSpecs().map { spec ->
            PvzCalibrationPoint(
                key = spec.key,
                label = spec.label,
                x = spec.defaultX,
                y = spec.defaultY
            )
        }
    }

    private data class OtherPointSpec(
        val key: String,
        val label: String,
        val defaultX: Float,
        val defaultY: Float
    )

    private fun otherPointSpecs(): List<OtherPointSpec> {
        val screen = currentProgramScreenSize()
        val labels = listOf(
            PvzCalibrationStorage.OTHER_SPEED_UP to service.getString(R.string.pvz_other_speed_up),
            PvzCalibrationStorage.OTHER_PAUSE to service.getString(R.string.pvz_other_pause),
            PvzCalibrationStorage.OTHER_CONTINUE to service.getString(R.string.pvz_other_continue),
            PvzCalibrationStorage.OTHER_RESTART to service.getString(R.string.pvz_other_restart),
            PvzCalibrationStorage.OTHER_BACK_TO_MAP to service.getString(R.string.pvz_other_back_to_map),
            PvzCalibrationStorage.OTHER_SHOVEL to service.getString(R.string.pvz_other_shovel),
            PvzCalibrationStorage.OTHER_NEXT_WAVE to service.getString(R.string.pvz_other_next_wave),
            PvzCalibrationStorage.OTHER_SWITCH_FORM to service.getString(R.string.pvz_other_switch_form)
        )
        val columns = 3
        val rows = (labels.size + columns - 1) / columns
        return labels.mapIndexed { index, (key, label) ->
            val column = index % columns
            val row = index / columns
            OtherPointSpec(
                key = key,
                label = label,
                defaultX = screen.width * (column + 1).toFloat() / (columns + 1).toFloat(),
                defaultY = screen.height * (row + 1).toFloat() / (rows + 1).toFloat()
            )
        }
    }

    private fun saveOtherCalibration(points: List<PvzCalibrationPoint>) {
        PvzCalibrationStorage.saveOtherPoints(
            service,
            points.map { point ->
                PvzCalibrationStorage.NamedPoint(
                    key = point.key,
                    x = pixelXToStoredPercent(point.x.toInt()),
                    y = pixelYToStoredPercent(point.y.toInt())
                )
            }
        )
    }

    fun showEndlessSupplyCalibrationPicker() {
        val wm = windowManagerProvider() ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = PvzEndlessSupplyCalibrationView(service)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        view.setCalibration(endlessSupplyCalibrationArea(), endlessSupplyCalibrationPoints())
        view.onSave = { area, points ->
            saveEndlessSupplyCalibration(area, points)
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(service, R.string.pvz_endless_supply_calibration_saved, Toast.LENGTH_SHORT).show()
        }
        view.onCancel = {
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(STOP_DEBUG_TAG, "Failed to show PVZ2 endless supply calibration picker", e)
        }
    }

    private fun endlessSupplyCalibrationArea(): PvzCalibrationArea {
        val saved = PvzCalibrationStorage.getEndlessSupplyTextArea(service)
        val screen = currentProgramScreenSize()
        val rect = if (saved != null) {
            Rect(
                storedPercentXToEdgePx(saved.left),
                storedPercentYToEdgePx(saved.top),
                storedPercentXToEdgePx(saved.right),
                storedPercentYToEdgePx(saved.bottom)
            )
        } else {
            Rect(
                (screen.width * 0.38f).toInt(),
                (screen.height * 0.22f).toInt(),
                (screen.width * 0.62f).toInt(),
                (screen.height * 0.34f).toInt()
            )
        }
        return PvzCalibrationArea(
            label = service.getString(R.string.pvz_endless_supply_text_area),
            rect = android.graphics.RectF(rect)
        )
    }

    private fun endlessSupplyCalibrationPoints(): List<PvzCalibrationPoint> {
        val saved = PvzCalibrationStorage.getEndlessSupplyPoints(service).associateBy { it.key }
        if (saved.isNotEmpty()) {
            return endlessSupplyPointSpecs().map { spec ->
                val point = saved[spec.key]
                PvzCalibrationPoint(
                    key = spec.key,
                    label = spec.label,
                    x = point?.x?.let { storedPercentXToPointPx(it).toFloat() } ?: spec.defaultX,
                    y = point?.y?.let { storedPercentYToPointPx(it).toFloat() } ?: spec.defaultY
                )
            }
        }
        return endlessSupplyPointSpecs().map { spec ->
            PvzCalibrationPoint(
                key = spec.key,
                label = spec.label,
                x = spec.defaultX,
                y = spec.defaultY
            )
        }
    }

    private data class EndlessSupplyPointSpec(
        val key: String,
        val label: String,
        val defaultX: Float,
        val defaultY: Float
    )

    private fun endlessSupplyPointSpecs(): List<EndlessSupplyPointSpec> {
        val screen = currentProgramScreenSize()
        val labels = listOf(
            PvzCalibrationStorage.ENDLESS_SUPPLY_ABILITY to service.getString(R.string.pvz_endless_supply_ability),
            PvzCalibrationStorage.ENDLESS_SUPPLY_BLUE_CONFIRM to service.getString(R.string.pvz_endless_supply_blue_confirm),
            PvzCalibrationStorage.ENDLESS_SUPPLY_GREEN_CONFIRM to service.getString(R.string.pvz_endless_supply_green_confirm),
            PvzCalibrationStorage.ENDLESS_SUPPLY_FINAL_CONFIRM to service.getString(R.string.pvz_endless_supply_final_confirm),
            PvzCalibrationStorage.ENDLESS_SUPPLY_CONTINUE_CHALLENGE to service.getString(R.string.pvz_endless_supply_continue_challenge)
        )
        val columns = 5
        val rows = (labels.size + columns - 1) / columns
        return labels.mapIndexed { index, (key, label) ->
            val column = index % columns
            val row = index / columns
            EndlessSupplyPointSpec(
                key = key,
                label = label,
                defaultX = screen.width * (column + 1).toFloat() / (columns + 1).toFloat(),
                defaultY = screen.height * (row + 1).toFloat() / (rows + 1).toFloat()
            )
        }
    }

    private fun saveEndlessSupplyCalibration(
        area: PvzCalibrationArea,
        points: List<PvzCalibrationPoint>
    ) {
        val rect = area.rect
        PvzCalibrationStorage.saveEndlessSupply(
            service,
            textArea = PvzCalibrationStorage.Area(
                left = pixelXToStoredPercent(rect.left.toInt()),
                top = pixelYToStoredPercent(rect.top.toInt()),
                right = pixelXToStoredPercent(rect.right.toInt()),
                bottom = pixelYToStoredPercent(rect.bottom.toInt())
            ),
            points = points.map { point ->
                PvzCalibrationStorage.NamedPoint(
                    key = point.key,
                    x = pixelXToStoredPercent(point.x.toInt()),
                    y = pixelYToStoredPercent(point.y.toInt())
                )
            }
        )
    }

    private fun restorePlantSlotsCalibrationArea(selectionView: AreaSelectionView) {
        val area = PvzCalibrationStorage.getPlantSlotsArea(service) ?: return
        selectionView.setInitialRect(
            storedPercentXToEdgePx(area.left),
            storedPercentYToEdgePx(area.top),
            storedPercentXToEdgePx(area.right),
            storedPercentYToEdgePx(area.bottom)
        )
    }

    private fun savePlantSlotsCalibration(rect: Rect) {
        val slotHeight = rect.height().toFloat() / PLANT_SLOT_COUNT.toFloat()
        val centerX = rect.left + rect.width() / 2f
        val points = (0 until PLANT_SLOT_COUNT).map { index ->
            val centerY = rect.top + slotHeight * (index + 0.5f)
            PvzCalibrationStorage.Point(
                x = pixelXToStoredPercent(centerX.toInt()),
                y = pixelYToStoredPercent(centerY.toInt())
            )
        }
        PvzCalibrationStorage.savePlantSlots(
            service,
            left = pixelXToStoredPercent(rect.left),
            top = pixelYToStoredPercent(rect.top),
            right = pixelXToStoredPercent(rect.right),
            bottom = pixelYToStoredPercent(rect.bottom),
            centers = points
        )
    }

    fun hideCalibrationPickerOverlay(revealOverlays: Boolean) {
        val view = calibrationPickerView ?: return
        if (view is PvzPointCalibrationView) {
            view.cleanup()
        }
        if (view is PvzEndlessSupplyCalibrationView) {
            view.cleanup()
        }
        try {
            windowManagerProvider()?.removeView(view)
        } catch (_: Exception) {
        }
        calibrationPickerView = null
        // Recycle the background bitmap if present
        areaPickerBackgroundBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        areaPickerBackgroundBitmap = null
        if (revealOverlays) {
            ScreenshotHider.revealAll()
        }
    }

    private fun pixelXToStoredPercent(x: Int): Int {
        return ProgramCoordinateAdapter.pointToStoredPercent(x, currentProgramScreenSize().width)
    }

    private fun pixelYToStoredPercent(y: Int): Int {
        return ProgramCoordinateAdapter.pointToStoredPercent(y, currentProgramScreenSize().height)
    }

    private fun storedPercentXToEdgePx(value: Int): Int {
        return ProgramCoordinateAdapter.storedPercentToEdgePx(value, currentProgramScreenSize().width)
    }

    private fun storedPercentYToEdgePx(value: Int): Int {
        return ProgramCoordinateAdapter.storedPercentToEdgePx(value, currentProgramScreenSize().height)
    }

    private fun storedPercentXToPointPx(value: Int): Int {
        return ProgramCoordinateAdapter.storedPercentToPointPx(value, currentProgramScreenSize().width)
    }

    private fun storedPercentYToPointPx(value: Int): Int {
        return ProgramCoordinateAdapter.storedPercentToPointPx(value, currentProgramScreenSize().height)
    }

    fun currentProgramScreenSize(): ProgramScreenSize {
        ScreenCaptureManager.refreshDisplayMetrics(service)
        val display = ScreenCaptureDisplayReader.current(service)
        return ProgramScreenSizePolicy.choose(
            captureWidth = ScreenCaptureManager.getCaptureWidth(),
            captureHeight = ScreenCaptureManager.getCaptureHeight(),
            displayWidth = display.width,
            displayHeight = display.height,
            fallbackWidth = service.resources.displayMetrics.widthPixels,
            fallbackHeight = service.resources.displayMetrics.heightPixels
        )
    }

    fun createFullScreenPickerParams(): WindowManager.LayoutParams {
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

    private companion object {
        const val STOP_DEBUG_TAG = "ClickerStopDebug"
        const val PLANT_SLOT_COUNT = 8
    }
}
