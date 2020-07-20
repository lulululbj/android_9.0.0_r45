/*
 * Copyright (C) 2007 The Android Open Source Project
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

package dalvik.system;

import dalvik.annotation.optimization.FastNative;
import java.lang.ref.FinalizerReference;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides an interface to VM-global, Dalvik-specific features.
 * An application cannot create its own Runtime instance, and must obtain
 * one from the getRuntime method.
 *
 * @hide
 */
public final class VMRuntime {

    /**
     * Holds the VMRuntime singleton.
     */
    private static final VMRuntime THE_ONE = new VMRuntime();

    // Note: Instruction set names are used to construct the names of some
    // system properties. To be sure that the properties stay valid the
    // instruction set name should not exceed 7 characters. See installd
    // and the package manager for the actual propeties.
    private static final Map<String, String> ABI_TO_INSTRUCTION_SET_MAP
            = new HashMap<String, String>(16);
    static {
        ABI_TO_INSTRUCTION_SET_MAP.put("armeabi", "arm");
        ABI_TO_INSTRUCTION_SET_MAP.put("armeabi-v7a", "arm");
        ABI_TO_INSTRUCTION_SET_MAP.put("mips", "mips");
        ABI_TO_INSTRUCTION_SET_MAP.put("mips64", "mips64");
        ABI_TO_INSTRUCTION_SET_MAP.put("x86", "x86");
        ABI_TO_INSTRUCTION_SET_MAP.put("x86_64", "x86_64");
        ABI_TO_INSTRUCTION_SET_MAP.put("arm64-v8a", "arm64");
    }

    /**
     * Magic version number for a current development build, which has not
     * yet turned into an official release. This number must be larger than
     * any released version in {@code android.os.Build.VERSION_CODES}.
     * @hide
     */
    public static final int SDK_VERSION_CUR_DEVELOPMENT = 10000;

    private static Consumer<String> nonSdkApiUsageConsumer = null;

    private int targetSdkVersion = SDK_VERSION_CUR_DEVELOPMENT;

    /**
     * Prevents this class from being instantiated.
     */
    private VMRuntime() {
    }

    /**
     * Returns the object that represents the VM instance's Dalvik-specific
     * runtime environment.
     *
     * @return the runtime object
     */
    public static VMRuntime getRuntime() {
        return THE_ONE;
    }

    /**
     * Returns a copy of the VM's command-line property settings.
     * These are in the form "name=value" rather than "-Dname=value".
     */
    public native String[] properties();

    /**
     * Returns the VM's boot class path.
     */
    public native String bootClassPath();

    /**
     * Returns the VM's class path.
     */
    public native String classPath();

    /**
     * Returns the VM's version.
     */
    public native String vmVersion();

    /**
     * Returns the name of the shared library providing the VM implementation.
     */
    public native String vmLibrary();

    /**
     * Returns the VM's instruction set.
     */
    public native String vmInstructionSet();

    /**
     * Returns whether the VM is running in 64-bit mode.
     */
    @FastNative
    public native boolean is64Bit();

    /**
     * Returns whether the VM is running with JNI checking enabled.
     */
    @FastNative
    public native boolean isCheckJniEnabled();

    /**
     * Gets the current ideal heap utilization, represented as a number
     * between zero and one.  After a GC happens, the Dalvik heap may
     * be resized so that (size of live objects) / (size of heap) is
     * equal to this number.
     *
     * @return the current ideal heap utilization
     */
    public native float getTargetHeapUtilization();

    /**
     * Sets the current ideal heap utilization, represented as a number
     * between zero and one.  After a GC happens, the Dalvik heap may
     * be resized so that (size of live objects) / (size of heap) is
     * equal to this number.
     *
     * <p>This is only a hint to the garbage collector and may be ignored.
     *
     * @param newTarget the new suggested ideal heap utilization.
     *                  This value may be adjusted internally.
     * @return the previous ideal heap utilization
     * @throws IllegalArgumentException if newTarget is &lt;= 0.0 or &gt;= 1.0
     */
    public float setTargetHeapUtilization(float newTarget) {
        if (newTarget <= 0.0f || newTarget >= 1.0f) {
            throw new IllegalArgumentException(newTarget +
                    " out of range (0,1)");
        }
        /* Synchronize to make sure that only one thread gets
         * a given "old" value if both update at the same time.
         * Allows for reliable save-and-restore semantics.
         */
        synchronized (this) {
            float oldTarget = getTargetHeapUtilization();
            nativeSetTargetHeapUtilization(newTarget);
            return oldTarget;
        }
    }

