cmake_minimum_required(VERSION 3.22)
project(bragincad C)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LIBREDWG_LIBONLY ON CACHE BOOL "" FORCE)
set(LIBREDWG_DISABLE_WRITE ON CACHE BOOL "" FORCE)
set(LIBREDWG_DISABLE_JSON ON CACHE BOOL "" FORCE)
set(DISABLE_WERROR ON CACHE BOOL "" FORCE)
set(ENABLE_LTO OFF CACHE BOOL "" FORCE)
add_subdirectory(../../../../third_party/libredwg libredwg-build EXCLUDE_FROM_ALL)

add_library(bragincad SHARED native_dwg.c)
target_include_directories(bragincad PRIVATE ../../../../third_party/libredwg/include)
target_link_libraries(bragincad PRIVATE redwg log m)
