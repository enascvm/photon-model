
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DistributedVirtualSwitchHostMemberHostComponentState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="DistributedVirtualSwitchHostMemberHostComponentState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="up"/&gt;
 *     &lt;enumeration value="pending"/&gt;
 *     &lt;enumeration value="outOfSync"/&gt;
 *     &lt;enumeration value="warning"/&gt;
 *     &lt;enumeration value="disconnected"/&gt;
 *     &lt;enumeration value="down"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "DistributedVirtualSwitchHostMemberHostComponentState")
@XmlEnum
public enum DistributedVirtualSwitchHostMemberHostComponentState {

    @XmlEnumValue("up")
    UP("up"),
    @XmlEnumValue("pending")
    PENDING("pending"),
    @XmlEnumValue("outOfSync")
    OUT_OF_SYNC("outOfSync"),
    @XmlEnumValue("warning")
    WARNING("warning"),
    @XmlEnumValue("disconnected")
    DISCONNECTED("disconnected"),
    @XmlEnumValue("down")
    DOWN("down");
    private final String value;

    DistributedVirtualSwitchHostMemberHostComponentState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static DistributedVirtualSwitchHostMemberHostComponentState fromValue(String v) {
        for (DistributedVirtualSwitchHostMemberHostComponentState c: DistributedVirtualSwitchHostMemberHostComponentState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
