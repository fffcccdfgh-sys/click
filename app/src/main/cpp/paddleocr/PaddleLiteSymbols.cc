#include "paddle_api.h"

#include <android/log.h>
#include <dlfcn.h>
#include <memory>
#include <string>
#include <vector>

namespace {

constexpr const char *TAG = "PaddleLiteSymbols";

void *paddleHandle() {
  static void *handle = []() -> void * {
    void *loaded = dlopen("libpaddle_lite_jni.so", RTLD_NOW | RTLD_NOLOAD);
    if (loaded == nullptr) {
      loaded = dlopen("libpaddle_lite_jni.so", RTLD_NOW | RTLD_GLOBAL);
    }
    if (loaded == nullptr) {
      const char *error = dlerror();
      __android_log_print(ANDROID_LOG_ERROR, TAG,
                          "Unable to open libpaddle_lite_jni.so: %s",
                          error == nullptr ? "unknown" : error);
    }
    return loaded;
  }();
  return handle;
}

template <typename Fn>
Fn paddleSymbol(const char *name) {
  void *handle = paddleHandle();
  if (handle == nullptr) {
    return nullptr;
  }
  auto symbol = reinterpret_cast<Fn>(dlsym(handle, name));
  if (symbol == nullptr) {
    const char *error = dlerror();
    __android_log_print(ANDROID_LOG_ERROR, TAG, "Missing symbol %s: %s", name,
                        error == nullptr ? "unknown" : error);
  }
  return symbol;
}

} // namespace

namespace paddle {
namespace lite_api {

ConfigBase::ConfigBase(PowerMode mode, int threads) {
  using Fn = void (*)(ConfigBase *, PowerMode, int);
  static Fn fn = paddleSymbol<Fn>(
      "_ZN6paddle8lite_api10ConfigBaseC2ENS0_9PowerModeEi");
  if (fn != nullptr) {
    fn(this, mode, threads);
  }
}

void ConfigBase::set_threads(int threads) {
  using Fn = void (*)(ConfigBase *, int);
  static Fn fn =
      paddleSymbol<Fn>("_ZN6paddle8lite_api10ConfigBase11set_threadsEi");
  if (fn != nullptr) {
    fn(this, threads);
  }
}

void ConfigBase::set_power_mode(PowerMode mode) {
  using Fn = void (*)(ConfigBase *, PowerMode);
  static Fn fn = paddleSymbol<Fn>(
      "_ZN6paddle8lite_api10ConfigBase14set_power_modeENS0_9PowerModeE");
  if (fn != nullptr) {
    fn(this, mode);
  }
}

void MobileConfig::set_model_from_file(const std::string &path) {
  using Fn = void (*)(MobileConfig *, const std::string &);
  static Fn fn = paddleSymbol<Fn>(
      "_ZN6paddle8lite_api12MobileConfig19set_model_from_fileERKNSt6__ndk112basic_stringIcNS2_11char_traitsIcEENS2_9allocatorIcEEEE");
  if (fn != nullptr) {
    fn(this, path);
  }
}

template <>
std::shared_ptr<PaddlePredictor>
CreatePaddlePredictor<MobileConfig>(const MobileConfig &config) {
  using Fn = std::shared_ptr<PaddlePredictor> (*)(const MobileConfig &);
  static Fn fn = paddleSymbol<Fn>(
      "_ZN6paddle8lite_api21CreatePaddlePredictorINS0_12MobileConfigEEENSt6__ndk110shared_ptrINS0_15PaddlePredictorEEERKT_");
  if (fn != nullptr) {
    return fn(config);
  }
  return nullptr;
}

void Tensor::Resize(const shape_t &shape) {
  using Fn = void (*)(Tensor *, const shape_t &);
  static Fn fn =
      paddleSymbol<Fn>("_ZN6paddle8lite_api6Tensor6ResizeERKNSt6__ndk16vectorIlNS2_9allocatorIlEEEE");
  if (fn != nullptr) {
    fn(this, shape);
  }
}

template <>
float *Tensor::mutable_data<float>(TargetType type) const {
  using Fn = float *(*)(const Tensor *, TargetType);
  static Fn fn = paddleSymbol<Fn>(
      "_ZNK6paddle8lite_api6Tensor12mutable_dataIfEEPT_NS0_10TargetTypeE");
  return fn == nullptr ? nullptr : fn(this, type);
}

template <>
const float *Tensor::data<float>() const {
  using Fn = const float *(*)(const Tensor *);
  static Fn fn =
      paddleSymbol<Fn>("_ZNK6paddle8lite_api6Tensor4dataIfEEPKT_v");
  return fn == nullptr ? nullptr : fn(this);
}

shape_t Tensor::shape() const {
  using Fn = shape_t (*)(const Tensor *);
  static Fn fn = paddleSymbol<Fn>("_ZNK6paddle8lite_api6Tensor5shapeEv");
  if (fn != nullptr) {
    return fn(this);
  }
  return {};
}

} // namespace lite_api
} // namespace paddle
