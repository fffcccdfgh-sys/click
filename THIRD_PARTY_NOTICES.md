# Third-Party Software

Click includes some third-party libraries, native binaries, and OCR model files.
They keep their own licenses; the project license only applies to code written
for this app.

## Included In The Repository

- OpenCV 4.2.0 headers and `libopencv_java4.so`
  - Used by the PaddleOCR native pipeline.
  - License: OpenCV BSD 3-Clause style license.
  - Website: https://opencv.org/

- Paddle Lite headers and `libpaddle_lite_jni.so`
  - Used to run PaddleOCR models on Android.
  - License: Apache License 2.0.
  - Project: https://github.com/PaddlePaddle/Paddle-Lite

- PaddleOCR native code and mobile OCR model files
  - Stored under `app/src/main/cpp/paddleocr/` and
    `app/src/main/assets/paddleocr/`.
  - License: Apache License 2.0.
  - Project: https://github.com/PaddlePaddle/PaddleOCR

- Clipper
  - Stored in `app/src/main/cpp/paddleocr/clipper.*`.
  - License: Boost Software License 1.0.
  - Author noted in source: Angus Johnson.

- Android C++ runtime
  - `app/src/main/jniLibs/arm64-v8a/libc++_shared.so`
  - Comes from the Android NDK.

## Gradle Dependencies

The app also uses dependencies from Google Maven and Maven Central, including
AndroidX, Material Components, Kotlin coroutines, Google ML Kit Chinese Text
Recognition, LuaJ, JUnit, AndroidX Test, Espresso, and JSON.org for tests.

Google ML Kit terms:

- https://developers.google.com/ml-kit/terms
