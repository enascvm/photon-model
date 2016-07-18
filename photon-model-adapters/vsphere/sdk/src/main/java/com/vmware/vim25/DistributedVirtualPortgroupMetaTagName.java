
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DistributedVirtualPortgroupMetaTagName.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="DistributedVirtualPortgroupMetaTagName"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="dvsName"/&gt;
 *     &lt;enumeration value="portgroupName"/&gt;
 *     &lt;enumeration value="portIndex"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "DistributedVirtualPortgroupMetaTagName")
@XmlEnum
public enum DistributedVirtualPortgroupMetaTagName {

    @XmlEnumValue("dvsName")
    DVS_NAME("dvsName"),
    @XmlEnumValue("portgroupName")
    PORTGROUP_NAME("portgroupName"),
    @XmlEnumValue("portIndex")
    PORT_INDEX("portIndex");
    private final String value;

    DistributedVirtualPortgroupMetaTagName(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static DistributedVirtualPortgroupMetaTagName fromValue(String v) {
        for (DistributedVirtualPortgroupMetaTagName c: DistributedVirtualPortgroupMetaTagName.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
