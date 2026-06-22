# 第三方软件

Click 包含一些第三方库、native 二进制文件和 OCR 模型文件。这些内容保留各自的许可证；本项目许可证只适用于为这个 App 编写的自有代码。

## 随仓库提供的内容

- OpenCV 4.2.0 头文件和 `libopencv_java4.so`
  - 用于 PaddleOCR native 流程。
  - 许可证：OpenCV BSD 3-Clause 风格许可证。
  - 官网：https://opencv.org/

- Paddle Lite 头文件和 `libpaddle_lite_jni.so`
  - 用于在 Android 上运行 PaddleOCR 模型。
  - 许可证：Apache License 2.0。
  - 项目：https://github.com/PaddlePaddle/Paddle-Lite

- PaddleOCR native 代码和移动端 OCR 模型文件
  - 存放在 `app/src/main/cpp/paddleocr/` 和 `app/src/main/assets/paddleocr/`。
  - 许可证：Apache License 2.0。
  - 项目：https://github.com/PaddlePaddle/PaddleOCR

- Clipper
  - 存放在 `app/src/main/cpp/paddleocr/clipper.*`。
  - 许可证：Boost Software License 1.0。
  - 源码中注明的作者：Angus Johnson。

- Android C++ runtime
  - `app/src/main/jniLibs/arm64-v8a/libc++_shared.so`
  - 来自 Android NDK。

## Gradle 依赖

App 还使用来自 Google Maven 和 Maven Central 的依赖，包括 AndroidX、Material Components、Kotlin coroutines、Google ML Kit Chinese Text Recognition、LuaJ、JUnit、AndroidX Test、Espresso，以及测试用的 JSON.org。

Google ML Kit 条款：

- https://developers.google.com/ml-kit/terms
