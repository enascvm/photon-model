
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DatastoreAccessible.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="DatastoreAccessible"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="True"/&gt;
 *     &lt;enumeration value="False"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "DatastoreAccessible")
@XmlEnum
public enum DatastoreAccessible {

    @XmlEnumValue("True")
    TRUE("True"),
    @XmlEnumValue("False")
    FALSE("False");
    private final String value;

    DatastoreAccessible(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static DatastoreAccessible fromValue(String v) {
        for (DatastoreAccessible c: DatastoreAccessible.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
