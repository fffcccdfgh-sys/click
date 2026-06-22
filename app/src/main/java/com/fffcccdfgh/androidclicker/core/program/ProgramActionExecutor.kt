package com.fffcccdfgh.androidclicker.core.program

import com.fffcccdfgh.androidclicker.core.accessibility.ClickAccessibilityService
import com.fffcccdfgh.androidclicker.core.execution.ActionStep
import com.fffcccdfgh.androidclicker.core.execution.ClickerBridge
import com.fffcccdfgh.androidclicker.core.execution.StopExecutionException
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenCaptureDisplayReader
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenCaptureManager
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.JsePlatform

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
        onBlocked: (() -> Unit)?,
        extraGlobalRegistrars: List<ProgramLuaGlobalRegistrar> = emptyList()
    ) {
        luaThread = Thread.currentThread()
        try {
            withContext(Dispatchers.IO) {
                val globals = JsePlatform.standardGlobals()
                luaGlobals = globals

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

                extraGlobalRegistrars.forEach { registrar ->
                    registrar.register(service, globals)
                }

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
}
