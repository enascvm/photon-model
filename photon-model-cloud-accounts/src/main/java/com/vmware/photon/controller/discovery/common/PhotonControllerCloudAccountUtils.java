/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.common;

import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.SEPARATOR;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.ProjectService;
import com.vmware.photon.controller.discovery.common.utils.ErrorUtil;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupUtils;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.UserService;

public class PhotonControllerCloudAccountUtils {

    private static final String SHA_256 = "SHA-256";

    /**
     * Get orgLink from orgId
     */
    public static String getOrgLinkFromId(String orgId) {
        return UriUtils.buildUriPath(OrganizationService.FACTORY_LINK, Utils.computeHash(orgId));
    }

    /**
     * Returns the automation user link.
     */
    public static String getAutomationUserLink() {
        return UriUtils.buildUriPath(UserService.FACTORY_LINK,
                computeHashWithSHA256(CloudAccountConstants.AUTOMATION_USER_EMAIL));
    }

    /**
     * Gets the current service user password.
     */
    public static String getServiceUserPassword() {
        return (System.getProperty(CloudAccountConstants.SERVICE_USER_PASSWORD_PROPERTY) != null)
                ? System.getProperty(CloudAccountConstants.SERVICE_USER_PASSWORD_PROPERTY)
                : CloudAccountConstants.SERVICE_USER_DEFAULT_PASSWORD;
    }

    /**
     * Invoke sendRequest if the service does not exist, else just invoke the completion handler
     */
    public static void checkIfExists(Service service, String serviceLink, Consumer<Operation> ifExists,
            Consumer<Operation> ifNotExists, boolean isSuperUser) {
        Operation getOp = Operation.createGet(UriUtils.buildUri(service.getHost(), serviceLink))
                .setReferer(service.getHost().getUri())
                .setCompletion((resultGetOp, resultGetEx) -> {
                    if (resultGetOp.getStatusCode() == Operation.STATUS_CODE_OK) {
                        ifExists.accept(resultGetOp);
                    } else {
                        ifNotExists.accept(resultGetOp);
                    }
                });
        if (isSuperUser) {
            service.setAuthorizationContext(getOp, service.getSystemAuthorizationContext());
        }
        service.sendRequest(getOp);
    }

    /**
     * Compute the SHA-256 hash of a string
     *
     * @param originalString The string to hash
     * @return The SHA-256 hash of the original string
     */
    public static String computeHashWithSHA256(String originalString) {
        byte[] encodedhash = null;
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            encodedhash = digest.digest(
                    originalString.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error computing hash: ", e);
        }

        return bytesToHex(encodedhash);
    }

    /**
     * Utility method to convert a byte[] array to a Hex string.
     *
     * @param hash The hashed byte array
     * @return A Hex representation of the byte array
     */
    private static String bytesToHex(byte[] hash) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Fails an {@code Operation}. The http status code of the request is set to
     * the passed-in status code. The response body is set to an object
     * of type {@link ServiceErrorResponse} created based on passed message args.
     */
    public static void failOperation(Operation op, int statusCode, String msgFormat, Object... args) {
        failOperation(op, ErrorUtil.create(statusCode, msgFormat, args));
    }

    /**
     * Fails the request with status code 405 and a message mentioning that the action
     * is not supported
     */
    public static void failOperation(Operation op, ServiceErrorResponse rsp) {
        op.fail(rsp.statusCode, new IllegalStateException(rsp.message), rsp);
    }

    /**
     * Utility method to check if the specified set of factories are available
     * @param host host context to invoke this method on
     * @param op Operation to pass back on completion
     * @param factories factories to check
     * @param onSuccess Consumer class to invoke on success
     */
    public static void checkFactoryAvailability(ServiceHost host, Operation op, Set<URI> factories,
            Consumer<Operation> onSuccess) {
        AtomicInteger successfulOps = new AtomicInteger(0);
        AtomicInteger totalOps = new AtomicInteger(0);
        int expectedOps = factories.size();
        CompletionHandler handler = (waitOp, waitEx) -> {
            if (waitEx == null) {
                factories.remove(waitOp.getUri());
                successfulOps.incrementAndGet();
            }
            if (totalOps.incrementAndGet() == expectedOps) {
                if (successfulOps.get() == expectedOps) {
                    onSuccess.accept(op);
                } else {
                    host.schedule(() -> checkFactoryAvailability(host, op, factories, onSuccess),
                            host.getMaintenanceIntervalMicros(), TimeUnit.MICROSECONDS);
                }
            }
        };
        for (URI factory : factories) {
            NodeGroupUtils.checkServiceAvailability(handler, host,
                    factory, ServiceUriPaths.DEFAULT_NODE_SELECTOR);
        }
    }

    /**
     * Returns orgId based on projectLink or orgLink.
     */
    public static String getOrgId(String link) {
        String linkLastPath = UriUtils.getLastPathSegment(link);
        if (link.startsWith(OrganizationService.FACTORY_LINK)) {
            return linkLastPath;
        } else if (link.startsWith(ProjectService.FACTORY_LINK)) {
            String[] tokens = linkLastPath.split(SEPARATOR);
            if (tokens.length > 1) {
                return tokens[0];
            }
        }
        return null;
    }

    /**
     * Returns org ID based on a list of tenantLinks.
     */
    public static String getOrgId(Collection<String> tenantLinks) {
        for (String link : tenantLinks) {
            String orgId = getOrgId(link);
            if (orgId != null) {
                return orgId;
            }
        }
        return null;
    }

    /**
     * Returns orgId based on endpoint state.
     */
    public static String getOrgId(EndpointState endpointState) {
        if (endpointState.tenantLinks != null && !endpointState.tenantLinks.isEmpty()) {
            return getOrgId(endpointState.tenantLinks);
        }
        return null;
    }
}