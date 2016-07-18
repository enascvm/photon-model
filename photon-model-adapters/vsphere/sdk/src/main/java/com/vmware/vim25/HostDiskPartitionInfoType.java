
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostDiskPartitionInfoType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostDiskPartitionInfoType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="none"/&gt;
 *     &lt;enumeration value="vmfs"/&gt;
 *     &lt;enumeration value="linuxNative"/&gt;
 *     &lt;enumeration value="linuxSwap"/&gt;
 *     &lt;enumeration value="extended"/&gt;
 *     &lt;enumeration value="ntfs"/&gt;
 *     &lt;enumeration value="vmkDiagnostic"/&gt;
 *     &lt;enumeration value="vffs"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostDiskPartitionInfoType")
@XmlEnum
public enum HostDiskPartitionInfoType {

    @XmlEnumValue("none")
    NONE("none"),
    @XmlEnumValue("vmfs")
    VMFS("vmfs"),
    @XmlEnumValue("linuxNative")
    LINUX_NATIVE("linuxNative"),
    @XmlEnumValue("linuxSwap")
    LINUX_SWAP("linuxSwap"),
    @XmlEnumValue("extended")
    EXTENDED("extended"),
    @XmlEnumValue("ntfs")
    NTFS("ntfs"),
    @XmlEnumValue("vmkDiagnostic")
    VMK_DIAGNOSTIC("vmkDiagnostic"),
    @XmlEnumValue("vffs")
    VFFS("vffs");
    private final String value;

    HostDiskPartitionInfoType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostDiskPartitionInfoType fromValue(String v) {
        for (HostDiskPartitionInfoType c: HostDiskPartitionInfoType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
