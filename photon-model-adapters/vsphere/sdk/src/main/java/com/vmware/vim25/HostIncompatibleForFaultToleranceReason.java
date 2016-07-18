
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostIncompatibleForFaultToleranceReason.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostIncompatibleForFaultToleranceReason"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="product"/&gt;
 *     &lt;enumeration value="processor"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostIncompatibleForFaultToleranceReason")
@XmlEnum
public enum HostIncompatibleForFaultToleranceReason {

    @XmlEnumValue("product")
    PRODUCT("product"),
    @XmlEnumValue("processor")
    PROCESSOR("processor");
    private final String value;

    HostIncompatibleForFaultToleranceReason(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostIncompatibleForFaultToleranceReason fromValue(String v) {
        for (HostIncompatibleForFaultToleranceReason c: HostIncompatibleForFaultToleranceReason.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
