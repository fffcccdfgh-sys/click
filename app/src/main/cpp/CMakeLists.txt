cmake_minimum_required(VERSION 3.22.1)

project(androidclicker_paddleocr)

set(PaddleLite_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/PaddleLite")
set(OpenCV_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/OpenCV/sdk/native")
set(OpenCV_LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

include_directories(${PaddleLite_DIR}/cxx/include)
include_directories(${OpenCV_DIR}/include)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS
    "${CMAKE_CXX_FLAGS} -ffast-math -Ofast -Os -DNDEBUG -fexceptions -fvisibility=hidden -fvisibility-inlines-hidden")
set(CMAKE_SHARED_LINKER_FLAGS
    "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -Wl,-z,nocopyreloc -Wl,--unresolved-symbols=ignore-all")

add_library(
    paddle_ocr_native
    SHARED
    paddleocr/PaddleOcrNative.cc
    paddleocr/pipeline.cc
    paddleocr/utils.cc
    paddleocr/cls_process.cc
    paddleocr/rec_process.cc
    paddleocr/det_process.cc
    paddleocr/db_post_process.cc
    paddleocr/clipper.cpp
    paddleocr/PaddleLiteSymbols.cc)

add_library(
    paddle_ocr_loader
    SHARED
    paddleocr/PaddleOcrLoader.cc)

add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(
    opencv_java4
    PROPERTIES
    IMPORTED_LOCATION
    ${OpenCV_LIB_DIR}/libopencv_java4.so)

find_library(log-lib log)

target_link_libraries(
    paddle_ocr_native
    opencv_java4
    jnigraphics
    GLESv2
    EGL
    ${log-lib})

target_link_libraries(
    paddle_ocr_loader
    ${log-lib})
