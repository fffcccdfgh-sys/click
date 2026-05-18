package com.fffcccdfgh.androidclicker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.luaj.vm2.Globals
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

                // Register callable functions
                globals.rawset(LuaValue.valueOf("tap"), object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        ClickerBridge.tap(
                            args.arg(1).checkint(),
                            args.arg(2).checkint(),
                            if (args.narg() >= 3) args.arg(3).checklong() else 1L
                        )
                        return LuaValue.NIL
                    }
                })
                globals.rawset(LuaValue.valueOf("swipe"), object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        ClickerBridge.swipe(
                            args.arg(1).checkint(),
                            args.arg(2).checkint(),
                            args.arg(3).checkint(),
                            args.arg(4).checkint(),
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
                        return LuaValue.valueOf(
                            ClickerBridge.checkText(
                                args.arg(1).tojstring(),
                                args.arg(2).checkint(),
                                args.arg(3).checkint(),
                                args.arg(4).checkint(),
                                args.arg(5).checkint()
                            )
                        )
                    }
                })
                globals.rawset(LuaValue.valueOf("check_text_not"), object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        return LuaValue.valueOf(
                            ClickerBridge.checkTextNot(
                                args.arg(1).tojstring(),
                                args.arg(2).checkint(),
                                args.arg(3).checkint(),
                                args.arg(4).checkint(),
                                args.arg(5).checkint()
                            )
                        )
                    }
                })
                globals.rawset(LuaValue.valueOf("check_color"), object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        return LuaValue.valueOf(
                            ClickerBridge.checkColor(
                                args.arg(1).tojstring(),
                                if (args.narg() >= 2) args.arg(2).checkint() else 10,
                                if (args.narg() >= 3) args.arg(3).checkint() else 0,
                                if (args.narg() >= 4) args.arg(4).checkint() else 0
                            )
                        )
                    }
                })
                globals.rawset(LuaValue.valueOf("check_color_not"), object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        return LuaValue.valueOf(
                            ClickerBridge.checkColorNot(
                                args.arg(1).tojstring(),
                                if (args.narg() >= 2) args.arg(2).checkint() else 10,
                                if (args.narg() >= 3) args.arg(3).checkint() else 0,
                                if (args.narg() >= 4) args.arg(4).checkint() else 0
                            )
                        )
                    }
                })
                globals.rawset(LuaValue.valueOf("ocr_text"), object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        return LuaValue.valueOf(
                            ClickerBridge.ocrText(
                                args.arg(1).tojstring(),
                                args.arg(2).checkint(),
                                args.arg(3).checkint(),
                                args.arg(4).checkint(),
                                args.arg(5).checkint()
                            )
                        )
                    }
                })
                globals.rawset(LuaValue.valueOf("ocr_text_not"), object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        return LuaValue.valueOf(
                            ClickerBridge.ocrTextNot(
                                args.arg(1).tojstring(),
                                args.arg(2).checkint(),
                                args.arg(3).checkint(),
                                args.arg(4).checkint(),
                                args.arg(5).checkint()
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
}
