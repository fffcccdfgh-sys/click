package com.fffcccdfgh.androidclicker

object ScreenshotHider {
    private val hiders = mutableMapOf<String, () -> Unit>()
    private val revealers = mutableMapOf<String, () -> Unit>()

    fun register(key: String, hide: () -> Unit, reveal: () -> Unit) {
        hiders[key] = hide
        revealers[key] = reveal
    }

    fun unregister(key: String) {
        hiders.remove(key)
        revealers.remove(key)
    }

    fun hideAll() {
        hiders.values.toList().forEach { it() }
    }

    fun revealAll() {
        revealers.values.toList().forEach { it() }
    }
}