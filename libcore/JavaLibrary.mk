# -*- mode: makefile -*-
# Copyright (C) 2007 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# Definitions for building the Java library and associated tests.
#

#
# Common definitions for host and target.
#

# libcore is divided into modules.
#
# The structure of each module is:
#
#   src/
#       main/               # To be shipped on every device.
#            java/          # Java source for library code.
#            native/        # C++ source for library code.
#            resources/     # Support files.
#       test/               # Built only on demand, for testing.
#            java/          # Java source for tests.
#            native/        # C++ source for tests (rare).
#            resources/     # Support files.
#
# All subdirectories are optional (hence the "2> /dev/null"s below).

define all-test-java-files-under
$(foreach dir,$(1),$(patsubst ./%,%,$(shell cd $(LOCAL_PATH) && (find $(dir)/src/test/java -name "*.java" 2> /dev/null) | grep -v -f java_tests_blacklist)))
endef

define all-core-resource-dirs
$(shell cd $(LOCAL_PATH) && ls -d */src/$(1)/{java,resources} 2> /dev/null)
endef

# The Java files and their associated resources.
core_resource_dirs := \
  luni/src/main/java \
  ojluni/src/main/resources/
test_resource_dirs := $(filter-out ojluni/%,$(call all-core-resource-dirs,test))
test_src_files := $(call all-test-java-files-under,dalvik dalvik/test-rules dom harmony-tests json luni xml)
ojtest_src_files := $(call all-test-java-files-under,ojluni)
ojtest_resource_dirs := $(filter ojluni/%,$(call all-core-resource-dirs,test))

ifeq ($(EMMA_INSTRUMENT),true)
ifneq ($(EMMA_INSTRUMENT_STATIC),true)
    nojcore_src_files += $(call all-java-files-under, ../external/emma/core ../external/emma/pregenerated)
    core_resource_dirs += ../external/emma/core/res ../external/emma/pregenerated/res
endif
endif

local_javac_flags=-encoding UTF-8
#local_javac_flags+=-Xlint:all -Xlint:-serial,-deprecation,-unchecked
local_javac_flags+=-Xmaxwarns 9999999

# For user / userdebug builds, strip the local variable table and the local variable
# type table. This has no bearing on stack traces, but will leave less information
# available via JDWP.
#
# TODO: Should this be conditioned on a PRODUCT_ flag or should we just turn this
# on for all builds. Also, name of the flag TBD.
ifneq (,$(PRODUCT_MINIMIZE_JAVA_DEBUG_INFO))
ifneq (,$(filter userdebug user,$(TARGET_BUILD_VARIANT)))
local_javac_flags+= -g:source,lines
local_jack_flags+= -D jack.dex.debug.vars=false -D jack.dex.debug.vars.synthetic=false
endif
endif

#
# ICU4J related rules.
#
# We compile android_icu4j along with core-libart because we're implementing parts of core-libart
# in terms of android_icu4j.
android_icu4j_root := ../external/icu/android_icu4j/
android_icu4j_src_files := $(call all-java-files-under,$(android_icu4j_root)/src/main/java)
android_icu4j_resource_dirs := $(android_icu4j_root)/resources

#
# Build jaif-annotated source files for ojluni target .
#
ojluni_annotate_dir := $(call intermediates-dir-for,JAVA_LIBRARIES,core-oj,,COMMON)/annotated
ojluni_annotate_target := $(ojluni_annotate_dir)/timestamp
ojluni_annotate_jaif := $(LOCAL_PATH)/annotations/ojluni.jaif
ojluni_annotate_input := $(annotated_ojluni_files)
ojluni_annotate_output := $(patsubst $(LOCAL_PATH)/ojluni/src/main/java/%, $(ojluni_annotate_dir)/%, $(ojluni_annotate_input))

