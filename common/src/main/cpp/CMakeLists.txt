cmake_minimum_required(VERSION 3.22.1)

project(rootencoder_common)

add_library(
    rootencoder_common
    SHARED
    tcp_info.cpp
)
