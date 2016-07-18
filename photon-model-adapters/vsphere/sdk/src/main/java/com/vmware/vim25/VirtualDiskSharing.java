
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDiskSharing.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualDiskSharing"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="sharingNone"/&gt;
 *     &lt;enumeration value="sharingMultiWriter"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualDiskSharing")
@XmlEnum
public enum VirtualDiskSharing {

    @XmlEnumValue("sharingNone")
    SHARING_NONE("sharingNone"),
    @XmlEnumValue("sharingMultiWriter")
    SHARING_MULTI_WRITER("sharingMultiWriter");
    private final String value;

    VirtualDiskSharing(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualDiskSharing fromValue(String v) {
        for (VirtualDiskSharing c: VirtualDiskSharing.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
