#include <jni.h>
#include <android/log.h>
#include "kiss_fft.h"
#include "kiss_fftr.h"
#include <unordered_map>
#include <array>
#include <string_view>

template<typename T>
struct FftCfgs {
    T m_inverse_cfg, m_direct_cfg;

    FftCfgs()
    {
        m_inverse_cfg = nullptr;
        m_direct_cfg = nullptr;
    }

    FftCfgs(FftCfgs &&other)
    {
        m_inverse_cfg = other.m_inverse_cfg;
        other.m_inverse_cfg = nullptr;
        m_direct_cfg = other.m_direct_cfg;
        other.m_direct_cfg = nullptr;
    }

    T get(int is_inverse)
    {
        return is_inverse ? m_inverse_cfg : m_direct_cfg;
    }

    void set(int is_inverse, T fft_cfg) {
        if (is_inverse) {
            m_inverse_cfg = fft_cfg;
        } else {
            m_direct_cfg = fft_cfg;
        }
    }

    ~FftCfgs() {
        if (m_inverse_cfg)
            free(m_inverse_cfg);
        else if (m_direct_cfg)
            free(m_direct_cfg);
    }
};

// agalilov: added FFT_CFG and related code to avoid the recon of FFT coefficients
template<typename T>
using FFT_CFG = std::unordered_map<int, FftCfgs<T>>;

struct State {
    FFT_CFG<kiss_fft_cfg> m_kiss_fft_cfgs;
    FFT_CFG<kiss_fftr_cfg> m_kiss_fftr_cfgs;
};

extern "C" {

#define TAG "KISSFFT"

const char *STATE_FLD_NAME = "m_state";

static void
update_state_field(JNIEnv *env, jobject thisObj, const State *pState) {
    // Get a reference to this object's class
    jclass thisClass = env->GetObjectClass(thisObj);
    // Get the Field ID of the instance variables STATE_FLD_NAME
    jfieldID fidNumber = env->GetFieldID(thisClass, STATE_FLD_NAME, "J");
    if (nullptr == fidNumber) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "update_state_field: field not found");
        return;
    }
    // Change the variable
    env->SetLongField(thisObj, fidNumber, (jlong) pState);
}

static State *
get_state_field(JNIEnv *env, jobject thisObj) {
    // Get a reference to this object's class
    jclass thisClass = env->GetObjectClass(thisObj);
    // Get the Field ID of the instance variables fldName
    jfieldID fidNumber = env->GetFieldID(thisClass, STATE_FLD_NAME, "J");
    if (nullptr == fidNumber) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "get_state_field: field not found");
        return nullptr;
    }
    // Get the int given the Field ID
    return (State *) env->GetLongField(thisObj, fidNumber);
}

JNIEXPORT jdoubleArray
Java_uk_me_berndporr_kiss_1fft_KISSFastFourierTransformer_dofft(JNIEnv *env, jobject self,
                                                                jdoubleArray inArray,
                                                                jint is_inverse) {

    if (inArray == NULL) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "inArray has nullptr.");
        return NULL;
    }

    int n = env->GetArrayLength(inArray) / 2;

    if (n < 1) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "FFT array length < 1!");
        return NULL;
    }
    if (is_inverse != 0) is_inverse = 1;
    kiss_fft_cfg cfg = nullptr;
    auto pState = get_state_field(env, self);
    if (pState == nullptr) {
        pState = new State;
        update_state_field(env, self, pState);
    }

    auto it = pState->m_kiss_fft_cfgs.find(n);
    if (it != pState->m_kiss_fft_cfgs.end()) {
        cfg = it->second.get(is_inverse);
    }

    if (cfg == nullptr) {
        cfg = kiss_fft_alloc(n, is_inverse);
        pState->m_kiss_fft_cfgs[n].set(is_inverse, cfg);
    }

    double *inValues = env->GetDoubleArrayElements(inArray, 0);

    jdoubleArray outArray = env->NewDoubleArray(n * 2);
    double *outValues = env->GetDoubleArrayElements(outArray, 0);

    kiss_fft(cfg, (kiss_fft_cpx *) inValues, (kiss_fft_cpx *) outValues);

    env->ReleaseDoubleArrayElements(outArray, outValues, 0);
    env->ReleaseDoubleArrayElements(inArray, inValues, 0);

    return outArray;
}


JNIEXPORT jobjectArray
Java_uk_me_berndporr_kiss_1fft_KISSFastFourierTransformer_dofftdouble(JNIEnv *env, jobject self,
                                                                      jdoubleArray data,
                                                                      jint is_inverse) {

    jclass complex = env->FindClass("org/apache/commons/math3/complex/Complex");

    if (data == NULL) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "data has nullptr.");
        return NULL;
    }

    int n = env->GetArrayLength(data);

    if (n < 1) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "FFT array length < 1!");
        return NULL;
    }
    if (is_inverse != 0) is_inverse = 1;
    kiss_fft_cfg cfg = nullptr;
    auto pState = get_state_field(env, self);
    if (pState == nullptr) {
        pState = new State;
        update_state_field(env, self, pState);
    }
    auto it = pState->m_kiss_fft_cfgs.find(n);
    if (it != pState->m_kiss_fft_cfgs.end()) {
        cfg = it->second.get(is_inverse);
    }

    if (cfg == nullptr) {
        cfg = kiss_fft_alloc(n, is_inverse);
        pState->m_kiss_fft_cfgs[n].set(is_inverse, cfg);
    }
    kiss_fft_cpx *inArray = new kiss_fft_cpx[n];
    kiss_fft_cpx *outArray = new kiss_fft_cpx[n];

    jdouble *values = env->GetDoubleArrayElements(data, 0);

    for (int j = 0; j < n; j++) {
        inArray[j].r = values[j];
        inArray[j].i = 0;
    }

    env->ReleaseDoubleArrayElements(data, values, 0);

    kiss_fft(cfg, inArray, outArray);

    jobjectArray ret = (jobjectArray) env->NewObjectArray(n, complex, NULL);
    jmethodID complexDoubleInit = env->GetMethodID(complex, "<init>", "(DD)V");

    for (int j = 0; j < n; j++) {
        double re = outArray[j].r;
        double im = outArray[j].i;
        jobject cObj = env->NewObject(complex, complexDoubleInit, re, im);
        env->SetObjectArrayElement(ret, j, cObj);
        env->DeleteLocalRef(cObj);
    }

    delete[] inArray;
    delete[] outArray;

    return ret;
}


