
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostProfileManagerAnswerFileStatus.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostProfileManagerAnswerFileStatus"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="valid"/&gt;
 *     &lt;enumeration value="invalid"/&gt;
 *     &lt;enumeration value="unknown"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostProfileManagerAnswerFileStatus")
@XmlEnum
public enum HostProfileManagerAnswerFileStatus {

    @XmlEnumValue("valid")
    VALID("valid"),
    @XmlEnumValue("invalid")
    INVALID("invalid"),
    @XmlEnumValue("unknown")
    UNKNOWN("unknown");
    private final String value;

    HostProfileManagerAnswerFileStatus(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostProfileManagerAnswerFileStatus fromValue(String v) {
        for (HostProfileManagerAnswerFileStatus c: HostProfileManagerAnswerFileStatus.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
