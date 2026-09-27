if(NOT TARGET OpenCV::opencv_java4)
add_library(OpenCV::opencv_java4 SHARED IMPORTED)
set_target_properties(OpenCV::opencv_java4 PROPERTIES
    IMPORTED_LOCATION "/home/pluginbot/.gradle/caches/transforms-3/70dcea9a455d073e76f3c0ca2a88367c/transformed/jetified-opencv-4.9.0/prefab/modules/opencv_java4/libs/android.x86/libopencv_java4.so"
    INTERFACE_INCLUDE_DIRECTORIES "/home/pluginbot/.gradle/caches/transforms-3/70dcea9a455d073e76f3c0ca2a88367c/transformed/jetified-opencv-4.9.0/prefab/modules/opencv_java4/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

