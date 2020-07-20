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
 * limitations under the License
 */

package libcore.dalvik.system;

import junit.framework.TestCase;
import libcore.io.Streams;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import dalvik.system.DelegateLastClassLoader;
import dalvik.system.PathClassLoader;

public class DelegateLastClassLoaderTest extends TestCase {

    // NOTE: the jar files used by this test are created by create_test_jars.sh in the same
    // directory as this test.
    //
    // Contents of child.jar
    // ----------------------------------------------------
    // A.java
    // ------
    // package libcore.test.delegatelast;
    // public class A {
    //   public String toString() {
    //     return "A_child";
    //   }
    // }
    //
    // Child.java
    // ----------
    // package libcore.test.delegatelast;
    //
    // public class Child {
    //   public String toString() {
    //     return "Child_child";
    //   }
    // }
    //
    // Contents of parent.jar
    // ----------------------------------------------------
    // A.java
    // ------
    // package libcore.test.delegatelast;
    // public class A {
    //   public String toString() {
    //     return "A_parent";
    //   }
    // }
    //
    // Parent.java
    // ----------
    // package libcore.test.delegatelast;
    //
    // public class Parent {
    //   public String toString() {
    //     return "Parent_parent";
    //   }
    // }

    private Map<String, File> resourcesMap;

    @Override
    public void setUp() throws Exception {
        resourcesMap = ClassLoaderTestSupport.setupAndCopyResources(
                Arrays.asList("parent.jar", "child.jar", "bootoverride.jar"));
    }

    @Override
    public void tearDown() throws Exception {
        ClassLoaderTestSupport.cleanUpResources(resourcesMap);
    }

    private ClassLoader createClassLoader(String parentName, String thisName) {
        File parentPath = resourcesMap.get(parentName);
        File thisPath = resourcesMap.get(thisName);
        assertNotNull(parentPath);
        assertNotNull(thisPath);

        ClassLoader parent = new PathClassLoader(parentPath.getAbsolutePath(),
                Object.class.getClassLoader());

        return new DelegateLastClassLoader(thisPath.getAbsolutePath(), parent);
    }

    private static String callMethod(ClassLoader cl, String name) throws Exception {
        Class<?> clazz = cl.loadClass("libcore.test.delegatelast.A");
        assertSame(cl, clazz.getClassLoader());

        Method method = clazz.getMethod(name);
        Object obj = clazz.newInstance();
        return (String) method.invoke(obj);
    }

    private static String readResource(ClassLoader cl, String resourceName) throws Exception {
        InputStream in = cl.getResourceAsStream(resourceName);
        assertNotNull(in);

        byte[] contents = Streams.readFully(in);
        return new String(contents, StandardCharsets.UTF_8);
    }

    private static List<String> readResources(ClassLoader cl, String resourceName)
            throws Exception {
        Enumeration<URL> resources = cl.getResources(resourceName);

        List<String> contents = new ArrayList<>();
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();

            try (InputStream is = url.openStream()) {
                byte[] bytes = Streams.readFully(is);
                contents.add(new String(bytes, StandardCharsets.UTF_8));
            }
        }

        return contents;
    }

    public void testLookupOrder_loadClass() throws Exception {
        ClassLoader delegate = createClassLoader("parent.jar", "child.jar");
        assertEquals("A_child", callMethod(delegate, "toString"));

        // Note that the order is reversed here. "parent" is looked up before "child".
        delegate = createClassLoader("child.jar", "parent.jar");
        assertEquals("A_parent", callMethod(delegate, "toString"));
    }

    public void testLookupOrder_foundInParent() throws Exception {
        ClassLoader delegate = createClassLoader("parent.jar", "child.jar");

        Class<?> child = delegate.loadClass("libcore.test.delegatelast.Child");
        assertSame(delegate, child.getClassLoader());

        Class<?> parent = delegate.loadClass("libcore.test.delegatelast.Parent");
        assertSame(delegate.getParent(), parent.getClassLoader());
    }

    public void testLoadClass_exceptionMessages() throws Exception {
        ClassLoader delegate = createClassLoader("parent.jar", "child.jar");

        ClassNotFoundException msgFromDelegate = null;
        try {
            delegate.loadClass("libcore.test.delegatelast.NonExistent");
            fail();
        } catch (ClassNotFoundException ex) {
            msgFromDelegate = ex;
        }

        ClassLoader pathClassLoader = new PathClassLoader(
                resourcesMap.get("child.jar").getAbsolutePath(), null, null);

        ClassNotFoundException msgFromPathClassloader = null;
        try {
            pathClassLoader.loadClass("libcore.test.delegatelast.NonExistent");
            fail();
        } catch (ClassNotFoundException ex) {
            msgFromPathClassloader = ex;
        }

        assertEquals(msgFromPathClassloader.getMessage(), msgFromDelegate.getMessage());
    }

    public void testLookupOrder_getResource() throws Exception {
        ClassLoader delegate = createClassLoader("parent.jar", "child.jar");
        assertEquals("child", readResource(delegate, "resource.txt"));

        delegate = createClassLoader("child.jar", "parent.jar");
        assertEquals("parent", readResource(delegate, "resource.txt"));
    }

    public void testLookupOrder_getResources() throws Exception {
        ClassLoader delegate = createClassLoader("parent.jar", "child.jar");
        List<String> resources = readResources(delegate, "resource.txt");

        assertEquals(2, resources.size());
        assertEquals("child", resources.get(0));
        assertEquals("parent", resources.get(1));

        delegate = createClassLoader("child.jar", "parent.jar");
        resources = readResources(delegate, "resource.txt");

        assertEquals(2, resources.size());
        assertEquals("parent", resources.get(0));
        assertEquals("child", resources.get(1));
    }

    public void testLookupOrder_bootOverride() throws Exception {
        // The dex file in this jar contains a single class ("java.util.HashMap") and a single
        // resource ("android/icu/ICUConfig.properties"). Both of these appear in the boot
        // classpath as well.
        File override = resourcesMap.get("bootoverride.jar");
        assertNotNull(override);

        ClassLoader cl = new DelegateLastClassLoader(override.getAbsolutePath(), null);

        Class<?> clazz = cl.loadClass("java.util.HashMap");
        // This should be loaded by the boot classloader and not "cl".
        assertNotSame(cl, clazz.getClassLoader());

        String resource = readResource(cl, "android/icu/ICUConfig.properties");
        assertFalse("NOT ICU".equals(resource));

        List<String> resources = readResources(cl, "android/icu/ICUConfig.properties");
        assertEquals(2, resources.size());
        assertFalse("NOT ICU".equals(resources.get(0)));
        assertTrue("NOT ICU".equals(resources.get(1)));
    }
}