$(ojluni_annotate_target): PRIVATE_ANNOTATE_TARGET := $(ojluni_annotate_target)
$(ojluni_annotate_target): PRIVATE_ANNOTATE_DIR := $(ojluni_annotate_dir)
$(ojluni_annotate_target): PRIVATE_ANNOTATE_JAIF := $(ojluni_annotate_jaif)
$(ojluni_annotate_target): PRIVATE_ANNOTATE_INPUT := $(ojluni_annotate_input)
$(ojluni_annotate_target): PRIVATE_ANNOTATE_GENERATE_CMD := $(LOCAL_PATH)/annotations/generate_annotated_java_files.py
$(ojluni_annotate_target): PRIVATE_ANNOTATE_GENERATE_OUTPUT := $(LOCAL_PATH)/annotated_java_files.bp
$(ojluni_annotate_target): PRIVATE_INSERT_ANNOTATIONS_TO_SOURCE := external/annotation-tools/annotation-file-utilities/scripts/insert-annotations-to-source

# Diff output of _ojluni_annotate_generate_cmd with what we have, and if generate annotated source.
$(ojluni_annotate_target):  $(ojluni_annotate_input) $(ojluni_annotate_jaif)
	rm -rf $(PRIVATE_ANNOTATE_DIR)
	mkdir -p $(PRIVATE_ANNOTATE_DIR)
	$(PRIVATE_ANNOTATE_GENERATE_CMD) $(PRIVATE_ANNOTATE_JAIF) > $(PRIVATE_ANNOTATE_DIR)/annotated_java_files.bp.tmp
	diff -u $(PRIVATE_ANNOTATE_GENERATE_OUTPUT) $(PRIVATE_ANNOTATE_DIR)/annotated_java_files.bp.tmp || \
	(echo -e "********************" >&2; \
	 echo -e "annotated_java_files.bp needs regenerating. Please run:" >&2; \
	 echo -e "libcore/annotations/generate_annotated_java_files.py libcore/annotations/ojluni.jaif > libcore/annotated_java_files.bp" >&2; \
	 echo -e "********************" >&2; exit 1)
	rm $(PRIVATE_ANNOTATE_DIR)/annotated_java_files.bp.tmp
	$(PRIVATE_INSERT_ANNOTATIONS_TO_SOURCE) -d $(PRIVATE_ANNOTATE_DIR) $(PRIVATE_ANNOTATE_JAIF) $(PRIVATE_ANNOTATE_INPUT)
	touch $@
$(ojluni_annotate_target): .KATI_IMPLICIT_OUTPUTS := $(ojluni_annotate_output)

ojluni_annotate_dir:=
ojluni_annotate_target:=
ojluni_annotate_jaif:=
ojluni_annotate_input:=
ojluni_annotate_output:=

#
# Build for the target (device).
#
ifeq ($(LIBCORE_SKIP_TESTS),)
# Build a library just containing files from luni/src/test/filesystems for use in tests.
include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(call all-java-files-under, luni/src/test/filesystems/src)
LOCAL_JAVA_RESOURCE_DIRS := luni/src/test/filesystems/resources
LOCAL_NO_STANDARD_LIBRARIES := true
LOCAL_MODULE := filesystemstest
LOCAL_JAVA_LIBRARIES := core-oj core-libart
LOCAL_DEX_PREOPT := false
LOCAL_ERROR_PRONE_FLAGS := -Xep:MissingOverride:OFF
include $(BUILD_JAVA_LIBRARY)

filesystemstest_jar := $(intermediates)/$(LOCAL_MODULE).jar
$(filesystemstest_jar): $(LOCAL_BUILT_MODULE)
	$(call copy-file-to-target)

# Build a library just containing files from luni/src/test/parameter_metadata for use in tests.
include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(call all-java-files-under, luni/src/test/parameter_metadata/src)
LOCAL_NO_STANDARD_LIBRARIES := true
LOCAL_MODULE := parameter-metadata-test
LOCAL_JAVA_LIBRARIES := core-oj core-libart
LOCAL_DEX_PREOPT := false
LOCAL_JAVACFLAGS := -parameters
LOCAL_ERROR_PRONE_FLAGS := -Xep:MissingOverride:OFF
include $(BUILD_JAVA_LIBRARY)

