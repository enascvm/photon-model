/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.model.security.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;

public class EncryptionUtilsTest {

    @BeforeClass
    public static void setEncryptionKeyFile() throws IOException {
        // initializing an existing encryption key
        File file = EncryptorServiceTest.getKeyFile();

        System.setProperty(EncryptionUtils.ENCRYPTION_KEY, file.getPath());
        System.setProperty(EncryptionUtils.INIT_KEY_IF_MISSING, "true");
        EncryptionUtils.initEncryptionService();
    }

    @Test
    public void testEncryptDecrypt() {

        String plainText = EncryptorServiceTest.generatePlainText();

        String encryptedString = EncryptionUtils.encrypt(plainText);
        assertNotNull(encryptedString);
        assertNotEquals(plainText, encryptedString);
        assertTrue(encryptedString.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));

        String decryptedString = EncryptionUtils.decrypt(encryptedString);
        assertNotNull(decryptedString);

        assertEquals(plainText, decryptedString);
    }

    @Test
    public void testNulls() {
        String value = EncryptionUtils.encrypt(null);
        assertNull(value);

        value = EncryptionUtils.decrypt(null);
        assertNull(value);
    }

    @Test
    public void testDecryptPlainText() {
        String plainText = EncryptorServiceTest.generatePlainText();

        String value = EncryptionUtils.decrypt(plainText);
        assertEquals(plainText, value);
    }

    private static final String TEST_ENCRYPTED_PASSWORD = "s2enc~j0yxofeNf+z2CL21BPNv3g==";
    private static final String TEST_DECRYPTED_PASSWORD = "secret";

    @Test
    public void testDecryptCafePassword() {
        String value = EncryptionUtils.decrypt(TEST_ENCRYPTED_PASSWORD);
        assertEquals(TEST_DECRYPTED_PASSWORD, value);
    }

}
