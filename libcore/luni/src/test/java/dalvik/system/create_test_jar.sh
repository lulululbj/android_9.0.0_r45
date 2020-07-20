#!/bin/bash
set -e

# Create the child JAR
# --------------------------------------
mkdir -p /tmp/delegate_last_child/libcore/test/delegatelast;
pushd /tmp/delegate_last_child
echo "package libcore.test.delegatelast;\
      public class A {\
          public String toString() {\
              return \"A_child\";\
          }\
      }" > libcore/test/delegatelast/A.java
echo "package libcore.test.delegatelast;\
      public class Child {\
          public String toString() {\
              return \"Child_child\";\
          }\
      }" > libcore/test/delegatelast/Child.java
javac libcore/test/delegatelast/*.java
dx --dex --output=./child.jar --verbose libcore/test/delegatelast/*.class
echo -ne "child" > ./resource.txt
jar uf ./child.jar resource.txt
cp ./child.jar $ANDROID_BUILD_TOP/libcore/luni/src/test/resources/dalvik/system/child.jar
popd

# Create the parent JAR
# --------------------------------------
mkdir -p /tmp/delegate_last_parent/libcore/test/delegatelast;
pushd /tmp/delegate_last_parent
echo "package libcore.test.delegatelast;\
      public class A {\
          public String toString() {\
              return \"A_parent\";\
          }\
      }" > libcore/test/delegatelast/A.java
echo "package libcore.test.delegatelast;\
      public class Parent {\
          public String toString() {\
              return \"Parent_parent\";\
          }\
      }" > libcore/test/delegatelast/Parent.java
javac libcore/test/delegatelast/*.java
dx --dex --output=./parent.jar --verbose libcore/test/delegatelast/*.class
echo -ne "parent" > ./resource.txt
jar uf ./parent.jar resource.txt
cp ./parent.jar $ANDROID_BUILD_TOP/libcore/luni/src/test/resources/dalvik/system/parent.jar
popd


# Create a jar that overloads boot classpath classes and resources
# ----------------------------------------------------------------
mkdir -p /tmp/delegate_last_bootoverride/java/util;
pushd /tmp/delegate_last_bootoverride
echo "package java.util;\
      public class HashMap {\
          public String toString() {\
              return \"I'm not really a HashMap\";\
          }\
      }" > java/util/HashMap.java
javac java/util/HashMap.java
dx --dex --core-library --output=./bootoverride.jar --verbose java/util/HashMap.class

mkdir -p android/icu
echo -ne "NOT ICU" > android/icu/ICUConfig.properties
jar uf ./bootoverride.jar android/icu/ICUConfig.properties
cp ./bootoverride.jar $ANDROID_BUILD_TOP/libcore/luni/src/test/resources/dalvik/system/bootoverride.jar
popd
