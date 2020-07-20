/*
 * Copyright (C) 2016 The Android Open Source Project
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

package libcore.dalvik.system;

import java.lang.reflect.Method;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import libcore.io.Streams;
import junit.framework.TestCase;

import dalvik.system.InMemoryDexClassLoader;

/**
 * Tests for the class {@link InMemoryDexClassLoader}.
 */
public class InMemoryDexClassLoaderTest extends TestCase {
    private static final String PACKAGE_PATH = "dalvik/system/";

    private File srcDir;
    private File dex1;
    private File dex2;

    protected void setUp() throws Exception {
        srcDir = File.createTempFile("src", "");
        assertTrue(srcDir.delete());
        assertTrue(srcDir.mkdirs());

        dex1 = new File(srcDir, "loading-test.dex");
        dex2 = new File(srcDir, "loading-test2.dex");

        copyResource("loading-test.dex", dex1);
        copyResource("loading-test2.dex", dex2);
    }

    protected void tearDown() {
        cleanUpDir(srcDir);
    }

    private static void cleanUpDir(File dir) {
        if (!dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                cleanUpDir(file);
            } else {
                assertTrue(file.delete());
            }
        }
        assertTrue(dir.delete());
    }

    /**
     * Copy a resource in the package directory to the indicated
     * target file.
     */
    private static void copyResource(String resourceName,
            File destination) throws IOException {
        ClassLoader loader = InMemoryDexClassLoaderTest.class.getClassLoader();
        InputStream in = loader.getResourceAsStream(PACKAGE_PATH + resourceName);
        if (in == null) {
            throw new IllegalStateException("Resource not found: " + PACKAGE_PATH + resourceName);
        }
        try (FileOutputStream out = new FileOutputStream(destination)) {
            Streams.copy(in, out);
        } finally {
            in.close();
        }
    }

    private static ByteBuffer ReadFileToByteBufferDirect(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            ByteBuffer buffer = ByteBuffer.allocateDirect((int)file.length());
            int done = 0;
            while (done != file.length()) {
                done += raf.getChannel().read(buffer);
            }
            buffer.rewind();
            return buffer;
        }
    }

    private static ByteBuffer ReadFileToByteBufferIndirect(File file) throws IOException {
        ByteBuffer direct = ReadFileToByteBufferDirect(file);
        byte[] array = new byte[direct.limit()];
        direct.get(array);
        return ByteBuffer.wrap(array);
    }

    /**
     * Helper to construct a InMemoryDexClassLoader instance to test.
     *
     * Creates InMemoryDexClassLoader from ByteBuffer instances that are
     * direct allocated.
     *
     * @param files The .dex files to use for the class path.
     */
    private static ClassLoader createLoaderDirect(File... files) throws IOException {
        assertNotNull(files);
        assertTrue(files.length > 0);
        ClassLoader result = ClassLoader.getSystemClassLoader();
        for (int i = 0; i < files.length; ++i) {
            ByteBuffer buffer = ReadFileToByteBufferDirect(files[i]);
            result = new InMemoryDexClassLoader(buffer, result);
        }
        return result;
    }

    /**
     * Helper to construct a InMemoryDexClassLoader instance to test.
     *
     * Creates InMemoryDexClassLoader from ByteBuffer instances that are
     * heap allocated.
     *
     * @param files The .dex files to use for the class path.
     */
    private static ClassLoader createLoaderIndirect(File... files) throws IOException {
        assertNotNull(files);
        assertTrue(files.length > 0);
        ClassLoader result = ClassLoader.getSystemClassLoader();
        for (int i = 0; i < files.length; ++i) {
            ByteBuffer buffer = ReadFileToByteBufferIndirect(files[i]);
            result = new InMemoryDexClassLoader(buffer, result);
        }
        return result;
    }

    /**
     * Helper to construct a new InMemoryDexClassLoader via direct
     * ByteBuffer instances.
     *
     * @param className The name of the class of the method to call.
     * @param methodName The name of the method to call.
     * @param files The .dex or .jar files to use for the class path.
     */
    private Object createLoaderDirectAndCallMethod(
            String className, String methodName, File... files)
            throws IOException, ReflectiveOperationException {
        ClassLoader cl = createLoaderDirect(files);
        Class c = cl.loadClass(className);
        Method m = c.getMethod(methodName, (Class[]) null);
        assertNotNull(m);
        return m.invoke(null, (Object[]) null);
    }

    /**
     * Helper to construct a new InMemoryDexClassLoader via indirect
     * ByteBuffer instances.
     *
     * @param className The name of the class of the method to call.
     * @param methodName The name of the method to call.
     * @param files The .dex or .jar files to use for the class path.
     */
    private Object createLoaderIndirectAndCallMethod(
            String className, String methodName, File... files)
            throws IOException, ReflectiveOperationException {
        ClassLoader cl = createLoaderIndirect(files);
        Class c = cl.loadClass(className);
        Method m = c.getMethod(methodName, (Class[]) null);
        assertNotNull(m);
        return m.invoke(null, (Object[]) null);
    }

    // ONE_DEX with direct ByteBuffer.

    public void test_oneDexDirect_simpleUse() throws Exception {
        String result = (String) createLoaderDirectAndCallMethod("test.Test1", "test", dex1);
        assertSame("blort", result);
    }

    public void test_oneDexDirect_constructor() throws Exception {
        createLoaderDirectAndCallMethod("test.TestMethods", "test_constructor", dex1);
    }

    public void test_oneDexDirect_callStaticMethod() throws Exception {
        createLoaderDirectAndCallMethod("test.TestMethods", "test_callStaticMethod", dex1);
    }

    public void test_oneDexDirect_getStaticVariable() throws Exception {
        createLoaderDirectAndCallMethod("test.TestMethods", "test_getStaticVariable", dex1);
    }

    public void test_oneDexDirect_callInstanceMethod() throws Exception {
        createLoaderDirectAndCallMethod("test.TestMethods", "test_callInstanceMethod", dex1);
    }

    public void test_oneDexDirect_getInstanceVariable() throws Exception {
        createLoaderDirectAndCallMethod("test.TestMethods", "test_getInstanceVariable", dex1);
    }

    // ONE_DEX with non-direct ByteBuffer.

    public void test_oneDexIndirect_simpleUse() throws Exception {
        String result = (String) createLoaderIndirectAndCallMethod("test.Test1", "test", dex1);
        assertSame("blort", result);
    }

    public void test_oneDexIndirect_constructor() throws Exception {
        createLoaderIndirectAndCallMethod("test.TestMethods", "test_constructor", dex1);
    }

    public void test_oneDexIndirect_callStaticMethod() throws Exception {
        createLoaderIndirectAndCallMethod("test.TestMethods", "test_callStaticMethod", dex1);
    }

    public void test_oneDexIndirect_getStaticVariable() throws Exception {
        createLoaderIndirectAndCallMethod("test.TestMethods", "test_getStaticVariable", dex1);
    }

    public void test_oneDexIndirect_callInstanceMethod() throws Exception {
        createLoaderIndirectAndCallMethod("test.TestMethods", "test_callInstanceMethod", dex1);
    }

    public void test_oneDexIndirect_getInstanceVariable() throws Exception {
        createLoaderIndirectAndCallMethod("test.TestMethods", "test_getInstanceVariable", dex1);
    }

    // TWO_DEX with direct ByteBuffer

    public void test_twoDexDirect_simpleUse() throws Exception {
        String result = (String) createLoaderDirectAndCallMethod("test.Test1", "test", dex1, dex2);
        assertSame("blort", result);
    }

    public void test_twoDexDirect_constructor() throws Exception {
        createLoaderDirectAndCallMethod("test.TestMethods", "test_constructor", dex1, dex2);
    }

    public void test_twoDexDirect_callStaticMethod() throws Exception {
        createLoaderDirectAndCallMethod("test.TestMethods", "test_callStaticMethod", dex1, dex2);
    }

    public void test_twoDexDirect_getStaticVariable() throws Exception {
        createLoaderDirectAndCallMethod("test.TestMethods", "test_getStaticVariable", dex1, dex2);
    }

    public void test_twoDexDirect_callInstanceMethod() throws Exception {
        createLoaderDirectAndCallMethod("test.TestMethods", "test_callInstanceMethod", dex1, dex2);
    }

    public void test_twoDexDirect_getInstanceVariable() throws Exception {
        createLoaderDirectAndCallMethod(
            "test.TestMethods", "test_getInstanceVariable", dex1, dex2);
    }

    public void test_twoDexDirect_target2_static_method() throws Exception {
        String result =
                (String) createLoaderDirectAndCallMethod("test2.Target2", "frotz", dex1, dex2);
        assertSame("frotz", result);
    }

    public void test_twoDexDirect_diff_constructor() throws Exception {
        // NB Ordering dex2 then dex1 as classloader's are nested and
        // each only supports a single DEX image. The
        // test.TestMethods.test_diff* methods depend on dex2 hence
        // ordering.
        createLoaderDirectAndCallMethod("test.TestMethods", "test_diff_constructor", dex2, dex1);
    }

    public void test_twoDexDirect_diff_callStaticMethod() throws Exception {
        // NB See comment in test_twoDexDirect_diff_constructor.
        createLoaderDirectAndCallMethod(
            "test.TestMethods", "test_diff_callStaticMethod", dex2, dex1);
    }

    public void test_twoDexDirect_diff_getStaticVariable() throws Exception {
        // NB See comment in test_twoDexDirect_diff_constructor.
        createLoaderDirectAndCallMethod(
            "test.TestMethods", "test_diff_getStaticVariable", dex2, dex1);
    }

    public void test_twoDexDirect_diff_callInstanceMethod() throws Exception {
        // NB See comment in test_twoDexDirect_diff_constructor.
        createLoaderDirectAndCallMethod(
            "test.TestMethods", "test_diff_callInstanceMethod", dex2, dex1);
    }

    public void test_twoDexDirect_diff_getInstanceVariable() throws Exception {
        // NB See comment in test_twoDexDirect_diff_constructor.
        createLoaderDirectAndCallMethod(
            "test.TestMethods", "test_diff_getInstanceVariable", dex2, dex1);
    }
}
