
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterDasConfigInfoHBDatastoreCandidate.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ClusterDasConfigInfoHBDatastoreCandidate"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="userSelectedDs"/&gt;
 *     &lt;enumeration value="allFeasibleDs"/&gt;
 *     &lt;enumeration value="allFeasibleDsWithUserPreference"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ClusterDasConfigInfoHBDatastoreCandidate")
@XmlEnum
public enum ClusterDasConfigInfoHBDatastoreCandidate {

    @XmlEnumValue("userSelectedDs")
    USER_SELECTED_DS("userSelectedDs"),
    @XmlEnumValue("allFeasibleDs")
    ALL_FEASIBLE_DS("allFeasibleDs"),
    @XmlEnumValue("allFeasibleDsWithUserPreference")
    ALL_FEASIBLE_DS_WITH_USER_PREFERENCE("allFeasibleDsWithUserPreference");
    private final String value;

    ClusterDasConfigInfoHBDatastoreCandidate(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ClusterDasConfigInfoHBDatastoreCandidate fromValue(String v) {
        for (ClusterDasConfigInfoHBDatastoreCandidate c: ClusterDasConfigInfoHBDatastoreCandidate.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
