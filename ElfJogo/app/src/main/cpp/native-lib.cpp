#include <jni.h>
#include <vector>
#include <android/log.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/video/tracking.hpp>
#include <algorithm>
#include <chrono>

#define LOG_TAG "NativeVisionEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

using namespace std;
using namespace cv;

// Variaveis de estado global
Mat prevGray;
vector<Point2f> prevPts;
const int MAX_POINTS = 50;
const double OBSTACLE_THRESHOLD_FACTOR = 2.5;
const int SCALE = 2; // Downsampling 2x

// v18: Estabilizacao pos-giro
using namespace std::chrono;
steady_clock::time_point lastRotationTime = steady_clock::now();
const milliseconds ROTATION_STABILIZATION_MS(350);

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_felipe_elftemplate_movement_NativeVisionEngine_processOpticalFlow(
        JNIEnv* env,
        jobject /* this */,
        jobject yuvBuffer,
        jint width,
        jint height,
        jdouble yawChangePx) {

    uint8_t* currData = (uint8_t*) env->GetDirectBufferAddress(yuvBuffer);
    if (currData == nullptr) {
        LOGW("Buffer nulo ou inválido");
        return env->NewDoubleArray(3);
    }

    // v18: Se houve giro brusco (yawChangePx alto), resetamos o tracker e esperamos estabilizar
    if (abs(yawChangePx) > 10.0) {
        lastRotationTime = steady_clock::now();
        prevPts.clear();
    }

    auto now = steady_clock::now();
    if (now - lastRotationTime < ROTATION_STABILIZATION_MS) {
        // Reporta confiança 0 durante estabilização
        jdoubleArray ret = env->NewDoubleArray(3);
        jdouble initialData[3] = {0.0, 0.0, 0.0};
        env->SetDoubleArrayRegion(ret, 0, 3, initialData);
        return ret;
    }

    // Criar Mat OpenCV para o frame atual (Luminância apenas)
    Mat fullGray(height, width, CV_8UC1, currData);

    // Downsampling para acelerar processamento (320x240)
    Mat currGray;
    resize(fullGray, currGray, Size(width/SCALE, height/SCALE), 0, 0, INTER_NEAREST);

    // Ajustar compensação de yaw para a nova escala
    double scaledYawChange = yawChangePx / SCALE;

    if (prevGray.empty() || prevGray.cols != currGray.cols || prevGray.rows != currGray.rows) {
        currGray.copyTo(prevGray);
        prevPts.clear();
        
        jdoubleArray ret = env->NewDoubleArray(3);
        jdouble initialData[3] = {0.0, 0.0, 0.0};
        env->SetDoubleArrayRegion(ret, 0, 3, initialData);
        return ret;
    }

    // Detecção de pontos apenas na escala reduzida
    if (prevPts.size() < 20) {
        goodFeaturesToTrack(prevGray, prevPts, MAX_POINTS, 0.01, 10);
    }

    if (prevPts.empty()) {
        currGray.copyTo(prevGray);
        jdoubleArray ret = env->NewDoubleArray(3);
        jdouble initialData[3] = {0.0, 0.0, 0.0};
        env->SetDoubleArrayRegion(ret, 0, 3, initialData);
        return ret;
    }

    vector<Point2f> nextPts;
    vector<uchar> status;
    vector<float> err;

    // Lucas-Kanade na escala reduzida
    calcOpticalFlowPyrLK(prevGray, currGray, prevPts, nextPts, status, err, Size(11, 11), 2);

    vector<double> dxs, dys;
    vector<Point2f> validPrev, validNext;

    for (size_t i = 0; i < status.size(); i++) {
        if (status[i]) {
            double dx = (nextPts[i].x - prevPts[i].x) - scaledYawChange;
            double dy = nextPts[i].y - prevPts[i].y;

            // Rejeição básica de outliers (limiares escalados)
            if (abs(dx) < 50 && abs(dy) < 50) {
                dxs.push_back(dx);
                dys.push_back(dy);
                validPrev.push_back(prevPts[i]);
                validNext.push_back(nextPts[i]);
            }
        }
    }

    double dx_median = 0.0;
    double dy_median = 0.0;

    if (!dxs.empty()) {
        // Uso de nth_element para mediana (O(n) vs O(n log n))
        auto mid_idx = static_cast<long>(dxs.size() / 2);
        auto mid_it = dxs.begin() + mid_idx;
        nth_element(dxs.begin(), mid_it, dxs.end());
        dx_median = *mid_it;

        mid_idx = static_cast<long>(dys.size() / 2);
        mid_it = dys.begin() + mid_idx;
        nth_element(dys.begin(), mid_it, dys.end());
        dy_median = *mid_it;
    }

    // v18: Calcula Confiança baseada no número de pontos válidos (0.0 a 1.0)
    double confidence = (double)validNext.size() / (double)MAX_POINTS;
    if (confidence > 1.0) confidence = 1.0;

    vector<double> results;
    // Escalar de volta para a resolução original para manter compatibilidade com o Java
    results.push_back(dx_median * SCALE);
    results.push_back(dy_median * SCALE);
    results.push_back(confidence);

    // Detecção de Obstáculos
    if (abs(dy_median) > 0.1) {
        for (size_t i = 0; i < validNext.size(); i++) {
            double localDy = validNext[i].y - validPrev[i].y;
            if (abs(localDy) > abs(dy_median) * OBSTACLE_THRESHOLD_FACTOR + 0.5) {
                results.push_back(validNext[i].x * SCALE);
                results.push_back(validNext[i].y * SCALE);
            }
        }
    }

    currGray.copyTo(prevGray);
    prevPts = validNext;

    jdoubleArray ret = env->NewDoubleArray(results.size());
    env->SetDoubleArrayRegion(ret, 0, results.size(), results.data());
    return ret;
}

extern "C" JNIEXPORT void JNICALL
Java_com_felipe_elftemplate_movement_NativeVisionEngine_resetTracker(JNIEnv* env, jobject /* this */) {
    prevGray.release();
    prevPts.clear();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_felipe_elftemplate_tracking_OpenCVDepthFilter_filterDepthNative(
        JNIEnv* env,
        jclass /* clazz */,
        jobject depthDirectBuffer,
        jint width,
        jint height,
        jint filterMode) {

    if (depthDirectBuffer == nullptr || width <= 0 || height <= 0) {
        return JNI_FALSE;
    }

    uint16_t* depthPtr = (uint16_t*) env->GetDirectBufferAddress(depthDirectBuffer);
    if (depthPtr == nullptr) {
        return JNI_FALSE;
    }

    // Wrap diretamente no buffer sem alocar nova memória (Zero-Allocation)
    Mat depthMat(height, width, CV_16UC1, depthPtr);

    if (filterMode == 0) {
        // Fast 3x3 Median Blur nativo em 16-bit
        medianBlur(depthMat, depthMat, 3);
    } else if (filterMode == 1) {
        // Fechamento morfológico para supressão de buracos em bordas
        Mat kernel = getStructuringElement(MORPH_RECT, Size(3, 3));
        morphologyEx(depthMat, depthMat, MORPH_CLOSE, kernel);
    }

    return JNI_TRUE;
}

