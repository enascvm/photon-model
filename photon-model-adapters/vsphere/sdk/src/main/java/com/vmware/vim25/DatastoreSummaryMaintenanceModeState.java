
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DatastoreSummaryMaintenanceModeState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="DatastoreSummaryMaintenanceModeState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="normal"/&gt;
 *     &lt;enumeration value="enteringMaintenance"/&gt;
 *     &lt;enumeration value="inMaintenance"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "DatastoreSummaryMaintenanceModeState")
@XmlEnum
public enum DatastoreSummaryMaintenanceModeState {

    @XmlEnumValue("normal")
    NORMAL("normal"),
    @XmlEnumValue("enteringMaintenance")
    ENTERING_MAINTENANCE("enteringMaintenance"),
    @XmlEnumValue("inMaintenance")
    IN_MAINTENANCE("inMaintenance");
    private final String value;

    DatastoreSummaryMaintenanceModeState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static DatastoreSummaryMaintenanceModeState fromValue(String v) {
        for (DatastoreSummaryMaintenanceModeState c: DatastoreSummaryMaintenanceModeState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
