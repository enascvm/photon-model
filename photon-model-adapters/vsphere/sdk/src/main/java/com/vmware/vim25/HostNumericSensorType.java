
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostNumericSensorType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostNumericSensorType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="fan"/&gt;
 *     &lt;enumeration value="power"/&gt;
 *     &lt;enumeration value="temperature"/&gt;
 *     &lt;enumeration value="voltage"/&gt;
 *     &lt;enumeration value="other"/&gt;
 *     &lt;enumeration value="processor"/&gt;
 *     &lt;enumeration value="memory"/&gt;
 *     &lt;enumeration value="storage"/&gt;
 *     &lt;enumeration value="systemBoard"/&gt;
 *     &lt;enumeration value="battery"/&gt;
 *     &lt;enumeration value="bios"/&gt;
 *     &lt;enumeration value="cable"/&gt;
 *     &lt;enumeration value="watchdog"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostNumericSensorType")
@XmlEnum
public enum HostNumericSensorType {

    @XmlEnumValue("fan")
    FAN("fan"),
    @XmlEnumValue("power")
    POWER("power"),
    @XmlEnumValue("temperature")
    TEMPERATURE("temperature"),
    @XmlEnumValue("voltage")
    VOLTAGE("voltage"),
    @XmlEnumValue("other")
    OTHER("other"),
    @XmlEnumValue("processor")
    PROCESSOR("processor"),
    @XmlEnumValue("memory")
    MEMORY("memory"),
    @XmlEnumValue("storage")
    STORAGE("storage"),
    @XmlEnumValue("systemBoard")
    SYSTEM_BOARD("systemBoard"),
    @XmlEnumValue("battery")
    BATTERY("battery"),
    @XmlEnumValue("bios")
    BIOS("bios"),
    @XmlEnumValue("cable")
    CABLE("cable"),
    @XmlEnumValue("watchdog")
    WATCHDOG("watchdog");
    private final String value;

    HostNumericSensorType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostNumericSensorType fromValue(String v) {
        for (HostNumericSensorType c: HostNumericSensorType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
