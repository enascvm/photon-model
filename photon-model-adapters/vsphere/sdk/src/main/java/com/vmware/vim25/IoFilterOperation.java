
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for IoFilterOperation.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="IoFilterOperation"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="install"/&gt;
 *     &lt;enumeration value="uninstall"/&gt;
 *     &lt;enumeration value="upgrade"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "IoFilterOperation")
@XmlEnum
public enum IoFilterOperation {

    @XmlEnumValue("install")
    INSTALL("install"),
    @XmlEnumValue("uninstall")
    UNINSTALL("uninstall"),
    @XmlEnumValue("upgrade")
    UPGRADE("upgrade");
    private final String value;

    IoFilterOperation(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static IoFilterOperation fromValue(String v) {
        for (IoFilterOperation c: IoFilterOperation.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
