package com.fffcccdfgh.androidclicker

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ActionStepTest {
    @Test
    fun legacyStepGapMsIsIgnoredWhenScriptsAreSavedAgain() {
        val action = ActionStep.fromJson(
            JSONObject(
                """
                {
                  "type": "tap",
                  "x": 10,
                  "y": 20,
                  "durationMs": 1,
                  "delayBeforeMs": 2,
                  "stepGapMs": 1000,
                  "repeatCount": 3
                }
                """.trimIndent()
            )
        )

        assertFalse(action.toJson().has("stepGapMs"))
    }

    @Test
    fun textConditionOcrFilterIsSavedAndLoaded() {
        val action = ActionStep(
            type = ActionStep.TYPE_TAP,
            x = 10,
            y = 20,
            conditionType = ActionStep.CONDITION_TEXT_CONTAINS,
            conditionText = "ready",
            conditionOcrFilter = OcrFilterMode.THRESHOLD_INVERT
        )

        val reloaded = ActionStep.fromJson(action.toJson())

        assertEquals(OcrFilterMode.THRESHOLD_INVERT, reloaded.conditionOcrFilter)
    }

    @Test
    fun legacyTextConditionDefaultsToBlackWhiteOcrFilter() {
        val action = ActionStep.fromJson(
            JSONObject(
                """
                {
                  "type": "tap",
                  "x": 10,
                  "y": 20,
                  "conditionType": "text_contains",
                  "conditionText": "ready"
                }
                """.trimIndent()
            )
        )

        assertEquals(OcrFilterMode.THRESHOLD, action.conditionOcrFilter)
    }
}
