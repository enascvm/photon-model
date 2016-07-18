
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostPatchManagerIntegrityStatus.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostPatchManagerIntegrityStatus"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="validated"/&gt;
 *     &lt;enumeration value="keyNotFound"/&gt;
 *     &lt;enumeration value="keyRevoked"/&gt;
 *     &lt;enumeration value="keyExpired"/&gt;
 *     &lt;enumeration value="digestMismatch"/&gt;
 *     &lt;enumeration value="notEnoughSignatures"/&gt;
 *     &lt;enumeration value="validationError"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostPatchManagerIntegrityStatus")
@XmlEnum
public enum HostPatchManagerIntegrityStatus {

    @XmlEnumValue("validated")
    VALIDATED("validated"),
    @XmlEnumValue("keyNotFound")
    KEY_NOT_FOUND("keyNotFound"),
    @XmlEnumValue("keyRevoked")
    KEY_REVOKED("keyRevoked"),
    @XmlEnumValue("keyExpired")
    KEY_EXPIRED("keyExpired"),
    @XmlEnumValue("digestMismatch")
    DIGEST_MISMATCH("digestMismatch"),
    @XmlEnumValue("notEnoughSignatures")
    NOT_ENOUGH_SIGNATURES("notEnoughSignatures"),
    @XmlEnumValue("validationError")
    VALIDATION_ERROR("validationError");
    private final String value;

    HostPatchManagerIntegrityStatus(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostPatchManagerIntegrityStatus fromValue(String v) {
        for (HostPatchManagerIntegrityStatus c: HostPatchManagerIntegrityStatus.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
