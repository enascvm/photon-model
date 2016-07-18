
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VmFailedStartingSecondaryEventFailureReason.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VmFailedStartingSecondaryEventFailureReason"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="incompatibleHost"/&gt;
 *     &lt;enumeration value="loginFailed"/&gt;
 *     &lt;enumeration value="registerVmFailed"/&gt;
 *     &lt;enumeration value="migrateFailed"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VmFailedStartingSecondaryEventFailureReason")
@XmlEnum
public enum VmFailedStartingSecondaryEventFailureReason {

    @XmlEnumValue("incompatibleHost")
    INCOMPATIBLE_HOST("incompatibleHost"),
    @XmlEnumValue("loginFailed")
    LOGIN_FAILED("loginFailed"),
    @XmlEnumValue("registerVmFailed")
    REGISTER_VM_FAILED("registerVmFailed"),
    @XmlEnumValue("migrateFailed")
    MIGRATE_FAILED("migrateFailed");
    private final String value;

    VmFailedStartingSecondaryEventFailureReason(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VmFailedStartingSecondaryEventFailureReason fromValue(String v) {
        for (VmFailedStartingSecondaryEventFailureReason c: VmFailedStartingSecondaryEventFailureReason.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
