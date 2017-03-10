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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.LocalizableValidationException;

/**
 * Simple encryption service that provides methods to encrypt and decrypt byte arrays and strings
 * based on the provided symmetric key. The key can be provided directly as a byte array or through
 * a file which contains it.
 */
public final class EncryptorService {

    private final byte[] keyBytes;

    /**
     * Creates a new {@link EncryptorService} instance from the provided encryption key.
     * @param encryptionKeyBytes
     *         Encryption key as byte array
     */
    public EncryptorService(byte[] encryptionKeyBytes) {
        AssertUtil.assertNotNull(encryptionKeyBytes, "encryptionKeyBytes");
        this.keyBytes = encryptionKeyBytes.clone();
    }

    /**
     * Creates a new {@link EncryptorService} instance from the provided encryption key file.
     * @param encryptionKeyFile
     *         File containing the encryption key (as byte array)
     */
    public EncryptorService(File encryptionKeyFile) {
        AssertUtil.assertNotNull(encryptionKeyFile, "encryptionKeyFile");
        try {
            this.keyBytes = Files.readAllBytes(Paths.get(encryptionKeyFile.toURI()));
        } catch (IOException e) {
            throw new LocalizableValidationException(e, "Invalid encryption key file!",
                    "common.ecryptor.file.invalid");
        }
    }

    /**
     * Encrypts the provided string.
     * @param input
     *         String (UTF-8 encoded) to be encrypted
     * @return The encrypted version of the input string.
     */
    public String encrypt(String input) {
        if (input == null || input.length() == 0) {
            return input;
        }
        byte[] inputBytes = input.getBytes(UTF_8);
        byte[] outputBytes = encrypt(inputBytes);
        return new String(outputBytes, UTF_8);
    }

    /**
     * Encrypts the provided byte array.
     * @param input
     *         Byte array to be encrypted
     * @return The encrypted version of the input byte array (in base 64).
     */
    public byte[] encrypt(final byte[] input) {
        if (input == null || input.length == 0) {
            return input;
        }

        try {
            BufferedBlockCipher cipher = getCipher(true);
            byte[] output = new byte[cipher.getOutputSize(input.length)];

            int length = cipher.processBytes(input, 0, input.length, output, 0);
            length += cipher.doFinal(output, length);

            return Base64.getEncoder().encode(Arrays.copyOfRange(output, 0, length));
        } catch (Exception e) {
            throw new LocalizableValidationException(e, "Encryption error!",
                    "common.ecryption.error");
        }
    }

    /**
     * Decrypts the provided string.
     * @param input
     *         String (UTF-8 encoded) to be decrypted
     * @return The decrypted version of the input string.
     */
    public String decrypt(String input) {
        if (input == null || input.length() == 0) {
            return input;
        }

        byte[] inputBytes = input.getBytes(UTF_8);
        byte[] outputBytes = decrypt(inputBytes);
        return new String(outputBytes, UTF_8);
    }

    /**
     * Decrypts the provided byte array.
     * @param input
     *         Byte array (in base 64) to be decrypted
     * @return The decrypted version of the input byte array.
     */
    public byte[] decrypt(final byte[] input) {
        if (input == null || input.length == 0) {
            return input;
        }

        try {
            BufferedBlockCipher cipher = getCipher(false);
            byte[] bytes = Base64.getDecoder().decode(input);
            byte[] output = new byte[cipher.getOutputSize(bytes.length)];

            int length = cipher.processBytes(bytes, 0, bytes.length, output, 0);
            length += cipher.doFinal(output, length);

            return Arrays.copyOfRange(output, 0, length);
        } catch (Exception e) {
            throw new LocalizableValidationException(e, "Decryption error!",
                    "common.dercyption.error");
        }
    }

    /*
     * Secure random settings
     */

    private static final String ALGORITH_SECURE_RANDOM = "SHA1PRNG";

    // SecureRandom is thread-safe
    private static SecureRandom secureRandom;

    static {
        if (secureRandom == null) {
            try {
                secureRandom = SecureRandom.getInstance(ALGORITH_SECURE_RANDOM);
            } catch (NoSuchAlgorithmException e) {
                // this should not happen at all (Sun provides this algorithm)
                throw new IllegalStateException(e);
            }
        }
    }

    /*
     * Symmetric key settings
     */

    private static final int KEY_LENGTH = 32; // 256 bit length for AES
    private static final int IV_LENGTH = 16;

    /**
     * Generates a new symmetric encryption key.
     * @return The generated key as byte array.
     */
    public static byte[] generateKey() {

        byte[] keyData = new byte[KEY_LENGTH];
        secureRandom.nextBytes(keyData);

        byte[] ivData = new byte[IV_LENGTH];
        secureRandom.nextBytes(ivData);

        byte[] key = new byte[IV_LENGTH + KEY_LENGTH];
        System.arraycopy(ivData, 0, key, 0, IV_LENGTH);
        System.arraycopy(keyData, 0, key, IV_LENGTH, KEY_LENGTH);
        return key;
    }

    /*
     * Cipher settings
     */

    private BufferedBlockCipher getCipher(boolean forEncryption) {
        BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(
                new CBCBlockCipher(new AESEngine()), new PKCS7Padding());
        cipher.init(forEncryption, new ParametersWithIV(new KeyParameter(this.keyBytes, IV_LENGTH,
                this.keyBytes.length - IV_LENGTH), this.keyBytes, 0, IV_LENGTH));
        return cipher;
    }
}
