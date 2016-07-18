
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ProfileExecuteResultStatus.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ProfileExecuteResultStatus"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="success"/&gt;
 *     &lt;enumeration value="needInput"/&gt;
 *     &lt;enumeration value="error"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ProfileExecuteResultStatus")
@XmlEnum
public enum ProfileExecuteResultStatus {

    @XmlEnumValue("success")
    SUCCESS("success"),
    @XmlEnumValue("needInput")
    NEED_INPUT("needInput"),
    @XmlEnumValue("error")
    ERROR("error");
    private final String value;

    ProfileExecuteResultStatus(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ProfileExecuteResultStatus fromValue(String v) {
        for (ProfileExecuteResultStatus c: ProfileExecuteResultStatus.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
