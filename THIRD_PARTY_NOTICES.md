# Third-Party Notices

This project includes third-party source code, native libraries, model files,
and Gradle dependencies. The project license covers only the project-owned code;
third-party components remain under their own licenses and terms.

## Bundled Native And Model Components

### OpenCV

- Location:
  - `app/src/main/cpp/third_party/OpenCV/`
  - `app/src/main/jniLibs/arm64-v8a/libopencv_java4.so`
- Version observed in bundled headers: 4.2.0.
- License: BSD 3-Clause style OpenCV license, as included in the bundled OpenCV
  headers.
- Upstream: https://opencv.org/

### Paddle Lite

- Location:
  - `app/src/main/cpp/third_party/PaddleLite/`
  - `app/src/main/jniLibs/arm64-v8a/libpaddle_lite_jni.so`
- License: Apache License 2.0, as indicated by bundled PaddlePaddle file
  headers.
- Upstream: https://github.com/PaddlePaddle/Paddle-Lite

### PaddleOCR

- Location:
  - `app/src/main/cpp/paddleocr/`
  - `app/src/main/assets/paddleocr/`
- Model assets:
  - `ch_ppocr_mobile_v2.0_cls_slim_opt.nb`
  - `ch_ppocr_mobile_v2.0_det_slim_opt.nb`
  - `ch_ppocr_mobile_v2.0_rec_slim_opt.nb`
  - `ppocr_keys_v1.txt`
- License: Apache License 2.0 for PaddleOCR code and model assets, as
  indicated by bundled PaddlePaddle file headers and upstream project license.
- Upstream: https://github.com/PaddlePaddle/PaddleOCR

### Clipper

- Location: `app/src/main/cpp/paddleocr/clipper.*`
- License: Boost Software License 1.0, as indicated in the bundled Clipper
  source header.
- Original author noted in source: Angus Johnson.

### Android C++ Runtime

- Location: `app/src/main/jniLibs/arm64-v8a/libc++_shared.so`
- Source: Android NDK runtime library.
- License: Android Open Source Project / LLVM runtime license terms supplied by
  the Android NDK distribution.

## Gradle Dependencies

The app resolves these dependencies from Google Maven and Maven Central:

- AndroidX Core KTX
- AndroidX AppCompat
- AndroidX Activity
- AndroidX ConstraintLayout
- Material Components for Android
- Kotlin coroutines for Android
- Google ML Kit Chinese Text Recognition
- LuaJ JSE
- JUnit, AndroidX Test, Espresso, and JSON.org test dependency

Google ML Kit is subject to Google's ML Kit terms in addition to the dependency
metadata published with the artifact:

- https://developers.google.com/ml-kit/terms

## Maintainer Notes

- Do not remove upstream copyright or license headers from vendored source.
- If bundled native libraries or model files are replaced, update this file with
  the source, version, and license used for the replacement.
- The release signing keystore and signing passwords are not third-party
  redistributable assets and must stay out of the repository.
