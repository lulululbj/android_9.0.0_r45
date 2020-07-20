/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <nativehelper/JniConstants.h>
#include <nativehelper/JNIHelp.h>

/** Signature for VarHandle access mode methods with a void return type. */
static const char* kVarHandleVoidSignature = "([Ljava/lang/Object;)V";

/** Signature for VarHandle access mode methods returning an object reference. */
static const char* kVarHandleBooleanSignature = "([Ljava/lang/Object;)Z";

/** Signature for VarHandle access mode methods returning a boolean value. */
static const char* kVarHandleObjectSignature = "([Ljava/lang/Object;)Ljava/lang/Object;";


static void ThrowUnsupportedOperationForAccessMode(JNIEnv* env, const char* accessMode) {
  // VarHandle access mode methods should be dispatched by the
  // interpreter or inlined into compiled code. The JNI methods below
  // are discoverable via reflection, but are not intended to be
  // invoked this way.
  jniThrowExceptionFmt(env,
                       "java/lang/UnsupportedOperationException",
                       "VarHandle.%s cannot be invoked reflectively.",
                       accessMode);
}

static void VarHandle_compareAndExchange(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "compareAndExchange");
}

static void VarHandle_compareAndExchangeAcquire(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "compareAndExchangeAcquire");
}

static void VarHandle_compareAndExchangeRelease(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "compareAndExchangeRelease");
}

static void VarHandle_compareAndSet(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "compareAndSet");
}

static void VarHandle_get(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "get");
}

static void VarHandle_getAcquire(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "getAcquire");
}

static void VarHandle_getAndAdd(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "getAndAdd");
}

static void VarHandle_getAndAddAcquire(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "getAndAddAcquire");
}

static void VarHandle_getAndAddRelease(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "getAndAddRelease");
}

static void VarHandle_getAndBitwiseAnd(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "getAndBitwiseAnd");
}

static void VarHandle_getAndBitwiseAndAcquire(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "getAndBitwiseAndAcquire");
}

static void VarHandle_getAndBitwiseAndRelease(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "getAndBitwiseAndRelease");
}

static void VarHandle_getAndBitwiseOr(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "getAndBitwiseOr");
}

static void VarHandle_getAndBitwiseOrAcquire(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "getAndBitwiseOrAcquire");
}

static void VarHandle_getAndBitwiseOrRelease(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "getAndBitwiseOrRelease");
}

static void VarHandle_getAndBitwiseXor(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "getAndBitwiseXor");
}

static void VarHandle_getAndBitwiseXorAcquire(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "getAndBitwiseXorAcquire");
}

static void VarHandle_getAndBitwiseXorRelease(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "getAndBitwiseXorRelease");
}

static void VarHandle_getAndSet(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "getAndSet");
}

static void VarHandle_getAndSetAcquire(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "getAndSetAcquire");
}

static void VarHandle_getAndSetRelease(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "getAndSetRelease");
}

static void VarHandle_getOpaque(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "getOpaque");
}

static void VarHandle_getVolatile(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "getVolatile");
}

static void VarHandle_set(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "set");
}

static void VarHandle_setOpaque(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "setOpaque");
}

static void VarHandle_setRelease(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "setRelease");
}

static void VarHandle_setVolatile(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "setVolatile");
}

static void VarHandle_weakCompareAndSet(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "weakCompareAndSet");
}

static void VarHandle_weakCompareAndSetAcquire(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "weakCompareAndSetAcquire");
}

static void VarHandle_weakCompareAndSetPlain(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "weakCompareAndSetPlain");
}

static void VarHandle_weakCompareAndSetRelease(JNIEnv* env, jobject, jobjectArray) {
  // Only reachable with reflection (see comment in ThrowUnsupportedOperationForAccessMode).
  ThrowUnsupportedOperationForAccessMode(env, "weakCompareAndSetRelease");
}

static JNINativeMethod gMethods[] = {
  NATIVE_METHOD(VarHandle, compareAndExchange, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, compareAndExchangeAcquire, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, compareAndExchangeRelease, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, compareAndSet, kVarHandleBooleanSignature),
  NATIVE_METHOD(VarHandle, get, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, getAcquire, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, getAndAdd, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, getAndAddAcquire, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, getAndAddRelease, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, getAndBitwiseAnd, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, getAndBitwiseAndAcquire, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, getAndBitwiseAndRelease, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, getAndBitwiseOr, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, getAndBitwiseOrAcquire, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, getAndBitwiseOrRelease, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, getAndBitwiseXor, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, getAndBitwiseXorAcquire, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, getAndBitwiseXorRelease, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, getAndSet, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, getAndSetAcquire, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, getAndSetRelease, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, getOpaque, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, getVolatile, kVarHandleObjectSignature),
  NATIVE_METHOD(VarHandle, set, kVarHandleVoidSignature),
  NATIVE_METHOD(VarHandle, setOpaque, kVarHandleVoidSignature),
  NATIVE_METHOD(VarHandle, setRelease, kVarHandleVoidSignature),
  NATIVE_METHOD(VarHandle, setVolatile, kVarHandleVoidSignature),
  NATIVE_METHOD(VarHandle, weakCompareAndSet, kVarHandleBooleanSignature),
  NATIVE_METHOD(VarHandle, weakCompareAndSetAcquire, kVarHandleBooleanSignature),
  NATIVE_METHOD(VarHandle, weakCompareAndSetPlain, kVarHandleBooleanSignature),
  NATIVE_METHOD(VarHandle, weakCompareAndSetRelease, kVarHandleBooleanSignature),
};

void register_java_lang_invoke_VarHandle(JNIEnv* env) {
    jniRegisterNativeMethods(env, "java/lang/invoke/VarHandle", gMethods, NELEM(gMethods));
}
