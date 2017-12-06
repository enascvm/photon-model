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

package com.vmware.photon.controller.model.adapters.vsphere.util.connection;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.TrustManager;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.handler.HandlerResolver;
import javax.xml.ws.handler.MessageContext;

import io.netty.handler.codec.http.HttpHeaderNames;
import org.xml.sax.SAXException;

import com.vmware.pbm.PbmPortType;
import com.vmware.pbm.PbmService;
import com.vmware.pbm.PbmServiceInstanceContent;
import com.vmware.vim25.InvalidLocaleFaultMsg;
import com.vmware.vim25.InvalidLoginFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.ServiceContent;
import com.vmware.vim25.SessionManagerHttpServiceRequestSpec;
import com.vmware.vim25.UserSession;
import com.vmware.vim25.VimPortType;
import com.vmware.vim25.VimService;

/**
 * This simple object shows how to set up a vSphere connection as it was done in vSphere 4.x and is provided
 * as a reference for anyone working with older vSphere servers that do not support modern SSO features.
 * It is intended as a utility class for use by Samples that will need to connect before they can do anything useful.
 * This is a light weight POJO that should be very easy to reuse later.
 * <p>
 * Samples that need a connection open before they can do anything useful extend ConnectedVimServiceBase so that the
 * code in those samples can focus on demonstrating the feature at hand. The logic of most samples will not be
 * changed by the use of the BasicConnection or the SsoConnection.
 * </p>
 *
 */
public class BasicConnection implements Connection {
    public static final String SERVICE_INSTANCE = "ServiceInstance";
    private static final String PBM_SERVICE_INSTANCE_TYPE = "PbmServiceInstance";
    private static final String REQUEST_TIMEOUT = "com.sun.xml.internal.ws.request.timeout";
    private static VimService VIM_SERVICE;
    private VimPortType vimPort;
    private PbmService pbmService;
    private PbmPortType pbmPort;
    private ServiceContent serviceContent;
    private PbmServiceInstanceContent pbmServiceContent;
    private UserSession userSession;
    private ManagedObjectReference svcInstRef;
    private ManagedObjectReference pbmSvcInstRef;

    private boolean ignoreSslErrors;

    private URL spbmurl;
    private URI uri;
    private String username;
    private String password = ""; // default password is empty since on rare occasion passwords are not set
    private String token;
    private Map<String, List<String>> headers;
    private long requestTimeoutMillis = -1;
    private TrustManager trustManager;

