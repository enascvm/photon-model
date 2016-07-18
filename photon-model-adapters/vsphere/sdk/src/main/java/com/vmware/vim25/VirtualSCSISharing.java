
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualSCSISharing.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualSCSISharing"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="noSharing"/&gt;
 *     &lt;enumeration value="virtualSharing"/&gt;
 *     &lt;enumeration value="physicalSharing"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualSCSISharing")
@XmlEnum
public enum VirtualSCSISharing {

    @XmlEnumValue("noSharing")
    NO_SHARING("noSharing"),
    @XmlEnumValue("virtualSharing")
    VIRTUAL_SHARING("virtualSharing"),
    @XmlEnumValue("physicalSharing")
    PHYSICAL_SHARING("physicalSharing");
    private final String value;

    VirtualSCSISharing(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualSCSISharing fromValue(String v) {
        for (VirtualSCSISharing c: VirtualSCSISharing.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
