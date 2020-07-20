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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A set of .java files (either from ojluni or from an upstream).
 */
abstract class Repository {

    protected final Path rootPath;
    protected final String name;

    protected Repository(Path rootPath, String name) {
        this.rootPath = Objects.requireNonNull(rootPath);
        this.name = Objects.requireNonNull(name);
        if (!rootPath.toFile().isDirectory()) {
            throw new IllegalArgumentException("Missing or not a directory: " + rootPath);
        }
    }

    /**
     * @param relPath a relative path of a .java file in the repository, e.g.
     *        "java/util/ArrayList.java".
     * @return the path of the indicated file (either absolute, or relative to the current
     *         working directory), or null if the file does not exist in this Repository.
     */
    public final Path absolutePath(Path relPath) {
        Path p = pathFromRepository(relPath);
        return p == null ? null : rootPath.resolve(p).toAbsolutePath();
    }

    public abstract Path pathFromRepository(Path relPath);

    /**
     * @return A human readable name to identify this repository, suitable for use as a
     *         directory name.
     */
    public final String name() {
        return name;
    }

    @Override
    public String toString() {
        return name() + " repository";
    }

    /**
     * A checkout of the hg repository of OpenJDK 9 or higher, located in the
     * subdirectory {@code upstreamName} under the directory {@code upstreamRoot}.
     */
    public static Repository openJdk9(Path upstreamRoot, String upstreamName) {
        List<String> sourceDirs = Arrays.asList(
                "jdk/src/java.base/share/classes",
                "jdk/src/java.logging/share/classes",
                "jdk/src/java.prefs/share/classes",
                "jdk/src/java.sql/share/classes",
                "jdk/src/java.desktop/share/classes",
                "jdk/src/java.base/solaris/classes",
                "jdk/src/java.base/unix/classes",
                "jdk/src/java.prefs/unix/classes",
                "jdk/src/jdk.unsupported/share/classes",
                "jdk/src/jdk.net/share/classes",
                "jdk/src/java.base/linux/classes",
                "build/linux-x86_64-normal-server-release/support/gensrc/java.base"
        );
        return new OpenJdkRepository(upstreamRoot, upstreamName, sourceDirs);
    }

    /**
     * A checkout of the hg repository of OpenJDK 8 or earlier, located in the
     * subdirectory {@code upstreamName} under the directory {@code upstreamRoot}.
     */
    public static Repository openJdkLegacy(Path upstreamRoot, String upstreamName) {
        List<String> sourceDirs = Arrays.asList(
                "jdk/src/share/classes",
                "jdk/src/solaris/classes",
                "build/linux-x86_64-normal-server-release/jdk/gensrc"
                );

        return new OpenJdkRepository(upstreamRoot, upstreamName, sourceDirs);
    }

    /**
     * Checkouts of hg repositories of OpenJDK 8 or earlier, located in the
     * respective {@code upstreamNames} subdirectories under the join parent
     * directory {@code upstreamRoot}.
     */
    public static List<Repository> openJdkLegacy(Path upstreamRoot, List<String> upstreamNames) {
        List<Repository> result = new ArrayList<>();
        for (String upstreamName : upstreamNames) {
            result.add(openJdkLegacy(upstreamRoot, upstreamName));
        }
        return Collections.unmodifiableList(result);
    }

    static class OjluniRepository extends Repository {

        /**
         * The repository of ojluni java files belonging to the Android sources under
         * {@code buildTop}.
         *
         * @param buildTop The root path of an Android checkout, as identified by the
         *        {@quote ANDROID_BUILD_TOP} environment variable.
         */
        public OjluniRepository(Path buildTop) {
            super(buildTop.resolve("libcore"), "ojluni");
        }


        @Override
        public Path pathFromRepository(Path relPath) {
            return Paths.get("ojluni/src/main/java").resolve(relPath);
        }

        /**
         * Returns the list of relative paths to .java files parsed from openjdk_java_files.mk
         */
        public List<Path> loadRelPathsFromMakefile() throws IOException {
            List<Path> result = new ArrayList<>();
            Path makefile = rootPath.resolve("openjdk_java_files.bp");
            Pattern pattern = Pattern.compile("\"ojluni/src/main/java/(.+\\.java)\"");
            for (String line : Util.readLines(makefile)) {
                Matcher matcher = pattern.matcher(line);
                while (matcher.find()) {
                    Path path = new File(matcher.group(1)).toPath();
                    result.add(path);
                }
            }
            return result;
        }

        @Override
        public String toString() {
            return "libcore ojluni";
        }
    }

    static class OpenJdkRepository extends Repository {
        private final List<String> sourceDirs;

        public OpenJdkRepository(Path upstreamRoot, String name, List<String> sourceDirs) {
            super(upstreamRoot.resolve(name), name);
            this.sourceDirs = Objects.requireNonNull(sourceDirs);
        }

        @Override
        public Path pathFromRepository(Path relPath) {
            for (String sourceDir : sourceDirs) {
                Path repositoryRelativePath = Paths.get(sourceDir).resolve(relPath);
                Path file = rootPath.resolve(repositoryRelativePath);
                if (file.toFile().exists()) {
                    return repositoryRelativePath;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return "OpenJDK " + name;
        }
    }


}
