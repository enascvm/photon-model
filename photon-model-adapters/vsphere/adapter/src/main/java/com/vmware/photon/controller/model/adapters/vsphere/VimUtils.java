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

package com.vmware.photon.controller.model.adapters.vsphere;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.GregorianCalendar;
import java.util.Objects;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.codec.binary.Hex;

import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimPath;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.WaitForValues;
import com.vmware.vim25.InvalidCollectorVersionFaultMsg;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.LocalizedMethodFault;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.MethodFault;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.xenon.common.Utils;

/**
 */
public final class VimUtils {

    private static final String SCHEME_VSPHERE = "vc";

    private static final String DELIMITER = ":";
    public static final String EXCEPTION_SUFFIX = "FaultMsg";
    private static final byte[] HASH_DELIMITER = new byte[] { ':' };

    private VimUtils() {

    }

    /**
     * This method never returns but throws an Exception wrapping the fault.
     *
     * @param lmf
     * @param <T>
     * @return
     * @throws Exception
     */
    public static <T> T rethrow(LocalizedMethodFault lmf) throws Exception {
        Class<?> type = lmf.getFault().getClass();
        String possibleWrapperType = type.getName() + EXCEPTION_SUFFIX;

        Exception ex;
        try {
            ClassLoader cl = type.getClassLoader();
            Class<?> faultClass = cl.loadClass(possibleWrapperType);
            Constructor<?> ctor = faultClass.getConstructor(String.class, type);
            ex = (Exception) ctor.newInstance(lmf.getLocalizedMessage(), lmf.getFault());
        } catch (ReflectiveOperationException e) {
            throw new GenericVimFault(lmf.getLocalizedMessage(), lmf.getFault());
        }

        throw ex;
    }

    /**
     * Converts an URI in the format file://datastoreName/path/to/file to a string like
     * "[datastoreName] /path/to/file".
     *
     * @param uri
     * @return
     */
    public static String uriToDatastorePath(URI uri) {
        if (uri == null) {
            return null;
        }

        if (!SCHEME_VSPHERE.equals(uri.getScheme())) {
            throw new IllegalArgumentException("Expected vc:// scheme, found " + uri);
        }

        String path = uri.getSchemeSpecificPart();
        path = stripLeadingSlashes(path);
        int i;

        // strip the /datastore/ prefix
        i = path.indexOf('/');
        if (i <= 0) {
            throw new IllegalArgumentException("Path to datastore not found:" + uri);
        }

        path = path.substring(i + 1);
        i = path.indexOf('/');
        if (i <= 0) {
            throw new IllegalArgumentException("Path to datastore not found:" + uri);
        }


        String ds = path.substring(0, i);
        path = path.substring(i + 1);

        return String.format("[%s] %s", decode(ds), decode(path));
    }

    protected static String stripLeadingSlashes(String path) {
        // strip leading slashes
        int i = 0;
        while (i < path.length() && path.charAt(i) == '/') {
            i++;
        }
        path = path.substring(i);
        return path;
    }

    /**
     * Convert a string [dsName] /path/to/file into a URI datastore://dsName/path/to/file.
     *
     * @param path
     * @return
     */
    public static URI datastorePathToUri(String path) {
        if (path == null) {
            return null;
        }

        int i = path.indexOf("] ");
        if (i <= 0) {
            throw new IllegalArgumentException("Invalid datastore path " + path);
        }
        String dsName = path.substring(1, i);
        String pathOnly = path.substring(i + 2);
        return URI.create(SCHEME_VSPHERE + "://datastore/" + encode(dsName) + "/" + encode(stripLeadingSlashes(pathOnly)));
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, Utils.CHARSET).replace("%2F", "/");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, Utils.CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Serializes a MoRef into a String.
     *
     * @param ref
     * @return
     */
    public static String convertMoRefToString(ManagedObjectReference ref) {
        if (ref == null) {
            return null;
        }

        return ref.getType() + DELIMITER + ref.getValue();
    }

    /**
     * Return the first non-null value or null if all values are null.
     * @param values
     * @return
     */
    @SafeVarargs
    public static <T> T firstNonNull(T... values) {
        for (T s : values) {
            if (s != null) {
                return s;
            }
        }

        return null;
    }

