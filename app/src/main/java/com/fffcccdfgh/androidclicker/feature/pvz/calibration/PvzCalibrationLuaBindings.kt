package com.fffcccdfgh.androidclicker.feature.pvz.calibration

import com.fffcccdfgh.androidclicker.core.accessibility.ClickAccessibilityService
import com.fffcccdfgh.androidclicker.core.program.ProgramLuaGlobalRegistrar
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

object PvzCalibrationLuaBindings : ProgramLuaGlobalRegistrar {
    override fun register(service: ClickAccessibilityService, globals: Globals) {
        val plantSlots = PvzCalibrationStorage.getPlantSlots(service)
        if (plantSlots.isNotEmpty()) {
            val slotsTable = LuaTable()
            plantSlots.forEachIndexed { index, point ->
                val slotIndex = index + 1
                val slotTable = pointTable(point.x, point.y)
                globals.rawset(LuaValue.valueOf("plant_slot_$slotIndex"), slotTable)
                slotsTable.rawset(slotIndex, slotTable)
            }
            globals.rawset(LuaValue.valueOf("plant_slots"), slotsTable)
        }

        val boardPoints = PvzCalibrationStorage.getBoardPoints(service)
        if (boardPoints.isNotEmpty()) {
            val boardTable = LuaTable()
            for (row in 1..PvzCalibrationStorage.BOARD_ROWS) {
                val rowTable = LuaTable()
                for (column in 1..PvzCalibrationStorage.BOARD_COLUMNS) {
                    val point = boardPoints.firstOrNull {
                        it.row == row && it.column == column
                    } ?: continue
                    val cellTable = pointTable(point.x, point.y)
                    globals.rawset(LuaValue.valueOf("board_r${row}_c$column"), cellTable)
                    rowTable.rawset(column, cellTable)
                }
                boardTable.rawset(row, rowTable)
            }
            globals.rawset(LuaValue.valueOf("board"), boardTable)
        }

        val sunPoints = PvzCalibrationStorage.getSunPoints(service)
        if (sunPoints.isNotEmpty()) {
            val sunTable = LuaTable()
            sunPoints.forEach { point ->
                val pointTable = pointTable(point.x, point.y)
                globals.rawset(LuaValue.valueOf(point.key), pointTable)
                sunTable.rawset(LuaValue.valueOf(point.key), pointTable)
            }
            globals.rawset(LuaValue.valueOf("sun_points"), sunTable)
        }

        val plantFoodPoints = PvzCalibrationStorage.getPlantFoodPoints(service)
        if (plantFoodPoints.isNotEmpty()) {
            val plantFoodTable = LuaTable()
            plantFoodPoints.forEach { point ->
                val pointTable = pointTable(point.x, point.y)
                globals.rawset(LuaValue.valueOf(point.key), pointTable)
                plantFoodTable.rawset(LuaValue.valueOf(point.key), pointTable)
            }
            globals.rawset(LuaValue.valueOf("plant_food_points"), plantFoodTable)
        }

        val artifactPoints = PvzCalibrationStorage.getArtifactPoints(service)
        if (artifactPoints.isNotEmpty()) {
            val artifactTable = LuaTable()
            artifactPoints.forEach { point ->
                val pointTable = pointTable(point.x, point.y)
                globals.rawset(LuaValue.valueOf(point.key), pointTable)
                artifactTable.rawset(LuaValue.valueOf(point.key), pointTable)
            }
            globals.rawset(LuaValue.valueOf("artifact_points"), artifactTable)
        }

        val cucumberPoints = PvzCalibrationStorage.getCucumberPoints(service)
        if (cucumberPoints.isNotEmpty()) {
            val cucumberTable = LuaTable()
            cucumberPoints.forEach { point ->
                val pointTable = pointTable(point.x, point.y)
                globals.rawset(LuaValue.valueOf(point.key), pointTable)
                cucumberTable.rawset(LuaValue.valueOf(point.key), pointTable)
            }
            globals.rawset(LuaValue.valueOf("cucumber_points"), cucumberTable)
        }

        val rechargePoints = PvzCalibrationStorage.getRechargePoints(service)
        if (rechargePoints.isNotEmpty()) {
            val rechargeTable = LuaTable()
            rechargePoints.forEach { point ->
                val pointTable = pointTable(point.x, point.y)
                globals.rawset(LuaValue.valueOf(point.key), pointTable)
                rechargeTable.rawset(LuaValue.valueOf(point.key), pointTable)
            }
            globals.rawset(LuaValue.valueOf("recharge_points"), rechargeTable)
        }

        val cardsPoints = PvzCalibrationStorage.getCardsPoints(service)
        if (cardsPoints.isNotEmpty()) {
            val cardsTable = LuaTable()
            cardsPoints.forEach { point ->
                val pointTable = pointTable(point.x, point.y)
                globals.rawset(LuaValue.valueOf(point.key), pointTable)
                cardsTable.rawset(LuaValue.valueOf(point.key), pointTable)
            }
            globals.rawset(LuaValue.valueOf("cards_points"), cardsTable)
        }
        val finalWaveTextArea = PvzCalibrationStorage.getFinalWaveTextArea(service)
        if (finalWaveTextArea != null) {
            globals.rawset(
                LuaValue.valueOf("final_wave_text_area"),
                areaTable(finalWaveTextArea)
            )
        }

        val startBattlePoints = PvzCalibrationStorage.getStartBattlePoints(service)
        if (startBattlePoints.isNotEmpty()) {
            val startBattleTable = LuaTable()
            startBattlePoints.forEach { point ->
                val pointTable = pointTable(point.x, point.y)
                globals.rawset(LuaValue.valueOf(point.key), pointTable)
                startBattleTable.rawset(LuaValue.valueOf(point.key), pointTable)
            }
            globals.rawset(LuaValue.valueOf("start_battle_points"), startBattleTable)
        }

        val otherPoints = PvzCalibrationStorage.getOtherPoints(service)
        if (otherPoints.isNotEmpty()) {
            val otherTable = LuaTable()
            otherPoints.forEach { point ->
                val pointTable = pointTable(point.x, point.y)
                globals.rawset(LuaValue.valueOf(point.key), pointTable)
                otherTable.rawset(LuaValue.valueOf(point.key), pointTable)
            }
            globals.rawset(LuaValue.valueOf("other_points"), otherTable)
        }

        val endlessSupplyArea = PvzCalibrationStorage.getEndlessSupplyTextArea(service)
        if (endlessSupplyArea != null) {
            globals.rawset(
                LuaValue.valueOf("endless_supply_text_area"),
                areaTable(endlessSupplyArea)
            )
        }

        val endlessSupplyPoints = PvzCalibrationStorage.getEndlessSupplyPoints(service)
        if (endlessSupplyPoints.isNotEmpty()) {
            val endlessSupplyTable = LuaTable()
            endlessSupplyPoints.forEach { point ->
                val pointTable = pointTable(point.x, point.y)
                globals.rawset(LuaValue.valueOf(point.key), pointTable)
                endlessSupplyTable.rawset(LuaValue.valueOf(point.key), pointTable)
            }
            globals.rawset(LuaValue.valueOf("endless_supply_points"), endlessSupplyTable)
        }
    }

    private fun pointTable(x: Int, y: Int): LuaTable {
        return LuaTable().apply {
            rawset(
                LuaValue.valueOf("x"),
                LuaValue.valueOf(storedPercentToLuaPercent(x))
            )
            rawset(
                LuaValue.valueOf("y"),
                LuaValue.valueOf(storedPercentToLuaPercent(y))
            )
        }
    }

    private fun areaTable(area: PvzCalibrationStorage.Area): LuaTable {
        return LuaTable().apply {
            rawset(
                LuaValue.valueOf("left"),
                LuaValue.valueOf(storedPercentToLuaPercent(area.left))
            )
            rawset(
                LuaValue.valueOf("top"),
                LuaValue.valueOf(storedPercentToLuaPercent(area.top))
            )
            rawset(
                LuaValue.valueOf("right"),
                LuaValue.valueOf(storedPercentToLuaPercent(area.right))
            )
            rawset(
                LuaValue.valueOf("bottom"),
                LuaValue.valueOf(storedPercentToLuaPercent(area.bottom))
            )
        }
    }

    private fun storedPercentToLuaPercent(value: Int): Double {
        return value.toDouble() / 100.0
    }
}
