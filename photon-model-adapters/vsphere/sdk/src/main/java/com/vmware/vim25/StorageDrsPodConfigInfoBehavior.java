
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for StorageDrsPodConfigInfoBehavior.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="StorageDrsPodConfigInfoBehavior"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="manual"/&gt;
 *     &lt;enumeration value="automated"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "StorageDrsPodConfigInfoBehavior")
@XmlEnum
public enum StorageDrsPodConfigInfoBehavior {

    @XmlEnumValue("manual")
    MANUAL("manual"),
    @XmlEnumValue("automated")
    AUTOMATED("automated");
    private final String value;

    StorageDrsPodConfigInfoBehavior(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static StorageDrsPodConfigInfoBehavior fromValue(String v) {
        for (StorageDrsPodConfigInfoBehavior c: StorageDrsPodConfigInfoBehavior.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
