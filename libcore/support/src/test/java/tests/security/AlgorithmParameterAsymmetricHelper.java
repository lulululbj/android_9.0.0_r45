/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tests.security;

import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import junit.framework.Assert;

public class AlgorithmParameterAsymmetricHelper extends TestHelper<AlgorithmParameters> {

    private static final String plainData = "some data to encrypt and decrypt";
    private final String algorithmName;

    public AlgorithmParameterAsymmetricHelper(String algorithmName) {
        this.algorithmName = algorithmName;
    }

    @Override
    public void test(AlgorithmParameters parameters) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithmName);
        generator.initialize(1024);
        KeyPair keyPair = generator.generateKeyPair();

        Cipher cipher = Cipher.getInstance(algorithmName);
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic(), parameters);
        byte[] bs = cipher.doFinal(plainData.getBytes());

        cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate(), parameters);
        byte[] decrypted = cipher.doFinal(bs);
        Assert.assertTrue(Arrays.equals(plainData.getBytes(), decrypted));
    }
}
