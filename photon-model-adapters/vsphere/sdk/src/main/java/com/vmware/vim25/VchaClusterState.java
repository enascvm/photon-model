
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VchaClusterState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VchaClusterState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="healthy"/&gt;
 *     &lt;enumeration value="degraded"/&gt;
 *     &lt;enumeration value="isolated"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VchaClusterState")
@XmlEnum
public enum VchaClusterState {

    @XmlEnumValue("healthy")
    HEALTHY("healthy"),
    @XmlEnumValue("degraded")
    DEGRADED("degraded"),
    @XmlEnumValue("isolated")
    ISOLATED("isolated");
    private final String value;

    VchaClusterState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VchaClusterState fromValue(String v) {
        for (VchaClusterState c: VchaClusterState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
