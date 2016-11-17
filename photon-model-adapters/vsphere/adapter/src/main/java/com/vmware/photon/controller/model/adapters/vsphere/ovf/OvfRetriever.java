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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Properties;

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

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.vmware.xenon.common.Utils;

/**
 * Downloads an OVF descriptor over http or file. Only checks if the input is a valid xml.
 */
public class OvfRetriever {

    public static final int TAR_MAGIC_OFFSET = 0x101;
    private static final String MARKER_FILE = "status.properties";

    private static final Logger logger = LoggerFactory.getLogger(OvfRetriever.class.getName());

    private HttpClient client;

    /**
     * TAR magic numbers https://en.wikipedia.org/wiki/Tar_(computing)
     */
    private static final byte[] TAR_MAGIC = new byte[] { 0x75, 0x73, 0x74, 0x61, 0x72, 0x00, 0x30, 0x30 };
    private static final byte[] TAR_MAGIC2 = new byte[] { 0x75, 0x73, 0x74, 0x61, 0x72, 0x20, 0x20, 0x00 };

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

    /**
     * If ovaOrOvfUri points to a OVA it will be download locally and extracted. The uri is considered OVA if it is
     * in tar format and there is at least one .ovf inside.
     *
     * @param ovaOrOvfUri
     * @return the first .ovf file from the extracted tar of the input parameter if it's a local file or not a
     * tar archive
     */
    public URI downloadIfOva(URI ovaOrOvfUri) throws IOException {
        if (ovaOrOvfUri.getScheme().equals("file")) {
            // local files are assumed to be ovfs
            return ovaOrOvfUri;
        }

        HttpGet get = new HttpGet(ovaOrOvfUri);
        HttpResponse check = null;

        logger.debug("Downloading ovf/ova from {}", ovaOrOvfUri);

        try {
            check = this.client.execute(get);
            byte[] buffer = new byte[TAR_MAGIC_OFFSET + TAR_MAGIC.length];
            int read = IOUtils.read(check.getEntity().getContent(), buffer);
            if (read != buffer.length) {
                // not a tar file, probably OVF, lets caller decide further
                return ovaOrOvfUri;
            }
            for (int i = 0; i < TAR_MAGIC.length; i++) {
                byte b = buffer[TAR_MAGIC_OFFSET + i];
                if (b != TAR_MAGIC[i] && b != TAR_MAGIC2[i]) {
                    // magic numbers don't match
                    logger.info("{} is not a tar file, assuming OVF", ovaOrOvfUri);
                    return ovaOrOvfUri;
                }
            }
        } finally {
            get.abort();
            if (check != null) {
                EntityUtils.consumeQuietly(check.getEntity());
            }
        }

        // it's an OVA (at least a tar file), download to a local folder
        String folderName = hash(ovaOrOvfUri);
        File destination = new File(getBaseOvaExtractionDir(), folderName);
        if (new File(destination, MARKER_FILE).isFile()) {
            // marker file exists so the archive is already downloaded
            logger.info("Marker file for {} exists in {}, not downloading again", ovaOrOvfUri, destination);
            return findFirstOvfInFolder(destination);
        }

        destination.mkdirs();
        logger.info("Downloading OVA to {}", destination);

        get = new HttpGet(ovaOrOvfUri);
        HttpResponse response = null;
        try {
            response = this.client.execute(get);
            TarArchiveInputStream tarStream = new TarArchiveInputStream(response.getEntity().getContent());
            TarArchiveEntry entry;
            while ((entry = tarStream.getNextTarEntry()) != null) {
                extractEntry(tarStream, destination, entry);
            }
        } finally {
            if (response != null) {
                EntityUtils.consumeQuietly(response.getEntity());
            }
        }

        // store download progress
        writeMarkerFile(destination, ovaOrOvfUri);

        return findFirstOvfInFolder(destination);
    }

    protected URI findFirstOvfInFolder(File destination) throws IOException {
        File[] files = destination.listFiles(f -> f.getName().endsWith(".ovf"));
        if (files == null || files.length == 0) {
            throw new IOException("OVA archive does not contain an .ovf descriptor");
        }

        return files[0].toURI();
    }

    private void writeMarkerFile(File destination, URI ovaOrOvfUri) {
        Properties props = new Properties();
        props.setProperty("download-uri", ovaOrOvfUri.toString());
        props.setProperty("download-date", new Date().toString());
        props.setProperty("download-folder", destination.getAbsolutePath());

        try {
            File propFile = new File(destination, MARKER_FILE);
            try (FileOutputStream fos = new FileOutputStream(propFile)) {
                try {
                    props.store(fos, null);
                    logger.debug("Stored OVA download progress to {}", propFile.getAbsoluteFile());
                } catch (IOException ignore) {

                }
            }
        } catch (IOException e) {

        }
    }

    protected String getBaseOvaExtractionDir() {
        // good idea to make this configurable
        return System.getProperty("java.io.tmpdir");
    }

    private void extractEntry(TarArchiveInputStream tar, File destination, TarArchiveEntry entry) throws IOException {
        File file = new File(destination, entry.getName());
        if (entry.isDirectory()) {
            file.mkdirs();
        } else {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                logger.debug("Extracting {} to {}", entry.getName(), file.getAbsoluteFile());
                IOUtils.copy(tar, fos);
            }
        }
    }

    private String hash(URI ovaOrOvfUri) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-1");
            sha256.update(ovaOrOvfUri.toString().getBytes(Utils.CHARSET));
            byte[] digest = sha256.digest();
            return Hex.encodeHexString(digest);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public HttpClient getClient() {
        return this.client;
    }
}
