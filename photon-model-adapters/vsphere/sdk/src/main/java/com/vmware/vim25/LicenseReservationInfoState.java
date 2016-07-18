
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for LicenseReservationInfoState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="LicenseReservationInfoState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="notUsed"/&gt;
 *     &lt;enumeration value="noLicense"/&gt;
 *     &lt;enumeration value="unlicensedUse"/&gt;
 *     &lt;enumeration value="licensed"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "LicenseReservationInfoState")
@XmlEnum
public enum LicenseReservationInfoState {

    @XmlEnumValue("notUsed")
    NOT_USED("notUsed"),
    @XmlEnumValue("noLicense")
    NO_LICENSE("noLicense"),
    @XmlEnumValue("unlicensedUse")
    UNLICENSED_USE("unlicensedUse"),
    @XmlEnumValue("licensed")
    LICENSED("licensed");
    private final String value;

    LicenseReservationInfoState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static LicenseReservationInfoState fromValue(String v) {
        for (LicenseReservationInfoState c: LicenseReservationInfoState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
