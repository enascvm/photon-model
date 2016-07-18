
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CannotEnableVmcpForClusterReason.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="CannotEnableVmcpForClusterReason"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="APDTimeoutDisabled"/&gt;
 *     &lt;enumeration value="IncompatibleHostVersion"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "CannotEnableVmcpForClusterReason")
@XmlEnum
public enum CannotEnableVmcpForClusterReason {

    @XmlEnumValue("APDTimeoutDisabled")
    APD_TIMEOUT_DISABLED("APDTimeoutDisabled"),
    @XmlEnumValue("IncompatibleHostVersion")
    INCOMPATIBLE_HOST_VERSION("IncompatibleHostVersion");
    private final String value;

    CannotEnableVmcpForClusterReason(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static CannotEnableVmcpForClusterReason fromValue(String v) {
        for (CannotEnableVmcpForClusterReason c: CannotEnableVmcpForClusterReason.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
