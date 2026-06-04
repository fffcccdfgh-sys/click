package com.fffcccdfgh.androidclicker

import android.content.Context

object PvzCalibrationStorage {
    private const val PREFS_NAME = "pvz_calibration"
    private const val PLANT_SLOT_COUNT = 8
    const val BOARD_ROWS = 5
    const val BOARD_COLUMNS = 9

    const val PLANT_SLOTS = "plant_slots"
    const val BOARD = "board"
    const val SUN = "sun"
    const val PLANT_FOOD = "plant_food"
    const val ARTIFACT = "artifact"
    const val CUCUMBER = "cucumber"
    const val RECHARGE = "recharge"
    const val CARDS = "cards"
    const val OTHER = "other"
    const val SUN_BUY_KEY = "sun_buy_key"
    const val SUN_AD = "sun_ad"
    const val SUN_10_DIAMOND = "sun_10_diamond"
    const val SUN_CLOSE = "sun_close"
    val SUN_POINT_KEYS = listOf(SUN_BUY_KEY, SUN_AD, SUN_10_DIAMOND, SUN_CLOSE)
    const val PLANT_FOOD_BEAN = "plant_food_bean"
    const val PLANT_FOOD_PLUS = "plant_food_plus"
    const val PLANT_FOOD_BUY = "plant_food_buy"
    val PLANT_FOOD_POINT_KEYS = listOf(PLANT_FOOD_BEAN, PLANT_FOOD_PLUS, PLANT_FOOD_BUY)
    const val ARTIFACT_MAIN = "artifact_main"
    const val ARTIFACT_SMALL = "artifact_small"
    const val ARTIFACT_MEDIUM = "artifact_medium"
    const val ARTIFACT_LARGE = "artifact_large"
    val ARTIFACT_POINT_KEYS = listOf(ARTIFACT_MAIN, ARTIFACT_SMALL, ARTIFACT_MEDIUM, ARTIFACT_LARGE)
    const val CUCUMBER_MAIN = "cucumber_main"
    const val CUCUMBER_DROP = "cucumber_drop"
    const val CUCUMBER_CLOSE = "cucumber_close"
    val CUCUMBER_POINT_KEYS = listOf(CUCUMBER_MAIN, CUCUMBER_DROP, CUCUMBER_CLOSE)
    const val RECHARGE_MAIN = "recharge_main"
    const val RECHARGE_CLOSE = "recharge_close"
    val RECHARGE_POINT_KEYS = listOf(RECHARGE_MAIN, RECHARGE_CLOSE)
    const val CARDS_EDGE = "cards_edge"
    val CARDS_POINT_KEYS = listOf(CARDS_EDGE)
    const val OTHER_SPEED_UP = "other_speed_up"
    const val OTHER_PAUSE = "other_pause"
    const val OTHER_CONTINUE = "other_continue"
    const val OTHER_RESTART = "other_restart"
    const val OTHER_BACK_TO_MAP = "other_back_to_map"
    const val OTHER_SHOVEL = "other_shovel"
    const val OTHER_CARD_START_BATTLE = "other_card_start_battle"
    const val OTHER_START_BATTLE = "other_start_battle"
    const val OTHER_FINAL_WAVE_RED = "other_final_wave_red"
    const val OTHER_NEXT_WAVE = "other_next_wave"
    const val OTHER_SWITCH_FORM = "other_switch_form"
    val OTHER_POINT_KEYS = listOf(
        OTHER_SPEED_UP,
        OTHER_PAUSE,
        OTHER_CONTINUE,
        OTHER_RESTART,
        OTHER_BACK_TO_MAP,
        OTHER_SHOVEL,
        OTHER_CARD_START_BATTLE,
        OTHER_START_BATTLE,
        OTHER_FINAL_WAVE_RED,
        OTHER_NEXT_WAVE,
        OTHER_SWITCH_FORM
    )

    data class Point(
        val x: Int,
        val y: Int
    )

    data class Area(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    data class GridPoint(
        val row: Int,
        val column: Int,
        val x: Int,
        val y: Int
    )

    data class NamedPoint(
        val key: String,
        val x: Int,
        val y: Int
    )

    fun isCalibrated(context: Context, key: String): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(key, false)
    }

