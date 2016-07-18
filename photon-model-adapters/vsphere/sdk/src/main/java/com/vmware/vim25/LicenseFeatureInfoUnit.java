
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for LicenseFeatureInfoUnit.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="LicenseFeatureInfoUnit"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="host"/&gt;
 *     &lt;enumeration value="cpuCore"/&gt;
 *     &lt;enumeration value="cpuPackage"/&gt;
 *     &lt;enumeration value="server"/&gt;
 *     &lt;enumeration value="vm"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "LicenseFeatureInfoUnit")
@XmlEnum
public enum LicenseFeatureInfoUnit {

    @XmlEnumValue("host")
    HOST("host"),
    @XmlEnumValue("cpuCore")
    CPU_CORE("cpuCore"),
    @XmlEnumValue("cpuPackage")
    CPU_PACKAGE("cpuPackage"),
    @XmlEnumValue("server")
    SERVER("server"),
    @XmlEnumValue("vm")
    VM("vm");
    private final String value;

    LicenseFeatureInfoUnit(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static LicenseFeatureInfoUnit fromValue(String v) {
        for (LicenseFeatureInfoUnit c: LicenseFeatureInfoUnit.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