JNIEXPORT jobjectArray
Java_uk_me_berndporr_kiss_1fft_KISSFastFourierTransformer_dofftr(JNIEnv *env, jobject self,
                                                                 jdoubleArray data) {

    jclass complex = env->FindClass("org/apache/commons/math3/complex/Complex");

    if (data == NULL) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "data has nullptr.");
        return NULL;
    }

    int n = env->GetArrayLength(data);

    if (n < 1) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "FFT array length < 1!");
        return NULL;
    }

    const int is_inverse = 0;
    kiss_fftr_cfg cfg = nullptr;
    auto pState = get_state_field(env, self);
    if (pState == nullptr) {
        pState = new State;
        update_state_field(env, self, pState);
    }

    auto it = pState->m_kiss_fftr_cfgs.find(n);
    if (it != pState->m_kiss_fftr_cfgs.end()) {
        cfg = it->second.get(is_inverse);
    }
    if (cfg == nullptr) {
        cfg = kiss_fftr_alloc(n, is_inverse);
        pState->m_kiss_fftr_cfgs[n].set(is_inverse, cfg);
    }

    kiss_fft_cpx *outArray = new kiss_fft_cpx[n];

    double *values = env->GetDoubleArrayElements(data, 0);

    kiss_fftr(cfg, values, outArray);

    env->ReleaseDoubleArrayElements(data, values, 0);

    int complex_data_points = n / 2 + 1;

    jobjectArray ret = (jobjectArray) env->NewObjectArray(complex_data_points, complex, NULL);
    jmethodID complexDoubleInit = env->GetMethodID(complex, "<init>", "(DD)V");

    for (int j = 0; j < complex_data_points; j++) {
        double re = outArray[j].r;
        double im = outArray[j].i;
        jobject cObj = env->NewObject(complex, complexDoubleInit, re, im);
        env->SetObjectArrayElement(ret, j, cObj);
        env->DeleteLocalRef(cObj);
    }

    delete[] outArray;

    return ret;
}

JNIEXPORT jdoubleArray
Java_uk_me_berndporr_kiss_1fft_KISSFastFourierTransformer_dofftri(JNIEnv *env, jobject self,
                                                                  jobjectArray data) {

    jclass complex = env->FindClass("org/apache/commons/math3/complex/Complex");
    jmethodID getImaginary = env->GetMethodID(complex, "getImaginary", "()D");
    jmethodID getReal = env->GetMethodID(complex, "getReal", "()D");

    if (data == NULL) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "data has nullptr.");
        return NULL;
    }

    int n = env->GetArrayLength(data);

    if (n < 1) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "FFT array length < 1!");
        return NULL;
    }

    constexpr int is_inverse = 1;
    // length of real sequence
    const int real_data_points = 2 * n - 2;
    kiss_fftr_cfg cfg = nullptr;
    auto pState = get_state_field(env, self);
    if (pState == nullptr) {
        pState = new State;
        update_state_field(env, self, pState);
    }
    auto it = pState->m_kiss_fftr_cfgs.find(real_data_points);
    if (it != pState->m_kiss_fftr_cfgs.end()) {
        cfg = it->second.get(is_inverse);
    }
    if (cfg == nullptr) {
        cfg = kiss_fftr_alloc(real_data_points, is_inverse);
        pState->m_kiss_fftr_cfgs[real_data_points].set(is_inverse,cfg);
    }

    kiss_fft_cpx *inArray = new kiss_fft_cpx[n];

    for (int j = 0; j < n; j++) {
        jobject one = env->GetObjectArrayElement(data, j);
        double re = 0;
        double im = 0;
        if (!(env->IsSameObject(one, NULL))) {
            re = env->CallDoubleMethod(one, getReal);
            im = env->CallDoubleMethod(one, getImaginary);
        }
        inArray[j].r = re;
        inArray[j].i = im;
        env->DeleteLocalRef(one);
    }

    jdoubleArray outArray = env->NewDoubleArray(real_data_points);
    double *outValues = env->GetDoubleArrayElements(outArray, 0);

    // inverse transform assuming that the complex numbers are complex conjugate
    kiss_fftri(cfg, inArray, outValues);

    env->ReleaseDoubleArrayElements(outArray, outValues, 0);

    delete[] inArray;

    return outArray;
}

JNIEXPORT void
Java_uk_me_berndporr_kiss_1fft_KISSFastFourierTransformer_removeConfigs(JNIEnv *env, jobject self) {
    auto pState = get_state_field(env, self);
    update_state_field(env, self, nullptr);
    delete pState;
}
}