    fun saveBoard(
        context: Context,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        centers: List<GridPoint>
    ) {
        if (centers.size != BOARD_ROWS * BOARD_COLUMNS) return
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(BOARD, true)
            .putInt("${BOARD}_left", left)
            .putInt("${BOARD}_top", top)
            .putInt("${BOARD}_right", right)
            .putInt("${BOARD}_bottom", bottom)
        centers.forEach { point ->
            if (point.row in 1..BOARD_ROWS && point.column in 1..BOARD_COLUMNS) {
                val prefix = boardCellPrefix(point.row, point.column)
                editor
                    .putInt("${prefix}_x", point.x)
                    .putInt("${prefix}_y", point.y)
            }
        }
        editor.apply()
    }

    fun getBoardPoints(context: Context): List<GridPoint> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(BOARD, false)) return emptyList()
        val points = mutableListOf<GridPoint>()
        for (row in 1..BOARD_ROWS) {
            for (column in 1..BOARD_COLUMNS) {
                val prefix = boardCellPrefix(row, column)
                val xKey = "${prefix}_x"
                val yKey = "${prefix}_y"
                if (!prefs.contains(xKey) || !prefs.contains(yKey)) return emptyList()
                points.add(
                    GridPoint(
                        row = row,
                        column = column,
                        x = prefs.getInt(xKey, 0),
                        y = prefs.getInt(yKey, 0)
                    )
                )
            }
        }
        return points
    }

    fun getBoardPoint(context: Context, row: Int, column: Int): GridPoint? {
        if (row !in 1..BOARD_ROWS || column !in 1..BOARD_COLUMNS) return null
        return getBoardPoints(context).firstOrNull { it.row == row && it.column == column }
    }

    fun getBoardArea(context: Context): Area? {
        return getArea(context, BOARD)
    }

    fun saveSunPoints(context: Context, points: List<NamedPoint>) {
        if (points.size != SUN_POINT_KEYS.size) return
        val pointsByKey = points.associateBy { it.key }
        if (SUN_POINT_KEYS.any { it !in pointsByKey }) return

        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(SUN, true)
        SUN_POINT_KEYS.forEach { key ->
            val point = pointsByKey.getValue(key)
            editor
                .putInt("${key}_x", point.x)
                .putInt("${key}_y", point.y)
        }
        editor.apply()
    }

    fun getSunPoints(context: Context): List<NamedPoint> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(SUN, false)) return emptyList()
        return SUN_POINT_KEYS.mapNotNull { key ->
            val xKey = "${key}_x"
            val yKey = "${key}_y"
            if (!prefs.contains(xKey) || !prefs.contains(yKey)) {
                null
            } else {
                NamedPoint(
                    key = key,
                    x = prefs.getInt(xKey, 0),
                    y = prefs.getInt(yKey, 0)
                )
            }
        }.takeIf { it.size == SUN_POINT_KEYS.size }.orEmpty()
    }

    fun savePlantFoodPoints(context: Context, points: List<NamedPoint>) {
        saveNamedPoints(context, PLANT_FOOD, PLANT_FOOD_POINT_KEYS, points)
    }

    fun getPlantFoodPoints(context: Context): List<NamedPoint> {
        return getNamedPoints(context, PLANT_FOOD, PLANT_FOOD_POINT_KEYS)
    }

    fun saveArtifactPoints(context: Context, points: List<NamedPoint>) {
        saveNamedPoints(context, ARTIFACT, ARTIFACT_POINT_KEYS, points)
    }

    fun getArtifactPoints(context: Context): List<NamedPoint> {
        return getNamedPoints(context, ARTIFACT, ARTIFACT_POINT_KEYS)
    }

    fun saveCucumberPoints(context: Context, points: List<NamedPoint>) {
        saveNamedPoints(context, CUCUMBER, CUCUMBER_POINT_KEYS, points)
    }

    fun getCucumberPoints(context: Context): List<NamedPoint> {
        return getNamedPoints(context, CUCUMBER, CUCUMBER_POINT_KEYS)
    }

    fun saveRechargePoints(context: Context, points: List<NamedPoint>) {
        saveNamedPoints(context, RECHARGE, RECHARGE_POINT_KEYS, points)
    }

    fun getRechargePoints(context: Context): List<NamedPoint> {
        return getNamedPoints(context, RECHARGE, RECHARGE_POINT_KEYS)
    }

    fun saveCardsPoints(context: Context, points: List<NamedPoint>) {
        saveNamedPoints(context, CARDS, CARDS_POINT_KEYS, points)
    }

    fun getCardsPoints(context: Context): List<NamedPoint> {
        return getNamedPoints(context, CARDS, CARDS_POINT_KEYS)
    }

    fun saveOtherPoints(context: Context, points: List<NamedPoint>) {
        saveNamedPoints(context, OTHER, OTHER_POINT_KEYS, points)
    }

    fun getOtherPoints(context: Context): List<NamedPoint> {
        return getNamedPoints(context, OTHER, OTHER_POINT_KEYS)
    }

    fun savePlantSlots(
        context: Context,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        centers: List<Point>
    ) {
        if (centers.size != PLANT_SLOT_COUNT) return
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(PLANT_SLOTS, true)
            .putInt("${PLANT_SLOTS}_left", left)
            .putInt("${PLANT_SLOTS}_top", top)
            .putInt("${PLANT_SLOTS}_right", right)
            .putInt("${PLANT_SLOTS}_bottom", bottom)
        centers.forEachIndexed { index, point ->
            val slot = index + 1
            editor
                .putInt("${PLANT_SLOTS}_${slot}_x", point.x)
                .putInt("${PLANT_SLOTS}_${slot}_y", point.y)
        }
        editor.apply()
    }

    fun getPlantSlots(context: Context): List<Point> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PLANT_SLOTS, false)) return emptyList()
        return (1..PLANT_SLOT_COUNT).mapNotNull { slot ->
            val xKey = "${PLANT_SLOTS}_${slot}_x"
            val yKey = "${PLANT_SLOTS}_${slot}_y"
            if (!prefs.contains(xKey) || !prefs.contains(yKey)) {
                null
            } else {
                Point(
                    x = prefs.getInt(xKey, 0),
                    y = prefs.getInt(yKey, 0)
                )
            }
        }.takeIf { it.size == PLANT_SLOT_COUNT }.orEmpty()
    }

    fun getPlantSlot(context: Context, slot: Int): Point? {
        if (slot !in 1..PLANT_SLOT_COUNT) return null
        return getPlantSlots(context).getOrNull(slot - 1)
    }

    fun getPlantSlotsArea(context: Context): Area? {
        return getArea(context, PLANT_SLOTS)
    }

    private fun getArea(context: Context, key: String): Area? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(key, false)) return null
        val leftKey = "${key}_left"
        val topKey = "${key}_top"
        val rightKey = "${key}_right"
        val bottomKey = "${key}_bottom"
        if (!prefs.contains(leftKey) ||
            !prefs.contains(topKey) ||
            !prefs.contains(rightKey) ||
            !prefs.contains(bottomKey)
        ) {
            return null
        }
        val area = Area(
            left = prefs.getInt(leftKey, 0),
            top = prefs.getInt(topKey, 0),
            right = prefs.getInt(rightKey, 0),
            bottom = prefs.getInt(bottomKey, 0)
        )
        return area.takeIf { it.right > it.left && it.bottom > it.top }
    }

    private fun boardCellPrefix(row: Int, column: Int): String {
        return "${BOARD}_r${row}_c${column}"
    }

    private fun saveNamedPoints(
        context: Context,
        calibrationKey: String,
        requiredKeys: List<String>,
        points: List<NamedPoint>
    ) {
        if (points.size != requiredKeys.size) return
        val pointsByKey = points.associateBy { it.key }
        if (requiredKeys.any { it !in pointsByKey }) return

        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(calibrationKey, true)
        requiredKeys.forEach { key ->
            val point = pointsByKey.getValue(key)
            editor
                .putInt("${key}_x", point.x)
                .putInt("${key}_y", point.y)
        }
        editor.apply()
    }

    private fun getNamedPoints(
        context: Context,
        calibrationKey: String,
        requiredKeys: List<String>
    ): List<NamedPoint> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(calibrationKey, false)) return emptyList()
        return requiredKeys.mapNotNull { key ->
            val xKey = "${key}_x"
            val yKey = "${key}_y"
            if (!prefs.contains(xKey) || !prefs.contains(yKey)) {
                null
            } else {
                NamedPoint(
                    key = key,
                    x = prefs.getInt(xKey, 0),
                    y = prefs.getInt(yKey, 0)
                )
            }
        }.takeIf { it.size == requiredKeys.size }.orEmpty()
    }
}
