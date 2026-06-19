package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.core.ocr.OcrEngineSelectionPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrEngineSelectionPolicyTest {
    @Test
    fun prefersPaddleOnArm64PhysicalDevice() {
        val runtime = OcrEngineSelectionPolicy.RuntimeInfo(
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
            hardware = "qcom",
            model = "DBR-W10",
            manufacturer = "HUAWEI",
            fingerprint = "HUAWEI/DBR-W10/HWDBR-H:12/HUAWEIDBR-W10/104.2.0.245C00:user/release-keys",
            product = "DBR-W10",
            brand = "HUAWEI",
            device = "HWDBR-H",
            isQemu = false
        )

        assertTrue(OcrEngineSelectionPolicy.preferPaddle(runtime))
    }

    @Test
    fun usesMlKitOnX86Runtime() {
        val runtime = OcrEngineSelectionPolicy.RuntimeInfo(
            supportedAbis = listOf("x86_64"),
            hardware = "ranchu",
            model = "sdk_gphone64_x86_64",
            manufacturer = "Google",
            fingerprint = "google/sdk_gphone64_x86_64/emulator:12/ABC:userdebug/test-keys",
            product = "sdk_gphone64_x86_64",
            brand = "google",
            device = "emulator64_x86_64",
            isQemu = true
        )

        assertFalse(OcrEngineSelectionPolicy.preferPaddle(runtime))
    }

    @Test
    fun usesMlKitWhenX86RuntimeExposesArm64ThroughNativeBridge() {
        val runtime = OcrEngineSelectionPolicy.RuntimeInfo(
            supportedAbis = listOf("x86_64", "arm64-v8a", "x86", "armeabi-v7a", "armeabi"),
            hardware = "HONOR",
            model = "BVL-AN20",
            manufacturer = "HONOR",
            fingerprint = "HONOR/Bvlgari/Bvlgari:12/V417IR/1892:user/release-keys",
            product = "Bvlgari",
            brand = "HONOR",
            device = "Bvlgari",
            isQemu = false
        )

        assertFalse(OcrEngineSelectionPolicy.preferPaddle(runtime))
    }

    @Test
    fun usesMlKitOnArm64EmulatorRuntime() {
        val runtime = OcrEngineSelectionPolicy.RuntimeInfo(
            supportedAbis = listOf("arm64-v8a"),
            hardware = "ranchu",
            model = "sdk_gphone64_arm64",
            manufacturer = "Google",
            fingerprint = "google/sdk_gphone64_arm64/emulator:12/ABC:userdebug/test-keys",
            product = "sdk_gphone64_arm64",
            brand = "google",
            device = "emulator64_arm64",
            isQemu = true
        )

        assertFalse(OcrEngineSelectionPolicy.preferPaddle(runtime))
    }
}
