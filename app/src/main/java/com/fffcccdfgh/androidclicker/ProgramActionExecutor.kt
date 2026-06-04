package com.fffcccdfgh.androidclicker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import kotlin.coroutines.coroutineContext

object ProgramActionExecutor {

    private var luaThread: Thread? = null
    private var luaGlobals: Globals? = null

    /**
     * Execute Lua code on a background thread.
     */
    suspend fun execute(
        service: ClickAccessibilityService,
        code: String,
        canDispatchAction: ((ActionStep) -> Boolean)?,
        onBlocked: (() -> Unit)?
    ) {
        luaThread = Thread.currentThread()
        try {
            withContext(Dispatchers.IO) {
                val globals = JsePlatform.standardGlobals()
                luaGlobals = globals
                registerPvzCalibrationGlobals(service, globals)

                // Register callable functions
                globals.rawset(LuaValue.valueOf("tap"), object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        val screen = currentScreenSize(service)
                        ClickerBridge.tap(
                            ProgramCoordinateAdapter.xArgToPointPx(args.arg(1), screen.width),
                            ProgramCoordinateAdapter.yArgToPointPx(args.arg(2), screen.height),
                            if (args.narg() >= 3) args.arg(3).checklong() else 1L
                        )
                        return LuaValue.NIL
                    }
                })
                globals.rawset(LuaValue.valueOf("swipe"), object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        val screen = currentScreenSize(service)
                        ClickerBridge.swipe(
                            ProgramCoordinateAdapter.xArgToPointPx(args.arg(1), screen.width),
                            ProgramCoordinateAdapter.yArgToPointPx(args.arg(2), screen.height),
                            ProgramCoordinateAdapter.xArgToPointPx(args.arg(3), screen.width),
                            ProgramCoordinateAdapter.yArgToPointPx(args.arg(4), screen.height),
                            if (args.narg() >= 5) args.arg(5).checklong() else 300L
                        )
                        return LuaValue.NIL
                    }
                })
                globals.rawset(LuaValue.valueOf("wait"), object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        ClickerBridge.wait_(args.arg(1).checklong())
                        return LuaValue.NIL
                    }
                })
                globals.rawset(LuaValue.valueOf("check_text"), object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        val screen = currentScreenSize(service)
                        return LuaValue.valueOf(
                            ClickerBridge.checkText(
                                args.arg(1).tojstring(),
                                ProgramCoordinateAdapter.xArgToEdgePx(args.arg(2), screen.width),
                                ProgramCoordinateAdapter.yArgToEdgePx(args.arg(3), screen.height),
                                ProgramCoordinateAdapter.xArgToEdgePx(args.arg(4), screen.width),
                                ProgramCoordinateAdapter.yArgToEdgePx(args.arg(5), screen.height)
                            )
                        )
                    }
                })
                globals.rawset(LuaValue.valueOf("check_text_not"), object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        val screen = currentScreenSize(service)
                        return LuaValue.valueOf(
                            ClickerBridge.checkTextNot(
                                args.arg(1).tojstring(),
                                ProgramCoordinateAdapter.xArgToEdgePx(args.arg(2), screen.width),
                                ProgramCoordinateAdapter.yArgToEdgePx(args.arg(3), screen.height),
                                ProgramCoordinateAdapter.xArgToEdgePx(args.arg(4), screen.width),
                                ProgramCoordinateAdapter.yArgToEdgePx(args.arg(5), screen.height)
                            )
                        )
                    }
                })
                globals.rawset(LuaValue.valueOf("check_color"), object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        val screen = currentScreenSize(service)
                        return LuaValue.valueOf(
                            ClickerBridge.checkColor(
                                args.arg(1).tojstring(),
                                if (args.narg() >= 2) args.arg(2).checkint() else 10,
                                if (args.narg() >= 3) ProgramCoordinateAdapter.xArgToPointPx(args.arg(3), screen.width) else 0,
                                if (args.narg() >= 4) ProgramCoordinateAdapter.yArgToPointPx(args.arg(4), screen.height) else 0
                            )
                        )
                    }
                })
                globals.rawset(LuaValue.valueOf("check_color_not"), object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        val screen = currentScreenSize(service)
                        return LuaValue.valueOf(
                            ClickerBridge.checkColorNot(
                                args.arg(1).tojstring(),
                                if (args.narg() >= 2) args.arg(2).checkint() else 10,
                                if (args.narg() >= 3) ProgramCoordinateAdapter.xArgToPointPx(args.arg(3), screen.width) else 0,
                                if (args.narg() >= 4) ProgramCoordinateAdapter.yArgToPointPx(args.arg(4), screen.height) else 0
                            )
                        )
                    }
                })
                globals.rawset(LuaValue.valueOf("ocr_text"), object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        val screen = currentScreenSize(service)
                        return LuaValue.valueOf(
                            ClickerBridge.ocrText(
                                args.arg(1).tojstring(),
                                ProgramCoordinateAdapter.xArgToEdgePx(args.arg(2), screen.width),
                                ProgramCoordinateAdapter.yArgToEdgePx(args.arg(3), screen.height),
                                ProgramCoordinateAdapter.xArgToEdgePx(args.arg(4), screen.width),
                                ProgramCoordinateAdapter.yArgToEdgePx(args.arg(5), screen.height)
                            )
                        )
                    }
                })
                globals.rawset(LuaValue.valueOf("ocr_text_not"), object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        val screen = currentScreenSize(service)
                        return LuaValue.valueOf(
                            ClickerBridge.ocrTextNot(
                                args.arg(1).tojstring(),
                                ProgramCoordinateAdapter.xArgToEdgePx(args.arg(2), screen.width),
                                ProgramCoordinateAdapter.yArgToEdgePx(args.arg(3), screen.height),
                                ProgramCoordinateAdapter.xArgToEdgePx(args.arg(4), screen.width),
                                ProgramCoordinateAdapter.yArgToEdgePx(args.arg(5), screen.height)
                            )
                        )
                    }
                })
                globals.rawset(LuaValue.valueOf("parallel"), object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        val n = args.narg()
                        val threads = mutableListOf<Thread>()
                        val errors = mutableListOf<Exception>()
                        for (i in 1..n) {
                            val func = args.arg(i)
                            if (func.isfunction()) {
                                val t = Thread {
                                    try {
                                        func.call()
                                    } catch (_: StopExecutionException) {
                                        // Normal stop
                                    } catch (e: Exception) {
                                        synchronized(errors) { errors.add(e) }
                                    }
                                }
                                t.start()
                                threads.add(t)
                            }
                        }
                        for (t in threads) t.join()
                        // Propagate the first error, if any
                        synchronized(errors) {
                            if (errors.isNotEmpty()) throw errors.first()
                        }
                        return LuaValue.NIL
                    }
                })

                // Load and execute the user script
                val chunk = globals.load(code, "script")
                chunk.call()
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: StopExecutionException) {
            // Normal stop – re-throw so withContext aborts
            throw e
        } catch (_: Exception) {
            // Lua runtime error – silently ignore during execution
        } finally {
            ClickerBridge.stopped = false
            luaGlobals = null
            luaThread = null
        }
    }

    fun stopLua() {
        ClickerBridge.stopped = true
        luaThread?.interrupt()
        luaThread = null
    }

    private fun currentScreenSize(service: ClickAccessibilityService): ProgramScreenSize {
        ScreenCaptureManager.refreshDisplayMetrics(service)
        val captureWidth = ScreenCaptureManager.getCaptureWidth()
        val captureHeight = ScreenCaptureManager.getCaptureHeight()
        if (captureWidth > 0 && captureHeight > 0) {
            return ProgramScreenSize(captureWidth, captureHeight)
        }
        val display = ScreenCaptureDisplayReader.current(service)
        return ProgramScreenSize(display.width, display.height)
    }

    private fun registerPvzCalibrationGlobals(service: ClickAccessibilityService, globals: Globals) {
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

    private fun storedPercentToLuaPercent(value: Int): Double {
        return value.toDouble() / 100.0
    }
}