    /**
     * Sets the target SDK version. Should only be called before the
     * app starts to run, because it may change the VM's behavior in
     * dangerous ways. Defaults to {@link #SDK_VERSION_CUR_DEVELOPMENT}.
     */
    public synchronized void setTargetSdkVersion(int targetSdkVersion) {
        this.targetSdkVersion = targetSdkVersion;
        setTargetSdkVersionNative(this.targetSdkVersion);
    }

    /**
     * Gets the target SDK version. See {@link #setTargetSdkVersion} for
     * special values.
     */
    public synchronized int getTargetSdkVersion() {
        return targetSdkVersion;
    }

    private native void setTargetSdkVersionNative(int targetSdkVersion);

    /**
     * This method exists for binary compatibility.  It was part of a
     * heap sizing API which was removed in Android 3.0 (Honeycomb).
     */
    @Deprecated
    public long getMinimumHeapSize() {
        return 0;
    }

    /**
     * This method exists for binary compatibility.  It was part of a
     * heap sizing API which was removed in Android 3.0 (Honeycomb).
     */
    @Deprecated
    public long setMinimumHeapSize(long size) {
        return 0;
    }

    /**
     * This method exists for binary compatibility.  It used to
     * perform a garbage collection that cleared SoftReferences.
     */
    @Deprecated
    public void gcSoftReferences() {}

    /**
     * This method exists for binary compatibility.  It is equivalent
     * to {@link System#runFinalization}.
     */
    @Deprecated
    public void runFinalizationSync() {
        System.runFinalization();
    }

    /**
     * Implements setTargetHeapUtilization().
     *
     * @param newTarget the new suggested ideal heap utilization.
     *                  This value may be adjusted internally.
     */
    private native void nativeSetTargetHeapUtilization(float newTarget);

    /**
     * This method exists for binary compatibility.  It was part of
     * the external allocation API which was removed in Android 3.0 (Honeycomb).
     */
    @Deprecated
    public boolean trackExternalAllocation(long size) {
        return true;
    }

    /**
     * This method exists for binary compatibility.  It was part of
     * the external allocation API which was removed in Android 3.0 (Honeycomb).
     */
    @Deprecated
    public void trackExternalFree(long size) {}

    /**
     * This method exists for binary compatibility.  It was part of
     * the external allocation API which was removed in Android 3.0 (Honeycomb).
     */
    @Deprecated
    public long getExternalBytesAllocated() {
        return 0;
    }

    /**
     * Tells the VM to enable the JIT compiler. If the VM does not have a JIT
     * implementation, calling this method should have no effect.
     */
    public native void startJitCompilation();

    /**
     * Tells the VM to disable the JIT compiler. If the VM does not have a JIT
     * implementation, calling this method should have no effect.
     */
    public native void disableJitCompilation();

    /**
     * Returns true if the app has accessed a hidden API. This does not include
     * attempts which have been blocked.
     */
    public native boolean hasUsedHiddenApi();

    /**
     * Sets the list of exemptions from hidden API access enforcement.
     *
     * @param signaturePrefixes
     *         A list of signature prefixes. Each item in the list is a prefix match on the type
     *         signature of a blacklisted API. All matching APIs are treated as if they were on
     *         the whitelist: access permitted, and no logging..
     */
    public native void setHiddenApiExemptions(String[] signaturePrefixes);

    /**
     * Sets the log sampling rate of hidden API accesses written to the event log.
     *
     * @param rate Proportion of hidden API accesses that will be logged; an integer between
     *                0 and 0x10000 inclusive.
     */
    public native void setHiddenApiAccessLogSamplingRate(int rate);

    /**
     * Returns an array allocated in an area of the Java heap where it will never be moved.
     * This is used to implement native allocations on the Java heap, such as DirectByteBuffers
     * and Bitmaps.
     */
    @FastNative
    public native Object newNonMovableArray(Class<?> componentType, int length);

    /**
     * Returns an array of at least minLength, but potentially larger. The increased size comes from
     * avoiding any padding after the array. The amount of padding varies depending on the
     * componentType and the memory allocator implementation.
     */
    @FastNative
    public native Object newUnpaddedArray(Class<?> componentType, int minLength);

    /**
     * Returns the address of array[0]. This differs from using JNI in that JNI might lie and
     * give you the address of a copy of the array when in forcecopy mode.
     */
    @FastNative
    public native long addressOf(Object array);

    /**
     * Removes any growth limits, allowing the application to allocate
     * up to the maximum heap size.
     */
    public native void clearGrowthLimit();

    /**
     * Make the current growth limit the new non growth limit capacity by releasing pages which
     * are after the growth limit but before the non growth limit capacity.
     */
    public native void clampGrowthLimit();

    /**
     * Returns true if either a Java debugger or native debugger is active.
     */
    @FastNative
    public native boolean isDebuggerActive();

