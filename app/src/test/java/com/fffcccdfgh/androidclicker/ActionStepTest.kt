package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.core.execution.ActionStep
import org.json.JSONObject
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
    fun legacyTextConditionOcrFilterIsIgnoredWhenSavedAgain() {
        val action = ActionStep.fromJson(
            JSONObject(
                """
                {
                  "type": "tap",
                  "x": 10,
                  "y": 20,
                  "conditionType": "text_contains",
                  "conditionText": "ready",
                  "conditionOcrFilter": "threshold_invert"
                }
                """.trimIndent()
            )
        )

        assertFalse(action.toJson().has("conditionOcrFilter"))
    }
}
