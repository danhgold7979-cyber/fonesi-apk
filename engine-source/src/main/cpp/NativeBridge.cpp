#include "NativeHooks.h"
#include <jni.h>
#include <exception>
#include <stdexcept>
#include <memory>
#include <string>
#include <vector>

namespace {
void fail(JNIEnv* env, const char* type, const char* message) {
    if (env->ExceptionCheck()) return;
    const auto clazz = env->FindClass(type);
    if (clazz) { env->ThrowNew(clazz, message); env->DeleteLocalRef(clazz); }
}
std::string text(JNIEnv* env, jstring value) {
    if (!value) throw std::invalid_argument("Missing path");
    const auto chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) throw std::runtime_error("Cannot read path");
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
}

extern "C" JNIEXPORT void JNICALL
Java_vn_fonesi_engine_NativeRuntime_configureNative(JNIEnv* env, jclass, jstring root, jobjectArray from, jobjectArray to) {
    try {
        if (!from || !to || env->GetArrayLength(from) != env->GetArrayLength(to))
            throw std::invalid_argument("Routing arrays must match");
        std::vector<std::pair<std::string, std::string>> mappings;
        const auto size = env->GetArrayLength(from);
        mappings.reserve(size);
        for (jsize i = 0; i < size; ++i) {
            auto source = static_cast<jstring>(env->GetObjectArrayElement(from, i));
            auto target = static_cast<jstring>(env->GetObjectArrayElement(to, i));
            auto first = text(env, source); auto second = text(env, target);
            env->DeleteLocalRef(source); env->DeleteLocalRef(target);
            mappings.emplace_back(std::move(first), std::move(second));
        }
        fonesi::configureRules(std::make_shared<const fonesi::PathRules>(text(env, root), std::move(mappings)));
    } catch (const std::invalid_argument&) { fail(env, "java/lang/IllegalArgumentException", "Invalid guest routing policy"); }
    catch (const std::exception&) { fail(env, "java/lang/IllegalStateException", "Guest routing could not be configured"); }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_vn_fonesi_engine_NativeRuntime_enableNative(JNIEnv* env, jclass) {
    try {
        if (fonesi::enableHooks()) return JNI_TRUE;
        auto reason = fonesi::runtimeFailure();
        fail(env, "java/lang/IllegalStateException", reason.c_str()); return JNI_FALSE;
    }
    catch (const std::exception&) { fail(env, "java/lang/IllegalStateException", "Native hooks could not be installed"); return JNI_FALSE; }
}

extern "C" JNIEXPORT jint JNICALL
Java_vn_fonesi_engine_NativeRuntime_stateNative(JNIEnv*, jclass) {
    return static_cast<jint>(fonesi::runtimeState());
}

// Loading the library does not configure routing, patch libc, or start any guest.
extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM*, void*) { return JNI_VERSION_1_6; }
