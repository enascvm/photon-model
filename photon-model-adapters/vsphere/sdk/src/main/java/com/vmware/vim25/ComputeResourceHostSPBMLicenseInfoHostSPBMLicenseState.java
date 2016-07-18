
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ComputeResourceHostSPBMLicenseInfoHostSPBMLicenseState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ComputeResourceHostSPBMLicenseInfoHostSPBMLicenseState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="licensed"/&gt;
 *     &lt;enumeration value="unlicensed"/&gt;
 *     &lt;enumeration value="unknown"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ComputeResourceHostSPBMLicenseInfoHostSPBMLicenseState")
@XmlEnum
public enum ComputeResourceHostSPBMLicenseInfoHostSPBMLicenseState {

    @XmlEnumValue("licensed")
    LICENSED("licensed"),
    @XmlEnumValue("unlicensed")
    UNLICENSED("unlicensed"),
    @XmlEnumValue("unknown")
    UNKNOWN("unknown");
    private final String value;

    ComputeResourceHostSPBMLicenseInfoHostSPBMLicenseState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ComputeResourceHostSPBMLicenseInfoHostSPBMLicenseState fromValue(String v) {
        for (ComputeResourceHostSPBMLicenseInfoHostSPBMLicenseState c: ComputeResourceHostSPBMLicenseInfoHostSPBMLicenseState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
