
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PlacementAffinityRuleRuleType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PlacementAffinityRuleRuleType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="affinity"/&gt;
 *     &lt;enumeration value="antiAffinity"/&gt;
 *     &lt;enumeration value="softAffinity"/&gt;
 *     &lt;enumeration value="softAntiAffinity"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PlacementAffinityRuleRuleType")
@XmlEnum
public enum PlacementAffinityRuleRuleType {

    @XmlEnumValue("affinity")
    AFFINITY("affinity"),
    @XmlEnumValue("antiAffinity")
    ANTI_AFFINITY("antiAffinity"),
    @XmlEnumValue("softAffinity")
    SOFT_AFFINITY("softAffinity"),
    @XmlEnumValue("softAntiAffinity")
    SOFT_ANTI_AFFINITY("softAntiAffinity");
    private final String value;

    PlacementAffinityRuleRuleType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static PlacementAffinityRuleRuleType fromValue(String v) {
        for (PlacementAffinityRuleRuleType c: PlacementAffinityRuleRuleType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