    /**
     * Builds a MoRef from a string produced bu {@link #convertMoRefToString(ManagedObjectReference)}
     * @param s
     * @return
     */
    public static ManagedObjectReference convertStringToMoRef(String s) {
        if (s == null) {
            return null;
        }

        String[] parts = s.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Cannot convert string '" + s
                            + "' to ManagedObjectReference: expected Type:Value format");
        }

        if (parts[0].length() == 0) {
            throw new IllegalArgumentException("Missing Type in '" + s + "'");
        }

        if (parts[1].length() == 0) {
            throw new IllegalArgumentException("Missing Value in '" + s + "'");
        }
        ManagedObjectReference ref = new ManagedObjectReference();
        ref.setType(parts[0]);
        ref.setValue(parts[1]);
        return ref;
    }

    public static TaskInfo waitTaskEnd(Connection connection, ManagedObjectReference task)
            throws InvalidCollectorVersionFaultMsg, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        WaitForValues waitForValues = new WaitForValues(connection);

        Object[] info = waitForValues.wait(task,
                new String[] { VimPath.task_info },
                new String[] { VimPath.task_info_state },
                new Object[][] { new Object[] {
                        TaskInfoState.SUCCESS,
                        TaskInfoState.ERROR
                } });

        return (TaskInfo) info[0];
    }

    public static boolean isVirtualMachine(ManagedObjectReference obj) {
        if (obj == null) {
            return false;
        }

        return VimNames.TYPE_VM.equals(obj.getType());
    }

    public static boolean isResourcePool(ManagedObjectReference obj) {
        if (obj == null) {
            return false;
        }

        return VimNames.TYPE_RESOURCE_POOL.equals(obj.getType());
    }

    public static boolean isHost(ManagedObjectReference obj) {
        if (obj == null) {
            return false;
        }

        return VimNames.TYPE_HOST.equals(obj.getType());
    }

    public static XMLGregorianCalendar convertMillisToXmlCalendar(long timeMillis) {
        try {
            GregorianCalendar cal = new GregorianCalendar();
            cal.setTimeInMillis(timeMillis);
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(cal);
        } catch (DatatypeConfigurationException e) {
            throw new AssertionError(e);
        }
    }

    public static boolean isComputeResource(ManagedObjectReference obj) {
        if (obj == null) {
            return false;
        }

        return VimNames.TYPE_COMPUTE_RESOURCE.equals(obj.getType()) ||
                VimNames.TYPE_CLUSTER_COMPUTE_RESOURCE.equals(obj.getType());
    }

    public static boolean isClusterComputeResource(ManagedObjectReference obj) {
        if (obj == null) {
            return false;
        }

        return VimNames.TYPE_CLUSTER_COMPUTE_RESOURCE.equals(obj.getType());
    }

    public static boolean isNetwork(ManagedObjectReference obj) {
        if (obj == null) {
            return false;
        }

        return VimNames.TYPE_NETWORK.equals(obj.getType()) ||
                VimNames.TYPE_OPAQUE_NETWORK.equals(obj.getType()) ||
                VimNames.TYPE_DVS.equals(obj.getType()) ||
                VimNames.TYPE_PORTGROUP.equals(obj.getType());
    }

    public static boolean isDatastore(ManagedObjectReference obj) {
        if (obj == null) {
            return false;
        }

        return VimNames.TYPE_DATASTORE.equals(obj.getType());
    }

    /**
     * Converts arbitrary exception to a fault. If the exception has a fault it is returned
     * without modification.
     *
     * @param e
     * @return
     */
    public static LocalizedMethodFault convertExceptionToFault(Exception e) {
        LocalizedMethodFault lmf = new LocalizedMethodFault();

        try {
            Method m = e.getClass().getMethod("getFaultInfo");
            MethodFault mf = (MethodFault) m.invoke(e);
            lmf.setFault(mf);
            lmf.setLocalizedMessage(e.getLocalizedMessage());
            return lmf;
        } catch (NoSuchMethodException e1) {
            lmf.setLocalizedMessage(e.getMessage());
            lmf.setLocalizedMessage(e.getLocalizedMessage());
            return lmf;
        } catch (InvocationTargetException | IllegalAccessException e1) {
            lmf.setLocalizedMessage(e.getMessage());
            return lmf;
        }
    }

    public static boolean equals(ManagedObjectReference a, ManagedObjectReference b) {
        if (a == b) {
            return true;
        }

        if (a == null || b == null) {
            return false;
        }

        return Objects.equals(a.getType(), b.getType()) &&
                Objects.equals(a.getValue(), b.getValue());
    }

    /**
     * Builds a stable link from a managed object and a set of string that differentiate
     * @param ref
     * @param markers
     * @return
     */
    public static String buildStableManagedObjectId(ManagedObjectReference ref, String... markers) {
        return buildStableId(VimUtils.convertMoRefToString(ref), markers);
    }

    /**
     * Builds a stable link from a managed object and a set of string that differentiate
     * @param first
     * @param rest
     * @return
     */
    public static String buildStableId(String first, String... rest) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        if (first != null) {
            try {
                md.update(first.getBytes(Utils.CHARSET));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        md.update(HASH_DELIMITER);

        if (rest != null) {
            for (String s : rest) {
                if (s != null) {
                    try {
                        md.update(s.getBytes(Utils.CHARSET));
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }
                }
                md.update(HASH_DELIMITER);
            }
        }

        byte[] digest = md.digest();
        return Hex.encodeHexString(digest);
    }
}