    public void setURI(URI uri) {
        this.uri = uri;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public VimService getVimService() {
        synchronized (BasicConnection.class) {
            if (VIM_SERVICE == null) {
                VIM_SERVICE = new VimService();
            }
        }
        return this.VIM_SERVICE;
    }

    @Override
    public VimPortType getVimPort() {
        return this.vimPort;
    }

    @Override
    public PbmService getPbmService() {
        return this.pbmService;
    }

    @Override
    public PbmPortType getPbmPort() {
        return this.pbmPort;
    }

    @Override
    public PbmServiceInstanceContent getPbmServiceInstanceContent() {
        return this.pbmServiceContent;
    }

    @Override
    public ManagedObjectReference getPbmServiceInstanceReference() {
        if (this.pbmSvcInstRef == null) {
            ManagedObjectReference ref = new ManagedObjectReference();
            ref.setType(PBM_SERVICE_INSTANCE_TYPE);
            ref.setValue(this.getServiceInstanceName());
            this.pbmSvcInstRef = ref;
        }
        return this.pbmSvcInstRef;
    }

    @Override
    public ServiceContent getServiceContent() {
        return this.serviceContent;
    }

    @Override
    public UserSession getUserSession() {
        return this.userSession;
    }

    @Override
    public String getServiceInstanceName() {
        return SERVICE_INSTANCE;
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return this.headers;
    }

    @Override
    public ManagedObjectReference getServiceInstanceReference() {
        if (this.svcInstRef == null) {
            ManagedObjectReference ref = new ManagedObjectReference();
            ref.setType(this.getServiceInstanceName());
            ref.setValue(this.getServiceInstanceName());
            this.svcInstRef = ref;
        }
        return this.svcInstRef;
    }

    @Override
    public URL getSpbmURL() {
        if (this.spbmurl == null) {
            try {
                this.spbmurl =
                        new URL(getURI().toString().replace("/sdk", "/pbm"));
            } catch (MalformedURLException e) {
                throw new BasicConnectionException(
                        "malformed URL argument: '" + this.spbmurl + "'", e);
            }
        }
        return this.spbmurl;
    }

    public String getGenericServiceTicket(String url) {

        SessionManagerHttpServiceRequestSpec spec = new SessionManagerHttpServiceRequestSpec();
        spec.setMethod("httpPut");
        spec.setUrl(url);
        String ticket = null;
        try {
            ticket = this.vimPort.acquireGenericServiceTicket(this.getServiceContent()
                    .getSessionManager(), spec).getId();
        } catch (Exception ex) {
            Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
            throw new BasicConnectionException(
                    "failed to fetch GenericServiceTicket: " + ex.getMessage() + " : " + cause.getMessage(), cause);
        }
        return ticket;
    }

    public void connect() {
        try {
            _connect();
        } catch (Exception e) {
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            throw new BasicConnectionException(
                    "failed to connect: " + e.getMessage() + " : " + cause.getMessage(), cause);
        }
    }

    @SuppressWarnings("unchecked")
    private void _connect()
            throws RuntimeFaultFaultMsg, InvalidLocaleFaultMsg, InvalidLoginFaultMsg,
            com.vmware.pbm.RuntimeFaultFaultMsg {

        this.vimPort = getVimService().getVimPort();
        updateBindingProvider(getBindingsProvider(), this.uri.toString());
        this.serviceContent = this.vimPort
                .retrieveServiceContent(this.getServiceInstanceReference());
        if (this.token != null) {
            HandlerResolver defaultResolver = getVimService().getHandlerResolver();
            HeaderHandlerResolver handlerResolver = new HeaderHandlerResolver();
            handlerResolver.addHandler(new TimeStampHandler());
            handlerResolver.addHandler(new SamlTokenExtractionHandler());

            try {
                handlerResolver.addHandler(new SamlTokenHandler(SamlUtils.createSamlDocument
                        (this.token).getDocumentElement()));
            } catch (ParserConfigurationException | SAXException | IOException e) {
                throw new RuntimeFaultFaultMsg("Unable to authenticate", e);
            }

            try {
                getVimService().setHandlerResolver(handlerResolver);
                this.userSession = this.vimPort
                        .loginByToken(this.serviceContent.getSessionManager(),
                                null);
            } finally {
                getVimService().setHandlerResolver(defaultResolver);
            }
        } else {
            this.userSession = this.vimPort.login(
                    this.serviceContent.getSessionManager(),
                    this.username,
                    this.password,
                    null);
        }

        this.headers = (Map<String, List<String>>) getBindingsProvider()
                .getResponseContext().get(MessageContext.HTTP_RESPONSE_HEADERS);

        // Need to extract only the cookie value
        List<String> cookieHeaders = this.headers
                .getOrDefault(HttpHeaderNames.SET_COOKIE.toString(), Collections.EMPTY_LIST);
        if (cookieHeaders.isEmpty()) {
            throw new RuntimeFaultFaultMsg("Failure in connecting to server, no session cookie found");
        }
        String cookieVal = cookieHeaders.get(0);
        String[] tokens = cookieVal.split(";");
        tokens = tokens[0].split("=");
        String extractedCookie = tokens[1];

        // PbmPortType
        this.pbmService = new PbmService();
        // Setting the header resolver for adding the VC session cookie to the
        // requests for authentication
        HeaderHandlerResolver headerResolver = new HeaderHandlerResolver();
        headerResolver.addHandler(new VcSessionHandler(extractedCookie));
        this.pbmService.setHandlerResolver(headerResolver);

        this.pbmPort = this.pbmService.getPbmPort();
        updateBindingProvider((BindingProvider) this.pbmPort,
                this.getSpbmURL().toString());

        this.pbmServiceContent = this.pbmPort
                .pbmRetrieveServiceContent(this.getPbmServiceInstanceReference());
    }

    private void updateRequestTimeout() {
        if (this.requestTimeoutMillis > 0 && getBindingsProvider() != null) {
            getBindingsProvider().getRequestContext()
                    .put(REQUEST_TIMEOUT, (int) this.requestTimeoutMillis);
        }
    }

    private BindingProvider getBindingsProvider() {
        return (BindingProvider) this.vimPort;
    }

    private void updateBindingProvider(BindingProvider bindingProvider, String uri) {
        Map<String, Object> requestContext = bindingProvider.getRequestContext();

        requestContext.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, uri);
        requestContext.put(BindingProvider.SESSION_MAINTAIN_PROPERTY, true);

        updateRequestTimeout();

        if (this.ignoreSslErrors) {
            IgnoreSslErrors.ignoreErrors(bindingProvider);
        }

        if (this.trustManager != null) {
            IgnoreSslErrors.useTrustManager(bindingProvider, this.trustManager);
        }
    }

    @Override
    public void close() {
        if (this.userSession == null) {
            return;
        }

        try {
            this.vimPort.logout(this.serviceContent.getSessionManager());
        } catch (Exception e) {
            Throwable cause = e.getCause();
            throw new BasicConnectionException(
                    "failed to close properly: " + e.getMessage() + " : " + cause
                            .getMessage(), cause);
        } finally {
            this.userSession = null;
            this.serviceContent = null;
            this.vimPort = null;
            this.pbmPort = null;
            this.pbmService = null;
        }
    }

    @Override
    public void closeQuietly() {
        try {
            close();
        } catch (Exception ignore) {

        }
    }

    @Override
    public URI getURI() {
        return this.uri;
    }

    public boolean isIgnoreSslErrors() {
        return this.ignoreSslErrors;
    }

    public void setIgnoreSslErrors(boolean ignoreSslErrors) {
        this.ignoreSslErrors = ignoreSslErrors;
    }

    @Override
    public void setRequestTimeout(long time, TimeUnit unit) {
        this.requestTimeoutMillis = TimeUnit.MILLISECONDS.convert(time, unit);

        updateRequestTimeout();
    }

    @Override
    public long getRequestTimeout(TimeUnit unit) {
        return this.requestTimeoutMillis;
    }

    public void setTrustManager(TrustManager trustManager) {
        this.trustManager = trustManager;
    }

    public TrustManager getTrustManager() {
        return this.trustManager;
    }

    public static class BasicConnectionException extends ConnectionException {
        private static final long serialVersionUID = 1L;

        public BasicConnectionException(String s, Throwable t) {
            super(s, t);
        }
    }

    @Override
    public Connection createUnmanagedCopy() {
        BasicConnection res = new BasicConnection();
        res.setURI(this.getURI());
        res.setPassword(this.getPassword());
        res.setIgnoreSslErrors(this.ignoreSslErrors);
        res.setTrustManager(this.trustManager);
        res.setUsername(this.getUsername());
        res.setRequestTimeout(this.getRequestTimeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
        res.connect();
        return res;
    }
}