parameter_metadata_test_jar := $(intermediates)/$(LOCAL_MODULE).jar
$(parameter_metadata_test_jar): $(LOCAL_BUILT_MODULE)
	$(call copy-file-to-target)

endif

ifeq ($(LIBCORE_SKIP_TESTS),)
# Make the core-tests library.
include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(test_src_files)
LOCAL_JAVA_RESOURCE_DIRS := $(test_resource_dirs)
# Include individual dex.jar files (jars containing resources and a classes.dex) so that they
# be loaded by tests using ClassLoaders but are not in the main classes.dex.
LOCAL_JAVA_RESOURCE_FILES := $(filesystemstest_jar) $(parameter_metadata_test_jar)
LOCAL_NO_STANDARD_LIBRARIES := true
LOCAL_JAVA_LIBRARIES := core-oj core-libart okhttp bouncycastle
LOCAL_STATIC_JAVA_LIBRARIES := \
	archive-patcher \
	core-test-rules \
	core-tests-support \
	junit-params \
	mockftpserver \
	mockito-target \
	mockwebserver \
	nist-pkix-tests \
	slf4j-jdk14 \
	sqlite-jdbc \
	tzdata-testing
LOCAL_JAVACFLAGS := $(local_javac_flags)
LOCAL_JACK_FLAGS := $(local_jack_flags)
LOCAL_ERROR_PRONE_FLAGS := \
        -Xep:TryFailThrowable:ERROR \
        -Xep:ComparisonOutOfRange:ERROR \
        -Xep:MissingOverride:OFF
LOCAL_MODULE := core-tests
include $(BUILD_STATIC_JAVA_LIBRARY)
endif

# Make the core-ojtests library.
ifeq ($(LIBCORE_SKIP_TESTS),)
    include $(CLEAR_VARS)
    LOCAL_JAVA_RESOURCE_DIRS := $(ojtest_resource_dirs)
    LOCAL_NO_STANDARD_LIBRARIES := true
    LOCAL_JAVA_LIBRARIES := core-oj core-libart okhttp bouncycastle
    LOCAL_STATIC_JAVA_LIBRARIES := testng
    LOCAL_JAVACFLAGS := $(local_javac_flags)
    LOCAL_JACK_FLAGS := $(local_jack_flags)
    LOCAL_DX_FLAGS := --core-library
    LOCAL_MODULE_TAGS := optional
    LOCAL_MODULE := core-ojtests
    # jack bug workaround: int[] java.util.stream.StatefulTestOp.-getjava-util-stream-StreamShapeSwitchesValues() is a private synthetic method in an interface which causes a hard verifier error
    LOCAL_DEX_PREOPT := false # disable AOT preverification which breaks the build. it will still throw VerifyError at runtime.
    LOCAL_ERROR_PRONE_FLAGS := -Xep:MissingOverride:OFF
    include $(BUILD_JAVA_LIBRARY)
endif

# Make the core-ojtests-public library. Excludes any private API tests.
ifeq ($(LIBCORE_SKIP_TESTS),)
    include $(CLEAR_VARS)
    # Filter out the following:
    # 1.) DeserializeMethodTest and SerializedLambdaTest, because they depends on stub classes
    #     and won't actually run, and
    # 2.) util/stream/boot*. Those directories contain classes in the package java.util.stream;
    #     excluding them means we don't need LOCAL_PATCH_MODULE := java.base
    LOCAL_SRC_FILES := $(filter-out %/DeserializeMethodTest.java %/SerializedLambdaTest.java ojluni/src/test/java/util/stream/boot%,$(ojtest_src_files))
    # Include source code as part of JAR
    LOCAL_JAVA_RESOURCE_DIRS := ojluni/src/test/dist $(ojtest_resource_dirs)
    LOCAL_NO_STANDARD_LIBRARIES := true
    LOCAL_JAVA_LIBRARIES := \
        bouncycastle \
        core-libart \
        core-oj \
        okhttp \
        testng
    LOCAL_JAVACFLAGS := $(local_javac_flags)
    LOCAL_JACK_FLAGS := $(local_jack_flags)
    LOCAL_DX_FLAGS := --core-library
    LOCAL_MODULE_TAGS := optional
    LOCAL_MODULE := core-ojtests-public
    # jack bug workaround: int[] java.util.stream.StatefulTestOp.-getjava-util-stream-StreamShapeSwitchesValues() is a private synthetic method in an interface which causes a hard verifier error
    LOCAL_DEX_PREOPT := false # disable AOT preverification which breaks the build. it will still throw VerifyError at runtime.
    LOCAL_ERROR_PRONE_FLAGS := -Xep:MissingOverride:OFF
    include $(BUILD_JAVA_LIBRARY)
