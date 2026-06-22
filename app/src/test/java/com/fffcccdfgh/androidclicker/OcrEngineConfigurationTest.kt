package com.fffcccdfgh.androidclicker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OcrEngineConfigurationTest {
    @Test
    fun ocrHelperPrefersPaddleWithMlKitFallback() {
        val source = file("app/src/main/java/com/fffcccdfgh/androidclicker/core/ocr/OcrHelper.kt").readText()

        assertTrue(source.contains("OcrEngineSelectionPolicy.preferPaddle()"))
        assertTrue(source.contains("PaddleOcrHelper.recognizeTextFromBitmap(debugContext, bitmap)"))
        assertTrue(source.contains("MlKitOcrHelper.recognizeTextFromBitmap(debugContext, bitmap)"))
        assertTrue(source.contains("engine=PaddleOCR"))
        assertTrue(source.contains("engine=ML Kit"))
    }

    @Test
    fun nativePaddleBuildAndRuntimeFilesArePresent() {
        val build = file("app/build.gradle.kts").readText()

        assertTrue(build.contains("externalNativeBuild"))
        assertTrue(build.contains("abiFilters += listOf(\"arm64-v8a\")"))
        assertTrue(build.contains("CMakeLists.txt"))
        assertTrue(file("app/src/main/java/com/fffcccdfgh/androidclicker/PaddleOcrNative.java").exists())
        assertTrue(file("app/src/main/java/com/fffcccdfgh/androidclicker/core/ocr/PaddleOcrHelper.kt").exists())
        assertTrue(file("app/src/main/assets/paddleocr").exists())
        assertTrue(file("app/src/main/cpp").exists())
    }

    @Test
    fun opencvSharedLibraryIsNotDuplicatedInThirdPartySdk() {
        val cmake = file("app/src/main/cpp/CMakeLists.txt").readText()

        assertFalse(
            "OpenCV runtime library should live only in jniLibs",
            file("app/src/main/cpp/third_party/OpenCV/sdk/native/libs/arm64-v8a/libopencv_java4.so").exists()
        )
        assertTrue(cmake.contains("OpenCV_LIB_DIR"))
        assertTrue(cmake.contains("jniLibs"))
        assertTrue(cmake.contains("\${OpenCV_LIB_DIR}/libopencv_java4.so"))
    }

    @Test
    fun screenCaptureServiceStillWarmsUpOcrAfterCaptureStarts() {
        val source = file(
            "app/src/main/java/com/fffcccdfgh/androidclicker/core/screencapture/ScreenCaptureForegroundService.kt"
        ).readText()

        assertTrue(source.contains("OcrHelper.debugContext = this"))
        assertTrue(source.contains("OcrHelper.warmUpAsync()"))
    }

    @Test
    fun mlKitOnlyGuardWasRemoved() {
        assertFalse(file("app/src/test/java/com/fffcccdfgh/androidclicker/OcrMlKitOnlyConfigurationTest.kt").exists())
    }

    private fun file(path: String): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val root = if (File(cwd, "settings.gradle.kts").exists()) {
            cwd
        } else {
            cwd.parentFile!!
        }
        return File(root, path)
    }
}
