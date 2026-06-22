#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <string>

namespace {

constexpr const char *TAG = "PaddleOcrLoader";

void *impl_handle = nullptr;

using NativeInitFn = jlong (*)(JNIEnv *, jclass, jstring, jstring, jstring,
                               jstring, jstring, jint, jstring);
using NativeReleaseFn = jboolean (*)(JNIEnv *, jclass, jlong);
using NativeRecognizeBitmapFn = jstring (*)(JNIEnv *, jclass, jlong, jobject);

NativeInitFn impl_native_init = nullptr;
NativeReleaseFn impl_native_release = nullptr;
NativeRecognizeBitmapFn impl_native_recognize_bitmap = nullptr;

void throwUnsatisfiedLinkError(JNIEnv *env, const char *message) {
  jclass errorClass = env->FindClass("java/lang/UnsatisfiedLinkError");
  if (errorClass != nullptr) {
    env->ThrowNew(errorClass, message);
  }
}

bool dlopenGlobal(const char *libraryName, JNIEnv *env) {
  void *handle = dlopen(libraryName, RTLD_NOW | RTLD_GLOBAL);
  if (handle != nullptr) {
    return true;
  }
  const char *error = dlerror();
  __android_log_print(ANDROID_LOG_ERROR, TAG, "dlopen %s failed: %s",
                      libraryName, error == nullptr ? "unknown" : error);
  if (env != nullptr) {
    throwUnsatisfiedLinkError(env, error == nullptr ? libraryName : error);
  }
  return false;
}

bool dlopenGlobalFromDir(const std::string &nativeLibraryDir,
                         const char *libraryName, JNIEnv *env) {
  if (!nativeLibraryDir.empty()) {
    std::string path = nativeLibraryDir + "/" + libraryName;
    if (dlopenGlobal(path.c_str(), nullptr)) {
      __android_log_print(ANDROID_LOG_DEBUG, TAG, "dlopen global ok: %s",
                          path.c_str());
      return true;
    }
    const char *error = dlerror();
    __android_log_print(ANDROID_LOG_WARN, TAG, "dlopen absolute failed: %s: %s",
                        path.c_str(), error == nullptr ? "unknown" : error);
  }
  return dlopenGlobal(libraryName, env);
}

template <typename Fn>
bool resolveSymbol(JNIEnv *env, const char *symbolName, Fn *target) {
  *target = reinterpret_cast<Fn>(dlsym(impl_handle, symbolName));
  if (*target != nullptr) {
    return true;
  }
  const char *error = dlerror();
  __android_log_print(ANDROID_LOG_ERROR, TAG, "dlsym %s failed: %s",
                      symbolName, error == nullptr ? "unknown" : error);
  if (env != nullptr) {
    throwUnsatisfiedLinkError(env, error == nullptr ? symbolName : error);
  }
  return false;
}

std::string jstringToString(JNIEnv *env, jstring value) {
  if (value == nullptr) {
    return "";
  }
  const char *chars = env->GetStringUTFChars(value, nullptr);
  if (chars == nullptr) {
    return "";
  }
  std::string result(chars);
  env->ReleaseStringUTFChars(value, chars);
  return result;
}

bool ensureLoaded(JNIEnv *env, const std::string &nativeLibraryDir) {
  if (impl_handle != nullptr) {
    return true;
  }
  __android_log_print(ANDROID_LOG_DEBUG, TAG, "nativeLibraryDir=%s",
                      nativeLibraryDir.c_str());
  if (!dlopenGlobalFromDir(nativeLibraryDir, "libc++_shared.so", env)) {
    return false;
  }
  if (!dlopenGlobalFromDir(nativeLibraryDir, "libopencv_java4.so", env)) {
    return false;
  }
  if (!dlopenGlobalFromDir(nativeLibraryDir, "libpaddle_lite_jni.so", env)) {
    return false;
  }

  if (!nativeLibraryDir.empty()) {
    std::string implPath = nativeLibraryDir + "/libpaddle_ocr_native.so";
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "dlopen impl: %s",
                        implPath.c_str());
    impl_handle = dlopen(implPath.c_str(), RTLD_NOW | RTLD_GLOBAL);
  }
  if (impl_handle == nullptr) {
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "dlopen impl by soname");
    impl_handle = dlopen("libpaddle_ocr_native.so", RTLD_NOW | RTLD_GLOBAL);
  }
  if (impl_handle == nullptr) {
    const char *error = dlerror();
    __android_log_print(ANDROID_LOG_ERROR, TAG,
                        "dlopen libpaddle_ocr_native.so failed: %s",
                        error == nullptr ? "unknown" : error);
    throwUnsatisfiedLinkError(env, error == nullptr ? "libpaddle_ocr_native.so" : error);
    return false;
  }
  __android_log_print(ANDROID_LOG_DEBUG, TAG, "dlopen impl ok");

  bool resolved = resolveSymbol(
             env,
             "PaddleOcrNativeImpl_nativeInit",
             &impl_native_init) &&
         resolveSymbol(
             env,
             "PaddleOcrNativeImpl_nativeRelease",
             &impl_native_release) &&
         resolveSymbol(
             env,
             "PaddleOcrNativeImpl_nativeRecognizeBitmap",
             &impl_native_recognize_bitmap);
  if (resolved) {
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "resolved impl JNI symbols");
  }
  return resolved;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_fffcccdfgh_androidclicker_PaddleOcrNative_nativeInit(
    JNIEnv *env, jclass thiz, jstring nativeLibraryDir, jstring detModelPath,
    jstring clsModelPath, jstring recModelPath, jstring configPath,
    jstring labelPath, jint cpuThreadNum, jstring cpuPowerMode) {
  if (!ensureLoaded(env, jstringToString(env, nativeLibraryDir))) {
    return 0;
  }
  __android_log_print(ANDROID_LOG_DEBUG, TAG, "forward nativeInit");
  return impl_native_init(env, thiz, detModelPath, clsModelPath, recModelPath,
                          configPath, labelPath, cpuThreadNum, cpuPowerMode);
}

JNIEXPORT jboolean JNICALL
Java_com_fffcccdfgh_androidclicker_PaddleOcrNative_nativeRelease(JNIEnv *env,
                                                                 jclass thiz,
                                                                 jlong handle) {
  if (impl_handle == nullptr) {
    return JNI_FALSE;
  }
  return impl_native_release(env, thiz, handle);
}

JNIEXPORT jstring JNICALL
Java_com_fffcccdfgh_androidclicker_PaddleOcrNative_nativeRecognizeBitmap(
    JNIEnv *env, jclass thiz, jlong handle, jobject bitmap) {
  if (impl_handle == nullptr) {
    return env->NewStringUTF("");
  }
  return impl_native_recognize_bitmap(env, thiz, handle, bitmap);
}

}
