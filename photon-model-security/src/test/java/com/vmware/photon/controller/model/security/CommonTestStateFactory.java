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

package com.vmware.photon.controller.model.security;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.cert.X509Certificate;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.security.service.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.security.util.CertificateUtil;

public class CommonTestStateFactory {
    public static final String AUTH_CREDENTIALS_ID = "test-credentials-id";
    public static final String REGISTRATION_DOCKER_ID = "test-docker-registration-id";
    public static final String SSL_TRUST_CERT_ID = "test-ssl-trust-cert-id";
    public static final String DOCKER_HOST_REGISTRATION_NAME = "docker-host";
    public static final String DOCKER_COMPUTE_ID = "test-docker-host-compute-id";
    public static final String ENDPOINT_ID = "test-endpoint-id";
    public static final String ENDPOINT_REGION_ID = "us-east-1"; // used for zoneId too

    public static final String RSA = "RSA";

    public static final String CERTS_CHAIN_PEM = "certs/chain.pem";

    public static X509Certificate[] loadCertificateChain(String path) {

        try (InputStream is = CommonTestStateFactory.class.getClassLoader()
                .getResourceAsStream(path);
                BufferedReader buffer = new BufferedReader(new InputStreamReader(is))) {
            String content = buffer.lines().collect(Collectors.joining(System.lineSeparator()));
            return CertificateUtil.createCertificateChain(content);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load resources from: " + path, e);
        }
    }

    public static SslTrustCertificateState createSslTrustCertificateState(String pemFileName,
            String id) {

        SslTrustCertificateState sslTrustState = new SslTrustCertificateState();
        sslTrustState.documentSelfLink = id;
        sslTrustState.certificate = getFileContent(pemFileName);
        return sslTrustState;
    }

    // TODO: This method seems pretty similar to FileUtil.getResourceAsString...
    public static String getFileContent(String fileName) {
        try (InputStream is = CommonTestStateFactory.class.getClassLoader()
                .getResourceAsStream(fileName)) {
            if (is != null) {
                return readFile(new InputStreamReader(is));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try (FileReader fileReader = new FileReader(fileName)) {
            return readFile(fileReader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private static String readFile(Reader reader) throws IOException {
        BufferedReader br = new BufferedReader(reader);
        StringBuilder sb = new StringBuilder();
        String read = br.readLine();
        String newLine = System.getProperty("line.separator");
        while (read != null) {
            sb.append(read);
            sb.append(newLine);
            read = br.readLine();
        }
        return sb.toString();
    }
}
