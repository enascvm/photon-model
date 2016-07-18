
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostLowLevelProvisioningManagerFileType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostLowLevelProvisioningManagerFileType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="File"/&gt;
 *     &lt;enumeration value="VirtualDisk"/&gt;
 *     &lt;enumeration value="Directory"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostLowLevelProvisioningManagerFileType")
@XmlEnum
public enum HostLowLevelProvisioningManagerFileType {

    @XmlEnumValue("File")
    FILE("File"),
    @XmlEnumValue("VirtualDisk")
    VIRTUAL_DISK("VirtualDisk"),
    @XmlEnumValue("Directory")
    DIRECTORY("Directory");
    private final String value;

    HostLowLevelProvisioningManagerFileType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostLowLevelProvisioningManagerFileType fromValue(String v) {
        for (HostLowLevelProvisioningManagerFileType c: HostLowLevelProvisioningManagerFileType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
