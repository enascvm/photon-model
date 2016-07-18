
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VsanHostNodeState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VsanHostNodeState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="error"/&gt;
 *     &lt;enumeration value="disabled"/&gt;
 *     &lt;enumeration value="agent"/&gt;
 *     &lt;enumeration value="master"/&gt;
 *     &lt;enumeration value="backup"/&gt;
 *     &lt;enumeration value="starting"/&gt;
 *     &lt;enumeration value="stopping"/&gt;
 *     &lt;enumeration value="enteringMaintenanceMode"/&gt;
 *     &lt;enumeration value="exitingMaintenanceMode"/&gt;
 *     &lt;enumeration value="decommissioning"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VsanHostNodeState")
@XmlEnum
public enum VsanHostNodeState {

    @XmlEnumValue("error")
    ERROR("error"),
    @XmlEnumValue("disabled")
    DISABLED("disabled"),
    @XmlEnumValue("agent")
    AGENT("agent"),
    @XmlEnumValue("master")
    MASTER("master"),
    @XmlEnumValue("backup")
    BACKUP("backup"),
    @XmlEnumValue("starting")
    STARTING("starting"),
    @XmlEnumValue("stopping")
    STOPPING("stopping"),
    @XmlEnumValue("enteringMaintenanceMode")
    ENTERING_MAINTENANCE_MODE("enteringMaintenanceMode"),
    @XmlEnumValue("exitingMaintenanceMode")
    EXITING_MAINTENANCE_MODE("exitingMaintenanceMode"),
    @XmlEnumValue("decommissioning")
    DECOMMISSIONING("decommissioning");
    private final String value;

    VsanHostNodeState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VsanHostNodeState fromValue(String v) {
        for (VsanHostNodeState c: VsanHostNodeState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
