
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for NumVirtualCpusIncompatibleReason.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="NumVirtualCpusIncompatibleReason"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="recordReplay"/&gt;
 *     &lt;enumeration value="faultTolerance"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "NumVirtualCpusIncompatibleReason")
@XmlEnum
public enum NumVirtualCpusIncompatibleReason {

    @XmlEnumValue("recordReplay")
    RECORD_REPLAY("recordReplay"),
    @XmlEnumValue("faultTolerance")
    FAULT_TOLERANCE("faultTolerance");
    private final String value;

    NumVirtualCpusIncompatibleReason(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static NumVirtualCpusIncompatibleReason fromValue(String v) {
        for (NumVirtualCpusIncompatibleReason c: NumVirtualCpusIncompatibleReason.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
