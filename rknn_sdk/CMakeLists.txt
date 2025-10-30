cmake_minimum_required(VERSION 3.4.1)

# Add your native library
add_library(rknn4j SHARED
        src/main/cpp/native-lib.cc
        src/main/cpp/post_process.cc
        src/main/cpp/yolo_image.cc
        src/main/cpp/rknn_api.h)

# Find the Android log library
find_library(log-lib log)

# Add imported shared library for librga.so
add_library(rga SHARED IMPORTED)
set_target_properties(rga PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/librga.so)

# Add imported shared library for librknnrt.so
add_library(rknnrt SHARED IMPORTED)
set_target_properties(rknnrt PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/librknnrt.so)

# Link libraries
target_link_libraries(rknn4j
        rknnrt
        rga
        ${log-lib})
