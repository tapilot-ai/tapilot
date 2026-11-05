cmake_minimum_required(VERSION 3.5.0)
project(probe)
add_library(probe SHARED probe.cpp)
target_link_libraries(probe PUBLIC libace_napi.z.so libohinput.so libhilog_ndk.z.so)
