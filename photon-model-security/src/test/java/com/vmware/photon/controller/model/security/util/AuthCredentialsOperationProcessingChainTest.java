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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CUSTOM_PROP_CREDENTIALS_SCOPE;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.HashMap;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vmware.photon.controller.model.constants.PhotonModelConstants.CredentialsScope;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class AuthCredentialsOperationProcessingChainTest extends BaseModelTest {

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();

    private static Method findService;

    static {
        try {
            findService = ServiceHost.class.getDeclaredMethod("findService", String.class);
            findService.setAccessible(true);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    @Before
    public void setUp() throws Throwable {
        // wait for needed services
        waitForServiceAvailability(AuthCredentialsService.FACTORY_LINK);

        // set AuthCredentialsOperationProcessingChain in the factory
        FactoryService fs = (FactoryService) findService.invoke(host,
                AuthCredentialsService.FACTORY_LINK);
        fs.setOperationProcessingChain(new AuthCredentialsOperationProcessingChain(fs));

        // common setup
        System.clearProperty(EncryptionUtils.ENCRYPTION_KEY);
        System.clearProperty(EncryptionUtils.INIT_KEY_IF_MISSING);
        EncryptionUtils.initEncryptionService();
    }

    @Test
    public void testPlainTextCredentials() throws Throwable {

        // do NOT init EncryptionUtils

        AuthCredentialsServiceState credentials = createCredentials("username", "password", false);

        assertEquals("username", credentials.userEmail);
        assertNotNull(credentials.privateKey);
        assertFalse(credentials.privateKey.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));

        String publicKey = "-----BEGIN CERTIFICATE-----\nABC\n-----END CERTIFICATE-----";

        credentials = createCredentialsWithKeys(publicKey,
                "-----BEGIN PRIVATE KEY-----\nDEF\n-----END PRIVATE KEY-----");

        assertEquals(publicKey, credentials.publicKey);
        assertNotNull(credentials.privateKey);
        assertFalse(credentials.privateKey.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));
    }

    @Test
    public void testEncryptedCredentials() throws Throwable {

        // init EncryptionUtils

        File keyFile = Paths.get(folder.newFolder().getPath(), "encryption.key").toFile();
        System.setProperty(EncryptionUtils.ENCRYPTION_KEY, keyFile.getPath());
        System.setProperty(EncryptionUtils.INIT_KEY_IF_MISSING, "true");
        EncryptionUtils.initEncryptionService();

        AuthCredentialsServiceState credentials = createCredentials("username", "password", false);

        assertEquals("username", credentials.userEmail);
        assertNotNull(credentials.privateKey);
        assertTrue(credentials.privateKey.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));

        String publicKey = "-----BEGIN CERTIFICATE-----\nABC\n-----END CERTIFICATE-----";

        credentials = createCredentialsWithKeys(publicKey,
                "-----BEGIN PRIVATE KEY-----\nDEF\n-----END PRIVATE KEY-----");

        assertEquals(publicKey, credentials.publicKey);
        assertNotNull(credentials.privateKey);
        assertTrue(credentials.privateKey.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));

        // if the private key is (sent) already encrypted, it's not re-encrypted

        String encryptedOnce = credentials.privateKey;

        String publicKeyNew = "-----BEGIN CERTIFICATE-----\nGHI\n-----END CERTIFICATE-----";

        credentials.publicKey = publicKeyNew;

        putServiceSynchronously(credentials.documentSelfLink, credentials);
        credentials = getServiceSynchronously(credentials.documentSelfLink,
                AuthCredentialsServiceState.class);

        assertEquals(publicKeyNew, credentials.publicKey);
        assertNotNull(credentials.privateKey);
        assertTrue(credentials.privateKey.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));
        assertEquals(encryptedOnce, credentials.privateKey);

        // if the private key has changed, it's re-encrypted

        credentials.privateKey = "-----BEGIN PRIVATE KEY-----\nJKL\n-----END PRIVATE KEY-----";

        putServiceSynchronously(credentials.documentSelfLink, credentials);
        credentials = getServiceSynchronously(credentials.documentSelfLink,
                AuthCredentialsServiceState.class);

        assertEquals(publicKeyNew, credentials.publicKey);
        assertNotNull(credentials.privateKey);
        assertTrue(credentials.privateKey.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));
        assertNotEquals(encryptedOnce, credentials.privateKey);
    }

    @Test
    public void testPlainTextSystemCredentials() throws Throwable {

        // init EncryptionUtils

        File keyFile = Paths.get(folder.newFolder().getPath(), "encryption.key").toFile();
        System.setProperty(EncryptionUtils.ENCRYPTION_KEY, keyFile.getPath());
        System.setProperty(EncryptionUtils.INIT_KEY_IF_MISSING, "true");
        EncryptionUtils.initEncryptionService();

        AuthCredentialsServiceState credentials = createCredentials("username", "password", true);

        assertEquals("username", credentials.userEmail);
        assertNotNull(credentials.privateKey);
        assertFalse(credentials.privateKey.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));
        assertEquals("password", credentials.privateKey);

        credentials = createCredentials("username2", "password2", false);

        assertEquals("username2", credentials.userEmail);
        assertNotNull(credentials.privateKey);
        assertTrue(credentials.privateKey.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));

        // like AuthBootstrapService does

        AuthCredentialsServiceState credentialsPatch = new AuthCredentialsServiceState();
        credentialsPatch.privateKey = "password2";
        credentialsPatch.customProperties = new HashMap<>();
        credentialsPatch.customProperties.put(CUSTOM_PROP_CREDENTIALS_SCOPE,
                CredentialsScope.SYSTEM.toString());

        credentials = patchServiceSynchronously(credentials.documentSelfLink, credentialsPatch,
                AuthCredentialsServiceState.class);

        assertEquals("username2", credentials.userEmail);
        assertNotNull(credentials.privateKey);
        assertFalse(credentials.privateKey.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));
        assertEquals("password2", credentials.privateKey);
    }

    protected AuthCredentialsServiceState createCredentials(String username, String password,
            boolean isSystem) throws Throwable {
        AuthCredentialsServiceState credentials = new AuthCredentialsServiceState();
        credentials.userEmail = username;
        credentials.privateKey = password;
        credentials.type = AuthCredentialsType.Password.toString();
        if (isSystem) {
            credentials.customProperties = new HashMap<>();
            credentials.customProperties.put(CUSTOM_PROP_CREDENTIALS_SCOPE,
                    CredentialsScope.SYSTEM.toString());
        }
        return injectOperationProcessingChain(postServiceSynchronously(
                AuthCredentialsService.FACTORY_LINK, credentials,
                AuthCredentialsServiceState.class));
    }

    protected AuthCredentialsServiceState createCredentialsWithKeys(String publicKey,
            String privateKey) throws Throwable {
        AuthCredentialsServiceState credentials = new AuthCredentialsServiceState();
        credentials.publicKey = publicKey;
        credentials.privateKey = privateKey;
        credentials.type = AuthCredentialsType.PublicKey.toString();
        return injectOperationProcessingChain(postServiceSynchronously(
                AuthCredentialsService.FACTORY_LINK, credentials,
                AuthCredentialsServiceState.class));
    }

    private AuthCredentialsServiceState injectOperationProcessingChain(
            AuthCredentialsServiceState state) throws Exception {
        AuthCredentialsService s = (AuthCredentialsService) findService.invoke(host,
                state.documentSelfLink);
        s.setOperationProcessingChain(new AuthCredentialsOperationProcessingChain(s));
        return state;
    }
}