endif

#
# Build for the host.
#

ifeq ($(HOST_OS),linux)

# Make the core-tests-hostdex library.
ifeq ($(LIBCORE_SKIP_TESTS),)
    include $(CLEAR_VARS)
    LOCAL_SRC_FILES := $(test_src_files)
    LOCAL_JAVA_RESOURCE_DIRS := $(test_resource_dirs)
    LOCAL_NO_STANDARD_LIBRARIES := true
    LOCAL_JAVA_LIBRARIES := \
        bouncycastle-hostdex \
        core-libart-hostdex \
        core-oj-hostdex \
        core-tests-support-hostdex \
        junit-hostdex \
        mockito-api-hostdex \
        okhttp-hostdex
    LOCAL_STATIC_JAVA_LIBRARIES := \
        archive-patcher-hostdex \
        core-test-rules-hostdex \
        junit-params-hostdex \
        mockftpserver-hostdex \
        mockwebserver-host \
        nist-pkix-tests-host \
        slf4j-jdk14-hostdex \
        sqlite-jdbc-host \
        tzdata-testing-hostdex
    LOCAL_JAVACFLAGS := $(local_javac_flags)
    LOCAL_MODULE_TAGS := optional
    LOCAL_MODULE := core-tests-hostdex
    LOCAL_ERROR_PRONE_FLAGS := -Xep:MissingOverride:OFF
    include $(BUILD_HOST_DALVIK_JAVA_LIBRARY)
endif

# Make the core-ojtests-hostdex library.
ifeq ($(LIBCORE_SKIP_TESTS),)
    include $(CLEAR_VARS)
    LOCAL_SRC_FILES := $(ojtest_src_files)
    LOCAL_NO_STANDARD_LIBRARIES := true
    LOCAL_JAVA_LIBRARIES := \
        bouncycastle-hostdex \
        core-libart-hostdex \
        core-oj-hostdex \
        okhttp-hostdex
    LOCAL_STATIC_JAVA_LIBRARIES := testng-hostdex
    LOCAL_JAVACFLAGS := $(local_javac_flags)
    LOCAL_DX_FLAGS := --core-library
    LOCAL_MODULE_TAGS := optional
    LOCAL_MODULE := core-ojtests-hostdex
    # ojluni/src/test/java/util/stream/{bootlib,boottest}
    # contains tests that are in packages from java.base;
    # By default, OpenJDK 9's javac will only compile such
    # code if it's declared to also be in java.base at
    # compile time.
    #
    # For now, we use --patch-module to put all sources
    # and dependencies from this make target into java.base;
    # other source directories in this make target are in
    # packages not from java.base; if this becomes a proble
    # in future, this could be addressed eg. by splitting
    # boot{lib,test} out into a separate make target,
    # deleting those tests or moving them to a different
    # package.
    LOCAL_PATCH_MODULE := java.base
    LOCAL_ERROR_PRONE_FLAGS := -Xep:MissingOverride:OFF
    include $(BUILD_HOST_DALVIK_JAVA_LIBRARY)
endif

endif # HOST_OS == linux

