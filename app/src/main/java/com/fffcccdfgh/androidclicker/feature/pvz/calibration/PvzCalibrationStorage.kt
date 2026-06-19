package com.fffcccdfgh.androidclicker.feature.pvz.calibration

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
    const val START_BATTLE_RELATED = "start_battle_related"
    const val OTHER = "other"
    const val ENDLESS_SUPPLY = "endless_supply"
    const val ENDLESS_SUPPLY_TEXT_AREA = "endless_supply_text_area"
    const val SUN_BUY_KEY = "sun_buy_key"
    const val SUN_AD = "sun_ad"
    const val SUN_10_DIAMOND = "sun_10_diamond"
    const val SUN_CLOSE = "sun_close"
    val SUN_POINT_KEYS = listOf(SUN_BUY_KEY, SUN_AD, SUN_10_DIAMOND, SUN_CLOSE)
    const val PLANT_FOOD_BEAN = "plant_food_bean"
    const val PLANT_FOOD_PLUS = "plant_food_plus"
    const val PLANT_FOOD_BUY = "plant_food_buy"
    val PLANT_FOOD_POINT_KEYS = listOf(PLANT_FOOD_BEAN, PLANT_FOOD_PLUS, PLANT_FOOD_BUY)
    const val ARTIFACT_SWITCH = "artifact_switch"
    const val ARTIFACT_GOURD = "artifact_gourd"
    const val ARTIFACT_BOWLING = "artifact_bowling"
    const val ARTIFACT_EQUIPMENT = "artifact_equipment"
    const val ARTIFACT_CLOSE = "artifact_close"
    const val ARTIFACT_MAIN = "artifact_main"
    const val ARTIFACT_SMALL = "artifact_small"
    const val ARTIFACT_MEDIUM = "artifact_medium"
    const val ARTIFACT_LARGE = "artifact_large"
    val ARTIFACT_POINT_KEYS = listOf(
        ARTIFACT_MAIN,
        ARTIFACT_SMALL,
        ARTIFACT_MEDIUM,
        ARTIFACT_LARGE,
        ARTIFACT_SWITCH,
        ARTIFACT_GOURD,
        ARTIFACT_BOWLING,
        ARTIFACT_EQUIPMENT,
        ARTIFACT_CLOSE
    )
    const val CUCUMBER_MAIN = "cucumber_main"
    const val CUCUMBER_DROP = "cucumber_drop"
    const val CUCUMBER_CLOSE = "cucumber_close"
    val CUCUMBER_POINT_KEYS = listOf(CUCUMBER_MAIN, CUCUMBER_DROP, CUCUMBER_CLOSE)
    const val RECHARGE_MAIN = "recharge_main"
    const val RECHARGE_CLOSE = "recharge_close"
    val RECHARGE_POINT_KEYS = listOf(RECHARGE_MAIN, RECHARGE_CLOSE)
    const val CARDS_POKER = "cards_poker"
    const val FINAL_WAVE_RED = "final_wave_red"
    const val FINAL_WAVE_TEXT_AREA = "final_wave_text_area"
    val CARDS_POINT_KEYS = listOf(CARDS_POKER, FINAL_WAVE_RED)
    const val START_BATTLE = "start_battle"
    const val CARD_START_BATTLE = "card_start_battle"
    const val START_BATTLE_PAIR = "start_battle_pair"
    const val START_BATTLE_DECK_1 = "start_battle_deck_1"
    const val START_BATTLE_DECK_2 = "start_battle_deck_2"
    const val START_BATTLE_DECK_3 = "start_battle_deck_3"
    val START_BATTLE_POINT_KEYS = listOf(
        START_BATTLE,
        CARD_START_BATTLE,
        START_BATTLE_PAIR,
        START_BATTLE_DECK_1,
        START_BATTLE_DECK_2,
        START_BATTLE_DECK_3
    )
    const val OTHER_SPEED_UP = "other_speed_up"
    const val OTHER_PAUSE = "other_pause"
    const val OTHER_CONTINUE = "other_continue"
    const val OTHER_RESTART = "other_restart"
    const val OTHER_BACK_TO_MAP = "other_back_to_map"
    const val OTHER_SHOVEL = "other_shovel"
    const val OTHER_NEXT_WAVE = "other_next_wave"
    const val OTHER_SWITCH_FORM = "other_switch_form"
    val OTHER_POINT_KEYS = listOf(
        OTHER_SPEED_UP,
        OTHER_PAUSE,
        OTHER_CONTINUE,
        OTHER_RESTART,
        OTHER_BACK_TO_MAP,
        OTHER_SHOVEL,
        OTHER_NEXT_WAVE,
        OTHER_SWITCH_FORM
    )
    const val ENDLESS_SUPPLY_ABILITY = "endless_supply_ability"
    const val ENDLESS_SUPPLY_BLUE_CONFIRM = "endless_supply_blue_confirm"
    const val ENDLESS_SUPPLY_GREEN_CONFIRM = "endless_supply_green_confirm"
    const val ENDLESS_SUPPLY_FINAL_CONFIRM = "endless_supply_final_confirm"
    const val ENDLESS_SUPPLY_CONTINUE_CHALLENGE = "endless_supply_continue_challenge"
    val ENDLESS_SUPPLY_POINT_KEYS = listOf(
        ENDLESS_SUPPLY_ABILITY,
        ENDLESS_SUPPLY_BLUE_CONFIRM,
        ENDLESS_SUPPLY_GREEN_CONFIRM,
        ENDLESS_SUPPLY_FINAL_CONFIRM,
        ENDLESS_SUPPLY_CONTINUE_CHALLENGE
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
        if (key == CARDS) {
            return getFinalWaveTextArea(context) != null &&
                getCardsPoints(context).size == CARDS_POINT_KEYS.size
        }
        if (key == ENDLESS_SUPPLY) {
            return getEndlessSupplyTextArea(context) != null &&
                getEndlessSupplyPoints(context).size == ENDLESS_SUPPLY_POINT_KEYS.size
        }
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
        return getNamedPointsAllowingPartial(context, ARTIFACT, ARTIFACT_POINT_KEYS)
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

    fun saveCards(context: Context, textArea: Area, points: List<NamedPoint>) {
        if (textArea.right <= textArea.left || textArea.bottom <= textArea.top) return
        if (points.size != CARDS_POINT_KEYS.size) return
        val pointsByKey = points.associateBy { it.key }
        if (CARDS_POINT_KEYS.any { it !in pointsByKey }) return

        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(CARDS, true)
            .putBoolean(FINAL_WAVE_TEXT_AREA, true)
            .putInt("${FINAL_WAVE_TEXT_AREA}_left", textArea.left)
            .putInt("${FINAL_WAVE_TEXT_AREA}_top", textArea.top)
            .putInt("${FINAL_WAVE_TEXT_AREA}_right", textArea.right)
            .putInt("${FINAL_WAVE_TEXT_AREA}_bottom", textArea.bottom)
        CARDS_POINT_KEYS.forEach { key ->
            val point = pointsByKey.getValue(key)
            editor
                .putInt("${key}_x", point.x)
                .putInt("${key}_y", point.y)
        }
        editor.apply()
    }

    fun getCardsPoints(context: Context): List<NamedPoint> {
        return getNamedPoints(context, CARDS, CARDS_POINT_KEYS)
    }

    fun getFinalWaveTextArea(context: Context): Area? {
        return getArea(context, FINAL_WAVE_TEXT_AREA)
    }

    fun saveStartBattlePoints(context: Context, points: List<NamedPoint>) {
        saveNamedPoints(context, START_BATTLE_RELATED, START_BATTLE_POINT_KEYS, points)
    }

    fun getStartBattlePoints(context: Context): List<NamedPoint> {
        return getNamedPoints(context, START_BATTLE_RELATED, START_BATTLE_POINT_KEYS)
    }

    fun saveOtherPoints(context: Context, points: List<NamedPoint>) {
        saveNamedPoints(context, OTHER, OTHER_POINT_KEYS, points)
    }

    fun getOtherPoints(context: Context): List<NamedPoint> {
        return getNamedPoints(context, OTHER, OTHER_POINT_KEYS)
    }

    fun saveEndlessSupply(context: Context, textArea: Area, points: List<NamedPoint>) {
        if (textArea.right <= textArea.left || textArea.bottom <= textArea.top) return
        if (points.size != ENDLESS_SUPPLY_POINT_KEYS.size) return
        val pointsByKey = points.associateBy { it.key }
        if (ENDLESS_SUPPLY_POINT_KEYS.any { it !in pointsByKey }) return

        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(ENDLESS_SUPPLY, true)
            .putBoolean(ENDLESS_SUPPLY_TEXT_AREA, true)
            .putInt("${ENDLESS_SUPPLY_TEXT_AREA}_left", textArea.left)
            .putInt("${ENDLESS_SUPPLY_TEXT_AREA}_top", textArea.top)
            .putInt("${ENDLESS_SUPPLY_TEXT_AREA}_right", textArea.right)
            .putInt("${ENDLESS_SUPPLY_TEXT_AREA}_bottom", textArea.bottom)
        ENDLESS_SUPPLY_POINT_KEYS.forEach { key ->
            val point = pointsByKey.getValue(key)
            editor
                .putInt("${key}_x", point.x)
                .putInt("${key}_y", point.y)
        }
        editor.apply()
    }

    fun getEndlessSupplyTextArea(context: Context): Area? {
        return getArea(context, ENDLESS_SUPPLY_TEXT_AREA)
    }

    fun getEndlessSupplyPoints(context: Context): List<NamedPoint> {
        return getNamedPoints(context, ENDLESS_SUPPLY, ENDLESS_SUPPLY_POINT_KEYS)
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

    private fun getNamedPointsAllowingPartial(
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
        }
    }
}
