cmake_minimum_required(VERSION 3.22.1)

project("vision_engine")

# Encontrar o OpenCV via Prefab (O pacote oficial expõe o componente opencv_java4)
find_package(OpenCV REQUIRED COMPONENTS opencv_java4)

# Adicionar a biblioteca nativa principal
add_library(
        native-lib
        SHARED
        native-lib.cpp)

# Encontrar as bibliotecas log e android do NDK
find_library(log-lib log)
find_library(android-lib android)

# Linkar tudo junto
target_link_libraries(
        native-lib
        OpenCV::opencv_java4
        ${log-lib}
        ${android-lib})
