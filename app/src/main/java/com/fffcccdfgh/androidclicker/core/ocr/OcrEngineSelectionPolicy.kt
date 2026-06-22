package com.fffcccdfgh.androidclicker.core.ocr

import android.os.Build

object OcrEngineSelectionPolicy {
    private const val ARM64_ABI = "arm64-v8a"

    data class RuntimeInfo(
        val supportedAbis: List<String>,
        val hardware: String,
        val model: String,
        val manufacturer: String,
        val fingerprint: String,
        val product: String,
        val brand: String,
        val device: String,
        val isQemu: Boolean
    )

    fun preferPaddle(): Boolean = preferPaddle(fromBuild())

    fun preferPaddle(runtime: RuntimeInfo): Boolean {
        return runtime.supportedAbis.firstOrNull()?.equals(ARM64_ABI, ignoreCase = true) == true &&
            !runtime.isLikelyEmulator()
    }

    private fun fromBuild(): RuntimeInfo {
        val systemAbiList = systemProperty("ro.product.cpu.abilist")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val supportedAbis = systemAbiList.ifEmpty {
            Build.SUPPORTED_ABIS?.toList().orEmpty()
        }

        return RuntimeInfo(
            supportedAbis = supportedAbis,
            hardware = Build.HARDWARE.orEmpty(),
            model = Build.MODEL.orEmpty(),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            fingerprint = Build.FINGERPRINT.orEmpty(),
            product = Build.PRODUCT.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            isQemu = systemProperty("ro.kernel.qemu") == "1"
        )
    }

    private fun RuntimeInfo.isLikelyEmulator(): Boolean {
        if (isQemu) return true

        val signals = listOf(
            hardware,
            model,
            manufacturer,
            fingerprint,
            product,
            brand,
            device
        ).map { it.lowercase() }

        return signals.any { value ->
            value.contains("goldfish") ||
                value.contains("ranchu") ||
                value.contains("generic") ||
                value.contains("sdk_gphone") ||
                value.contains("google_sdk") ||
                value.contains("emulator") ||
                value.contains("genymotion") ||
                value.contains("vbox")
        }
    }

    private fun systemProperty(name: String): String {
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            systemProperties
                .getMethod("get", String::class.java, String::class.java)
                .invoke(null, name, "") as String
        } catch (_: Throwable) {
            ""
        }
    }
}