#
# Local droiddoc for faster libcore testing
#
#
# Run with:
#     mm -j32 libcore-docs
#
# Main output:
#     ../out/target/common/docs/libcore/reference/packages.html
#
# All text for proofreading (or running tools over):
#     ../out/target/common/docs/libcore-proofread.txt
#
# TODO list of missing javadoc, etc:
#     ../out/target/common/docs/libcore-docs-todo.html
#
# Rerun:
#     rm -rf ../out/target/common/docs/libcore-timestamp && mm -j32 libcore-docs
#
include $(CLEAR_VARS)

# for shared defintion of libcore_to_document
include $(LOCAL_PATH)/Docs.mk

# The libcore_to_document paths are relative to $(TOPDIR). We are in libcore so we must prepend
# ../ to make LOCAL_SRC_FILES relative to $(LOCAL_PATH).
LOCAL_SRC_FILES := $(addprefix ../, $(libcore_to_document))
LOCAL_INTERMEDIATE_SOURCES := \
    $(patsubst $(TARGET_OUT_COMMON_INTERMEDIATES)/%,%,$(libcore_to_document_generated))
LOCAL_ADDITIONAL_DEPENDENCIES := $(libcore_to_document_generated)
# rerun doc generation without recompiling the java
LOCAL_JAVACFLAGS := $(local_javac_flags)
LOCAL_MODULE_CLASS:=JAVA_LIBRARIES

LOCAL_MODULE := libcore

LOCAL_DROIDDOC_OPTIONS := \
 -offlinemode \
 -title "libcore" \
 -proofread $(OUT_DOCS)/$(LOCAL_MODULE)-proofread.txt \
 -todo ../$(LOCAL_MODULE)-docs-todo.html \
 -knowntags ./libcore/known_oj_tags.txt \
 -hdf android.whichdoc offline

LOCAL_DROIDDOC_CUSTOM_TEMPLATE_DIR:=external/doclava/res/assets/templates-sdk

include $(BUILD_DROIDDOC)

# For unbundled build we'll use the prebuilt jar from prebuilts/sdk.
ifeq (,$(TARGET_BUILD_APPS)$(filter true,$(TARGET_BUILD_PDK)))

# Generate the stub source files for core.current.stubs
# =====================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(addprefix ../, $(libcore_to_document))
LOCAL_GENERATED_SOURCES := $(libcore_to_document_generated)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES

LOCAL_DROIDDOC_OPTIONS:= \
    -stubs $(TARGET_OUT_COMMON_INTERMEDIATES)/JAVA_LIBRARIES/core.current.stubs_intermediates/src \
    -nodocs \

LOCAL_UNINSTALLABLE_MODULE := true
LOCAL_NO_STANDARD_LIBRARIES := true
LOCAL_MODULE := core-current-stubs-gen

include $(BUILD_DROIDDOC)

# Remember the target that will trigger the code generation.
core_current_gen_stamp := $(full_target)

# Build the core.current.stubs library
# ====================================
include $(CLEAR_VARS)

LOCAL_MODULE := core.current.stubs

LOCAL_SOURCE_FILES_ALL_GENERATED := true

# Make sure to run droiddoc first to generate the stub source files.
LOCAL_ADDITIONAL_DEPENDENCIES := $(core_current_gen_stamp)
core_current_gen_stamp :=

# Because javac refuses to compile these stubs with --system=none, ( http://b/72206056#comment31 ),
# just patch them into java.base at compile time.
LOCAL_PATCH_MODULE := java.base
LOCAL_NO_STANDARD_LIBRARIES := true
LOCAL_ERROR_PRONE_FLAGS := -Xep:MissingOverride:OFF
include $(BUILD_STATIC_JAVA_LIBRARY)

# Archive a copy of the classes.jar in SDK build.
$(call dist-for-goals,sdk win_sdk,$(full_classes_jar):core.current.stubs.jar)

endif  # not TARGET_BUILD_APPS not TARGET_BUILD_PDK=true
