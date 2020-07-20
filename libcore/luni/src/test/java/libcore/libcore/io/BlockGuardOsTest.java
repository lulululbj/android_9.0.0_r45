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
 * limitations under the License
 */

package libcore.libcore.io;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import android.system.OsConstants;
import android.system.StructAddrinfo;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import libcore.io.BlockGuardOs;
import libcore.io.Os;

import dalvik.system.BlockGuard;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class BlockGuardOsTest {

    final static Pattern pattern = Pattern.compile("[\\w\\$]+\\([^)]*\\)");

    @Mock private Os mockOsDelegate;
    @Mock private BlockGuard.Policy mockThreadPolicy;

    private BlockGuard.Policy savedThreadPolicy;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        savedThreadPolicy = BlockGuard.getThreadPolicy();
        BlockGuard.setThreadPolicy(mockThreadPolicy);
    }

    @After
    public void tearDown() {
        BlockGuard.setThreadPolicy(savedThreadPolicy);
    }

    @Test
    public void test_android_getaddrinfo_networkPolicy() {
        InetAddress[] addresses = new InetAddress[] { InetAddress.getLoopbackAddress() };
        when(mockOsDelegate.android_getaddrinfo(anyString(), any(), anyInt()))
                .thenReturn(addresses);

        BlockGuardOs blockGuardOs = new BlockGuardOs(mockOsDelegate);

        // Test with a numeric address that will not trigger a network policy check.
        {
            final String node = "numeric";
            final int netId = 1234;
            final StructAddrinfo numericAddrInfo = new StructAddrinfo();
            numericAddrInfo.ai_flags = OsConstants.AI_NUMERICHOST;
            InetAddress[] actual =
                    blockGuardOs.android_getaddrinfo(node, numericAddrInfo, netId);

            verify(mockThreadPolicy, times(0)).onNetwork();
            verify(mockOsDelegate, times(1)).android_getaddrinfo(node, numericAddrInfo, netId);
            assertSame(addresses, actual);
        }

        // Test with a non-numeric address that will trigger a network policy check.
        {
            final String node = "non-numeric";
            final int netId = 1234;
            final StructAddrinfo nonNumericAddrInfo = new StructAddrinfo();
            InetAddress[] actual =
                    blockGuardOs.android_getaddrinfo(node, nonNumericAddrInfo, netId);

            verify(mockThreadPolicy, times(1)).onNetwork();
            verify(mockOsDelegate, times(1)).android_getaddrinfo(node, nonNumericAddrInfo, netId);
            assertSame(addresses, actual);
        }
    }

    /**
     * Checks that BlockGuardOs is updated when the Os interface changes. BlockGuardOs extends
     * ForwardingOs so doing so isn't an obvious step and it can be missed. When adding methods to
     * Os developers must give consideration to whether extra behavior should be added to
     * BlockGuardOs. Developers failing this test should add to the list of method below
     * (if the calls cannot block) or should add an override for the method with the appropriate
     * calls to BlockGuard (if the calls can block).
     */
    @Test
    public void test_checkNewMethodsInPosix() {
        List<String> methodsNotRequireBlockGuardChecks = Arrays.asList(
                "bind(java.io.FileDescriptor,java.net.InetAddress,int)",
                "bind(java.io.FileDescriptor,java.net.SocketAddress)",
                "capget(android.system.StructCapUserHeader)",
                "capset(android.system.StructCapUserHeader,android.system.StructCapUserData[])",
                "dup(java.io.FileDescriptor)",
                "dup2(java.io.FileDescriptor,int)",
                "environ()",
                "fcntlFlock(java.io.FileDescriptor,int,android.system.StructFlock)",
                "fcntlInt(java.io.FileDescriptor,int,int)",
                "fcntlVoid(java.io.FileDescriptor,int)",
                "gai_strerror(int)",
                "getegid()",
                "getenv(java.lang.String)",
                "geteuid()",
                "getgid()",
                "getgroups()",
                "getifaddrs()",
                "getnameinfo(java.net.InetAddress,int)",
                "getpeername(java.io.FileDescriptor)",
                "getpgid(int)",
                "getpid()",
                "getppid()",
                "getpwnam(java.lang.String)",
                "getpwuid(int)",
                "getrlimit(int)",
                "getsockname(java.io.FileDescriptor)",
                "getsockoptByte(java.io.FileDescriptor,int,int)",
                "getsockoptInAddr(java.io.FileDescriptor,int,int)",
                "getsockoptInt(java.io.FileDescriptor,int,int)",
                "getsockoptLinger(java.io.FileDescriptor,int,int)",
                "getsockoptTimeval(java.io.FileDescriptor,int,int)",
                "getsockoptUcred(java.io.FileDescriptor,int,int)",
                "gettid()",
                "getuid()",
                "if_indextoname(int)",
                "if_nametoindex(java.lang.String)",
                "inet_pton(int,java.lang.String)",
                "ioctlFlags(java.io.FileDescriptor,java.lang.String)",
                "ioctlInetAddress(java.io.FileDescriptor,int,java.lang.String)",
                "ioctlInt(java.io.FileDescriptor,int,android.system.Int32Ref)",
                "ioctlMTU(java.io.FileDescriptor,java.lang.String)",
                "isatty(java.io.FileDescriptor)",
                "kill(int,int)",
                "listen(java.io.FileDescriptor,int)",
                "listxattr(java.lang.String)",
                "mincore(long,long,byte[])",
                "mlock(long,long)",
                "mmap(long,long,int,int,java.io.FileDescriptor,long)",
                "munlock(long,long)",
                "munmap(long,long)",
                "pipe2(int)",
                "prctl(int,long,long,long,long)",
                "setegid(int)",
                "setenv(java.lang.String,java.lang.String,boolean)",
                "seteuid(int)",
                "setgid(int)",
                "setgroups(int[])",
                "setpgid(int,int)",
                "setregid(int,int)",
                "setreuid(int,int)",
                "setsid()",
                "setsockoptByte(java.io.FileDescriptor,int,int,int)",
                "setsockoptGroupReq(java.io.FileDescriptor,int,int,android.system.StructGroupReq)",
                "setsockoptIfreq(java.io.FileDescriptor,int,int,java.lang.String)",
                "setsockoptInt(java.io.FileDescriptor,int,int,int)",
                "setsockoptIpMreqn(java.io.FileDescriptor,int,int,int)",
                "setsockoptLinger(java.io.FileDescriptor,int,int,android.system.StructLinger)",
                "setsockoptTimeval(java.io.FileDescriptor,int,int,android.system.StructTimeval)",
                "setuid(int)",
                "shutdown(java.io.FileDescriptor,int)",
                "strerror(int)",
                "strsignal(int)",
                "sysconf(int)",
                "tcdrain(java.io.FileDescriptor)",
                "tcsendbreak(java.io.FileDescriptor,int)",
                "umask(int)",
                "uname()",
                "unsetenv(java.lang.String)",
                "waitpid(int,android.system.Int32Ref,int)");
        Set<String> methodsNotRequiredBlockGuardCheckSet = new HashSet<>(
                methodsNotRequireBlockGuardChecks);

        Set<String> methodsInBlockGuardOs = new HashSet<>();

        // Populate the set of the public methods implemented in BlockGuardOs.
        for (Method method : BlockGuardOs.class.getDeclaredMethods()) {
            String methodNameAndParameters = getMethodNameAndParameters(method.toString());
            methodsInBlockGuardOs.add(methodNameAndParameters);
        }

        // Verify that all the methods in libcore.io.Os should either be overridden in BlockGuardOs
        // or else they should be in the "methodsNotRequiredBlockGuardCheckSet".
        for (Method method : Os.class.getDeclaredMethods()) {
            String methodSignature = method.toString();
            String methodNameAndParameters = getMethodNameAndParameters(methodSignature);
            if (!methodsNotRequiredBlockGuardCheckSet.contains(methodNameAndParameters) &&
                    !methodsInBlockGuardOs.contains(methodNameAndParameters)) {
                fail(methodNameAndParameters + " is not present in "
                        + "methodsNotRequiredBlockGuardCheckSet and is also not overridden in"
                        + " BlockGuardOs class. Either override the method in BlockGuardOs or"
                        + " add it in the methodsNotRequiredBlockGuardCheckSet");

            }
        }
    }

    /**
     * Extract method name and parameter information from the method signature.
     * For example, for input "public void package.class.method(A,B)", the output will be
     * "method(A,B)".
     */
    private static String getMethodNameAndParameters(String methodSignature) {
        Matcher methodPatternMatcher = pattern.matcher(methodSignature);
        if (methodPatternMatcher.find()) {
            return methodPatternMatcher.group();
        } else {
            throw new IllegalArgumentException(methodSignature);
        }
    }
}