    /**
     * Returns true if native debugging is on.
     */
    @FastNative
    public native boolean isNativeDebuggable();

    /**
     * Returns true if Java debugging is enabled.
     */
    public native boolean isJavaDebuggable();

    /**
     * Registers a native allocation so that the heap knows about it and performs GC as required.
     * If the number of native allocated bytes exceeds the native allocation watermark, the
     * function requests a concurrent GC. If the native bytes allocated exceeds a second higher
     * watermark, it is determined that the application is registering native allocations at an
     * unusually high rate and a GC is performed inside of the function to prevent memory usage
     * from excessively increasing.
     */
    public native void registerNativeAllocation(int bytes);

    /**
     * Registers a native free by reducing the number of native bytes accounted for.
     */
    public native void registerNativeFree(int bytes);

    /**
     * Wait for objects to be finalized.
     *
     * If finalization takes longer than timeout, then the function returns before all objects are
     * finalized.
     *
     * @param timeout
     *            timeout in nanoseconds of the maximum time to wait until all pending finalizers
     *            are run. If timeout is 0, then there is no timeout. Note that the timeout does
     *            not stop the finalization process, it merely stops the wait.
     *
     * @see #Runtime.runFinalization()
     * @see #wait(long,int)
     */
    public static void runFinalization(long timeout) {
        try {
            FinalizerReference.finalizeAllEnqueued(timeout);
        } catch (InterruptedException e) {
            // Interrupt the current thread without actually throwing the InterruptionException
            // for the caller.
            Thread.currentThread().interrupt();
        }
    }

    public native void requestConcurrentGC();
    public native void concurrentGC();
    public native void requestHeapTrim();
    public native void trimHeap();
    public native void startHeapTaskProcessor();
    public native void stopHeapTaskProcessor();
    public native void runHeapTasks();

    /**
     * Let the heap know of the new process state. This can change allocation and garbage collection
     * behavior regarding trimming and compaction.
     */
    public native void updateProcessState(int state);

    /**
     * Fill in dex caches with classes, fields, and methods that are
     * already loaded. Typically used after Zygote preloading.
     */
    public native void preloadDexCaches();

    /**
     * Register application info.
     * @param profileFile the path of the file where the profile information should be stored.
     * @param codePaths the code paths that should be profiled.
     */
    public static native void registerAppInfo(String profileFile, String[] codePaths);

    /**
     * Returns the runtime instruction set corresponding to a given ABI. Multiple
     * compatible ABIs might map to the same instruction set. For example
     * {@code armeabi-v7a} and {@code armeabi} might map to the instruction set {@code arm}.
     *
     * This influences the compilation of the applications classes.
     */
    public static String getInstructionSet(String abi) {
        final String instructionSet = ABI_TO_INSTRUCTION_SET_MAP.get(abi);
        if (instructionSet == null) {
            throw new IllegalArgumentException("Unsupported ABI: " + abi);
        }

        return instructionSet;
    }

    public static boolean is64BitInstructionSet(String instructionSet) {
        return "arm64".equals(instructionSet) ||
                "x86_64".equals(instructionSet) ||
                "mips64".equals(instructionSet);
    }

    public static boolean is64BitAbi(String abi) {
        return is64BitInstructionSet(getInstructionSet(abi));
    }

    /**
     * Return false if the boot class path for the given instruction
     * set mapped from disk storage, versus being interpretted from
     * dirty pages in memory.
     */
    public static native boolean isBootClassPathOnDisk(String instructionSet);

    /**
     * Returns the instruction set of the current runtime.
     */
    public static native String getCurrentInstructionSet();

    /**
     * Return true if the dalvik cache was pruned when booting. This may have happened for
     * various reasons, e.g., after an OTA. The return value is for the current instruction
     * set.
     */
    public static native boolean didPruneDalvikCache();

    /**
     * Register the current execution thread to the runtime as sensitive thread.
     * Should be called just once. Subsequent calls are ignored.
     */
    public static native void registerSensitiveThread();

    /**
     * Sets up the priority of the system daemon thread (caller).
     */
    public static native void setSystemDaemonThreadPriority();

    /**
     * Sets a callback that the runtime can call whenever a usage of a non SDK API is detected.
     */
    public static void setNonSdkApiUsageConsumer(Consumer<String> consumer) {
        nonSdkApiUsageConsumer = consumer;
    }

    /**
     * Sets whether or not the runtime should dedupe detection and warnings for hidden API usage.
     * If deduping is enabled, only the first usage of each API will be detected. The default
     * behaviour is to dedupe.
     */
    public static native void setDedupeHiddenApiWarnings(boolean dedupe);

    /**
     * Sets the package name of the app running in this process.
     */
    public static native void setProcessPackageName(String packageName);
}
