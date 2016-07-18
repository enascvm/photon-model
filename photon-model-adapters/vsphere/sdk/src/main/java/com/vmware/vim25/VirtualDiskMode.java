
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDiskMode.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualDiskMode"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="persistent"/&gt;
 *     &lt;enumeration value="nonpersistent"/&gt;
 *     &lt;enumeration value="undoable"/&gt;
 *     &lt;enumeration value="independent_persistent"/&gt;
 *     &lt;enumeration value="independent_nonpersistent"/&gt;
 *     &lt;enumeration value="append"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualDiskMode")
@XmlEnum
public enum VirtualDiskMode {

    @XmlEnumValue("persistent")
    PERSISTENT("persistent"),
    @XmlEnumValue("nonpersistent")
    NONPERSISTENT("nonpersistent"),
    @XmlEnumValue("undoable")
    UNDOABLE("undoable"),
    @XmlEnumValue("independent_persistent")
    INDEPENDENT_PERSISTENT("independent_persistent"),
    @XmlEnumValue("independent_nonpersistent")
    INDEPENDENT_NONPERSISTENT("independent_nonpersistent"),
    @XmlEnumValue("append")
    APPEND("append");
    private final String value;

    VirtualDiskMode(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualDiskMode fromValue(String v) {
        for (VirtualDiskMode c: VirtualDiskMode.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
