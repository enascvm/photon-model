
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for GuestFileType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="GuestFileType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="file"/&gt;
 *     &lt;enumeration value="directory"/&gt;
 *     &lt;enumeration value="symlink"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "GuestFileType")
@XmlEnum
public enum GuestFileType {

    @XmlEnumValue("file")
    FILE("file"),
    @XmlEnumValue("directory")
    DIRECTORY("directory"),
    @XmlEnumValue("symlink")
    SYMLINK("symlink");
    private final String value;

    GuestFileType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static GuestFileType fromValue(String v) {
        for (GuestFileType c: GuestFileType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
