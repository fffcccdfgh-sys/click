package com.fffcccdfgh.androidclicker

import android.graphics.Rect

object ControlZoneChecker {
    private const val DEFAULT_PADDING_DP = 30

    private val zoneProviders = mutableMapOf<String, () -> Rect?>()

    fun register(key: String, provider: () -> Rect?) {
        zoneProviders[key] = provider
    }

    fun unregister(key: String) {
        zoneProviders.remove(key)
    }

    fun isPointInAnyZone(x: Int, y: Int, paddingPx: Int): Boolean {
        return zoneProviders.values.any { provider ->
            val rect = provider() ?: return@any false
            Rect(rect).apply { inset(-paddingPx, -paddingPx) }.contains(x, y)
        }
    }

    fun doesRectIntersectAnyZone(rect: Rect, paddingPx: Int): Boolean {
        return zoneProviders.values.any { provider ->
            val zone = provider() ?: return@any false
            Rect(zone).apply { inset(-paddingPx, -paddingPx) }.intersect(rect)
        }
    }

    fun isActionInAnyZone(action: ActionStep, paddingPx: Int): Boolean {
        return when (action.type) {
            ActionStep.TYPE_TAP -> isPointInAnyZone(action.x!!, action.y!!, paddingPx)
            ActionStep.TYPE_SWIPE -> {
                // Check both endpoints
                if (isPointInAnyZone(action.startX!!, action.startY!!, paddingPx)) return true
                if (isPointInAnyZone(action.endX!!, action.endY!!, paddingPx)) return true
                // Check bounding box of the swipe path
                val pathRect = Rect(
                    minOf(action.startX!!, action.endX!!),
                    minOf(action.startY!!, action.endY!!),
                    maxOf(action.startX!!, action.endX!!),
                    maxOf(action.startY!!, action.endY!!)
                )
                doesRectIntersectAnyZone(pathRect, paddingPx)
            }
            ActionStep.TYPE_WAIT -> false
            else -> false
        }
    }

    fun doesAnyActionOverlap(sequence: List<ActionStep>, paddingPx: Int): Boolean {
        return sequence.any { isActionInAnyZone(it, paddingPx) }
    }

    fun hasActiveZones(): Boolean {
        return zoneProviders.values.any { it() != null }
    }

    fun dpToPx(density: Float): Int = (DEFAULT_PADDING_DP * density).toInt()
}
