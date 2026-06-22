#include "Native.h"
#include "pipeline.h"

#include <android/bitmap.h>
#include <android/log.h>
#include <opencv2/core.hpp>
#include <string>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL
PaddleOcrNativeImpl_nativeInit(
    JNIEnv *env, jclass thiz, jstring jDetModelPath, jstring jClsModelPath,
    jstring jRecModelPath, jstring jConfigPath, jstring jLabelPath,
    jint cpuThreadNum, jstring jCPUPowerMode) {
  std::string detModelPath = jstring_to_cpp_string(env, jDetModelPath);
  std::string clsModelPath = jstring_to_cpp_string(env, jClsModelPath);
  std::string recModelPath = jstring_to_cpp_string(env, jRecModelPath);
  std::string configPath = jstring_to_cpp_string(env, jConfigPath);
  std::string labelPath = jstring_to_cpp_string(env, jLabelPath);
  std::string cpuPowerMode = jstring_to_cpp_string(env, jCPUPowerMode);

  __android_log_print(ANDROID_LOG_DEBUG, "PaddleOcrNative",
                      "nativeInit det=%s cls=%s rec=%s config=%s labels=%s "
                      "threads=%d mode=%s",
                      detModelPath.c_str(), clsModelPath.c_str(),
                      recModelPath.c_str(), configPath.c_str(),
                      labelPath.c_str(), cpuThreadNum, cpuPowerMode.c_str());
  return reinterpret_cast<jlong>(
      new Pipeline(detModelPath, clsModelPath, recModelPath, cpuPowerMode,
                   cpuThreadNum, configPath, labelPath));
}

JNIEXPORT jboolean JNICALL
PaddleOcrNativeImpl_nativeRelease(JNIEnv *env, jclass thiz, jlong ctx) {
  if (ctx == 0) {
    return JNI_FALSE;
  }
  Pipeline *pipeline = reinterpret_cast<Pipeline *>(ctx);
  delete pipeline;
  return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
PaddleOcrNativeImpl_nativeRecognizeBitmap(
    JNIEnv *env, jclass thiz, jlong ctx, jobject bitmap) {
  if (ctx == 0 || bitmap == nullptr) {
    return env->NewStringUTF("");
  }

  AndroidBitmapInfo info;
  if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
    __android_log_print(ANDROID_LOG_WARN, "PaddleOcrNative",
                        "AndroidBitmap_getInfo failed");
    return env->NewStringUTF("");
  }
  if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
    __android_log_print(ANDROID_LOG_WARN, "PaddleOcrNative",
                        "Unsupported bitmap format: %d", info.format);
    return env->NewStringUTF("");
  }

  void *pixels = nullptr;
  if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
    __android_log_print(ANDROID_LOG_WARN, "PaddleOcrNative",
                        "AndroidBitmap_lockPixels failed");
    return env->NewStringUTF("");
  }

  cv::Mat rgbaImage(info.height, info.width, CV_8UC4, pixels, info.stride);
  cv::Mat rgbaCopy;
  rgbaImage.copyTo(rgbaCopy);
  AndroidBitmap_unlockPixels(env, bitmap);

  Pipeline *pipeline = reinterpret_cast<Pipeline *>(ctx);
  std::string result = pipeline->ProcessBitmap(rgbaCopy);
  return cpp_string_to_jstring(env, result);
}

#ifdef __cplusplus
}
#endif
