
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PlacementAffinityRuleRuleScope.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PlacementAffinityRuleRuleScope"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="cluster"/&gt;
 *     &lt;enumeration value="host"/&gt;
 *     &lt;enumeration value="storagePod"/&gt;
 *     &lt;enumeration value="datastore"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PlacementAffinityRuleRuleScope")
@XmlEnum
public enum PlacementAffinityRuleRuleScope {

    @XmlEnumValue("cluster")
    CLUSTER("cluster"),
    @XmlEnumValue("host")
    HOST("host"),
    @XmlEnumValue("storagePod")
    STORAGE_POD("storagePod"),
    @XmlEnumValue("datastore")
    DATASTORE("datastore");
    private final String value;

    PlacementAffinityRuleRuleScope(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static PlacementAffinityRuleRuleScope fromValue(String v) {
        for (PlacementAffinityRuleRuleScope c: PlacementAffinityRuleRuleScope.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
