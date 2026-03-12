cmake_minimum_required(VERSION 3.18.1)

project("nativesrt")

# Note: This CMakeLists.txt expects libsrt to be available.
# For production use, you need to:
# 1. Build libsrt for Android (all ABIs)
# 2. Place libsrt.so files in src/main/jniLibs/<ABI>/ directories
# 3. Place SRT headers in src/main/cpp/srt-include/ directory

add_library(nativesrt SHARED NativeSrt.cpp)

# Include SRT headers
# If you have SRT headers in a different location, update this path
target_include_directories(nativesrt PRIVATE
    ${CMAKE_SOURCE_DIR}/srt-include
)

# Link with Android libraries
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(nativesrt
    ${log-lib}
    ${android-lib}
)

# Link libsrt if available
# The library should be placed in src/main/jniLibs/${ANDROID_ABI}/libsrt.so
# Link libsrt
# Sử dụng CMAKE_CURRENT_SOURCE_DIR để trỏ đường dẫn chính xác
set(SRT_LIB_PATH "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libsrt.so")

if(EXISTS ${SRT_LIB_PATH})
    add_library(libsrt_bin SHARED IMPORTED)

    set_target_properties(libsrt_bin PROPERTIES IMPORTED_LOCATION ${SRT_LIB_PATH})

    target_link_libraries(nativesrt
        libsrt_bin
        ${log-lib}
        ${android-lib}
    )
    message(STATUS "Found and linked libsrt at: ${SRT_LIB_PATH}")
else()
    message(FATAL_ERROR "NOT FOUND: libsrt.so at ${SRT_LIB_PATH}")
endif()

if(ANDROID)
    target_link_options(nativesrt PRIVATE "-Wl,-z,max-page-size=16384")
endif()
