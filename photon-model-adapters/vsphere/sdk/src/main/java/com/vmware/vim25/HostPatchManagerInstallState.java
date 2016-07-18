
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostPatchManagerInstallState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostPatchManagerInstallState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="hostRestarted"/&gt;
 *     &lt;enumeration value="imageActive"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostPatchManagerInstallState")
@XmlEnum
public enum HostPatchManagerInstallState {

    @XmlEnumValue("hostRestarted")
    HOST_RESTARTED("hostRestarted"),
    @XmlEnumValue("imageActive")
    IMAGE_ACTIVE("imageActive");
    private final String value;

    HostPatchManagerInstallState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostPatchManagerInstallState fromValue(String v) {
        for (HostPatchManagerInstallState c: HostPatchManagerInstallState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
