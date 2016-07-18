
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PerformanceManagerUnit.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PerformanceManagerUnit"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="percent"/&gt;
 *     &lt;enumeration value="kiloBytes"/&gt;
 *     &lt;enumeration value="megaBytes"/&gt;
 *     &lt;enumeration value="megaHertz"/&gt;
 *     &lt;enumeration value="number"/&gt;
 *     &lt;enumeration value="microsecond"/&gt;
 *     &lt;enumeration value="millisecond"/&gt;
 *     &lt;enumeration value="second"/&gt;
 *     &lt;enumeration value="kiloBytesPerSecond"/&gt;
 *     &lt;enumeration value="megaBytesPerSecond"/&gt;
 *     &lt;enumeration value="watt"/&gt;
 *     &lt;enumeration value="joule"/&gt;
 *     &lt;enumeration value="teraBytes"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PerformanceManagerUnit")
@XmlEnum
public enum PerformanceManagerUnit {

    @XmlEnumValue("percent")
    PERCENT("percent"),
    @XmlEnumValue("kiloBytes")
    KILO_BYTES("kiloBytes"),
    @XmlEnumValue("megaBytes")
    MEGA_BYTES("megaBytes"),
    @XmlEnumValue("megaHertz")
    MEGA_HERTZ("megaHertz"),
    @XmlEnumValue("number")
    NUMBER("number"),
    @XmlEnumValue("microsecond")
    MICROSECOND("microsecond"),
    @XmlEnumValue("millisecond")
    MILLISECOND("millisecond"),
    @XmlEnumValue("second")
    SECOND("second"),
    @XmlEnumValue("kiloBytesPerSecond")
    KILO_BYTES_PER_SECOND("kiloBytesPerSecond"),
    @XmlEnumValue("megaBytesPerSecond")
    MEGA_BYTES_PER_SECOND("megaBytesPerSecond"),
    @XmlEnumValue("watt")
    WATT("watt"),
    @XmlEnumValue("joule")
    JOULE("joule"),
    @XmlEnumValue("teraBytes")
    TERA_BYTES("teraBytes");
    private final String value;

    PerformanceManagerUnit(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static PerformanceManagerUnit fromValue(String v) {
        for (PerformanceManagerUnit c: PerformanceManagerUnit.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
