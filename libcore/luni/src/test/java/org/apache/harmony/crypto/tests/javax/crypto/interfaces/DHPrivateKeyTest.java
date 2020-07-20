/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
* @author Vera Y. Petrashkova
* @version $Revision$
*/

package org.apache.harmony.crypto.tests.javax.crypto.interfaces;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;

import javax.crypto.KeyGenerator;
import javax.crypto.interfaces.DHKey;
import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.spec.DHParameterSpec;

import junit.framework.TestCase;

/**
 * Tests for <code>DHPrivateKey</code> class field
 *
 */
public class DHPrivateKeyTest extends TestCase {

    /**
     * Test for <code>serialVersionUID</code> field
     */
    public void testField() {
        checkDHPrivateKey key = new checkDHPrivateKey();
        assertEquals("Incorrect serialVersionUID",
                key.getSerVerUID(), //DHPrivateKey.serialVersionUID
                2211791113380396553L);
    }

    public void test_getParams_initToHardCoded() throws Exception {
        // (p, g) values from RFC 7919, Appendix A (2048-bit group)
        String pStr = "FFFFFFFFFFFFFFFFADF85458A2BB4A9AAFDC5620273D3CF1" +
                "D8B9C583CE2D3695A9E13641146433FBCC939DCE249B3EF9" +
                "7D2FE363630C75D8F681B202AEC4617AD3DF1ED5D5FD6561" +
                "2433F51F5F066ED0856365553DED1AF3B557135E7F57C935" +
                "984F0C70E0E68B77E2A689DAF3EFE8721DF158A136ADE735" +
                "30ACCA4F483A797ABC0AB182B324FB61D108A94BB2C8E3FB" +
                "B96ADAB760D7F4681D4F42A3DE394DF4AE56EDE76372BB19" +
                "0B07A7C8EE0A6D709E02FCE1CDF7E2ECC03404CD28342F61" +
                "9172FE9CE98583FF8E4F1232EEF28183C3FE3B1B4C6FAD73" +
                "3BB5FCBC2EC22005C58EF1837D1683B2C6F34A26C1B2EFFA" +
                "886B423861285C97FFFFFFFFFFFFFFFF";
        BigInteger p = new BigInteger(new BigInteger(pStr, 16).toByteArray());
        BigInteger g = BigInteger.valueOf(2);
        KeyPairGenerator kg = KeyPairGenerator.getInstance("DH");
        kg.initialize(new DHParameterSpec(p, g), new SecureRandom());
        check_getParams(kg);
    }

    public void test_getParams_initToRandom192bit() throws Exception {
        KeyPairGenerator kg = KeyPairGenerator.getInstance("DH");
        // DH group generation is slow, so we test with a small (insecure) value
        kg.initialize(192);
        check_getParams(kg);
    }

    private static void check_getParams(KeyPairGenerator kg) throws Exception {
        KeyPair kp1 = kg.genKeyPair();
        KeyPair kp2 = kg.genKeyPair();
        DHPrivateKey pk1 = (DHPrivateKey) kp1.getPrivate();
        DHPrivateKey pk2 = (DHPrivateKey) kp2.getPrivate();

        assertTrue(pk1.getX().getClass().getCanonicalName().equals("java.math.BigInteger"));
        assertTrue(pk1.getParams().getClass().getCanonicalName().equals("javax.crypto.spec.DHParameterSpec"));
        assertFalse(pk1.equals(pk2));
        assertTrue(pk1.getX().equals(pk1.getX()));
    }

    public class checkDHPrivateKey implements DHPrivateKey {
        public String getAlgorithm() {
            return "SecretKey";
        }
        public String getFormat() {
            return "Format";
        }
        public byte[] getEncoded() {
            return new byte[0];
        }
        public long getSerVerUID() {
            return serialVersionUID;
        }
        public BigInteger getX() {
            return null;
        }
        public DHParameterSpec getParams() {
            return null;
        }
    }
}
