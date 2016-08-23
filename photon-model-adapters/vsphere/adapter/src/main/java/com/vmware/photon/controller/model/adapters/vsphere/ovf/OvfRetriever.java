/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.vsphere.ovf;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Downloads an OVF descriptor over http or file. Only checks if the input is a valid xml.
 */
public class OvfRetriever {

    private HttpClient client;

    public OvfRetriever(HttpClient client) {
        this.client = client;
    }

    /**
     * Create a client that ignores all ssl errors.
     * Temporary solution until TrustStore service is ready
     *  https://jira-hzn.eng.vmware.com/browse/VSYM-1838
     * @return
     */
    public static CloseableHttpClient newInsecureClient() {
        return HttpClientBuilder.create()
                .setHostnameVerifier(newNaiveVerifier())
                .setSslcontext(newNaiveSslContext())
                .setMaxConnPerRoute(4)
                .setMaxConnTotal(8)
                .build();
    }

    private static SSLContext newNaiveSslContext() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(new KeyManager[] {}, new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
                                throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
                                throws CertificateException {
                        }

                        @Override public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            }, new SecureRandom());

            return ctx;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private static X509HostnameVerifier newNaiveVerifier() {
        return new X509HostnameVerifier() {
            @Override
            public void verify(String host, SSLSocket ssl) throws IOException {

            }

            @Override
            public void verify(String host, X509Certificate cert) throws SSLException {

            }

            @Override
            public void verify(String host, String[] cns, String[] subjectAlts)
                    throws SSLException {

            }

            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        };
    }

    private SAXParser newSaxParser() {
        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();

        try {
            return saxParserFactory.newSAXParser();
        } catch (SAXException | ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public String retrieveAsString(URI ovfUri) throws IOException {
        StoringInputStream storingInputStream = toStream(ovfUri);

        return new String(storingInputStream.getStoredBytes(), "UTF-8");
    }

    public InputStream retrieveAsStream(URI ovfUri) throws IOException {
        StoringInputStream storingInputStream = toStream(ovfUri);

        return new ByteArrayInputStream(storingInputStream.getStoredBytes());
    }

    private StoringInputStream toStream(URI ovfUri) throws IOException {
        SAXParser saxParser = newSaxParser();
        DefaultHandler handler = new DefaultHandler();

        InputStream is;
        HttpResponse response = null;
        HttpGet request = null;

        if (ovfUri.getScheme().equals("file")) {
            is = new FileInputStream(new File(ovfUri));
        } else {
            request = new HttpGet(ovfUri);
            response = this.client.execute(request);

            if (response.getStatusLine().getStatusCode() != 200) {
                throw new IOException("Ovf descriptor not found at " +
                        ovfUri + ". Error code " + response.getStatusLine());
            }

            is = response.getEntity().getContent();
        }

        StoringInputStream storingInputStream = new StoringInputStream(is);

        try {
            saxParser.parse(storingInputStream, handler);
            if (response != null) {
                EntityUtils.consumeQuietly(response.getEntity());
            }
        } catch (SAXException e) {
            // not a valid ovf - abort
            if (request != null) {
                request.abort();
            }
            EntityUtils.consumeQuietly(response.getEntity());

            throw new IOException("Ovf not a valid xml: " + e.getMessage(), e);
        } finally {
            //close stream, could be file
            IOUtils.closeQuietly(is);
        }

        return storingInputStream;
    }
}
