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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.utils.ErrorUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class PhotonControllerCloudAccountUtils {

    private static final String SHA_256 = "SHA-256";

    /**
     * Get orgLink from orgId
     */
    public static String getOrgLinkFromId(String orgId) {
        return UriUtils.buildUriPath(OrganizationService.FACTORY_LINK, Utils.computeHash(orgId));
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

}
