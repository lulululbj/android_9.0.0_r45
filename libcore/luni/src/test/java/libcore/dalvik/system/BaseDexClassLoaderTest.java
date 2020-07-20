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

import dalvik.system.BaseDexClassLoader;
import dalvik.system.PathClassLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;

import libcore.io.Streams;

import junit.framework.TestCase;

public final class BaseDexClassLoaderTest extends TestCase {
    private static class Reporter implements BaseDexClassLoader.Reporter {
        public List<BaseDexClassLoader> classLoaders = new ArrayList<>();
        public List<String> loadedDexPaths = new ArrayList<>();

        @Override
        public void report(List<BaseDexClassLoader> loaders, List<String> dexPaths) {
            classLoaders.addAll(loaders);
            loadedDexPaths.addAll(dexPaths);
        }
    }

    public void testReporting() throws Exception {
        // Extract loading-test.jar from the resource.
        ClassLoader pcl = BaseDexClassLoaderTest.class.getClassLoader();
        File jar = File.createTempFile("loading-test", ".jar");
        try (InputStream in = pcl.getResourceAsStream("dalvik/system/loading-test.jar");
             FileOutputStream out = new FileOutputStream(jar)) {
          Streams.copy(in, out);
        }

        // Set the reporter.
        Reporter reporter = new Reporter();
        BaseDexClassLoader.setReporter(reporter);
        // Load the jar file using a PathClassLoader.
        BaseDexClassLoader cl1 = new PathClassLoader(jar.getPath(),
            ClassLoader.getSystemClassLoader());

        // Verify the reporter files.
        assertEquals(2, reporter.loadedDexPaths.size());
        assertEquals(2, reporter.classLoaders.size());

        // First class loader should be the one loading the files
        assertEquals(jar.getPath(), reporter.loadedDexPaths.get(0));
        assertEquals(cl1, reporter.classLoaders.get(0));
        // Second class loader should be the system class loader.
        // Don't check the actual classpath as that might vary based on system properties.
        assertEquals(ClassLoader.getSystemClassLoader(), reporter.classLoaders.get(1));

        // Reset the reporter and check we don't report anymore.
        BaseDexClassLoader.setReporter(null);

        // Load the jar file using another PathClassLoader.
        BaseDexClassLoader cl2 = new PathClassLoader(jar.getPath(), pcl);

        // Verify the list reporter files did not change.
        assertEquals(2, reporter.loadedDexPaths.size());
        assertEquals(2, reporter.classLoaders.size());

        assertEquals(jar.getPath(), reporter.loadedDexPaths.get(0));
        assertEquals(cl1, reporter.classLoaders.get(0));
        assertEquals(ClassLoader.getSystemClassLoader(), reporter.classLoaders.get(1));

        // Clean up the extracted jar file.
        assertTrue(jar.delete());
    }
}
