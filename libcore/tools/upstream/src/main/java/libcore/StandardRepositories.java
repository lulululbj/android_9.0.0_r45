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

package libcore;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import libcore.Repository.OjluniRepository;

import static libcore.Repository.openJdk9;
import static libcore.Repository.openJdkLegacy;

public class StandardRepositories {

    private final List<Repository> historicUpstreams;
    private final Repository defaultUpstream;
    private final Repository jsr166Upstream;
    private final OjluniRepository ojluni;

    private StandardRepositories(Path buildTop, Path upstreamRoot) {
        this.historicUpstreams = openJdkLegacy(upstreamRoot, Arrays.asList("8u60", "7u40"));
        this.defaultUpstream = openJdkLegacy(upstreamRoot, "8u121-b13");
        this.jsr166Upstream = openJdk9(upstreamRoot, "9b113+");
        this.ojluni = new OjluniRepository(buildTop);
    }

    public List<Repository> historicUpstreams() {
        return historicUpstreams;
    }

    public OjluniRepository ojluni() {
        return ojluni;
    }

    /**
     * Returns all upstream repository snapshots, in order from latest to earliest.
     */
    public List<Repository> upstreams() {
        List<Repository> upstreams = new ArrayList<>(Arrays.asList(
                jsr166Upstream, defaultUpstream));
        upstreams.addAll(historicUpstreams);
        return Collections.unmodifiableList(upstreams);
    }

    public static StandardRepositories fromEnv() {
        Path androidBuildTop = Paths.get(getEnvOrThrow("ANDROID_BUILD_TOP"));
        Path upstreamRoot = Paths.get(getEnvOrThrow("OPENJDK_HOME"));
        return new StandardRepositories(androidBuildTop, upstreamRoot);
    }

    private static String getEnvOrThrow(String name) {
        String result = System.getenv(name);
        if (result == null) {
            throw new IllegalStateException("Environment variable undefined: " + name);
        }
        return result;
    }

    private static final Set<String> juFilesFromJsr166 = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "AbstractQueue",
                    "ArrayDeque",
                    "ArrayPrefixHelpers",
                    "Deque",
                    "Map",
                    "NavigableMap",
                    "NavigableSet",
                    "PriorityQueue",
                    "Queue",
                    "SplittableRandom"
            )));

    public Repository currentUpstream(Path relPath) {
        boolean isJsr166 = relPath.toString().startsWith("java/util/concurrent");
        String ju = "java/util/";
        String suffix = ".java";
        if (!isJsr166 && relPath.startsWith(ju)) {
            String name = relPath.toString().substring(ju.length());
            if (name.endsWith(suffix)) {
                name = name.substring(0, name.length() - suffix.length());
                isJsr166 = juFilesFromJsr166.contains(name);
            }
        }
        return isJsr166 ? jsr166Upstream : defaultUpstream;
    }

}
