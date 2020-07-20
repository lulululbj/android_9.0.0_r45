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

package libcore.libcore.util;

import org.junit.Test;

import libcore.util.TimeZoneDataFiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TimeZoneDataFilesTest {

    @Test
    public void getTimeZoneFilePaths() {
        String[] paths = TimeZoneDataFiles.getTimeZoneFilePaths("foo");
        assertEquals(2, paths.length);

        assertTrue(paths[0].contains("/misc/zoneinfo/current/"));
        assertTrue(paths[0].endsWith("foo"));

        assertTrue(paths[1].contains("/usr/share/zoneinfo/"));
        assertTrue(paths[1].endsWith("foo"));
    }

    // http://b/34867424
    @Test
    public void generateIcuDataPath_includesTimeZoneOverride() {
        String icuDataPath = System.getProperty("android.icu.impl.ICUBinary.dataPath");
        assertEquals(icuDataPath, TimeZoneDataFiles.generateIcuDataPath());

        String[] paths = icuDataPath.split(":");
        assertEquals(2, paths.length);

        assertTrue(paths[0].contains("/misc/zoneinfo/current/icu"));
        assertTrue(paths[1].contains("/usr/icu"));
    }
}
